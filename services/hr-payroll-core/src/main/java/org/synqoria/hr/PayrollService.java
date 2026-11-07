package org.synqoria.hr;

import static org.synqoria.hr.Db.m;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.synqoria.hr.Db.Reply;
import org.synqoria.hr.domain.Domain;
import org.synqoria.hr.domain.Domain.*;

/**
 * M24-S05/S06: versioned country/employer rule sets, period control, exact-decimal calculation, maker-checker approval,
 * locked periods, reversals, payment instructions, independently evidenced confirmation and employee-level reconciliation.
 * Payment status is NEVER set to CONFIRMED without external evidence recorded by a different person.
 */
@Service
public class PayrollService {
 private final Db db;
 private final TimeService time;
 public PayrollService(Db db,TimeService time){this.db=db;this.time=time;}

 public record RuleSetRequest(String countryCode,String employerCode,String currency,LocalDate effectiveFrom,Map<String,Object> rules,String sourceReference){}
 public record Decision(long expectedVersion,boolean approve,String note){}
 public record PeriodRequest(String employerCode,String countryCode,String currency,LocalDate periodStart,LocalDate periodEnd){}
 public record AdjustmentRequest(UUID periodId,UUID staffId,String kind,String amount,String reason){}
 public record Transition(long expectedVersion){}
 public record ReverseRequest(long expectedVersion,String reason){}
 public record Confirmation(boolean success,String providerRef,String amount,String currency,Instant confirmedAt,String failureReason){}

 /** Canonical JSON (sorted keys) so equal rule sets always hash equally. */
 String canon(Object o){
  if(o instanceof Map<?,?> mp){
   StringBuilder b=new StringBuilder("{");boolean first=true;
   for(Object k:new TreeSet<>(mp.keySet().stream().map(String::valueOf).toList())){if(!first)b.append(',');first=false;b.append(db.encode(k)).append(':').append(canon(mp.get(k)));}
   return b.append('}').toString();
  }
  if(o instanceof List<?> l){StringBuilder b=new StringBuilder("[");for(int i=0;i<l.size();i++){if(i>0)b.append(',');b.append(canon(l.get(i)));}return b.append(']').toString();}
  return db.encode(o);
 }

 /** Fixed scale so the hash is identical whether the amount comes from the engine (scale 0..4) or from numeric(20,4). */
 static String canonical(BigDecimal v){return v.setScale(4,java.math.RoundingMode.UNNECESSARY).toPlainString();}

