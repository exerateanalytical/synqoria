package org.synqoria.reliability;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
import org.synqoria.reliability.BackupService.*;
import org.synqoria.reliability.Db.Reply;
import org.synqoria.reliability.IncidentService.*;
import org.synqoria.reliability.ObservabilityService.*;
import org.synqoria.reliability.RecoveryService.*;
import org.synqoria.reliability.domain.Domain;
import org.synqoria.reliability.domain.Domain.*;
import tools.jackson.databind.json.JsonMapper;

/** Real PostgreSQL integration (disposable 18.x, restricted runtime role, forced RLS). Opt-in: SYNQORIA_RELIABILITY_PG_TEST=1. All services, backups and recoveries here are SYNTHETIC. */
class ReliabilityPgIntegrationTest {
 static String url,adminUrl,adminUser,adminPassword,runtimeUser="synqoria_reliability_runtime",runtimePassword="m40-ci-only";
 static class MutableClock extends Clock {
  volatile Instant now=Instant.parse("2026-11-07T06:00:00Z");
  public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now;}
 }
 MutableClock clock;Db db;ObservabilityService obs;IncidentService inc;BackupService bk;RecoveryService rec;JdbcTemplate admin;DataSource runtimeDs;
 final UUID cell=UUID.randomUUID(),facility=UUID.randomUUID();
 Actor ops,verifier,authorizer,approver;

 static Actor actor(UUID c,UUID f,String... p){return new Actor(UUID.randomUUID(),c,f,Set.of(p));}
 @BeforeAll static void env(){
  assumeTrue("1".equals(System.getenv("SYNQORIA_RELIABILITY_PG_TEST")),"set SYNQORIA_RELIABILITY_PG_TEST=1 with disposable PostgreSQL");
  url=System.getenv("SYNQORIA_RELIABILITY_TEST_JDBC_URL");adminUrl=System.getenv().getOrDefault("SYNQORIA_RELIABILITY_TEST_ADMIN_JDBC_URL",url);
  adminUser=System.getenv("SYNQORIA_RELIABILITY_TEST_ADMIN_USER");adminPassword=System.getenv("SYNQORIA_RELIABILITY_TEST_ADMIN_PASSWORD");
 }
 @BeforeEach void setUp()throws Exception{
  DriverManagerDataSource ads=new DriverManagerDataSource(adminUrl,adminUser,adminPassword);admin=new JdbcTemplate(ads);
  admin.execute("DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_reliability_runtime') THEN CREATE ROLE synqoria_reliability_runtime LOGIN NOINHERIT NOSUPERUSER NOCREATEROLE NOCREATEDB NOREPLICATION NOBYPASSRLS PASSWORD '"+runtimePassword+"'; END IF; END $$");
  admin.execute("DROP SCHEMA IF EXISTS synqoria_reliability CASCADE");
  try(Connection c=ads.getConnection();Statement s=c.createStatement()){s.execute(Files.readString(Path.of("db","migration","V001__m40_reliability.sql")));}
  DriverManagerDataSource rds=new DriverManagerDataSource(url,runtimeUser,runtimePassword);runtimeDs=rds;
  clock=new MutableClock();
  db=new Db(new JdbcTemplate(rds),JsonMapper.builder().build(),new DataSourceTransactionManager(rds),clock,"synqoria_reliability_runtime","UTC");
  obs=new ObservabilityService(db);inc=new IncidentService(db);bk=new BackupService(db);rec=new RecoveryService(db,bk);
  ops=actor(cell,facility,Perm.SERVICE_MANAGE,Perm.SIGNAL_REPORT,Perm.HEALTH_READ,Perm.TELEMETRY_INGEST,Perm.TELEMETRY_READ,Perm.INCIDENT_MANAGE,Perm.BACKUP_RECORD,Perm.BACKUP_READ,Perm.RECOVERY_PLAN,Perm.RECOVERY_OPERATE,Perm.RECOVERY_READ);
  verifier=actor(cell,facility,Perm.BACKUP_VERIFY,Perm.INCIDENT_VERIFY,Perm.RECOVERY_VALIDATE,Perm.BACKUP_READ,Perm.RECOVERY_READ,Perm.HEALTH_READ);
  authorizer=actor(cell,facility,Perm.RECOVERY_AUTHORIZE,Perm.RECOVERY_READ);
  approver=actor(cell,facility,Perm.REOPEN_APPROVE,Perm.RECOVERY_READ,Perm.HEALTH_READ);
 }
 static UUID k(){return UUID.randomUUID();}
 @SuppressWarnings("unchecked") Map<String,Object> body(Reply r){return (Map<String,Object>)r.body();}
 Reply ok(Reply r){assertTrue(r.status()<300,r.status()+" "+r.body());return r;}
 static UUID id(Map<String,Object> m){return m.get("id") instanceof UUID u?u:UUID.fromString((String)m.get("id"));}
 static long ver(Map<String,Object> m){return ((Number)m.get("version")).longValue();}
 String code(Runnable r){try{r.run();return "NO_ERROR";}catch(Violation v){return v.code();}}
 long count(String sql,Object...a){return admin.queryForObject(sql,Long.class,a);}
 @SuppressWarnings("unchecked") List<Map<String,Object>> services(){return (List<Map<String,Object>>)obs.board(ops).get("services");}
 Map<String,Object> svc(String key){return services().stream().filter(s->s.get("serviceKey").equals(key)).findFirst().orElseThrow();}
 UUID service(String key,String tier,List<Dependency> deps){return id(body(ok(obs.registerService(ops,k(),new ServiceRequest(key,"Synthetic "+key,tier,"team-"+key,"database+objects of "+key,99.9,deps)))));}
 void signal(UUID svc,String kind,double v){ok(obs.reportSignal(ops,k(),svc,new SignalRequest(kind,v,clock.instant(),null)));}

 // ------------------------------------------------------------------ B01/B02 health and telemetry
 @Test void healthIsDerivedFromFreshSignalsAndDependenciesAndUnknownIsNeverHealthy(){
  UUID db1=service("clinical-db","CRITICAL",List.of()),api=service("clinical-api","CRITICAL",List.of(new Dependency("clinical-db","HARD")));
  assertEquals("UNKNOWN",svc("clinical-db").get("health"));
  signal(db1,"READINESS",1);signal(api,"READINESS",1);
  assertEquals("HEALTHY",svc("clinical-api").get("health"));
  clock.now=clock.now.plusSeconds(10);signal(db1,"READINESS",0);
  Map<String,Object> a=svc("clinical-api");assertEquals("DEGRADED",a.get("health"));assertEquals(Boolean.TRUE,a.get("dependencyDegraded"));assertEquals("DOWN",svc("clinical-db").get("health"));
  clock.now=clock.now.plusSeconds(10);signal(db1,"READINESS",1);signal(api,"LATENCY_P95_MS",1500);
  assertEquals("DEGRADED",svc("clinical-api").get("health"));assertEquals(Boolean.FALSE,svc("clinical-api").get("dependencyDegraded"));
  clock.now=clock.now.plusSeconds(400);
  assertEquals("UNKNOWN",svc("clinical-api").get("health"),"silence is not health");
 }
 @Test void signalsAreValidatedImmutableAndPermissioned(){
  UUID s=service("lab-api","IMPORTANT",List.of());
  assertEquals("invalid_signal",code(()->obs.reportSignal(ops,k(),s,new SignalRequest("MOOD",1,clock.instant(),null))));
  assertEquals("invalid_signal",code(()->obs.reportSignal(ops,k(),s,new SignalRequest("BACKLOG",-1,clock.instant(),null))));
  assertEquals("invalid_signal",code(()->obs.reportSignal(ops,k(),s,new SignalRequest("BACKLOG",1,clock.instant().plusSeconds(3600),null))));
  assertEquals("permission_denied",code(()->obs.reportSignal(verifier,k(),s,new SignalRequest("BACKLOG",1,clock.instant(),null))));
  signal(s,"BACKLOG",5);
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_reliability.health_signals SET value=0"));
  assertThrows(DataAccessException.class,()->service("lab-api","SUPPORT",List.of()),"duplicate service key");
 }
 @Test void telemetryIsRedactedBeforeStorageAndEveryReadIsLogged(){
  UUID s=service("clinical-api","CRITICAL",List.of());
  Map<String,Object> attrs=new HashMap<>();attrs.put("patientName","Jane Example");attrs.put("status",500);attrs.put("contact","jane@example.org");
  ok(obs.ingest(ops,k(),new EventBatch(List.of(new EventIn("corr-0001-abcd","0123456789abcdef","a1b2c3d4","clinical-api","ERROR","request.failed","failed for jane@example.org id 9876543210",attrs,clock.instant())))));
  assertEquals(0L,count("SELECT count(*) FROM synqoria_reliability.telemetry_events WHERE message ILIKE '%@%' OR attributes::text ILIKE '%@%' OR attributes::text ILIKE '%Jane%' OR message LIKE '%9876543210%'"));
  assertTrue(count("SELECT redactions FROM synqoria_reliability.telemetry_events")>=4);
  Map<String,Object> res=obs.query(ops,"corr-0001-abcd",null,null,null,null,0,10);
  assertEquals(1L,((Number)res.get("total")).longValue());
  assertEquals(1L,count("SELECT count(*) FROM synqoria_reliability.telemetry_access_log"));
  assertEquals("permission_denied",code(()->obs.query(verifier,null,null,null,null,null,0,10)));
  assertEquals(1L,count("SELECT count(*) FROM synqoria_reliability.telemetry_access_log"),"a denied read is not a read");
  List<EventIn> many=new ArrayList<>();for(int i=0;i<101;i++)many.add(new EventIn("corr-"+String.format("%08d",i),null,null,null,"INFO","tick.event","m",Map.of(),clock.instant()));
  assertEquals("invalid_batch",code(()->obs.ingest(ops,k(),new EventBatch(many))));
  assertEquals("invalid_event",code(()->obs.ingest(ops,k(),new EventBatch(List.of(new EventIn("x","","","","INFO","tick.event","m",Map.of(),clock.instant()))))));
  assertThrows(DataAccessException.class,()->admin.update("DELETE FROM synqoria_reliability.telemetry_events"));
  assertNotNull(s);
 }

 // ------------------------------------------------------------------ B03 incidents
 @Test void incidentLifecycleWithSlaEscalationAndIndependentVerification(){
  service("clinical-api","CRITICAL",List.of());
  Map<String,Object> i=body(ok(inc.declare(ops,k(),new Declare("Clinical API down","SEV1","clinical-api","Wards cannot open charts"))));
  clock.now=clock.now.plusSeconds(6*60);
  assertEquals(Boolean.TRUE,inc.get(ops,id(i)).get("ackOverdue"));
  Map<String,Object> a=body(ok(inc.acknowledge(ops,k(),id(i),new Ack(ver(i)))));
  assertEquals(Boolean.FALSE,a.get("ackOverdue"));assertEquals(ops.id().toString(),String.valueOf(a.get("ownerId")));
  Reply n=ok(inc.note(ops,k(),id(i),new Note("NOTE","Looked at jane@example.org logs")));
  assertFalse(String.valueOf(body(n).get("timeline")).contains("@"));
  Map<String,Object> e=body(ok(inc.escalate(ops,k(),id(i),new Escalate(ver(a),"No mitigation after 10 minutes"))));
  assertEquals(1,((Number)e.get("escalationLevel")).intValue());
  Map<String,Object> cur=inc.get(ops,id(i));
  Map<String,Object> m=body(ok(inc.status(ops,k(),id(i),new StatusChange(ver(cur),"MITIGATED","Failed over to standby"))));
  assertEquals("permission_denied",code(()->inc.resolve(ops,k(),id(i),new Resolve(ver(m),"smoke tests green"))));
  assertEquals("verifier_must_be_independent",code(()->inc.resolve(new Actor(ops.id(),cell,facility,Set.of(Perm.INCIDENT_VERIFY)),k(),id(i),new Resolve(ver(m),"smoke tests green"))));
  assertEquals("evidence_required",code(()->inc.resolve(verifier,k(),id(i),new Resolve(ver(m),"x"))));
  Map<String,Object> r=body(ok(inc.resolve(verifier,k(),id(i),new Resolve(ver(m),"smoke tests green on all wards"))));
  assertEquals("RESOLVED",r.get("status"));assertEquals(verifier.id().toString(),String.valueOf(r.get("verifiedBy")));
  assertEquals("invalid_transition",code(()->inc.note(ops,k(),id(i),new Note("NOTE","after the fact"))));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_reliability.incident_timeline SET text='x'"));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_reliability.incidents SET status='RESOLVED',verified_by=NULL WHERE number=1"));
 }

 // ------------------------------------------------------------------ B04/B05 backups
 BackupRequest backup(String kind,String snap,long ws,long we,String digest){
  Instant fin=clock.instant().minusSeconds(60);
  return new BackupRequest(kind,snap,"db/cell-1",fin.minusSeconds(600),fin,ws,we,List.of("objects/2026-11-07/manifest"),1_000_000L,digest,"kms-ref/cell-1",clock.instant().plusSeconds(86400L*30));
 }
 static final String DG="a".repeat(64);
 List<Check> checks(String integrityResult,String observed){
  List<Check> l=new ArrayList<>();
  for(String c:Domain.BACKUP_CHECKS)l.add(new Check(c,c.equals("INTEGRITY")?integrityResult:"PASS","ev-"+c,c.equals("INTEGRITY")?observed:null));
  return l;
 }
 UUID verifiedBackup(String kind,String snap,long ws,long we){
  Map<String,Object> b=body(ok(bk.record(ops,k(),backup(kind,snap,ws,we,DG))));
  ok(bk.verify(verifier,k(),id(b),new Verification(checks("PASS",DG))));
  return id(b);
 }
 @Test void aBackupIsNotVerifiedBecauseItWasCreated(){
  Map<String,Object> b=body(ok(bk.record(ops,k(),backup("FULL","snap-1",100,200,DG))));
  assertEquals("RECORDED",b.get("status"));
  assertEquals("verifier_must_be_independent",code(()->bk.verify(new Actor(ops.id(),cell,facility,Set.of(Perm.BACKUP_VERIFY)),k(),id(b),new Verification(checks("PASS",DG)))));
  assertEquals("invalid_verification",code(()->bk.verify(verifier,k(),id(b),new Verification(checks("PASS",DG).subList(0,4)))));
  assertEquals("RECORDED",bk.get(ops,id(b)).get("status"));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_reliability.backups SET status='VERIFIED',verified_by=gen_random_uuid() WHERE id=?",id(b)));
  Map<String,Object> v=body(ok(bk.verify(verifier,k(),id(b),new Verification(checks("PASS",DG)))));
  assertEquals("VERIFIED",v.get("status"));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_reliability.backups SET digest=? WHERE id=?","b".repeat(64),id(b)));
  assertEquals("invalid_transition",code(()->bk.verify(verifier,k(),id(b),new Verification(checks("PASS",DG)))));
 }
 @Test void aCorruptBackupFailsIntegrityEvenIfTheClientSaysPass(){
  Map<String,Object> b=body(ok(bk.record(ops,k(),backup("FULL","snap-bad",100,200,DG))));
  Map<String,Object> v=body(ok(bk.verify(verifier,k(),id(b),new Verification(checks("PASS","c".repeat(64))))));      // recomputed digest differs: the claim of PASS is overruled
  assertEquals("VERIFICATION_FAILED",v.get("status"));
  assertEquals("FAIL",admin.queryForObject("SELECT result FROM synqoria_reliability.backup_verifications WHERE check_key='INTEGRITY'",String.class));
  Map<String,Object> b2=body(ok(bk.record(ops,k(),backup("FULL","snap-nodigest",100,200,DG))));
  assertEquals("VERIFICATION_FAILED",body(ok(bk.verify(verifier,k(),id(b2),new Verification(checks("PASS",null))))).get("status"));
  assertEquals("backup_not_verified",code(()->rec.plan(ops,k(),new Plan("DATABASE_FAILURE",true,id(b),250,null,clock.instant(),null))));
 }
 @Test void walContinuityOverVerifiedArchivesOnly(){
  UUID full=verifiedBackup("FULL","full-1",100,200);
  verifiedBackup("WAL_ARCHIVE","wal-1",200,300);verifiedBackup("WAL_ARCHIVE","wal-3",350,400);
  Map<String,Object> unverified=body(ok(bk.record(ops,k(),backup("WAL_ARCHIVE","wal-2",300,350,DG))));
  Map<String,Object> gap=bk.continuity(ops,full,380);
  assertEquals(Boolean.FALSE,gap.get("continuous"));assertEquals(300L,((Number)gap.get("gapStart")).longValue());assertEquals(350L,((Number)gap.get("gapEnd")).longValue());
  assertEquals(Boolean.TRUE,bk.continuity(ops,full,290).get("continuous"));
  ok(bk.verify(verifier,k(),id(unverified),new Verification(checks("PASS",DG))));
  assertEquals(Boolean.TRUE,bk.continuity(ops,full,380).get("continuous"),"filling the gap with a verified archive closes it");
  assertEquals("invalid_query",code(()->bk.continuity(ops,id(unverified),300)));
 }

 // ------------------------------------------------------------------ B06-B08 recovery
 UUID okBase(){verifiedBackup("WAL_ARCHIVE","wal-1",200,300);return verifiedBackup("FULL","full-1",100,200);}
 Plan plan(UUID base,boolean exercise){return new Plan("DATABASE_FAILURE",exercise,base,250,clock.instant().minusSeconds(3600),clock.instant().minusSeconds(1800),null);}
 StepRequest st(Map<String,Object> r,String step,String ev,Boolean eg,Boolean wr,Long lsn,Long obj,String audit,Long eff){return new StepRequest(ver(r),step,ev,eg,wr,lsn,obj,audit,eff);}
 Map<String,Object> step(Map<String,Object> r,StepRequest s){return body(ok(rec.step(ops,k(),id(r),s)));}
 Map<String,Object> toValidating(UUID base,boolean exercise){
  Map<String,Object> r=body(ok(rec.plan(ops,k(),plan(base,exercise))));
  r=body(ok(rec.authorize(authorizer,k(),id(r),new Authorize(ver(r)))));
  r=step(r,st(r,"FENCE","fence-1",true,true,null,null,null,null));
  r=step(r,st(r,"RESTORE_DB","restore-1",null,null,250L,null,null,null));
  r=step(r,st(r,"RECONCILE_OBJECTS","objects-1",null,null,null,0L,null,null));
  r=step(r,st(r,"RECOVER_AUDIT",null,null,null,null,null,DG,null));
  r=step(r,st(r,"RECONCILE_EFFECTS","effects-1",null,null,null,null,null,0L));
  return step(r,st(r,"BEGIN_VALIDATION",null,null,null,null,null,null,null));
 }
 @Test void planningNeedsAVerifiedBaseAContinuousArchiveAndAReachableTarget(){
  UUID full=verifiedBackup("FULL","full-1",100,200);
  assertEquals("wal_gap",code(()->rec.plan(ops,k(),plan(full,true))));                             // no archive covers 250
  verifiedBackup("WAL_ARCHIVE","wal-1",200,300);
  assertEquals("target_out_of_range",code(()->rec.plan(ops,k(),new Plan("DATABASE_FAILURE",true,full,150,null,clock.instant().minusSeconds(60),null))));
  Map<String,Object> raw=body(ok(bk.record(ops,k(),backup("FULL","full-raw",100,200,DG))));
  assertEquals("backup_not_verified",code(()->rec.plan(ops,k(),plan(id(raw),true))));
  assertEquals("permission_denied",code(()->rec.plan(verifier,k(),plan(full,true))));
  assertEquals("invalid_recovery",code(()->rec.plan(ops,k(),new Plan("ALIEN_INVASION",true,full,250,null,clock.instant(),null))));
  assertEquals("PLANNED",body(ok(rec.plan(ops,k(),plan(full,true)))).get("state"));
  clock.now=clock.now.plus(Duration.ofDays(400));
  assertEquals("backup_not_verified",code(()->rec.plan(ops,k(),plan(full,true))),"an expired backup cannot seed a recovery");
 }
 @Test void everyRecoveryStepNeedsPositiveEvidenceAndNoStepCanBeSkipped(){
  UUID base=okBase();
  Map<String,Object> r=body(ok(rec.plan(ops,k(),plan(base,true))));
  Map<String,Object> p0=r;
  assertEquals("invalid_transition",code(()->rec.step(ops,k(),id(p0),st(p0,"FENCE","f",true,true,null,null,null,null))));                  // not authorized yet
  assertEquals("approver_must_be_independent",code(()->rec.authorize(new Actor(ops.id(),cell,facility,Set.of(Perm.RECOVERY_AUTHORIZE)),k(),id(p0),new Authorize(ver(p0)))));
  r=body(ok(rec.authorize(authorizer,k(),id(r),new Authorize(ver(r)))));
  Map<String,Object> a=r;
  assertEquals("fencing_not_confirmed",code(()->rec.step(ops,k(),id(a),st(a,"FENCE","f",true,null,null,null,null,null))));
  assertEquals("fencing_not_confirmed",code(()->rec.step(ops,k(),id(a),st(a,"FENCE","f",false,true,null,null,null,null))));
  assertEquals("evidence_required",code(()->rec.step(ops,k(),id(a),st(a,"FENCE","",true,true,null,null,null,null))));
  assertEquals("invalid_transition",code(()->rec.step(ops,k(),id(a),st(a,"RESTORE_DB","r",null,null,250L,null,null,null))));              // cannot restore before fencing
  r=step(r,st(r,"FENCE","fence-1",true,true,null,null,null,null));
  Map<String,Object> f=r;
  assertEquals("restore_target_mismatch",code(()->rec.step(ops,k(),id(f),st(f,"RESTORE_DB","r",null,null,249L,null,null,null))));
  r=step(r,st(r,"RESTORE_DB","restore-1",null,null,250L,null,null,null));
  Map<String,Object> d=r;
  assertEquals("objects_unreconciled",code(()->rec.step(ops,k(),id(d),st(d,"RECONCILE_OBJECTS","o",null,null,null,3L,null,null))));
  r=step(r,st(r,"RECONCILE_OBJECTS","objects-1",null,null,null,0L,null,null));
  Map<String,Object> o=r;
  assertEquals("evidence_required",code(()->rec.step(ops,k(),id(o),st(o,"RECOVER_AUDIT",null,null,null,null,null,"short",null))));
  r=step(r,st(r,"RECOVER_AUDIT",null,null,null,null,null,DG,null));
  Map<String,Object> au=r;
  assertEquals("effects_unreconciled",code(()->rec.step(ops,k(),id(au),st(au,"RECONCILE_EFFECTS","e",null,null,null,null,null,2L))));
  assertEquals(4L,count("SELECT count(*) FROM synqoria_reliability.recovery_steps"));
  assertEquals("stale_version",code(()->rec.step(ops,k(),id(au),new StepRequest(1,"RECONCILE_EFFECTS","e",null,null,null,null,null,0L))));
 }
 @Test void reopeningNeedsAllSixValidationsAnIndependentApproverAndMeasuresRtoRpo(){
  UUID base=okBase();
  Map<String,Object> v=toValidating(base,true);
  assertEquals("VALIDATING",v.get("state"));
  for(String item:Domain.VALIDATION_ITEMS.subList(0,5))ok(rec.validate(verifier,k(),id(v),new ValidationItem(item,"PASS","ev-"+item,null)));
  Map<String,Object> cur=rec.get(ops,id(v));
  assertEquals("validation_incomplete",code(()->rec.reopen(approver,k(),id(v),new Reopen(ver(cur),List.of("clinical-api")))));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_reliability.recoveries SET state='REOPENED',reopen_approved_by=gen_random_uuid(),reopened_at=now(),reopen_scope='{x}' WHERE id=?",id(v)));
  ok(rec.validate(verifier,k(),id(v),new ValidationItem("EGRESS_STATE","PASS","ev-egress","egress stays disabled for payments until finance signs off")));
  assertThrows(DataAccessException.class,()->rec.validate(verifier,k(),id(v),new ValidationItem("EGRESS_STATE","PASS","again",null)));
  Map<String,Object> ready=rec.get(ops,id(v));
  service("clinical-api","CRITICAL",List.of());
  assertEquals("permission_denied",code(()->rec.reopen(ops,k(),id(v),new Reopen(ver(ready),List.of("clinical-api")))));
  assertEquals("reopener_must_be_independent",code(()->rec.reopen(new Actor(ops.id(),cell,facility,Set.of(Perm.REOPEN_APPROVE)),k(),id(v),new Reopen(ver(ready),List.of("clinical-api")))));
  assertEquals("reopener_must_be_independent",code(()->rec.reopen(new Actor(authorizer.id(),cell,facility,Set.of(Perm.REOPEN_APPROVE)),k(),id(v),new Reopen(ver(ready),List.of("clinical-api")))));
  assertEquals("not_found",code(()->rec.reopen(approver,k(),id(v),new Reopen(ver(ready),List.of("ghost-service")))));
  assertEquals("invalid_recovery",code(()->rec.reopen(approver,k(),id(v),new Reopen(ver(ready),List.of()))));
  clock.now=clock.now.plusSeconds(600);
  Map<String,Object> done=body(ok(rec.reopen(approver,k(),id(v),new Reopen(ver(ready),List.of("clinical-api")))));
  assertEquals("REOPENED",done.get("state"));
  @SuppressWarnings("unchecked") Map<String,Object> m=(Map<String,Object>)done.get("measurement");
  assertEquals("EXERCISE",m.get("source"));
  assertEquals(1800L+600L,((Number)m.get("rtoSeconds")).longValue());                     // outage 30 min before planning + 10 min until reopening
  assertEquals(1800L,((Number)m.get("rpoSeconds")).longValue());                         // target 60 min ago, outage 30 min ago
  assertEquals(1L,count("SELECT count(*) FROM synqoria_reliability.rpo_rto_measurements"));
  assertEquals("invalid_transition",code(()->rec.abort(ops,k(),id(v),new Abort(ver(done),"too late to abort now"))));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_reliability.rpo_rto_measurements SET rto_seconds=1"));
 }
 @Test void aFailedValidationBlocksReopeningAndNothingIsMeasuredForAbortedRecoveries(){
  UUID base=okBase();
  Map<String,Object> v=toValidating(base,false);
  for(String item:Domain.VALIDATION_ITEMS)ok(rec.validate(verifier,k(),id(v),new ValidationItem(item,item.equals("CLINICAL_RECONCILIATION")?"FAIL":"PASS","ev",item.equals("CLINICAL_RECONCILIATION")?"3 orders unreconciled":null)));
  Map<String,Object> cur=rec.get(ops,id(v));
  Violation x=assertThrows(Violation.class,()->rec.reopen(approver,k(),id(v),new Reopen(ver(cur),List.of("clinical-api"))));
  assertEquals("validation_incomplete",x.code());assertEquals(List.of("CLINICAL_RECONCILIATION"),x.details());
  Map<String,Object> ab=body(ok(rec.abort(ops,k(),id(v),new Abort(ver(cur),"Clinical reconciliation failed; re-plan"))));
  assertEquals("ABORTED",ab.get("state"));assertEquals(0L,count("SELECT count(*) FROM synqoria_reliability.rpo_rto_measurements"));
  assertEquals("PLANNED",body(ok(rec.plan(ops,k(),plan(base,false)))).get("state"),"after an abort a new real recovery may be planned");
 }
 @Test void onlyOneOpenRealRecoveryPerCellButExercisesAreIndependent(){
  UUID base=okBase();
  ok(rec.plan(ops,k(),plan(base,false)));
  assertThrows(DataAccessException.class,()->rec.plan(ops,k(),plan(base,false)));
  assertEquals("PLANNED",body(ok(rec.plan(ops,k(),plan(base,true)))).get("state"));
  assertEquals(0L,count("SELECT count(*) FROM synqoria_reliability.recovery_steps"));
 }

 // ------------------------------------------------------------------ B10 concurrency, idempotency, isolation
 @Test void concurrentAuthorizationsAndAcknowledgementsHaveOneWinner() throws Exception{
  UUID base=okBase();
  Map<String,Object> r=body(ok(rec.plan(ops,k(),plan(base,true))));
  service("clinical-api","CRITICAL",List.of());
  Map<String,Object> i=body(ok(inc.declare(ops,k(),new Declare("Slow","SEV3","clinical-api","Slow charts"))));
  Actor auth2=actor(cell,facility,Perm.RECOVERY_AUTHORIZE),ops2=actor(cell,facility,Perm.INCIDENT_MANAGE);
  ExecutorService ex=Executors.newFixedThreadPool(4);CyclicBarrier barrier=new CyclicBarrier(4);List<Future<String>> fs=new ArrayList<>();
  for(Actor who:List.of(authorizer,auth2))fs.add(ex.submit(()->{barrier.await();try{rec.authorize(who,k(),id(r),new Authorize(ver(r)));return "OK";}catch(Violation e){return e.code();}}));
  for(Actor who:List.of(ops,ops2))fs.add(ex.submit(()->{barrier.await();try{inc.acknowledge(who,k(),id(i),new Ack(ver(i)));return "OK";}catch(Violation e){return e.code();}}));
  List<String> res=new ArrayList<>();for(Future<String> f:fs)res.add(f.get());
  ex.shutdown();
  assertEquals(2,res.stream().filter("OK"::equals).count(),res.toString());          // one authorization and one acknowledgement
  assertEquals(2,res.stream().filter("stale_version"::equals).count(),res.toString());
 }
 @Test void replayedStepsAreIdempotent(){
  UUID base=okBase();
  Map<String,Object> r=body(ok(rec.plan(ops,k(),plan(base,true))));
  r=body(ok(rec.authorize(authorizer,k(),id(r),new Authorize(ver(r)))));
  UUID key=k();StepRequest s=st(r,"FENCE","fence-1",true,true,null,null,null,null);
  Reply a=rec.step(ops,key,id(r),s),b=rec.step(ops,key,id(r),s);
  assertEquals(a.status(),b.status());assertEquals(1L,count("SELECT count(*) FROM synqoria_reliability.recovery_steps"));
  assertEquals("idempotency_collision",code(()->rec.step(ops,key,id(body(a)),new StepRequest(s.expectedVersion(),"FENCE","other",true,true,null,null,null,null))));
 }
 @Test void isolationLeastPrivilegeAndOutboxConsistency(){
  UUID base=okBase();Map<String,Object> r=body(ok(rec.plan(ops,k(),plan(base,true))));
  Actor other=actor(UUID.randomUUID(),UUID.randomUUID(),Perm.HEALTH_READ,Perm.BACKUP_READ,Perm.RECOVERY_READ,Perm.TELEMETRY_READ);
  assertEquals(0L,((Number)bk.list(other,null,null,0,10).get("total")).longValue());
  assertEquals(0L,((Number)rec.list(other,0,10).get("total")).longValue());
  assertEquals("not_found",code(()->rec.get(other,id(r))));
  assertTrue(((List<?>)obs.board(other).get("services")).isEmpty());
  JdbcTemplate rt=new JdbcTemplate(runtimeDs);
  assertThrows(DataAccessException.class,()->rt.update("DELETE FROM synqoria_reliability.backups"));
  assertThrows(DataAccessException.class,()->rt.update("UPDATE synqoria_reliability.audit_events SET action='X'"));
  assertThrows(DataAccessException.class,()->rt.update("DELETE FROM synqoria_reliability.recovery_validations"));
  assertEquals(0L,rt.queryForObject("SELECT count(*) FROM synqoria_reliability.recoveries",Long.class),"forced RLS: no scope, no rows");
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_reliability.backups SET size_bytes=1"));
  assertTrue(count("SELECT count(*) FROM synqoria_reliability.audit_events")>=5);
  assertEquals(count("SELECT count(*) FROM synqoria_reliability.audit_events"),count("SELECT count(*) FROM synqoria_reliability.outbox"));
 }
}
