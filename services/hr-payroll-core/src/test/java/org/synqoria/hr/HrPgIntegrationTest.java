package org.synqoria.hr;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.synqoria.hr.Db.Reply;
import org.synqoria.hr.PayrollService.*;
import org.synqoria.hr.StaffService.*;
import org.synqoria.hr.TimeService.*;
import org.synqoria.hr.domain.Domain.*;
import tools.jackson.databind.json.JsonMapper;

/**
 * Real PostgreSQL integration (disposable 18.x, restricted non-owner runtime role, forced RLS).
 * Opt-in: SYNQORIA_HR_PG_TEST=1. Every person, amount, rate and account here is a SYNTHETIC fixture.
 */
class HrPgIntegrationTest {
 static String url,adminUrl,adminUser,adminPassword,runtimeUser="synqoria_hr_runtime",runtimePassword="m24-ci-only";
 static class MutableClock extends Clock {
  volatile Instant now=Instant.parse("2026-11-07T06:00:00Z");
  public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now;}
 }
 MutableClock clock;Db db;StaffService staff;TimeService time;PayrollService payroll;JdbcTemplate admin;DataSource runtimeDs;
 final UUID cell=UUID.randomUUID(),facility=UUID.randomUUID();
 // one distinct identity per role so every maker-checker rule is exercised
 final UUID officerId=UUID.randomUUID(),managerId=UUID.randomUUID(),rosterMgrId=UUID.randomUUID(),rosterRevId=UUID.randomUUID(),prepId=UUID.randomUUID(),approverId=UUID.randomUUID(),
  rulesMakerId=UUID.randomUUID(),rulesCheckerId=UUID.randomUUID(),instructorId=UUID.randomUUID(),confirmerId=UUID.randomUUID(),reconcilerId=UUID.randomUUID(),attId=UUID.randomUUID(),attRevId=UUID.randomUUID();
 Actor officer,officerConf,manager,rosterMgr,rosterRev,prep,approver,rulesMaker,rulesChecker,instructor,confirmer,reconciler,attendance,attReviewer,exporter,reader;
 UUID dept,pos;
 static final String EMP="SYNTH-EMP",CTRY="CM",CUR="XAF";

 @BeforeAll static void env(){
  assumeTrue("1".equals(System.getenv("SYNQORIA_HR_PG_TEST")),"set SYNQORIA_HR_PG_TEST=1 with disposable PostgreSQL");
  url=System.getenv("SYNQORIA_HR_TEST_JDBC_URL");adminUrl=System.getenv().getOrDefault("SYNQORIA_HR_TEST_ADMIN_JDBC_URL",url);
  adminUser=System.getenv("SYNQORIA_HR_TEST_ADMIN_USER");adminPassword=System.getenv("SYNQORIA_HR_TEST_ADMIN_PASSWORD");
 }
 @BeforeEach void setUp()throws Exception{
  DriverManagerDataSource ads=new DriverManagerDataSource(adminUrl,adminUser,adminPassword);admin=new JdbcTemplate(ads);
  admin.execute("DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_hr_runtime') THEN CREATE ROLE synqoria_hr_runtime LOGIN NOINHERIT NOSUPERUSER NOCREATEROLE NOCREATEDB NOREPLICATION NOBYPASSRLS PASSWORD '"+runtimePassword+"'; END IF; END $$");
  admin.execute("DROP SCHEMA IF EXISTS synqoria_hr CASCADE");
  try(Connection c=ads.getConnection();Statement s=c.createStatement()){s.execute(Files.readString(Path.of("db","migration","V001__m24_hr_payroll.sql")));}
  DriverManagerDataSource rds=new DriverManagerDataSource(url,runtimeUser,runtimePassword);runtimeDs=rds;
  clock=new MutableClock();
  db=new Db(new JdbcTemplate(rds),JsonMapper.builder().build(),new DataSourceTransactionManager(rds),clock,"synqoria_hr_runtime","UTC");
  staff=new StaffService(db);time=new TimeService(db,11);payroll=new PayrollService(db,time);
  officer=a(officerId,Perm.STAFF_MANAGE,Perm.CONTRACT_MANAGE,Perm.STAFF_READ,Perm.COMP_READ);
  officerConf=a(officerId,Perm.STAFF_MANAGE,Perm.CONTRACT_MANAGE,Perm.STAFF_READ,Perm.COMP_READ,Perm.CONFIDENTIAL_READ);
  manager=a(managerId,Perm.STAFF_APPROVE,Perm.CONTRACT_APPROVE,Perm.STAFF_READ,Perm.COMP_READ,Perm.LEAVE_DECIDE,Perm.LEAVE_READ,Perm.CONFIDENTIAL_READ);
  rosterMgr=a(rosterMgrId,Perm.ROSTER_MANAGE,Perm.ROSTER_READ,Perm.LEAVE_REQUEST,Perm.LEAVE_READ);
  rosterRev=a(rosterRevId,Perm.ROSTER_REVIEW,Perm.ROSTER_READ,Perm.OVERTIME_AUTHORIZE);
  prep=a(prepId,Perm.PAYROLL_PREPARE,Perm.PAYROLL_READ);
  approver=a(approverId,Perm.PAYROLL_APPROVE,Perm.PAYROLL_READ);
  rulesMaker=a(rulesMakerId,Perm.RULES_MANAGE,Perm.PAYROLL_READ);rulesChecker=a(rulesCheckerId,Perm.RULES_APPROVE,Perm.PAYROLL_READ);
  instructor=a(instructorId,Perm.PAYMENT_INSTRUCT,Perm.PAYROLL_READ);confirmer=a(confirmerId,Perm.PAYMENT_CONFIRM,Perm.PAYROLL_READ);reconciler=a(reconcilerId,Perm.RECONCILE,Perm.PAYROLL_READ);
  attendance=a(attId,Perm.ATTENDANCE_CAPTURE);attReviewer=a(attRevId,Perm.ATTENDANCE_REVIEW);
  exporter=a(UUID.randomUUID(),Perm.EXPORT,Perm.PAYROLL_READ);
  reader=a(UUID.randomUUID(),Perm.STAFF_READ,Perm.PAYROLL_READ,Perm.ROSTER_READ,Perm.LEAVE_READ);
  dept=id(body(ok(staff.createDepartment(officer,k(),new DeptRequest("WARD1","Ward 1")))));
  pos=id(body(ok(staff.createPosition(officer,k(),new PositionRequest("NURSE","Nurse",dept)))));
 }
 Actor a(UUID id,String...p){return new Actor(id,cell,facility,Set.copyOf(Arrays.stream(p).filter(Objects::nonNull).toList()));}
 static UUID k(){return UUID.randomUUID();}
 @SuppressWarnings("unchecked") Map<String,Object> body(Reply r){return (Map<String,Object>)r.body();}
 Reply ok(Reply r){assertTrue(r.status()<300,r.status()+" "+r.body());return r;}
 static UUID id(Map<String,Object> m){return m.get("id") instanceof UUID u?u:UUID.fromString((String)m.get("id"));}
 static long ver(Map<String,Object> m){return ((Number)m.get("version")).longValue();}
 String code(Runnable r){try{r.run();return "NO_ERROR";}catch(Violation v){return v.code();}}
 static final LocalDate HIRE=LocalDate.parse("2026-01-01");

 // ---------------------------------------------------------------- fixtures
 UUID activeStaff(String number,String base){
  Map<String,Object> s=body(ok(staff.onboard(officer,k(),new OnboardRequest(number,"Given"+number,"Family"+number,dept,pos,HIRE,"STANDARD",null))));
  Map<String,Object> ch=body(ok(staff.requestChange(officer,k(),id(s),new ChangeRequest(ver(s),"ACTIVATE",HIRE,"start of employment",null,null))));
  ok(staff.decideChange(manager,k(),id(ch),new StaffService.Decision(ver(ch),true,null)));
  Map<String,Object> c=body(ok(staff.draftContract(officer,k(),id(s),new ContractRequest(EMP,CTRY,CUR,"MONTHLY",base,pos,HIRE,null))));
  ok(staff.decideContract(manager,k(),id(c),new StaffService.Decision(ver(c),true,null)));
  ok(staff.setPaymentAccount(officer,k(),id(s),new AccountRequest("vault:tok:"+number+"00000","****"+number)));
  return id(s);
 }
 Map<String,Object> synthRules(){
  return Map.of("schemaVersion",1,"currency",CUR,"rounding",Map.of("mode","HALF_UP","scale",0),"monthlyHours","160","components",List.of(
   Map.of("code","HOUSING","kind","EARNING","type","FIXED","amount","50000"),
   Map.of("code","OT","kind","EARNING","type","OVERTIME","multiplier","1.5"),
   Map.of("code","SYNTH_TAX","kind","DEDUCTION","type","PERCENT_OF_GROSS","rate","0.10"),
   Map.of("code","SYNTH_SOCIAL","kind","DEDUCTION","type","PERCENT_OF_BASIC","rate","0.04","cap","20000")));
 }
 UUID approvedRules(){
  Map<String,Object> r=body(ok(payroll.createRuleSet(rulesMaker,k(),new RuleSetRequest(CTRY,EMP,CUR,LocalDate.parse("2026-01-01"),synthRules(),"SYNTHETIC test fixture, not a statutory instrument"))));
  ok(payroll.decideRuleSet(rulesChecker,k(),id(r),new PayrollService.Decision(ver(r),true,null)));
  return id(r);
 }
 UUID openPeriod(){
  return id(body(ok(payroll.createPeriod(prep,k(),new PeriodRequest(EMP,CTRY,CUR,LocalDate.parse("2026-10-01"),LocalDate.parse("2026-10-31"))))));
 }
 Map<String,Object> submittedRun(UUID period){
  Map<String,Object> run=body(ok(payroll.calculate(prep,k(),period)));
  return body(ok(payroll.submit(prep,k(),id(run),new PayrollService.Transition(ver(run)))));
 }
 Map<String,Object> approvedRun(String...bases){
  for(int i=0;i<bases.length;i++)activeStaff("S"+(i+1),bases[i]);
  approvedRules();UUID p=openPeriod();Map<String,Object> sub=submittedRun(p);
  return body(ok(payroll.decide(approver,k(),id(sub),new PayrollService.Decision(ver(sub),true,null))));
 }
 String st(String table,UUID id){return admin.queryForObject("SELECT status FROM synqoria_hr."+table+" WHERE id=?",String.class,id);}
 long count(String sql,Object...args){return admin.queryForObject(sql,Long.class,args);}

 // ---------------------------------------------------------------- workforce, lifecycle, security of records
 @Test void lifecycleIsDatedMakerCheckerAndEndsContractsOnTermination(){
  UUID s=activeStaff("S1","400000");
  Map<String,Object> cur=staff.get(manager,s);
  assertEquals("ACTIVE",cur.get("status"));
  Map<String,Object> sus=body(ok(staff.requestChange(officer,k(),s,new ChangeRequest(ver(cur),"SUSPEND",LocalDate.parse("2026-11-07"),"investigation",null,null))));
  assertEquals("approver_must_be_independent",code(()->staff.decideChange(a(officerId,Perm.STAFF_APPROVE),k(),id(sus),new StaffService.Decision(ver(sus),true,null))));
  assertEquals("permission_denied",code(()->staff.decideChange(officer,k(),id(sus),new StaffService.Decision(ver(sus),true,null))));
  assertEquals("duplicate_record",code(()->staff.requestChange(officer,k(),s,new ChangeRequest(ver(cur),"SUSPEND",LocalDate.parse("2026-11-07"),"again",null,null))));
  ok(staff.decideChange(manager,k(),id(sus),new StaffService.Decision(ver(sus),true,null)));
  assertEquals("SUSPENDED",staff.get(manager,s).get("status"));
  // invalid transitions and dates
  Map<String,Object> now=staff.get(manager,s);
  assertEquals("invalid_transition",code(()->staff.requestChange(officer,k(),s,new ChangeRequest(ver(now),"SUSPEND",LocalDate.parse("2026-11-07"),"x",null,null))));
  assertEquals("effective_date_conflict",code(()->staff.requestChange(officer,k(),s,new ChangeRequest(ver(now),"REINSTATE",LocalDate.parse("2026-06-01"),"x",null,null))),"before the last change");
  assertEquals("stale_version",code(()->staff.requestChange(officer,k(),s,new ChangeRequest(1,"REINSTATE",LocalDate.parse("2026-11-07"),"x",null,null))));
  // reinstate then terminate in the future: scheduled until apply-due
  Map<String,Object> re=body(ok(staff.requestChange(officer,k(),s,new ChangeRequest(ver(now),"REINSTATE",LocalDate.parse("2026-11-07"),"cleared",null,null))));
  ok(staff.decideChange(manager,k(),id(re),new StaffService.Decision(ver(re),true,null)));
  Map<String,Object> now2=staff.get(manager,s);
  Map<String,Object> term=body(ok(staff.requestChange(officer,k(),s,new ChangeRequest(ver(now2),"TERMINATE",LocalDate.parse("2026-12-31"),"resignation",null,null))));
  assertEquals("SCHEDULED",body(ok(staff.decideChange(manager,k(),id(term),new StaffService.Decision(ver(term),true,null)))).get("status"));
  assertEquals("ACTIVE",staff.get(manager,s).get("status"),"future-dated change is not applied yet");
  assertEquals(0,((Number)body(ok(staff.applyDue(manager,k()))).get("applied")).intValue());
  clock.now=Instant.parse("2027-01-02T06:00:00Z");
  assertEquals(1,((Number)body(ok(staff.applyDue(manager,k()))).get("applied")).intValue());
  assertEquals("TERMINATED",staff.get(manager,s).get("status"));
  assertEquals("ENDED",admin.queryForObject("SELECT status FROM synqoria_hr.contracts WHERE staff_id=?",String.class,s));
  assertEquals("2026-12-31",admin.queryForObject("SELECT termination_date::text FROM synqoria_hr.staff WHERE id=?",String.class,s));
  // rehire must be after termination
  Map<String,Object> t=staff.get(manager,s);
  assertEquals("effective_date_conflict",code(()->staff.requestChange(officer,k(),s,new ChangeRequest(ver(t),"REHIRE",LocalDate.parse("2026-12-31"),"x",null,null))));
  Map<String,Object> rh=body(ok(staff.requestChange(officer,k(),s,new ChangeRequest(ver(t),"REHIRE",LocalDate.parse("2027-02-01"),"returned",null,null))));
  clock.now=Instant.parse("2027-02-02T06:00:00Z");
  ok(staff.decideChange(manager,k(),id(rh),new StaffService.Decision(ver(rh),true,null)));
  assertEquals("ACTIVE",staff.get(manager,s).get("status"));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_hr.staff SET status='TERMINATED' WHERE id=?",s),"terminated needs a termination date (CHECK)");
  assertEquals(0L,count("SELECT count(*) FROM synqoria_hr.audit_events e LEFT JOIN synqoria_hr.outbox o USING(event_id) WHERE o.event_id IS NULL"));
 }
 @Test void contractsAreVersionedImmutableAndIndependentlyApproved(){
  UUID s=activeStaff("S1","400000");
  Map<String,Object> c2=body(ok(staff.draftContract(officer,k(),s,new ContractRequest(EMP,CTRY,CUR,"MONTHLY","450000",pos,LocalDate.parse("2026-07-01"),null))));
  assertEquals(2,((Number)c2.get("contractVersion")).intValue());
  assertEquals("approver_must_be_independent",code(()->staff.decideContract(a(officerId,Perm.CONTRACT_APPROVE),k(),id(c2),new StaffService.Decision(ver(c2),true,null))));
  ok(staff.decideContract(manager,k(),id(c2),new StaffService.Decision(ver(c2),true,null)));
  assertEquals(1L,count("SELECT count(*) FROM synqoria_hr.contracts WHERE status='ACTIVE'"));
  assertEquals(1L,count("SELECT count(*) FROM synqoria_hr.contracts WHERE status='SUPERSEDED'"));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_hr.contracts SET base_amount=1 WHERE id=?",id(c2)));
  assertThrows(DataAccessException.class,()->admin.update("DELETE FROM synqoria_hr.contracts"));
  assertThrows(DataAccessException.class,()->admin.update("INSERT INTO synqoria_hr.contracts(id,cell_id,facility_id,staff_id,contract_version,employer_code,country_code,currency,pay_basis,base_amount,position_id,effective_from,status,created_by,approved_by,version) "+
   "SELECT gen_random_uuid(),cell_id,facility_id,staff_id,99,employer_code,country_code,currency,pay_basis,base_amount,position_id,effective_from,'ACTIVE',created_by,approved_by,1 FROM synqoria_hr.contracts WHERE status='ACTIVE'"),"two ACTIVE contracts");
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_hr.contracts SET approved_by=created_by WHERE id=?",id(c2)));
 }
 @Test void compensationAndConfidentialRecordsAreProtectedAtApiAndDatabaseLevel()throws Exception{
  UUID s=activeStaff("S1","400000");
  assertFalse(staff.get(reader,s).containsKey("contracts"),"no compensation without comp_read");
  assertTrue(staff.get(officer,s).containsKey("contracts"));
  var page=staff.list(reader,null,null,null,0,25);
  assertFalse(page.toString().contains("400000"),"directory never shows pay");
  assertFalse(db.encode(admin.queryForList("SELECT details::text FROM synqoria_hr.audit_events")).contains("400000"),"amounts never reach audit/outbox");
  assertFalse(db.encode(admin.queryForList("SELECT payload::text FROM synqoria_hr.outbox")).contains("400000"));
  // confidential classification
  assertEquals("permission_denied",code(()->staff.onboard(officer,k(),new OnboardRequest("EXEC1","A","B",dept,pos,HIRE,"CONFIDENTIAL",null))));
  Map<String,Object> ex=body(ok(staff.onboard(officerConf,k(),new OnboardRequest("EXEC1","Exec","Utive",dept,pos,HIRE,"CONFIDENTIAL",null))));
  assertEquals(2L,((Number)staff.list(officerConf,null,null,null,0,50).get("total")).longValue());
  assertEquals(1L,((Number)staff.list(officer,null,null,null,0,50).get("total")).longValue(),"hidden by row-level security");
  assertEquals("not_found",code(()->staff.get(officer,id(ex))));
  try(Connection c=runtimeDs.getConnection()){
   c.setAutoCommit(false);
   try(PreparedStatement ps=c.prepareStatement("SELECT set_config('synqoria.cell',?,true),set_config('synqoria.facility',?,true),set_config('synqoria.hr_conf','off',true)")){ps.setString(1,cell.toString());ps.setString(2,facility.toString());ps.execute();}
   try(Statement q=c.createStatement();ResultSet rs=q.executeQuery("SELECT count(*) FROM synqoria_hr.staff WHERE classification='CONFIDENTIAL'")){rs.next();assertEquals(0,rs.getInt(1));}
   c.rollback();
  }
  assertEquals("invalid_account",code(()->staff.setPaymentAccount(officer,k(),s,new AccountRequest("1234 5678 9012","****"))),"raw account numbers are refused; only vault tokens");
  assertEquals(0L,count("SELECT count(*) FROM synqoria_hr.payment_accounts WHERE account_token ~ '^[0-9 ]+$'"));
 }

 // ---------------------------------------------------------------- payroll engine and controls
 @Test void payrollCalculatesExactlyAndRunResultsAreImmutable(){
  Map<String,Object> run=approvedRun("400000","250000.50");
  assertEquals("APPROVED",run.get("status"));
  var lines=payroll.getRun(prep,id(run));
  @SuppressWarnings("unchecked") List<Map<String,Object>> ls=(List<Map<String,Object>>)lines.get("lines");
  // basic 400000 + housing 50000 = 450000 gross; tax 10% of gross = 45000; social 4% of basic = 16000 (below cap) => net 389000
  Map<String,Object> s1=ls.stream().filter(l->"S1".equals(l.get("staffNumber"))).findFirst().orElseThrow();
  assertEquals(0,new BigDecimal("450000").compareTo((BigDecimal)s1.get("gross")));
  assertEquals(0,new BigDecimal("389000").compareTo((BigDecimal)s1.get("net")));
  // 250000.50: scale 0 half-up per line => basic 250001, +50000 = 300001, tax 30000, social min(10000,20000)=10000 => net 260001
  Map<String,Object> s2=ls.stream().filter(l->"S2".equals(l.get("staffNumber"))).findFirst().orElseThrow();
  assertEquals(0,new BigDecimal("260001").compareTo((BigDecimal)s2.get("net")));
  assertEquals(0,new BigDecimal("649001").compareTo((BigDecimal)run.get("totalNet")));
  assertEquals(2,((Number)run.get("staffCount")).intValue());
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_hr.payroll_lines SET net=1"));
  assertThrows(DataAccessException.class,()->admin.update("DELETE FROM synqoria_hr.payroll_lines"));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_hr.payroll_runs SET total_net=1 WHERE id=?",id(run)));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_hr.payroll_runs SET status='CALCULATED' WHERE id=?",id(run)),"no backwards state change");
  assertThrows(DataAccessException.class,()->admin.update("DELETE FROM synqoria_hr.payroll_runs"));
  assertEquals(0L,count("SELECT count(*) FROM synqoria_hr.payroll_lines WHERE net<>gross-total_deductions"));
 }
 @Test void rulesAreVersionedApprovedInDataNeverHardCoded(){
  activeStaff("S1","400000");UUID p=openPeriod();
  assertEquals("rules_not_approved",code(()->payroll.calculate(prep,k(),p)),"no approved rules => no payroll, no assumed rates");
  Map<String,Object> r=body(ok(payroll.createRuleSet(rulesMaker,k(),new RuleSetRequest(CTRY,EMP,CUR,LocalDate.parse("2026-01-01"),synthRules(),"synthetic"))));
  assertEquals("rules_not_approved",code(()->payroll.calculate(prep,k(),p)),"a DRAFT rule set is not usable");
  assertEquals("approver_must_be_independent",code(()->payroll.decideRuleSet(a(rulesMakerId,Perm.RULES_APPROVE),k(),id(r),new PayrollService.Decision(ver(r),true,null))));
  assertEquals("invalid_rule_set",code(()->payroll.createRuleSet(rulesMaker,k(),new RuleSetRequest(CTRY,EMP,"EUR",LocalDate.parse("2026-01-01"),synthRules(),"x"))),"currency mismatch inside rules");
  ok(payroll.decideRuleSet(rulesChecker,k(),id(r),new PayrollService.Decision(ver(r),true,null)));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_hr.rule_sets SET rules='{}'::jsonb WHERE id=?",id(r)));
  Map<String,Object> r2=body(ok(payroll.createRuleSet(rulesMaker,k(),new RuleSetRequest(CTRY,EMP,CUR,LocalDate.parse("2026-12-01"),synthRules(),"synthetic v2"))));
  assertEquals(2,((Number)r2.get("ruleVersion")).intValue());
  assertEquals("invalid_rule_set",code(()->payroll.createRuleSet(rulesMaker,k(),new RuleSetRequest(CTRY,EMP,CUR,LocalDate.parse("2026-12-01"),Map.of("schemaVersion",1,"currency",CUR,"rounding",Map.of("mode","HALF_UP","scale",0),"monthlyHours","160",
   "components",List.of(Map.of("code","X","kind","EARNING","type","FIXED","amount",12.5))),"float money"))));
  // a second country/employer/currency coexists with its own rules
  Map<String,Object> ke=body(ok(payroll.createRuleSet(rulesMaker,k(),new RuleSetRequest("KE","OTHER-EMP","KES",LocalDate.parse("2026-01-01"),
   Map.of("schemaVersion",1,"currency","KES","rounding",Map.of("mode","HALF_EVEN","scale",2),"monthlyHours","173.33","components",List.of(Map.of("code","T","kind","DEDUCTION","type","PERCENT_OF_GROSS","rate","0.05"))),"synthetic KE"))));
  assertEquals(1,((Number)ke.get("ruleVersion")).intValue(),"versions are per country and employer");
 }
 @Test void makerCheckerRunApprovalLocksPeriodAndSeparatesDuties(){
  activeStaff("S1","400000");approvedRules();UUID p=openPeriod();
  Map<String,Object> run=body(ok(payroll.calculate(prep,k(),p)));
  assertEquals("run_already_live",code(()->payroll.calculate(prep,k(),p)));
  assertEquals("invalid_transition",code(()->payroll.decide(approver,k(),id(run),new PayrollService.Decision(ver(run),true,null))),"must be submitted first");
  Map<String,Object> sub=body(ok(payroll.submit(prep,k(),id(run),new PayrollService.Transition(ver(run)))));
  Actor prepAsApprover=a(prepId,Perm.PAYROLL_APPROVE);
  assertEquals("approver_must_be_independent",code(()->payroll.decide(prepAsApprover,k(),id(sub),new PayrollService.Decision(ver(sub),true,null))));
  assertEquals("permission_denied",code(()->payroll.decide(prep,k(),id(sub),new PayrollService.Decision(ver(sub),true,null))));
  // tampering with stored lines after calculation is detected at approval (batch hash)
  admin.execute("ALTER TABLE synqoria_hr.payroll_lines DISABLE TRIGGER payroll_lines_immutable");
  admin.update("UPDATE synqoria_hr.payroll_lines SET gross=gross+1,net=net+1 WHERE run_id=?",id(sub));
  assertEquals("evidence_mismatch",code(()->payroll.decide(approver,k(),id(sub),new PayrollService.Decision(ver(sub),true,null))));
  admin.update("UPDATE synqoria_hr.payroll_lines SET gross=gross-1,net=net-1 WHERE run_id=?",id(sub));
  admin.execute("ALTER TABLE synqoria_hr.payroll_lines ENABLE TRIGGER payroll_lines_immutable");
  Map<String,Object> ap=body(ok(payroll.decide(approver,k(),id(sub),new PayrollService.Decision(ver(sub),true,null))));
  assertEquals("APPROVED",ap.get("status"));
  assertEquals("LOCKED",st("pay_periods",p));
  assertEquals("period_locked",code(()->payroll.calculate(prep,k(),p)));
  assertEquals("period_locked",code(()->payroll.requestAdjustment(prep,k(),new AdjustmentRequest(p,admin.queryForObject("SELECT id FROM synqoria_hr.staff",UUID.class),"BONUS","1000","late"))));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_hr.payroll_runs SET status='SUBMITTED' WHERE id=?",id(ap)));
  // reversal reopens the period (no money confirmed yet) and allows a fresh run
  Map<String,Object> rev=body(ok(payroll.reverse(approver,k(),id(ap),new ReverseRequest(ver(ap),"input error"))));
  assertEquals("REVERSED",rev.get("status"));assertEquals("OPEN",st("pay_periods",p));
  Map<String,Object> second=body(ok(payroll.calculate(prep,k(),p)));
  assertEquals(2,((Number)second.get("runNo")).intValue());
  Map<String,Object> r3=body(ok(payroll.submit(prep,k(),id(second),new PayrollService.Transition(ver(second)))));
  assertEquals("reason_required",code(()->payroll.decide(approver,k(),id(r3),new PayrollService.Decision(ver(r3),false,""))));
  assertEquals("REJECTED",body(ok(payroll.decide(approver,k(),id(r3),new PayrollService.Decision(ver(r3),false,"wrong overtime")))).get("status"));
  assertEquals("OPEN",st("pay_periods",p));
 }
 @Test void adjustmentsUnpaidLeaveAndOvertimeFlowIntoThePayslipWithIndependentApproval(){
  UUID s=activeStaff("S1","400000");approvedRules();UUID p=openPeriod();
  Map<String,Object> adj=body(ok(payroll.requestAdjustment(prep,k(),new AdjustmentRequest(p,s,"ARREARS","20000","Q3 correction"))));
  assertEquals("approver_must_be_independent",code(()->payroll.decideAdjustment(a(prepId,Perm.PAYROLL_APPROVE),k(),id(adj),new PayrollService.Decision(ver(adj),true,null))));
  ok(payroll.decideAdjustment(approver,k(),id(adj),new PayrollService.Decision(ver(adj),true,null)));
  Map<String,Object> pend=body(ok(payroll.requestAdjustment(prep,k(),new AdjustmentRequest(p,s,"BONUS","99999","not yet approved"))));
  // unpaid leave: Mon 12 and Tue 13 Oct 2026
  Map<String,Object> lt=body(ok(time.createLeaveType(manager,k(),new LeaveTypeRequest("UNPAID",false,"0","0"))));
  Map<String,Object> lr=body(ok(time.requestLeave(rosterMgr,k(),new LeaveRequestIn(s,id(lt),LocalDate.parse("2026-10-12"),LocalDate.parse("2026-10-13")))));
  ok(time.decideLeave(manager,k(),id(lr),new TimeService.Decision(ver(lr),true,null)));
  Map<String,Object> run=body(ok(payroll.calculate(prep,k(),p)));
  @SuppressWarnings("unchecked") var line=((List<Map<String,Object>>)payroll.getRun(prep,id(run)).get("lines")).getFirst();
  String bd=line.get("breakdown").toString();
  assertTrue(bd.contains("ADJ_ARREARS")&&bd.contains("20000"),bd);
  assertFalse(bd.contains("99999"),"unapproved adjustments never enter pay");
  assertTrue(bd.contains("UNPAID_LEAVE")&&bd.contains("36364"),"400000 x 2/22 = 36363.64 -> 36364: "+bd);
  assertNotNull(pend);
 }
 @Test void exportNeutralisesFormulaInjectionAndIsGatedAndAudited(){
  activeStaff("S1","400000");
  admin.update("UPDATE synqoria_hr.staff SET family_name='=cmd|x' WHERE staff_number='S1'");
  approvedRules();UUID p=openPeriod();Map<String,Object> sub=submittedRun(p);
  assertEquals("invalid_transition",code(()->payroll.exportRun(exporter,k(),id(sub))),"only approved runs export");
  Map<String,Object> ap=body(ok(payroll.decide(approver,k(),id(sub),new PayrollService.Decision(ver(sub),true,null))));
  assertEquals("permission_denied",code(()->payroll.exportRun(prep,k(),id(ap))));
  String csv=(String)body(ok(payroll.exportRun(exporter,k(),id(ap)))).get("content");
  assertTrue(csv.contains("'=cmd|x"),csv);assertFalse(csv.contains(",=cmd"));
  assertEquals(1L,count("SELECT count(*) FROM synqoria_hr.audit_events WHERE action='EXPORT'"));
 }

 // ---------------------------------------------------------------- payments and reconciliation
 @Test void paymentsNeedIndependentExternalEvidenceAndReconcileEmployeeByEmployee(){
  Map<String,Object> run=approvedRun("400000","250000");
  assertEquals("permission_denied",code(()->payroll.instruct(prep,k(),id(run),new PayrollService.Transition(ver(run)))));
  Map<String,Object> ins=body(ok(payroll.instruct(instructor,k(),id(run),new PayrollService.Transition(ver(run)))));
  assertEquals(2,((Number)ins.get("created")).intValue());
  assertEquals("payment_exists",code(()->payroll.instruct(instructor,k(),id(run),new PayrollService.Transition(ver(run)))),"no second payment batch for the same period");
  @SuppressWarnings("unchecked") List<Map<String,Object>> items=(List<Map<String,Object>>)payroll.listInstructions(prep,id(run)).get("items");
  assertEquals(List.of("INSTRUCTED","INSTRUCTED"),items.stream().map(i->(String)i.get("status")).toList());
  // an internal change can never claim funds moved
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_hr.payment_instructions SET status='CONFIRMED'"),"CONFIRMED needs evidence");
  Map<String,Object> i1=items.get(0),i2=items.get(1);
  Actor instructorAsConfirmer=a(instructorId,Perm.PAYMENT_CONFIRM);
  assertEquals("confirmer_must_be_independent",code(()->payroll.confirm(instructorAsConfirmer,k(),id(i1),new Confirmation(true,"PROV-0001-A",i1.get("amount").toString(),"XAF",clock.instant(),null))));
  // reconciliation is incomplete until every employee is independently confirmed
  Map<String,Object> early=body(payroll.reconcile(reconciler,k(),id(run),new PayrollService.Transition(ver(run))));
  assertEquals("reconciliation_incomplete",early.get("error"));assertEquals("APPROVED",st("payroll_runs",id(run)));
  ok(payroll.confirm(confirmer,k(),id(i1),new Confirmation(true,"PROV-0001-A",i1.get("amount").toString(),"XAF",clock.instant(),null)));
  assertEquals("duplicate_record",code(()->payroll.confirm(confirmer,k(),id(i2),new Confirmation(true,"PROV-0001-A",i2.get("amount").toString(),"XAF",clock.instant(),null))),"provider reference is single-use");
  // wrong amount is a recorded MISMATCH, not a success
  Map<String,Object> mm=body(ok(payroll.confirm(confirmer,k(),id(i2),new Confirmation(true,"PROV-0002-B","1.00","XAF",clock.instant(),null))));
  assertEquals("MISMATCH",mm.get("status"));
  var rec=payroll.getReconciliation(reconciler,id(run));
  assertEquals(false,rec.get("reconciled"));assertTrue(rec.get("discrepancies").toString().contains("AMOUNT_MISMATCH"));
  assertEquals("reversal_requires_correction",code(()->payroll.reverse(approver,k(),id(run),new ReverseRequest(ver(run),"too late"))),"money already confirmed");
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_hr.payment_instructions SET amount=1"),"instruction amount is immutable");
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_hr.payment_instructions SET provider_ref='X' WHERE id=?",id(i1)),"evidence is write-once");
 }
 @Test void failedPaymentsCanBeRetriedWithoutEverDoublePaying(){
  Map<String,Object> run=approvedRun("400000");
  ok(payroll.instruct(instructor,k(),id(run),new PayrollService.Transition(ver(run))));
  @SuppressWarnings("unchecked") Map<String,Object> i1=((List<Map<String,Object>>)payroll.listInstructions(prep,id(run)).get("items")).getFirst();
  assertEquals("reason_required",code(()->payroll.confirm(confirmer,k(),id(i1),new Confirmation(false,null,null,null,null,""))));
  assertEquals("FAILED",body(ok(payroll.confirm(confirmer,k(),id(i1),new Confirmation(false,null,null,null,null,"bank rejected account")))).get("status"));
  assertEquals("invalid_transition",code(()->payroll.confirm(confirmer,k(),id(i1),new Confirmation(true,"PROV-LATE-1",i1.get("amount").toString(),"XAF",clock.instant(),null))),"a failed instruction cannot be revived");
  Map<String,Object> retry=body(ok(payroll.instruct(instructor,k(),id(run),new PayrollService.Transition(ver(run)))));
  assertEquals(1,((Number)retry.get("created")).intValue());
  assertEquals(1L,count("SELECT count(*) FROM synqoria_hr.payment_instructions WHERE status='INSTRUCTED'"));
  assertTrue(admin.queryForObject("SELECT reference FROM synqoria_hr.payment_instructions WHERE status='INSTRUCTED'",String.class).endsWith("-R1"));
  // the database independently forbids two live instructions for one employee and period
  assertThrows(DataAccessException.class,()->admin.update("INSERT INTO synqoria_hr.payment_instructions(id,cell_id,facility_id,run_id,staff_id,period_id,reference,amount,currency,account_mask,status,instructed_by,version) "+
   "SELECT gen_random_uuid(),cell_id,facility_id,run_id,staff_id,period_id,'PAY-DUPLICATE-1',amount,currency,account_mask,'INSTRUCTED',instructed_by,1 FROM synqoria_hr.payment_instructions WHERE status='INSTRUCTED'"));
  // full success path ends in RECONCILED
  @SuppressWarnings("unchecked") Map<String,Object> live=((List<Map<String,Object>>)payroll.listInstructions(prep,id(run)).get("items")).stream().filter(x->"INSTRUCTED".equals(x.get("status"))).findFirst().orElseThrow();
  ok(payroll.confirm(confirmer,k(),id(live),new Confirmation(true,"PROV-OK-0001",live.get("amount").toString(),"XAF",clock.instant(),null)));
  Reply done=payroll.reconcile(reconciler,k(),id(run),new PayrollService.Transition(ver(run)));
  assertEquals(200,done.status());assertEquals("RECONCILED",st("payroll_runs",id(run)));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_hr.payroll_runs SET status='APPROVED' WHERE id=?",id(run)));
 }
 @Test void reversalBeforeAnyConfirmationCancelsInstructionsAndReopensThePeriod(){
  Map<String,Object> run=approvedRun("400000");
  ok(payroll.instruct(instructor,k(),id(run),new PayrollService.Transition(ver(run))));
  ok(payroll.reverse(approver,k(),id(run),new ReverseRequest(ver(run),"wrong rule set")));
  assertEquals(0L,count("SELECT count(*) FROM synqoria_hr.payment_instructions WHERE status<>'CANCELLED'"));
  assertEquals("OPEN",admin.queryForObject("SELECT status FROM synqoria_hr.pay_periods",String.class));
 }
 @Test void concurrentApprovalsCalculationsAndPaymentInstructionsHaveExactlyOneWinner()throws Exception{
  activeStaff("S1","400000");approvedRules();UUID p=openPeriod();
  ExecutorService pool=Executors.newFixedThreadPool(2);
  CyclicBarrier gate=new CyclicBarrier(2);
  Callable<String> calc1=()->{gate.await();try{return "OK"+payroll.calculate(prep,k(),p).status();}catch(Violation v){return v.code();}};
  Callable<String> calc2=()->{gate.await();try{return "OK"+payroll.calculate(a(UUID.randomUUID(),Perm.PAYROLL_PREPARE),k(),p).status();}catch(Violation v){return v.code();}};
  Future<String> f1=pool.submit(calc1),f2=pool.submit(calc2);
  List<String> r1=List.of(f1.get(30,TimeUnit.SECONDS),f2.get(30,TimeUnit.SECONDS));
  assertEquals(1,r1.stream().filter(x->x.equals("OK201")).count(),r1.toString());
  assertEquals(1L,count("SELECT count(*) FROM synqoria_hr.payroll_runs WHERE status='CALCULATED'"));
  Map<String,Object> run=payroll.getRun(prep,admin.queryForObject("SELECT id FROM synqoria_hr.payroll_runs",UUID.class));
  Map<String,Object> sub=body(ok(payroll.submit(prep,k(),id(run),new PayrollService.Transition(ver(run)))));
  Actor a2=a(UUID.randomUUID(),Perm.PAYROLL_APPROVE);
  CyclicBarrier gate2=new CyclicBarrier(2);
  Future<String> g1=pool.submit(()->{gate2.await();try{return "OK"+payroll.decide(approver,k(),id(sub),new PayrollService.Decision(ver(sub),true,null)).status();}catch(Violation v){return v.code();}});
  Future<String> g2=pool.submit(()->{gate2.await();try{return "OK"+payroll.decide(a2,k(),id(sub),new PayrollService.Decision(ver(sub),true,null)).status();}catch(Violation v){return v.code();}});
  List<String> r2=List.of(g1.get(30,TimeUnit.SECONDS),g2.get(30,TimeUnit.SECONDS));
  assertEquals(1,r2.stream().filter(x->x.equals("OK200")).count(),r2.toString());
  Map<String,Object> ap=payroll.getRun(prep,id(run));
  Actor i2=a(UUID.randomUUID(),Perm.PAYMENT_INSTRUCT);CyclicBarrier gate3=new CyclicBarrier(2);
  Future<String> h1=pool.submit(()->{gate3.await();try{return "OK"+payroll.instruct(instructor,k(),id(ap),new PayrollService.Transition(ver(ap))).status();}catch(Violation v){return v.code();}});
  Future<String> h2=pool.submit(()->{gate3.await();try{return "OK"+payroll.instruct(i2,k(),id(ap),new PayrollService.Transition(ver(ap))).status();}catch(Violation v){return v.code();}});
  List<String> r3=List.of(h1.get(30,TimeUnit.SECONDS),h2.get(30,TimeUnit.SECONDS));pool.shutdown();
  assertEquals(1,r3.stream().filter(x->x.equals("OK201")).count(),r3.toString());
  assertEquals(1L,count("SELECT count(*) FROM synqoria_hr.payment_instructions"),"never two payments for one employee and period");
 }

 // ---------------------------------------------------------------- rosters, attendance, leave
 @Test void rosterEnforcesConflictsRestCoverageOvertimeAndIndependentPublication(){
  UUID s1=activeStaff("S1","400000"),s2=activeStaff("S2","400000");
  UUID day=UUID.fromString((String)body(ok(time.createTemplate(rosterMgr,k(),new TemplateRequest("DAY","07:00",480)))).get("id"));
  UUID night=UUID.fromString((String)body(ok(time.createTemplate(rosterMgr,k(),new TemplateRequest("NIGHT","19:00",600)))).get("id"));
  ok(time.setCoverage(rosterMgr,k(),new CoverageRequest(dept,day,2)));
  LocalDate mon=LocalDate.parse("2026-11-09");
  Map<String,Object> ro=body(ok(time.createRoster(rosterMgr,k(),new RosterRequest(dept,mon,mon.plusDays(1)))));
  assertEquals("duplicate_record",code(()->time.createRoster(rosterMgr,k(),new RosterRequest(dept,mon,mon.plusDays(2)))));
  ok(time.addShift(rosterMgr,k(),id(ro),new ShiftRequest(day,s1,mon,false)));
  ok(time.addShift(rosterMgr,k(),id(ro),new ShiftRequest(night,s1,mon,false)));     // 4h after the day shift ends: rest violation
  ok(time.addShift(rosterMgr,k(),id(ro),new ShiftRequest(day,null,mon,false)));      // vacancy
  var chk=time.check(rosterMgr,id(ro));
  assertEquals(false,chk.get("ok"));assertTrue(chk.get("problems").toString().contains("REST:"+s1));assertTrue(chk.get("problems").toString().contains("GAP:"+day));
  assertEquals(1L,((Number)chk.get("vacancies")).longValue());
  assertEquals("rest_violation",code(()->time.submit(rosterMgr,k(),id(ro),new TimeService.Transition(1))));
  assertEquals("roster_conflict",code(()->time.addShift(rosterMgr,k(),id(ro),new ShiftRequest(day,s1,mon.plusDays(5),false))),"outside the period");
  // fix: remove the night shift, staff the day shift twice, add authorised overtime
  UUID nightShift=admin.queryForObject("SELECT id FROM synqoria_hr.roster_shifts WHERE template_id=? AND staff_id=?",UUID.class,night,s1);
  ok(time.removeShift(rosterMgr,k(),nightShift));
  admin.update("DELETE FROM synqoria_hr.roster_shifts WHERE staff_id IS NULL");
  ok(time.addShift(rosterMgr,k(),id(ro),new ShiftRequest(day,s2,mon,false)));
  Map<String,Object> ot=body(ok(time.addShift(rosterMgr,k(),id(ro),new ShiftRequest(day,s1,mon.plusDays(1),true))));
  assertEquals("roster_conflict",code(()->time.submit(rosterMgr,k(),id(ro),new TimeService.Transition(1))),"overtime not authorised");
  Actor creatorAsAuthoriser=a(rosterMgrId,Perm.OVERTIME_AUTHORIZE);
  assertEquals("approver_must_be_independent",code(()->time.authorizeOvertime(creatorAsAuthoriser,k(),id(ot))));
  ok(time.authorizeOvertime(rosterRev,k(),id(ot)));
  // coverage on day 2 still 1 < 2 requirement
  assertEquals("coverage_gap",code(()->time.submit(rosterMgr,k(),id(ro),new TimeService.Transition(1))));
  ok(time.addShift(rosterMgr,k(),id(ro),new ShiftRequest(day,s2,mon.plusDays(1),false)));
  Map<String,Object> sub=body(ok(time.submit(rosterMgr,k(),id(ro),new TimeService.Transition(1))));
  assertEquals("reviewer_must_be_independent",code(()->time.review(a(rosterMgrId,Perm.ROSTER_REVIEW),k(),id(sub),new TimeService.Decision(ver(sub),true,null))));
  assertEquals("invalid_transition",code(()->time.addShift(rosterMgr,k(),id(sub),new ShiftRequest(day,s2,mon,false))),"submitted rosters are frozen");
  Map<String,Object> pub=body(ok(time.review(rosterRev,k(),id(sub),new TimeService.Decision(ver(sub),true,null))));
  assertEquals("PUBLISHED",pub.get("status"));
  // revision supersedes the published roster only when independently published
  Map<String,Object> rev=body(ok(time.revise(rosterMgr,k(),id(pub),new TimeService.Transition(ver(pub)))));
  assertEquals(2,((Number)rev.get("revision")).intValue());assertEquals("PUBLISHED",st("rosters",id(pub)));
  assertEquals("roster_conflict",code(()->time.submit(rosterMgr,k(),id(rev),new TimeService.Transition(1))),"overtime authorisation does not carry over to a revision");
  ok(time.authorizeOvertime(rosterRev,k(),admin.queryForObject("SELECT id FROM synqoria_hr.roster_shifts WHERE roster_id=? AND overtime",UUID.class,id(rev))));
  Map<String,Object> rsub=body(ok(time.submit(rosterMgr,k(),id(rev),new TimeService.Transition(1))));
  ok(time.review(rosterRev,k(),id(rsub),new TimeService.Decision(ver(rsub),true,null)));
  assertEquals("SUPERSEDED",st("rosters",id(pub)));
  assertEquals(1L,count("SELECT count(*) FROM synqoria_hr.rosters WHERE status='PUBLISHED'"));
  assertThrows(DataAccessException.class,()->admin.update("INSERT INTO synqoria_hr.rosters(id,cell_id,facility_id,department_id,period_start,period_end,revision,status,created_by,reviewed_by,version) "+
   "SELECT gen_random_uuid(),cell_id,facility_id,department_id,period_start,period_end,9,'PUBLISHED',created_by,reviewed_by,1 FROM synqoria_hr.rosters WHERE status='PUBLISHED'"),"two published rosters for one period");
 }
 @Test void attendanceImportIsIdempotentCorrectionsKeepHistoryAndApprovalIsFinal(){
  UUID s=activeStaff("S1","400000");
  Instant in=Instant.parse("2026-10-05T07:00:00Z"),out=in.plus(Duration.ofHours(8));
  var rec=new AttendanceRequest(s,LocalDate.parse("2026-10-05"),in,out,"IMPORT","DEV-0001");
  assertEquals(1,((Number)body(ok(time.importBatch(attendance,k(),new ImportRequest(List.of(rec))))).get("inserted")).intValue());
  Map<String,Object> again=body(ok(time.importBatch(attendance,k(),new ImportRequest(List.of(rec)))));
  assertEquals(0,((Number)again.get("inserted")).intValue());assertEquals(1,((Number)again.get("skipped")).intValue());
  assertEquals(1L,count("SELECT count(*) FROM synqoria_hr.attendance_records"));
  UUID a1=admin.queryForObject("SELECT id FROM synqoria_hr.attendance_records",UUID.class);
  Map<String,Object> x=db.scoped(attReviewer,()->db.one("SELECT * FROM synqoria_hr.attendance_records WHERE id=?",a1));
  assertEquals("reviewer_must_be_independent",code(()->time.decideAttendance(a(attId,Perm.ATTENDANCE_REVIEW),k(),a1,new TimeService.Decision(ver(x),true,null))));
  Map<String,Object> disp=body(ok(time.dispute(attendance,k(),a1,new DisputeRequest(ver(x),"clock drift"))));
  Map<String,Object> fixed=body(ok(time.correct(attendance,k(),a1,new CorrectionRequest(ver(disp),in,out.plus(Duration.ofMinutes(30)),"badge reader slow"))));
  assertEquals(a1.toString(),String.valueOf(fixed.get("correctsId")));
  assertEquals("CORRECTED",st("attendance_records",a1),"the original is kept");
  assertEquals("APPROVED",body(ok(time.decideAttendance(attReviewer,k(),id(fixed),new TimeService.Decision(ver(fixed),true,null)))).get("status"));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_hr.attendance_records SET clock_out=clock_out+interval '1 hour' WHERE id=?",id(fixed)));
  assertThrows(DataAccessException.class,()->admin.update("DELETE FROM synqoria_hr.attendance_records"));
  assertEquals("invalid_attendance",code(()->time.capture(attendance,k(),new AttendanceRequest(s,LocalDate.parse("2026-10-06"),out,in,"MANUAL",null))));
 }
 @Test void leaveAccrualHolidaysBalancesAndIndependentDecisions(){
  UUID s=activeStaff("S1","400000");
  Map<String,Object> annual=body(ok(time.createLeaveType(manager,k(),new LeaveTypeRequest("ANNUAL",true,"1.25","30"))));
  ok(time.addHoliday(manager,k(),new HolidayRequest(LocalDate.parse("2026-11-18"),"Synthetic holiday")));
  // hired 1 Jan: by 16 Nov that is 10 whole months x 1.25 = 12.5 days
  assertEquals("12.5000",time.balances(manager,s).getFirst().get("balance").toString());
  Map<String,Object> lr=body(ok(time.requestLeave(rosterMgr,k(),new LeaveRequestIn(s,id(annual),LocalDate.parse("2026-11-16"),LocalDate.parse("2026-11-20")))));
  assertEquals(0,new BigDecimal("4").compareTo((BigDecimal)lr.get("days")),"Mon-Fri minus the Wednesday holiday");
  assertEquals("insufficient_leave",code(()->time.requestLeave(rosterMgr,k(),new LeaveRequestIn(s,id(annual),LocalDate.parse("2026-12-01"),LocalDate.parse("2026-12-31")))));
  assertEquals("duplicate_record",code(()->time.requestLeave(rosterMgr,k(),new LeaveRequestIn(s,id(annual),LocalDate.parse("2026-11-19"),LocalDate.parse("2026-11-20")))),"overlapping request");
  assertEquals("decider_must_be_independent",code(()->time.decideLeave(a(rosterMgrId,Perm.LEAVE_DECIDE),k(),id(lr),new TimeService.Decision(ver(lr),true,null))));
  ok(time.decideLeave(manager,k(),id(lr),new TimeService.Decision(ver(lr),true,null)));
  assertEquals("8.5000",time.balances(manager,s).getFirst().get("balance").toString());
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_hr.leave_requests SET status='REJECTED' WHERE id=?",id(lr)),"decided leave is immutable");
  // approved leave blocks rostering on those days
  UUID day=UUID.fromString((String)body(ok(time.createTemplate(rosterMgr,k(),new TemplateRequest("DAY","07:00",480)))).get("id"));
  Map<String,Object> ro=body(ok(time.createRoster(rosterMgr,k(),new RosterRequest(dept,LocalDate.parse("2026-11-16"),LocalDate.parse("2026-11-22")))));
  assertEquals("roster_conflict",code(()->time.addShift(rosterMgr,k(),id(ro),new ShiftRequest(day,s,LocalDate.parse("2026-11-17"),false))));
 }

 // ---------------------------------------------------------------- tenancy, privileges, idempotency
 @Test void crossHospitalAndCrossFacilityAccessIsImpossible()throws Exception{
  Map<String,Object> run=approvedRun("400000");
  UUID s=admin.queryForObject("SELECT id FROM synqoria_hr.staff",UUID.class);
  Actor otherCell=new Actor(UUID.randomUUID(),UUID.randomUUID(),facility,Set.copyOf(Perm.ALL));
  Actor otherFac=new Actor(UUID.randomUUID(),cell,UUID.randomUUID(),Set.copyOf(Perm.ALL));
  for(Actor f:List.of(otherCell,otherFac)){
   assertEquals("not_found",code(()->staff.get(f,s)));
   assertEquals("not_found",code(()->payroll.getRun(f,id(run))));
   assertEquals("not_found",code(()->payroll.getReconciliation(f,id(run))));
   assertEquals("not_found",code(()->time.getRoster(f,UUID.randomUUID())));
   assertEquals(0L,((Number)staff.list(f,null,null,null,0,10).get("total")).longValue());
   assertEquals(0L,((Number)payroll.listRuns(f,null,0,10).get("total")).longValue());
   assertEquals("not_found",code(()->payroll.reverse(f,k(),id(run),new ReverseRequest(3,"x"))));
   assertEquals("not_found",code(()->payroll.confirm(f,k(),UUID.randomUUID(),new Confirmation(false,null,null,null,null,"x"))));
  }
  try(Connection c=runtimeDs.getConnection()){
   c.setAutoCommit(false);
   try(Statement q=c.createStatement();ResultSet rs=q.executeQuery("SELECT count(*) FROM synqoria_hr.payroll_runs")){rs.next();assertEquals(0,rs.getInt(1),"no scope => no rows");}
   try(PreparedStatement ps=c.prepareStatement("SELECT set_config('synqoria.cell',?,true),set_config('synqoria.facility',?,true)")){ps.setString(1,UUID.randomUUID().toString());ps.setString(2,facility.toString());ps.execute();}
   try(Statement q=c.createStatement();ResultSet rs=q.executeQuery("SELECT count(*) FROM synqoria_hr.payroll_lines")){rs.next();assertEquals(0,rs.getInt(1));}
   assertThrows(SQLException.class,()->{try(Statement q=c.createStatement()){q.execute("INSERT INTO synqoria_hr.departments(id,cell_id,facility_id,code,name) VALUES (gen_random_uuid(),'"+cell+"','"+facility+"','FORGED','x')");}});
   c.rollback();
  }
 }
 @Test void runtimeRoleIsLeastPrivilegedRlsForcedAndOwnerLoginIsRefused(){
  assertEquals(List.of(),admin.queryForList("SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='synqoria_hr' AND c.relkind='r' AND NOT (c.relrowsecurity AND c.relforcerowsecurity)",String.class));
  assertEquals(List.of("roster_shifts"),admin.queryForList("SELECT DISTINCT table_name FROM information_schema.role_table_grants WHERE grantee='synqoria_hr_runtime' AND table_schema='synqoria_hr' AND privilege_type='DELETE'",String.class),"only draft roster edits may delete");
  assertEquals(0L,count("SELECT count(*) FROM information_schema.role_table_grants WHERE grantee='synqoria_hr_runtime' AND table_schema='synqoria_hr' AND privilege_type='TRUNCATE'"));
  assertEquals(0L,count("SELECT count(*) FROM information_schema.role_table_grants WHERE grantee='synqoria_hr_runtime' AND table_name IN ('audit_events','outbox','payroll_lines') AND privilege_type='UPDATE'"));
  assertEquals(0L,count("SELECT count(*) FROM information_schema.role_table_grants WHERE grantee='PUBLIC' AND table_schema='synqoria_hr'"));
  DriverManagerDataSource ads=new DriverManagerDataSource(adminUrl,adminUser,adminPassword);
  Db bad=new Db(new JdbcTemplate(ads),JsonMapper.builder().build(),new DataSourceTransactionManager(ads),clock,"synqoria_hr_runtime","UTC");
  assertEquals("database_role_denied",code(()->new StaffService(bad).organisation(officer)));
  assertThrows(IllegalStateException.class,()->new Db(admin,JsonMapper.builder().build(),new DataSourceTransactionManager(ads),clock,"postgres","UTC"));
  assertEquals(0L,count("SELECT count(*) FROM information_schema.columns WHERE table_schema='synqoria_hr' AND column_name ~* '(iban|account_number|card|pan|password)'"),"no raw financial identifiers in the schema");
 }
 @Test void idempotentReplayAndKeyReuseWithDifferentPayload(){
  UUID key=k();DeptRequest r=new DeptRequest("LAB","Laboratory");
  Reply first=staff.createDepartment(officer,key,r),replay=staff.createDepartment(officer,key,r);
  assertEquals(201,first.status());assertEquals(201,replay.status());assertEquals(id(body(first)).toString(),String.valueOf(body(replay).get("id")));
  assertEquals(2L,count("SELECT count(*) FROM synqoria_hr.departments"));
  assertEquals("idempotency_collision",code(()->staff.createDepartment(officer,key,new DeptRequest("LAB2","Other"))));
  assertEquals("idempotency_key_required",code(()->staff.createDepartment(officer,null,r)));
  assertEquals("permission_denied",code(()->staff.createDepartment(reader,k(),new DeptRequest("X","X"))));
 }
}