 // ---------------------------------------------------------------- rule sets
 public Reply createRuleSet(Actor a,UUID cmd,RuleSetRequest r){
  a.require(Perm.RULES_MANAGE);
  return db.command(a,cmd,"RULES_CREATE",r,()->{
   if(r==null||r.countryCode()==null||!r.countryCode().matches("[A-Z]{2}")||!Domain.validCode(r.employerCode())||r.currency()==null||!r.currency().matches("[A-Z]{3}")||
      r.effectiveFrom()==null||r.sourceReference()==null||r.sourceReference().isBlank()||r.sourceReference().length()>300)throw new Violation("invalid_rule_set");
   List<String> problems=Domain.validateRules(r.rules(),r.currency());
   if(!problems.isEmpty())throw new Violation("invalid_rule_set",problems);
   int next=db.jdbc.queryForObject("SELECT coalesce(max(rule_version),0)+1 FROM synqoria_hr.rule_sets WHERE country_code=? AND employer_code=?",Integer.class,r.countryCode(),r.employerCode());
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_hr.rule_sets(id,cell_id,facility_id,country_code,employer_code,rule_version,currency,effective_from,rules,rules_hash,source_reference,status,created_by,version) VALUES (?,?,?,?,?,?,?,?,?::jsonb,?,?,'DRAFT',?,1)",
    id,a.cell(),a.facility(),r.countryCode(),r.employerCode(),next,r.currency(),Date.valueOf(r.effectiveFrom()),db.encode(r.rules()),Domain.sha256(canon(r.rules())),r.sourceReference().strip(),a.id());
   db.audit(a,cmd,"RULESET",id,"CREATE",null,"DRAFT",1,m("country",r.countryCode(),"employer",r.employerCode(),"ruleVersion",next));
   return new Reply(201,db.one("SELECT * FROM synqoria_hr.rule_sets WHERE id=?",id));
  });
 }
 public Reply decideRuleSet(Actor a,UUID cmd,UUID id,Decision d){
  a.require(Perm.RULES_APPROVE);
  return db.command(a,cmd,"RULES_DECIDE|"+id,d,()->{
   List<Map<String,Object>> rows=db.rows("SELECT * FROM synqoria_hr.rule_sets WHERE id=? FOR UPDATE",id);
   if(rows.isEmpty())throw new Violation("not_found");
   Map<String,Object> x=rows.getFirst();
   if(((Number)x.get("version")).longValue()!=d.expectedVersion())throw new Violation("stale_version");
   if(!"DRAFT".equals(x.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(x.get("createdBy")))throw new Violation("approver_must_be_independent");
   if(!d.approve()&&(d.note()==null||d.note().isBlank()))throw new Violation("reason_required");
   if(d.approve())
    for(Map<String,Object> old:db.rows("SELECT id,version FROM synqoria_hr.rule_sets WHERE country_code=? AND employer_code=? AND status='APPROVED' AND effective_from>=? FOR UPDATE",x.get("countryCode"),x.get("employerCode"),Date.valueOf((String)x.get("effectiveFrom"))))
     throw new Violation("effective_date_conflict",List.of("NEWER_RULES_ALREADY_APPROVED"));
   String to=d.approve()?"APPROVED":"REJECTED";
   db.jdbc.update("UPDATE synqoria_hr.rule_sets SET status=?,approved_by=?,approved_at=clock_timestamp(),version=version+1 WHERE id=?",to,a.id(),id);
   db.audit(a,cmd,"RULESET",id,d.approve()?"APPROVE":"REJECT","DRAFT",to,d.expectedVersion()+1,m());
   return new Reply(200,db.one("SELECT * FROM synqoria_hr.rule_sets WHERE id=?",id));
  });
 }
 public List<Map<String,Object>> listRuleSets(Actor a){
  a.require(Perm.PAYROLL_READ);
  return db.scoped(a,()->db.rows("SELECT id,country_code,employer_code,rule_version,currency,effective_from,rules,rules_hash,source_reference,status,created_by,approved_by,version FROM synqoria_hr.rule_sets ORDER BY country_code,employer_code,rule_version DESC"));
 }

 // ---------------------------------------------------------------- periods and adjustments
 public Reply createPeriod(Actor a,UUID cmd,PeriodRequest r){
  a.require(Perm.PAYROLL_PREPARE);
  return db.command(a,cmd,"PERIOD",r,()->{
   if(r==null||!Domain.validCode(r.employerCode())||r.countryCode()==null||!r.countryCode().matches("[A-Z]{2}")||r.currency()==null||!r.currency().matches("[A-Z]{3}")||
      r.periodStart()==null||r.periodEnd()==null||r.periodEnd().isBefore(r.periodStart())||r.periodEnd().toEpochDay()-r.periodStart().toEpochDay()>31)throw new Violation("invalid_period");
   if(!db.rows("SELECT 1 FROM synqoria_hr.pay_periods WHERE employer_code=? AND country_code=? AND period_start<=? AND period_end>=?",r.employerCode(),r.countryCode(),Date.valueOf(r.periodEnd()),Date.valueOf(r.periodStart())).isEmpty())throw new Violation("duplicate_record");
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_hr.pay_periods(id,cell_id,facility_id,employer_code,country_code,currency,period_start,period_end,status,version,created_by) VALUES (?,?,?,?,?,?,?,?,'OPEN',1,?)",
    id,a.cell(),a.facility(),r.employerCode(),r.countryCode(),r.currency(),Date.valueOf(r.periodStart()),Date.valueOf(r.periodEnd()),a.id());
   db.audit(a,cmd,"PERIOD",id,"OPEN",null,"OPEN",1,m());
   return new Reply(201,db.one("SELECT * FROM synqoria_hr.pay_periods WHERE id=?",id));
  });
 }
 public Reply requestAdjustment(Actor a,UUID cmd,AdjustmentRequest r){
  a.require(Perm.PAYROLL_PREPARE);
  return db.command(a,cmd,"ADJ",r,()->{
   if(r==null||r.periodId()==null||r.staffId()==null||!List.of("ARREARS","BONUS","CORRECTION","ALLOWANCE","BENEFIT","DEDUCTION").contains(r.kind())||
      r.amount()==null||!r.amount().matches("\\d{1,14}(\\.\\d{1,4})?")||new BigDecimal(r.amount()).signum()<=0||r.reason()==null||r.reason().isBlank()||r.reason().length()>300)throw new Violation("invalid_adjustment");
   List<Map<String,Object>> p=db.rows("SELECT status FROM synqoria_hr.pay_periods WHERE id=? FOR SHARE",r.periodId());
   if(p.isEmpty()||db.rows("SELECT 1 FROM synqoria_hr.staff WHERE id=?",r.staffId()).isEmpty())throw new Violation("not_found");
   if(!"OPEN".equals(p.getFirst().get("status")))throw new Violation("period_locked");
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_hr.pay_adjustments(id,cell_id,facility_id,period_id,staff_id,kind,amount,reason,requested_by,status,version) VALUES (?,?,?,?,?,?,?,?,?,'REQUESTED',1)",
    id,a.cell(),a.facility(),r.periodId(),r.staffId(),r.kind(),new BigDecimal(r.amount()),r.reason().strip(),a.id());
   db.audit(a,cmd,"ADJUSTMENT",id,"REQUEST",null,"REQUESTED",1,m("kind",r.kind()));
   return new Reply(201,db.one("SELECT * FROM synqoria_hr.pay_adjustments WHERE id=?",id));
  });
 }
 public Reply decideAdjustment(Actor a,UUID cmd,UUID id,Decision d){
  a.require(Perm.PAYROLL_APPROVE);
  return db.command(a,cmd,"ADJ_DECIDE|"+id,d,()->{
   List<Map<String,Object>> rows=db.rows("SELECT * FROM synqoria_hr.pay_adjustments WHERE id=? FOR UPDATE",id);
   if(rows.isEmpty())throw new Violation("not_found");
   Map<String,Object> x=rows.getFirst();
   if(((Number)x.get("version")).longValue()!=d.expectedVersion())throw new Violation("stale_version");
   if(!"REQUESTED".equals(x.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(x.get("requestedBy")))throw new Violation("approver_must_be_independent");
   if(!"OPEN".equals(db.one("SELECT status FROM synqoria_hr.pay_periods WHERE id=?",x.get("periodId")).get("status")))throw new Violation("period_locked");
   if(!d.approve()&&(d.note()==null||d.note().isBlank()))throw new Violation("reason_required");
   String to=d.approve()?"APPROVED":"REJECTED";
   db.jdbc.update("UPDATE synqoria_hr.pay_adjustments SET status=?,approved_by=?,approved_at=clock_timestamp(),version=version+1 WHERE id=?",to,a.id(),id);
   db.audit(a,cmd,"ADJUSTMENT",id,d.approve()?"APPROVE":"REJECT","REQUESTED",to,d.expectedVersion()+1,m());
   return new Reply(200,db.one("SELECT * FROM synqoria_hr.pay_adjustments WHERE id=?",id));
  });
 }

 // ---------------------------------------------------------------- calculation
 public Reply calculate(Actor a,UUID cmd,UUID periodId){
  a.require(Perm.PAYROLL_PREPARE);
  return db.command(a,cmd,"CALC|"+periodId,m("periodId",periodId.toString()),()->{
   List<Map<String,Object>> pr=db.rows("SELECT * FROM synqoria_hr.pay_periods WHERE id=? FOR UPDATE",periodId);
   if(pr.isEmpty())throw new Violation("not_found");
   Map<String,Object> p=pr.getFirst();
   if(!"OPEN".equals(p.get("status")))throw new Violation("period_locked");
   if(!db.rows("SELECT 1 FROM synqoria_hr.payroll_runs WHERE period_id=? AND status IN ('CALCULATED','SUBMITTED','APPROVED','RECONCILED')",periodId).isEmpty())throw new Violation("run_already_live");
   LocalDate from=LocalDate.parse((String)p.get("periodStart")),to=LocalDate.parse((String)p.get("periodEnd"));
   List<Map<String,Object>> rs=db.rows("SELECT * FROM synqoria_hr.rule_sets WHERE country_code=? AND employer_code=? AND status='APPROVED' AND effective_from<=? ORDER BY effective_from DESC,rule_version DESC LIMIT 1",
    p.get("countryCode"),p.get("employerCode"),Date.valueOf(to));
   if(rs.isEmpty())throw new Violation("rules_not_approved");
   Map<String,Object> rule=rs.getFirst();
   @SuppressWarnings("unchecked") RuleSetSpec spec=Domain.spec((Map<String,Object>)rule.get("rules"));
   if(!spec.currency().equals(p.get("currency")))throw new Violation("currency_mismatch");
   Set<LocalDate> hol=time.holidays(from,to);
   BigDecimal workingDays=BigDecimal.valueOf(Domain.workingDays(from,to,hol));
   List<Map<String,Object>> staff=db.rows("SELECT s.id,s.staff_number,c.id AS contract_id,c.pay_basis,c.base_amount,c.currency FROM synqoria_hr.staff s JOIN synqoria_hr.contracts c ON c.staff_id=s.id AND c.status='ACTIVE' "+
    "WHERE s.status='ACTIVE' AND c.employer_code=? AND c.country_code=? AND c.effective_from<=? AND (c.effective_to IS NULL OR c.effective_to>=?) ORDER BY s.staff_number FOR SHARE OF s",
    p.get("employerCode"),p.get("countryCode"),Date.valueOf(to),Date.valueOf(from));
   if(staff.isEmpty())throw new Violation("contract_missing",List.of("NO_PAYABLE_STAFF"));
   UUID runId=UUID.randomUUID();
   int runNo=db.jdbc.queryForObject("SELECT coalesce(max(run_no),0)+1 FROM synqoria_hr.payroll_runs WHERE period_id=?",Integer.class,periodId);
   List<Object[]> lines=new ArrayList<>();List<String[]> hashRows=new ArrayList<>();
   BigDecimal totalGross=BigDecimal.ZERO,totalNet=BigDecimal.ZERO;
   for(Map<String,Object> s:staff){
    UUID sid=(UUID)s.get("id");
    BigDecimal ot=db.jdbc.queryForObject("SELECT coalesce(sum(extract(epoch from (s.ends_at-s.starts_at))/3600.0),0) FROM synqoria_hr.roster_shifts s JOIN synqoria_hr.rosters r ON r.id=s.roster_id "+
     "WHERE s.staff_id=? AND r.status='PUBLISHED' AND s.overtime AND s.overtime_authorized_by IS NOT NULL AND s.starts_at>=? AND s.starts_at<?",BigDecimal.class,sid,
     Timestamp.from(from.atStartOfDay(db.zone).toInstant()),Timestamp.from(to.plusDays(1).atStartOfDay(db.zone).toInstant()));
    BigDecimal worked=db.jdbc.queryForObject("SELECT coalesce(sum(extract(epoch from (clock_out-clock_in))/3600.0),0) FROM synqoria_hr.attendance_records WHERE staff_id=? AND status='APPROVED' AND work_date BETWEEN ? AND ?",
     BigDecimal.class,sid,Date.valueOf(from),Date.valueOf(to));
    BigDecimal unpaid=BigDecimal.ZERO;
    for(Map<String,Object> l:db.rows("SELECT l.from_date,l.to_date FROM synqoria_hr.leave_requests l JOIN synqoria_hr.leave_types t ON t.id=l.leave_type_id WHERE l.staff_id=? AND l.status='APPROVED' AND NOT t.paid AND l.from_date<=? AND l.to_date>=?",sid,Date.valueOf(to),Date.valueOf(from))){
     LocalDate f=LocalDate.parse((String)l.get("fromDate")),t=LocalDate.parse((String)l.get("toDate"));
     unpaid=unpaid.add(BigDecimal.valueOf(Domain.workingDays(f.isBefore(from)?from:f,t.isAfter(to)?to:t,hol)));
    }
    List<Adjustment> adj=new ArrayList<>();
    for(Map<String,Object> x:db.rows("SELECT kind,amount FROM synqoria_hr.pay_adjustments WHERE period_id=? AND staff_id=? AND status='APPROVED' ORDER BY created_at,id",periodId,sid))
     adj.add(new Adjustment((String)x.get("kind"),(BigDecimal)x.get("amount")));
    PayLine line;
    try{
     line=Domain.calculate(spec,(String)s.get("currency"),(String)s.get("payBasis"),(BigDecimal)s.get("baseAmount"),new PeriodInputs(worked,ot,unpaid,workingDays,adj));
    }catch(Violation v){throw new Violation(v.code(),List.of("STAFF:"+s.get("staffNumber")));}
    List<Map<String,Object>> items=line.items().stream().map(i->m("code",i.code(),"kind",i.kind(),"amount",i.amount().toPlainString())).toList();
    lines.add(new Object[]{UUID.randomUUID(),a.cell(),a.facility(),runId,sid,s.get("contractId"),line.currency(),line.gross(),line.totalDeductions(),line.net(),db.encode(items)});
    hashRows.add(new String[]{sid.toString(),s.get("contractId").toString(),canonical(line.gross()),canonical(line.net())});
    totalGross=totalGross.add(line.gross());totalNet=totalNet.add(line.net());
   }
   String hash=Domain.batchHash(periodId,(String)rule.get("rulesHash"),hashRows);
   db.jdbc.update("INSERT INTO synqoria_hr.payroll_runs(id,cell_id,facility_id,period_id,rule_set_id,run_no,status,currency,staff_count,total_gross,total_net,batch_hash,prepared_by,version) VALUES (?,?,?,?,?,?,'CALCULATED',?,?,?,?,?,?,1)",
    runId,a.cell(),a.facility(),periodId,rule.get("id"),runNo,p.get("currency"),lines.size(),totalGross,totalNet,hash,a.id());
   db.jdbc.batchUpdate("INSERT INTO synqoria_hr.payroll_lines(id,cell_id,facility_id,run_id,staff_id,contract_id,currency,gross,total_deductions,net,breakdown) VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb)",lines);
   db.audit(a,cmd,"PAYROLLRUN",runId,"CALCULATE",null,"CALCULATED",1,m("periodId",periodId.toString(),"staffCount",lines.size(),"batchHash",hash,"ruleSetId",rule.get("id").toString()));
   return new Reply(201,runView(runId,false));
  });
 }

 private Map<String,Object> lockRun(UUID id,long expected){
  List<Map<String,Object>> r=db.rows("SELECT * FROM synqoria_hr.payroll_runs WHERE id=? FOR UPDATE",id);
  if(r.isEmpty())throw new Violation("not_found");
  if(((Number)r.getFirst().get("version")).longValue()!=expected)throw new Violation("stale_version");
  return r.getFirst();
 }
 Map<String,Object> runView(UUID id,boolean withLines){
  Map<String,Object> r=new LinkedHashMap<>(db.one("SELECT * FROM synqoria_hr.payroll_runs WHERE id=?",id));
  if(withLines)r.put("lines",db.rows("SELECT l.id,l.staff_id,s.staff_number,s.family_name,s.given_name,l.currency,l.gross,l.total_deductions,l.net,l.breakdown FROM synqoria_hr.payroll_lines l JOIN synqoria_hr.staff s ON s.id=l.staff_id WHERE l.run_id=? ORDER BY s.staff_number",id));
  return r;
 }
 public Reply submit(Actor a,UUID cmd,UUID id,Transition t){
  a.require(Perm.PAYROLL_PREPARE);
  return db.command(a,cmd,"RUN_SUBMIT|"+id,t,()->{
   Map<String,Object> run=lockRun(id,t.expectedVersion());
   if(!"CALCULATED".equals(run.get("status")))throw new Violation("invalid_transition");
   db.jdbc.update("UPDATE synqoria_hr.payroll_runs SET status='SUBMITTED',submitted_at=clock_timestamp(),version=version+1,updated_at=clock_timestamp() WHERE id=?",id);
   db.audit(a,cmd,"PAYROLLRUN",id,"SUBMIT","CALCULATED","SUBMITTED",t.expectedVersion()+1,m());
   return new Reply(200,runView(id,false));
  });
 }
 /** Recomputes the batch hash from the stored lines; a mismatch means the lines were altered after calculation. */
 private String storedHash(Map<String,Object> run){
  Map<String,Object> rs=db.one("SELECT rules_hash FROM synqoria_hr.rule_sets WHERE id=?",run.get("ruleSetId"));
  List<String[]> rows=new ArrayList<>();
  for(Map<String,Object> l:db.rows("SELECT staff_id,contract_id,gross,net FROM synqoria_hr.payroll_lines WHERE run_id=?",run.get("id")))
   rows.add(new String[]{l.get("staffId").toString(),l.get("contractId").toString(),canonical((BigDecimal)l.get("gross")),canonical((BigDecimal)l.get("net"))});
  return Domain.batchHash((UUID)run.get("periodId"),((String)rs.get("rulesHash")).trim(),rows);
 }
 public Reply decide(Actor a,UUID cmd,UUID id,Decision d){
  a.require(Perm.PAYROLL_APPROVE);
  return db.command(a,cmd,"RUN_DECIDE|"+id,d,()->{
   Map<String,Object> run=lockRun(id,d.expectedVersion());
   if(!"SUBMITTED".equals(run.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(run.get("preparedBy")))throw new Violation("approver_must_be_independent");
   if(!d.approve()){
    if(d.note()==null||d.note().isBlank()||d.note().length()>300)throw new Violation("reason_required");
    db.jdbc.update("UPDATE synqoria_hr.payroll_runs SET status='REJECTED',decision_note=?,approved_by=?,approved_at=clock_timestamp(),version=version+1,updated_at=clock_timestamp() WHERE id=?",d.note().strip(),a.id(),id);
    db.audit(a,cmd,"PAYROLLRUN",id,"REJECT","SUBMITTED","REJECTED",d.expectedVersion()+1,m());
    return new Reply(200,runView(id,false));
   }
   if(!storedHash(run).equals(((String)run.get("batchHash")).trim()))throw new Violation("evidence_mismatch",List.of("BATCH_HASH"));
   db.jdbc.update("UPDATE synqoria_hr.payroll_runs SET status='APPROVED',approved_by=?,approved_at=clock_timestamp(),version=version+1,updated_at=clock_timestamp() WHERE id=?",a.id(),id);
   db.jdbc.update("UPDATE synqoria_hr.pay_periods SET status='LOCKED',version=version+1 WHERE id=?",run.get("periodId"));
   db.audit(a,cmd,"PAYROLLRUN",id,"APPROVE","SUBMITTED","APPROVED",d.expectedVersion()+1,m("batchHash",run.get("batchHash")));
   return new Reply(200,runView(id,false));
  });
 }
 /** Reversal is only possible while no money has been confirmed; otherwise a correcting run is required. */
 public Reply reverse(Actor a,UUID cmd,UUID id,ReverseRequest r){
  a.require(Perm.PAYROLL_APPROVE);
  return db.command(a,cmd,"RUN_REVERSE|"+id,r,()->{
   if(r.reason()==null||r.reason().isBlank()||r.reason().length()>300)throw new Violation("reason_required");
   Map<String,Object> run=lockRun(id,r.expectedVersion());
   if(!"APPROVED".equals(run.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(run.get("preparedBy")))throw new Violation("approver_must_be_independent");
   if(!db.rows("SELECT 1 FROM synqoria_hr.payment_instructions WHERE run_id=? AND status IN ('CONFIRMED','MISMATCH')",id).isEmpty())throw new Violation("reversal_requires_correction");
   db.jdbc.update("UPDATE synqoria_hr.payment_instructions SET status='CANCELLED',version=version+1 WHERE run_id=? AND status='INSTRUCTED'",id);
   db.jdbc.update("UPDATE synqoria_hr.payroll_runs SET status='REVERSED',decision_note=?,version=version+1,updated_at=clock_timestamp() WHERE id=?",r.reason().strip(),id);
   db.jdbc.update("UPDATE synqoria_hr.pay_periods SET status='OPEN',version=version+1 WHERE id=?",run.get("periodId"));
   db.audit(a,cmd,"PAYROLLRUN",id,"REVERSE","APPROVED","REVERSED",r.expectedVersion()+1,m("reason",r.reason().strip()));
   return new Reply(200,runView(id,false));
  });
 }

 // ---------------------------------------------------------------- reads and export
 public Map<String,Object> listRuns(Actor a,String status,int page,int size){
  a.require(Perm.PAYROLL_READ);
  if(page<0||size<1||size>100)throw new Violation("invalid_pagination");
  return db.scoped(a,()->{
   String w=status==null?"":" WHERE r.status=?";Object[] base=status==null?new Object[0]:new Object[]{status};
   String f=" FROM synqoria_hr.payroll_runs r JOIN synqoria_hr.pay_periods p ON p.id=r.period_id";
   long total=db.jdbc.queryForObject("SELECT count(*)"+f+w,Long.class,base);
   Object[] args=Arrays.copyOf(base,base.length+2);args[base.length]=size;args[base.length+1]=(long)page*size;
   return db.pageOf(db.rows("SELECT r.id,r.period_id,p.period_start,p.period_end,p.employer_code,p.country_code,r.run_no,r.status,r.currency,r.staff_count,r.total_gross,r.total_net,r.batch_hash,r.prepared_by,r.approved_by,r.version"+f+w+
    " ORDER BY p.period_start DESC,r.run_no DESC LIMIT ? OFFSET ?",args),page,size,total);
  });
 }
 public Map<String,Object> listPeriods(Actor a){
  a.require(Perm.PAYROLL_READ);
  return db.scoped(a,()->m("items",db.rows("SELECT * FROM synqoria_hr.pay_periods ORDER BY period_start DESC LIMIT 100")));
 }
 public Map<String,Object> getRun(Actor a,UUID id){
  a.require(Perm.PAYROLL_READ);
  return db.scoped(a,()->{
   Map<String,Object> r=runView(id,true);
   r.put("adjustments",db.rows("SELECT id,staff_id,kind,amount,reason,status,requested_by,approved_by FROM synqoria_hr.pay_adjustments WHERE period_id=? ORDER BY created_at",r.get("periodId")));
   r.put("history",db.rows("SELECT event_id,action,from_status,to_status,aggregate_version,actor_id,occurred_at FROM synqoria_hr.audit_events WHERE aggregate_type='PAYROLLRUN' AND aggregate_id=? ORDER BY occurred_at,event_id",id));
   return r;
  });
 }
 /** CSV export with spreadsheet formula-injection defence: cells starting with = + - @ tab or CR are prefixed with an apostrophe. */
 static String csvCell(String v){
  String s=v==null?"":v;
  if(!s.isEmpty()&&"=+-@\t\r".indexOf(s.charAt(0))>=0)s="'"+s;
  if(s.contains(",")||s.contains("\"")||s.contains("\n")||s.contains("\r"))s="\""+s.replace("\"","\"\"")+"\"";
  return s;
 }
 public Reply exportRun(Actor a,UUID cmd,UUID id){
  a.require(Perm.EXPORT);a.require(Perm.PAYROLL_READ);
  return db.command(a,cmd,"EXPORT|"+id,m("id",id.toString()),()->{
   Map<String,Object> run=runView(id,true);
   if(!List.of("APPROVED","RECONCILED").contains(run.get("status")))throw new Violation("invalid_transition");
   StringBuilder b=new StringBuilder("staff_number,family_name,given_name,currency,gross,total_deductions,net\r\n");
   @SuppressWarnings("unchecked") List<Map<String,Object>> ls=(List<Map<String,Object>>)run.get("lines");
   for(Map<String,Object> l:ls)b.append(String.join(",",csvCell((String)l.get("staffNumber")),csvCell((String)l.get("familyName")),csvCell((String)l.get("givenName")),
    csvCell(((String)l.get("currency")).trim()),((BigDecimal)l.get("gross")).toPlainString(),((BigDecimal)l.get("totalDeductions")).toPlainString(),((BigDecimal)l.get("net")).toPlainString())).append("\r\n");
   db.audit(a,cmd,"PAYROLLRUN",id,"EXPORT",(String)run.get("status"),(String)run.get("status"),((Number)run.get("version")).longValue(),m("rows",ls.size(),"batchHash",run.get("batchHash")));
   return new Reply(200,m("filename","payroll-"+id.toString().substring(0,8)+".csv","batchHash",run.get("batchHash"),"content",b.toString()));
  });
 }

 // ---------------------------------------------------------------- payments (B07)
 public Reply instruct(Actor a,UUID cmd,UUID runId,Transition t){
  a.require(Perm.PAYMENT_INSTRUCT);
  return db.command(a,cmd,"PAY_INSTRUCT|"+runId,t,()->{
   Map<String,Object> run=lockRun(runId,t.expectedVersion());
   if(!"APPROVED".equals(run.get("status")))throw new Violation("invalid_transition");
   int created=0;List<String> missing=new ArrayList<>();
   for(Map<String,Object> l:db.rows("SELECT l.staff_id,l.net,l.currency,s.staff_number FROM synqoria_hr.payroll_lines l JOIN synqoria_hr.staff s ON s.id=l.staff_id WHERE l.run_id=? AND l.net>0 ORDER BY s.staff_number",runId)){
    UUID sid=(UUID)l.get("staffId");
    if(!db.rows("SELECT 1 FROM synqoria_hr.payment_instructions WHERE period_id=? AND staff_id=? AND status IN ('INSTRUCTED','CONFIRMED','MISMATCH')",run.get("periodId"),sid).isEmpty())continue;
    List<Map<String,Object>> acct=db.rows("SELECT account_mask FROM synqoria_hr.payment_accounts WHERE staff_id=?",sid);
    if(acct.isEmpty()){missing.add("NO_ACCOUNT:"+l.get("staffNumber"));continue;}
    int n=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_hr.payment_instructions WHERE run_id=? AND staff_id=?",Integer.class,runId,sid);
    String ref=("PAY-"+runId.toString().substring(0,8)+"-"+l.get("staffNumber")+(n==0?"":"-R"+n)).toUpperCase();
    db.jdbc.update("INSERT INTO synqoria_hr.payment_instructions(id,cell_id,facility_id,run_id,staff_id,period_id,reference,amount,currency,account_mask,status,instructed_by,version) VALUES (?,?,?,?,?,?,?,?,?,?,'INSTRUCTED',?,1)",
     UUID.randomUUID(),a.cell(),a.facility(),runId,sid,run.get("periodId"),ref,l.get("net"),((String)l.get("currency")).trim(),acct.getFirst().get("accountMask"),a.id());
    created++;
   }
   if(created==0&&missing.isEmpty())throw new Violation("payment_exists");
   if(created==0)throw new Violation("contract_missing",missing);
   db.audit(a,cmd,"PAYROLLRUN",runId,"INSTRUCT_PAYMENTS","APPROVED","APPROVED",t.expectedVersion(),m("instructions",created,"missingAccounts",missing.size()));
   return new Reply(201,m("created",created,"missingAccounts",missing,"note","Instructions are NOT payments: funds move only when an external provider confirms and a second person records the evidence."));
  });
 }
 /** Records external evidence. The confirmer must differ from the instructor; amounts must match exactly; provider references are single-use. */
 public Reply confirm(Actor a,UUID cmd,UUID instructionId,Confirmation c){
  a.require(Perm.PAYMENT_CONFIRM);
  return db.command(a,cmd,"PAY_CONFIRM|"+instructionId,c,()->{
   List<Map<String,Object>> rows=db.rows("SELECT * FROM synqoria_hr.payment_instructions WHERE id=? FOR UPDATE",instructionId);
   if(rows.isEmpty())throw new Violation("not_found");
   Map<String,Object> x=rows.getFirst();
   if(!"INSTRUCTED".equals(x.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(x.get("instructedBy")))throw new Violation("confirmer_must_be_independent");
   if(!"APPROVED".equals(db.one("SELECT status FROM synqoria_hr.payroll_runs WHERE id=?",x.get("runId")).get("status")))throw new Violation("invalid_transition");
   if(!c.success()){
    if(c.failureReason()==null||c.failureReason().isBlank()||c.failureReason().length()>300)throw new Violation("reason_required");
    db.jdbc.update("UPDATE synqoria_hr.payment_instructions SET status='FAILED',failure_reason=?,version=version+1 WHERE id=?",c.failureReason().strip(),instructionId);
    db.audit(a,cmd,"PAYMENT",instructionId,"FAIL","INSTRUCTED","FAILED",((Number)x.get("version")).longValue()+1,m());
    return new Reply(200,db.one("SELECT * FROM synqoria_hr.payment_instructions WHERE id=?",instructionId));
   }
   if(c.providerRef()==null||!c.providerRef().matches("[A-Za-z0-9._:/-]{6,120}")||c.amount()==null||!c.amount().matches("\\d{1,16}(\\.\\d{1,4})?")||c.confirmedAt()==null||c.currency()==null)throw new Violation("invalid_confirmation");
   if(!db.rows("SELECT 1 FROM synqoria_hr.payment_instructions WHERE provider_ref=?",c.providerRef()).isEmpty())throw new Violation("duplicate_record",List.of("PROVIDER_REF_REUSED"));
   BigDecimal amt=new BigDecimal(c.amount());
   boolean match=amt.compareTo((BigDecimal)x.get("amount"))==0&&c.currency().equals(((String)x.get("currency")).trim());
   if(match)
    db.jdbc.update("UPDATE synqoria_hr.payment_instructions SET status='CONFIRMED',provider_ref=?,confirmed_amount=?,confirmed_by=?,confirmed_at=?,version=version+1 WHERE id=?",c.providerRef(),amt,a.id(),Timestamp.from(c.confirmedAt()),instructionId);
   else
    db.jdbc.update("UPDATE synqoria_hr.payment_instructions SET status='MISMATCH',provider_ref=?,confirmed_amount=?,confirmed_by=?,confirmed_at=?,failure_reason=?,version=version+1 WHERE id=?",c.providerRef(),amt,a.id(),Timestamp.from(c.confirmedAt()),"amount or currency differs from instruction",instructionId);
   db.audit(a,cmd,"PAYMENT",instructionId,match?"CONFIRM":"MISMATCH","INSTRUCTED",match?"CONFIRMED":"MISMATCH",((Number)x.get("version")).longValue()+1,m());
   return new Reply(200,db.one("SELECT * FROM synqoria_hr.payment_instructions WHERE id=?",instructionId));
  });
 }
 /** Employee-level reconciliation between the approved run and external confirmations. Read-only. */
 Map<String,Object> reconciliation(UUID runId){
  Map<String,Object> run=db.one("SELECT * FROM synqoria_hr.payroll_runs WHERE id=?",runId);
  List<Map<String,Object>> rows=new ArrayList<>();List<Map<String,Object>> discrepancies=new ArrayList<>();
  BigDecimal expected=BigDecimal.ZERO,confirmed=BigDecimal.ZERO;
  for(Map<String,Object> l:db.rows("SELECT l.staff_id,l.net,s.staff_number,s.family_name FROM synqoria_hr.payroll_lines l JOIN synqoria_hr.staff s ON s.id=l.staff_id WHERE l.run_id=? ORDER BY s.staff_number",runId)){
    BigDecimal net=(BigDecimal)l.get("net");expected=expected.add(net);
    List<Map<String,Object>> ins=db.rows("SELECT status,amount,confirmed_amount,reference,provider_ref FROM synqoria_hr.payment_instructions WHERE run_id=? AND staff_id=? AND status<>'CANCELLED' ORDER BY created_at DESC",runId,l.get("staffId"));
    String state;String kind=null;
    if(net.signum()==0){state="NOT_DUE";}
    else if(ins.isEmpty()){state="MISSING";kind="NO_INSTRUCTION";}
    else{
     Map<String,Object> live=ins.stream().filter(i->List.of("CONFIRMED","MISMATCH","INSTRUCTED").contains(i.get("status"))).findFirst().orElse(ins.getFirst());
     state=(String)live.get("status");
     if(state.equals("CONFIRMED"))confirmed=confirmed.add((BigDecimal)live.get("confirmedAmount"));
     else kind=switch(state){case "INSTRUCTED"->"AWAITING_CONFIRMATION";case "FAILED"->"PAYMENT_FAILED";case "MISMATCH"->"AMOUNT_MISMATCH";default->state;};
    }
    Map<String,Object> row=m("staffId",l.get("staffId").toString(),"staffNumber",l.get("staffNumber"),"familyName",l.get("familyName"),"expected",net.toPlainString(),"state",state);
    rows.add(row);
    if(kind!=null)discrepancies.add(m("staffId",l.get("staffId").toString(),"staffNumber",l.get("staffNumber"),"kind",kind));
  }
  boolean clean=discrepancies.isEmpty()&&expected.compareTo(confirmed)==0;
  return m("runId",runId.toString(),"runStatus",run.get("status"),"expectedTotal",expected.toPlainString(),"confirmedTotal",confirmed.toPlainString(),
   "reconciled",clean,"discrepancies",discrepancies,"lines",rows);
 }
 public Map<String,Object> getReconciliation(Actor a,UUID runId){
  a.require(Perm.PAYROLL_READ);
  return db.scoped(a,()->reconciliation(runId));
 }
 /** Marks the run RECONCILED only when every payable employee has independently confirmed, exactly matching funds. */
 public Reply reconcile(Actor a,UUID cmd,UUID runId,Transition t){
  a.require(Perm.RECONCILE);
  return db.command(a,cmd,"RECONCILE|"+runId,t,()->{
   Map<String,Object> run=lockRun(runId,t.expectedVersion());
   if(!"APPROVED".equals(run.get("status")))throw new Violation("invalid_transition");
   Map<String,Object> rec=reconciliation(runId);
   if(!Boolean.TRUE.equals(rec.get("reconciled")))return new Reply(409,m("error","reconciliation_incomplete","details",rec.get("discrepancies"),"report",rec));
   db.jdbc.update("UPDATE synqoria_hr.payroll_runs SET status='RECONCILED',version=version+1,updated_at=clock_timestamp() WHERE id=?",runId);
   db.audit(a,cmd,"PAYROLLRUN",runId,"RECONCILE","APPROVED","RECONCILED",t.expectedVersion()+1,m("confirmedTotal",rec.get("confirmedTotal")));
   return new Reply(200,rec);
  });
 }
 public Map<String,Object> listInstructions(Actor a,UUID runId){
  a.require(Perm.PAYROLL_READ);
  return db.scoped(a,()->m("items",db.rows("SELECT i.id,i.staff_id,s.staff_number,i.reference,i.amount,i.currency,i.account_mask,i.status,i.instructed_by,i.provider_ref,i.confirmed_amount,i.confirmed_by,i.confirmed_at,i.failure_reason,i.version "+
   "FROM synqoria_hr.payment_instructions i JOIN synqoria_hr.staff s ON s.id=i.staff_id WHERE i.run_id=? ORDER BY s.staff_number,i.created_at",runId)));
 }
}
