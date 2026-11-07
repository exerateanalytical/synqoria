package org.synqoria.offline;

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
import org.synqoria.offline.Db.Reply;
import org.synqoria.offline.SyncService.*;
import org.synqoria.offline.TransferService.*;
import org.synqoria.offline.domain.Domain.*;
import tools.jackson.databind.json.JsonMapper;

/** Real PostgreSQL integration (disposable 18.x, restricted runtime role, forced RLS). Opt-in: SYNQORIA_OFFLINE_PG_TEST=1. All records here are SYNTHETIC. */
class OfflinePgIntegrationTest {
 static String url,adminUrl,adminUser,adminPassword,runtimeUser="synqoria_offline_runtime",runtimePassword="m38-ci-only";
 static class MutableClock extends Clock {
  volatile Instant now=Instant.parse("2026-11-07T06:00:00Z");
  public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now;}
 }
 MutableClock clock;Db db;SyncService sync;TransferService transfers;JdbcTemplate admin;DataSource runtimeDs;
 final UUID cell=UUID.randomUUID(),facility=UUID.randomUUID(),ownerA=UUID.randomUUID(),ownerB=UUID.randomUUID();
 Actor maker,checker,nurse,nurse2,resolver,transferOp,transferChecker;

 static Actor actor(UUID cell,UUID fac,String... p){return new Actor(UUID.randomUUID(),cell,fac,Set.of(p));}
 @BeforeAll static void env(){
  assumeTrue("1".equals(System.getenv("SYNQORIA_OFFLINE_PG_TEST")),"set SYNQORIA_OFFLINE_PG_TEST=1 with disposable PostgreSQL");
  url=System.getenv("SYNQORIA_OFFLINE_TEST_JDBC_URL");adminUrl=System.getenv().getOrDefault("SYNQORIA_OFFLINE_TEST_ADMIN_JDBC_URL",url);
  adminUser=System.getenv("SYNQORIA_OFFLINE_TEST_ADMIN_USER");adminPassword=System.getenv("SYNQORIA_OFFLINE_TEST_ADMIN_PASSWORD");
 }
 @BeforeEach void setUp()throws Exception{
  DriverManagerDataSource ads=new DriverManagerDataSource(adminUrl,adminUser,adminPassword);admin=new JdbcTemplate(ads);
  admin.execute("DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_offline_runtime') THEN CREATE ROLE synqoria_offline_runtime LOGIN NOINHERIT NOSUPERUSER NOCREATEROLE NOCREATEDB NOREPLICATION NOBYPASSRLS PASSWORD '"+runtimePassword+"'; END IF; END $$");
  admin.execute("DROP SCHEMA IF EXISTS synqoria_offline CASCADE");
  try(Connection c=ads.getConnection();Statement s=c.createStatement()){s.execute(Files.readString(Path.of("db","migration","V001__m38_offline_sync.sql")));}
  DriverManagerDataSource rds=new DriverManagerDataSource(url,runtimeUser,runtimePassword);runtimeDs=rds;
  clock=new MutableClock();
  db=new Db(new JdbcTemplate(rds),JsonMapper.builder().build(),new DataSourceTransactionManager(rds),clock,"synqoria_offline_runtime","UTC");
  sync=new SyncService(db);transfers=new TransferService(db,sync);
  maker=actor(cell,facility,Perm.CONTRACT_MANAGE,Perm.DEVICE_MANAGE,Perm.HEAD_PUBLISH,Perm.SYNC_READ,Perm.HISTORY_READ);
  checker=actor(cell,facility,Perm.CONTRACT_APPROVE,Perm.SYNC_READ);
  nurse=actor(cell,facility,Perm.SYNC_SUBMIT,Perm.SYNC_READ);
  nurse2=actor(cell,facility,Perm.SYNC_SUBMIT,Perm.SYNC_READ);
  resolver=actor(cell,facility,Perm.CONFLICT_RESOLVE,Perm.SYNC_READ,Perm.HISTORY_READ);
  transferOp=actor(cell,facility,Perm.TRANSFER_PROPOSE,Perm.TRANSFER_OPERATE,Perm.SYNC_READ);
  transferChecker=actor(cell,facility,Perm.TRANSFER_APPROVE,Perm.SYNC_READ);
 }
 static UUID k(){return UUID.randomUUID();}
 @SuppressWarnings("unchecked") Map<String,Object> body(Reply r){return (Map<String,Object>)r.body();}
 @SuppressWarnings("unchecked") List<Map<String,Object>> results(Reply r){return (List<Map<String,Object>>)body(r).get("results");}
 Reply ok(Reply r){assertTrue(r.status()<300,r.status()+" "+r.body());return r;}
 static UUID id(Map<String,Object> m){return m.get("id") instanceof UUID u?u:UUID.fromString((String)m.get("id"));}
 static long ver(Map<String,Object> m){return ((Number)m.get("version")).longValue();}
 String code(Runnable r){try{r.run();return "NO_ERROR";}catch(Violation v){return v.code();}}
 long count(String sql,Object...a){return admin.queryForObject(sql,Long.class,a);}
 static final String HASH="a".repeat(64);

 void contract(String op,boolean offline,boolean gate,boolean mergeable,int ageMin){
  Map<String,Object> c=body(ok(sync.createContract(maker,k(),new ContractRequest(op,"note",gate?"clinical-safety":"documentation",offline,gate,mergeable,ageMin))));
  ok(sync.decideContract(checker,k(),id(c),new Decision(ver(c),true)));
 }
 UUID head(String key,UUID owner){return id(body(ok(sync.registerHead(maker,k(),new HeadRequest("note",key,owner,HASH)))));}
 UUID device(Actor user){return id(body(ok(sync.registerDevice(maker,k(),new DeviceRequest("dev-"+UUID.randomUUID().toString().substring(0,8),"Ward tablet",user.id(),60,100)))));}
 long seq=0;
 CommandIn cmd(String op,String key,long base,long epoch,String... fields){
  Map<String,Object> payload=new HashMap<>();payload.put("text","synthetic "+seq);
  return new CommandIn(UUID.randomUUID(),++seq,op,"note",key,base,epoch,List.of(fields),payload,clock.instant().minusSeconds(120),clock.instant().plusSeconds(86400));
 }
 Reply submit(Actor a,UUID dev,CommandIn... c){return sync.submitBatch(a,k(),new BatchRequest(dev,List.of(c)));}
 String status(Reply r,int i){return (String)results(r).get(i).get("status");}
 String codeOf(Reply r,int i){return (String)results(r).get(i).get("code");}
 void baseline(){contract("note.add",true,false,false,1440);}

 // ------------------------------------------------------------------ B01 contracts
 @Test void contractsNeedIndependentApprovalAndGatedOperationsCannotBeOffline(){
  Map<String,Object> c=body(ok(sync.createContract(maker,k(),new ContractRequest("note.add","note","docs",true,false,false,60))));
  assertEquals("approver_must_be_independent",code(()->sync.decideContract(new Actor(maker.id(),cell,facility,Set.of(Perm.CONTRACT_APPROVE)),k(),id(c),new Decision(ver(c),true))));
  assertEquals("permission_denied",code(()->sync.decideContract(maker,k(),id(c),new Decision(ver(c),true))));
  assertEquals("gate_means_online_only",code(()->sync.createContract(maker,k(),new ContractRequest("meds.give","note","clinical",true,true,false,60))));
  assertThrows(DataAccessException.class,()->admin.update("INSERT INTO synqoria_offline.contracts(id,cell_id,facility_id,operation_type,record_type,owner_domain,offline_allowed,requires_safety_gate,mergeable,max_queue_age_minutes,status,created_by,version) VALUES (gen_random_uuid(),?,?,'x.yz','note','d',true,true,false,60,'DRAFT',gen_random_uuid(),1)",cell,facility));
 }

 // ------------------------------------------------------------------ B03 protocol, B02 envelopes
 @Test void appliesAtCurrentBaseAndReplaysAreDuplicatesNotReapplied(){
  baseline();UUID h=head("n1",ownerA);UUID dev=device(nurse);
  CommandIn c=cmd("note.add","n1",1,1);
  Reply r=ok(submit(nurse,dev,c));
  assertEquals("APPLIED",status(r,0));assertEquals(2L,((Number)results(r).get(0).get("appliedVersion")).longValue());
  Reply again=ok(submit(nurse,dev,c));            // different batch key, same command id: a duplicate message
  assertEquals("DUPLICATE",status(again,0));
  assertEquals(2L,count("SELECT head_version FROM synqoria_offline.record_heads WHERE id=?",h));
  assertEquals(1L,count("SELECT count(*) FROM synqoria_offline.commands"));
  CommandIn forged=new CommandIn(c.commandId(),c.seq(),c.operationType(),c.recordType(),c.recordKey(),c.baseVersion(),c.baseEpoch(),c.fields(),Map.of("text","tampered"),c.createdLocalAt(),c.expiresAt());
  assertEquals("replay_mismatch",codeOf(ok(submit(nurse,dev,forged)),0));      // replay attack with altered content
  assertEquals("sequence_reuse",codeOf(ok(submit(nurse,dev,new CommandIn(UUID.randomUUID(),c.seq(),c.operationType(),"note","n1",2,1,List.of(),Map.of("t","x"),c.createdLocalAt(),c.expiresAt()))),0));
  assertEquals(1L,count("SELECT count(*) FROM synqoria_offline.commands"));
 }
 @Test void batchReplayWithSameKeyReturnsTheSameReplyAndAppliesOnce(){
  baseline();UUID h=head("n1",ownerA);UUID dev=device(nurse);
  UUID key=k();BatchRequest b=new BatchRequest(dev,List.of(cmd("note.add","n1",1,1)));
  Reply a=sync.submitBatch(nurse,key,b),c=sync.submitBatch(nurse,key,b);
  assertEquals(a.status(),c.status());assertEquals(results(a).get(0).get("commandId"),results(c).get(0).get("commandId"));assertEquals("APPLIED",status(c,0));
  assertEquals(((Number)body(a).get("cursor")).longValue(),((Number)body(c).get("cursor")).longValue());assertEquals(body(a).get("serverTime"),body(c).get("serverTime"));
  assertEquals(2L,count("SELECT head_version FROM synqoria_offline.record_heads WHERE id=?",h));
  assertEquals("idempotency_collision",code(()->sync.submitBatch(nurse,key,new BatchRequest(dev,List.of(cmd("note.add","n1",2,1))))));
 }
 @Test void batchBoundsCursorAndResume(){
  baseline();head("n1",ownerA);UUID dev=device(nurse);
  List<CommandIn> over=new ArrayList<>();for(int i=0;i<51;i++)over.add(cmd("note.add","n1",1,1));
  assertEquals("invalid_batch",code(()->sync.submitBatch(nurse,k(),new BatchRequest(dev,over))));
  assertEquals("invalid_batch",code(()->sync.submitBatch(nurse,k(),new BatchRequest(dev,List.of()))));
  Reply r=ok(submit(nurse,dev,cmd("note.add","n1",1,1),cmd("note.add","n1",2,1)));
  assertEquals(List.of("APPLIED","APPLIED"),List.of(status(r,0),status(r,1)));
  long cursor=((Number)body(r).get("cursor")).longValue();
  assertEquals(cursor,((Number)sync.cursor(nurse,dev).get("cursor")).longValue());
  assertTrue(cursor>=2);
 }
 @Test void queueLimitStopsTheBatchWithRetryAndPreservesOrder(){
  baseline();head("n2",ownerA);head("n3",ownerA);
  UUID dev=id(body(ok(sync.registerDevice(maker,k(),new DeviceRequest("dev-limit-1","Tablet",nurse.id(),60,1)))));
  assertEquals("REJECTED",status(ok(submit(nurse,dev,cmd("note.add","n2",9,1))),0));      // invalid base is rejected, not queued
  staleConflictOn(dev,"n4");                                                              // fills the queue (limit 1) with an unresolved conflict
  Reply full=ok(submit(nurse,dev,cmd("note.add","n2",1,1),cmd("note.add","n3",1,1)));
  assertEquals("RETRY",status(full,0));assertEquals("queue_full",codeOf(full,0));
  assertEquals("NOT_PROCESSED",status(full,1));
  assertEquals(0L,count("SELECT count(*) FROM synqoria_offline.commands WHERE record_key IN ('n3')"));
 }
 UUID staleConflictOn(UUID dev,String key){
  UUID h=head(key,ownerA);
  ok(sync.publishRevision(maker,k(),h,new RevisionRequest(1,"b".repeat(64),List.of("text"))));
  Reply r=ok(submit(nurse,dev,cmd("note.add",key,1,1,"text")));
  assertEquals("CONFLICT",status(r,0));
  return UUID.fromString((String)results(r).get(0).get("conflictId"));
 }

 // ------------------------------------------------------------------ B05/B06 conflicts
 UUID staleConflict(UUID dev,String key,boolean mergeable){return staleConflictOn(dev,key);}
 @SuppressWarnings("unchecked") Map<String,Object> headView(String key){
  return ((List<Map<String,Object>>)sync.heads(maker,0,100).get("items")).stream().filter(x->x.get("recordKey").equals(key)).findFirst().orElseThrow();
 }
 @Test void staleBaseNeverOverwritesAndKeepAuthoritativeRetainsTheLocalVersion(){
  baseline();UUID dev=device(nurse);UUID cf=staleConflict(dev,"n1",false);
  Map<String,Object> detail=sync.conflict(resolver,cf);
  assertEquals("STALE_BASE",detail.get("kind"));assertEquals(2L,((Number)detail.get("currentHeadVersion")).longValue());assertNotNull(detail.get("localPayload"));
  assertEquals(2L,count("SELECT head_version FROM synqoria_offline.record_heads WHERE record_key='n1'"));     // untouched
  assertEquals("reason_required",code(()->sync.resolve(resolver,k(),cf,new Resolution(ver(detail),"KEEP_AUTHORITATIVE",null,null,2))));
  assertEquals("reason_required",code(()->sync.resolve(resolver,k(),cf,new Resolution(ver(detail),"KEEP_AUTHORITATIVE","OTHER","x",2))));
  assertEquals("resolver_must_be_independent",code(()->sync.resolve(new Actor(nurse.id(),cell,facility,Set.of(Perm.CONFLICT_RESOLVE)),k(),cf,new Resolution(ver(detail),"KEEP_AUTHORITATIVE","DATA_ERROR",null,2))));
  assertEquals("permission_denied",code(()->sync.resolve(nurse,k(),cf,new Resolution(ver(detail),"KEEP_AUTHORITATIVE","DATA_ERROR",null,2))));
  ok(sync.resolve(resolver,k(),cf,new Resolution(ver(detail),"KEEP_AUTHORITATIVE","SUPERSEDED_BY_NEWER",null,2)));
  assertEquals("RESOLVED",sync.conflict(resolver,cf).get("status"));
  assertEquals(2L,count("SELECT head_version FROM synqoria_offline.record_heads WHERE record_key='n1'"));
  assertEquals(1L,count("SELECT count(*) FROM synqoria_offline.commands WHERE status='RESOLVED'"));            // the local envelope is retained
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_offline.conflicts SET reason_code='DATA_ERROR' WHERE id=?",cf));   // immutable decision
  assertEquals("invalid_transition",code(()->sync.resolve(resolver,k(),cf,new Resolution(ver(sync.conflict(resolver,cf)),"KEEP_AUTHORITATIVE","DATA_ERROR",null,2))));
 }
 @Test void acceptLocalNeedsTheReviewerToHaveSeenTheCurrentHead(){
  baseline();UUID dev=device(nurse);UUID cf=staleConflict(dev,"n1",false);
  long v=ver(sync.conflict(resolver,cf));
  assertEquals("head_moved",code(()->sync.resolve(resolver,k(),cf,new Resolution(v,"ACCEPT_LOCAL","CLINICAL_REVIEW",null,1))));
  ok(sync.resolve(resolver,k(),cf,new Resolution(v,"ACCEPT_LOCAL","CLINICAL_REVIEW",null,2)));
  assertEquals(3L,count("SELECT head_version FROM synqoria_offline.record_heads WHERE record_key='n1'"));
  assertEquals(1L,count("SELECT count(*) FROM synqoria_offline.reconciliations WHERE outcome='RESOLVED_LOCAL'"));
 }
 @Test void disjointChangesMergeOnlyWhenTheContractAllowsAndOverlapConflicts(){
  contract("note.merge",true,false,true,1440);
  UUID h=head("m1",ownerA);UUID dev=device(nurse);
  ok(sync.publishRevision(maker,k(),h,new RevisionRequest(1,"b".repeat(64),List.of("allergy_note"))));
  CommandIn disjoint=new CommandIn(UUID.randomUUID(),++seq,"note.merge","note","m1",1,1,List.of("comment"),Map.of("c","x"),clock.instant().minusSeconds(60),clock.instant().plusSeconds(600));
  Reply r=ok(submit(nurse,dev,disjoint));
  assertEquals("APPLIED",status(r,0));assertEquals("applied_merged",codeOf(r,0));
  CommandIn overlap=new CommandIn(UUID.randomUUID(),++seq,"note.merge","note","m1",1,1,List.of("allergy_note"),Map.of("c","y"),clock.instant().minusSeconds(60),clock.instant().plusSeconds(600));
  Reply o=ok(submit(nurse,dev,overlap));
  assertEquals("CONFLICT",status(o,0));assertEquals("incompatible_changes",codeOf(o,0));
  assertEquals("INCOMPATIBLE",count("SELECT count(*) FROM synqoria_offline.conflicts WHERE kind='INCOMPATIBLE' AND differing_fields='{allergy_note}'")==1?"INCOMPATIBLE":"X");
 }

 // ------------------------------------------------------------------ B08 safety and security
 @Test void gatedOfflineOrUnknownOperationsAreRejectedAndKeptAsEvidence(){
  baseline();contract("meds.give",false,true,false,60);head("n1",ownerA);UUID dev=device(nurse);
  Reply r=ok(submit(nurse,dev,cmd("meds.give","n1",1,1),cmd("surgery.plan","n1",1,1)));
  assertEquals("safety_gate_required",codeOf(r,0));assertEquals("no_offline_contract",codeOf(r,1));
  assertEquals(2L,count("SELECT count(*) FROM synqoria_offline.commands WHERE status='REJECTED'"));
  assertEquals(1L,count("SELECT head_version FROM synqoria_offline.record_heads WHERE record_key='n1'"));
 }
 @Test void expiredCommandsAreKeptButNeverApplied(){
  baseline();head("n1",ownerA);UUID dev=device(nurse);
  CommandIn late=new CommandIn(UUID.randomUUID(),++seq,"note.add","note","n1",1,1,List.of(),Map.of("t","x"),clock.instant().minusSeconds(600),clock.instant().minusSeconds(60));
  assertEquals("EXPIRED",status(ok(submit(nurse,dev,late)),0));
  CommandIn old=new CommandIn(UUID.randomUUID(),++seq,"note.add","note","n1",1,1,List.of(),Map.of("t","x"),clock.instant().minusSeconds(172800),clock.instant().plusSeconds(600));
  assertEquals("queue_age_exceeded",codeOf(ok(submit(nurse,dev,old)),0));
  assertEquals(1L,count("SELECT head_version FROM synqoria_offline.record_heads WHERE record_key='n1'"));
 }
 @Test void revokedExpiredAndForeignDevicesAreRefusedAndNothingIsApplied(){
  baseline();head("n1",ownerA);UUID dev=device(nurse);
  assertEquals("device_user_mismatch",code(()->submit(nurse2,dev,cmd("note.add","n1",1,1))));
  Map<String,Object> dv=sync.devices(maker).getFirst();
  assertEquals("reason_required",code(()->sync.revokeDevice(maker,k(),dev,new Revoke(ver(dv),"x"))));
  ok(sync.revokeDevice(maker,k(),dev,new Revoke(ver(dv),"Lost device reported by ward")));
  assertEquals("device_revoked",code(()->submit(nurse,dev,cmd("note.add","n1",1,1))));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_offline.devices SET status='ACTIVE' WHERE id=?",dev));
  UUID d2=device(nurse);clock.now=clock.now.plus(Duration.ofHours(2));
  assertEquals("device_session_expired",code(()->submit(nurse,d2,cmd("note.add","n1",1,1))));
  assertEquals(0L,count("SELECT count(*) FROM synqoria_offline.commands"));
  assertEquals("permission_denied",code(()->submit(checker,d2,cmd("note.add","n1",1,1))));
 }

 // ------------------------------------------------------------------ B04 ownership epochs
 Map<String,Object> toApproved(UUID h){
  Map<String,Object> t=body(ok(transfers.propose(transferOp,k(),new Propose(h,ownerB,"Patient moved to partner cell"))));
  t=body(ok(transfers.freeze(transferOp,k(),id(t),new Step(ver(t)))));
  t=body(ok(transfers.fence(transferOp,k(),id(t),new Fence(ver(t),"fence-ack-001",((Number)headView("n1").get("headVersion")).longValue()))));
  return body(ok(transfers.decide(transferChecker,k(),id(t),new Decide(ver(t),true,null))));
 }
 @Test void ownershipTransferRaisesTheEpochAndFencesTheStaleWriter(){
  baseline();UUID h=head("n1",ownerA);UUID dev=device(nurse);
  Map<String,Object> t=toApproved(h);
  assertEquals("APPROVED",t.get("status"));
  // while frozen/approved the record accepts no writes: the device is told to retry, nothing is stored
  Reply frozen=ok(submit(nurse,dev,cmd("note.add","n1",1,1)));
  assertEquals("RETRY",status(frozen,0));assertEquals("record_frozen",codeOf(frozen,0));
  assertEquals(0L,count("SELECT count(*) FROM synqoria_offline.commands"));
  Map<String,Object> done=body(ok(transfers.complete(transferOp,k(),id(t),new Step(ver(t)))));
  assertEquals("COMPLETED",done.get("status"));assertEquals(2L,((Number)done.get("completedEpoch")).longValue());
  Map<String,Object> hv=headView("n1");
  assertEquals(2L,((Number)hv.get("ownershipEpoch")).longValue());assertEquals(ownerB.toString(),String.valueOf(hv.get("ownerRef")));assertEquals(Boolean.FALSE,hv.get("frozen"));
  // the old owner's device still holds epoch 1: it is fenced, kept for review, never applied
  Reply stale=ok(submit(nurse,dev,cmd("note.add","n1",1,1)));
  assertEquals("CONFLICT",status(stale,0));assertEquals("stale_writer_fenced",codeOf(stale,0));
  assertEquals("OWNER_FENCED",admin.queryForObject("SELECT kind FROM synqoria_offline.conflicts",String.class));
  assertEquals(1L,count("SELECT head_version FROM synqoria_offline.record_heads WHERE record_key='n1'"));
  // a writer that has learned the new epoch can write
  Reply moved=ok(submit(nurse,dev,cmd("note.add","n1",1,2)));
  assertEquals("APPLIED",status(moved,0));
  assertEquals(1L,count("SELECT count(*) FROM synqoria_offline.reconciliations WHERE outcome='OWNERSHIP_TRANSFERRED'"));
 }
 @Test void transferGuardsEvidenceIndependenceAndSingleOpenTransfer(){
  baseline();UUID h=head("n1",ownerA);
  Map<String,Object> t=body(ok(transfers.propose(transferOp,k(),new Propose(h,ownerB,"Planned move to partner cell"))));
  assertEquals("already_owner",code(()->transfers.propose(transferOp,k(),new Propose(h,ownerA,"Planned move to same cell"))));
  assertThrows(DataAccessException.class,()->transfers.propose(transferOp,k(),new Propose(h,UUID.randomUUID(),"Second open transfer attempt")));   // one open transfer per record
  assertEquals("invalid_transition",code(()->transfers.fence(transferOp,k(),id(t),new Fence(ver(t),"ev-1",1))));          // cannot fence before freezing
  Map<String,Object> f=body(ok(transfers.freeze(transferOp,k(),id(t),new Step(ver(t)))));
  assertEquals("fence_version_mismatch",code(()->transfers.fence(transferOp,k(),id(f),new Fence(ver(f),"ev-1",7))));        // wrong last version is not evidence
  assertEquals("evidence_required",code(()->transfers.fence(transferOp,k(),id(f),new Fence(ver(f),"",1))));
  Map<String,Object> fe=body(ok(transfers.fence(transferOp,k(),id(f),new Fence(ver(f),"ev-1",1))));
  assertEquals("approver_must_be_independent",code(()->transfers.decide(new Actor(transferOp.id(),cell,facility,Set.of(Perm.TRANSFER_APPROVE)),k(),id(fe),new Decide(ver(fe),true,null))));
  assertEquals("invalid_transition",code(()->transfers.complete(transferOp,k(),id(fe),new Step(ver(fe)))));                // not approved yet
  assertEquals("permission_denied",code(()->transfers.decide(transferOp,k(),id(fe),new Decide(ver(fe),true,null))));
  Map<String,Object> ab=body(ok(transfers.abort(transferOp,k(),id(fe),new Abort(ver(fe),"Partner cell not ready"))));
  assertEquals("ABORTED",ab.get("status"));assertEquals(Boolean.FALSE,headView("n1").get("frozen"));
  assertEquals(1L,((Number)headView("n1").get("ownershipEpoch")).longValue());
  Map<String,Object> again=body(ok(transfers.propose(transferOp,k(),new Propose(h,ownerB,"Retry after partner is ready"))));
  assertEquals("PROPOSED",again.get("status"));
 }
 @Test void databaseRefusesNonMonotonicEpochsAndOwnerChangesWithoutAnEpochBump(){
  baseline();UUID h=head("n1",ownerA);
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_offline.record_heads SET owner_ref=? WHERE id=?",ownerB,h));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_offline.record_heads SET ownership_epoch=3,owner_ref=? WHERE id=?",ownerB,h));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_offline.record_heads SET ownership_epoch=2 WHERE id=?",h));
  assertEquals(1L,count("SELECT ownership_epoch FROM synqoria_offline.record_heads WHERE id=?",h));
  assertEquals(1,admin.update("UPDATE synqoria_offline.record_heads SET ownership_epoch=2,owner_ref=? WHERE id=?",ownerB,h));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_offline.record_heads SET ownership_epoch=1,owner_ref=? WHERE id=?",ownerA,h));
 }

 // ------------------------------------------------------------------ B10 concurrency
 @Test void concurrentWritersOnTheSameBaseYieldExactlyOneWinner() throws Exception{
  baseline();UUID h=head("n1",ownerA);UUID d1=device(nurse),d2=device(nurse2);
  ExecutorService ex=Executors.newFixedThreadPool(2);CyclicBarrier barrier=new CyclicBarrier(2);
  CommandIn c1=cmd("note.add","n1",1,1),c2=cmd("note.add","n1",1,1);
  Future<Reply> f1=ex.submit(()->{barrier.await();return submit(nurse,d1,c1);}),f2=ex.submit(()->{barrier.await();return submit(nurse2,d2,c2);});
  List<String> out=new ArrayList<>(List.of(status(f1.get(),0),status(f2.get(),0)));Collections.sort(out);
  ex.shutdown();
  assertEquals(List.of("APPLIED","CONFLICT"),out);
  assertEquals(2L,count("SELECT head_version FROM synqoria_offline.record_heads WHERE id=?",h));
  assertEquals(1L,count("SELECT count(*) FROM synqoria_offline.conflicts"));
 }
 @Test void concurrentTransferProposalsAllowExactlyOne() throws Exception{
  baseline();UUID h=head("n1",ownerA);
  ExecutorService ex=Executors.newFixedThreadPool(4);CyclicBarrier barrier=new CyclicBarrier(4);List<Future<String>> fs=new ArrayList<>();
  for(int i=0;i<4;i++)fs.add(ex.submit(()->{barrier.await();
   try{transfers.propose(transferOp,k(),new Propose(h,UUID.randomUUID(),"Concurrent move request"));return "OK";}catch(RuntimeException e){return "REFUSED";}}));
  int okCount=0;for(Future<String> f:fs)if(f.get().equals("OK"))okCount++;
  ex.shutdown();
  assertEquals(1,okCount);assertEquals(1L,count("SELECT count(*) FROM synqoria_offline.ownership_transfers"));
 }

 // ------------------------------------------------------------------ isolation, least privilege, history
 @Test void otherCellsSeeNothingAndRuntimeRoleIsLeastPrivileged(){
  baseline();head("n1",ownerA);UUID dev=device(nurse);ok(submit(nurse,dev,cmd("note.add","n1",1,1)));
  Actor other=actor(UUID.randomUUID(),UUID.randomUUID(),Perm.SYNC_READ,Perm.HISTORY_READ,Perm.SYNC_SUBMIT);
  assertTrue(sync.devices(other).isEmpty());assertEquals(0,((List<?>)sync.heads(other,0,10).get("items")).size());
  assertEquals(0,((List<?>)sync.commands(other,null,null,0,10).get("items")).size());
  assertEquals("not_found",code(()->sync.command(other,UUID.randomUUID())));
  assertEquals("not_found",code(()->submit(other,dev,cmd("note.add","n1",1,1))));      // a foreign device id is invisible
  JdbcTemplate rt=new JdbcTemplate(runtimeDs);
  assertThrows(DataAccessException.class,()->rt.update("DELETE FROM synqoria_offline.commands"));
  assertThrows(DataAccessException.class,()->rt.update("UPDATE synqoria_offline.audit_events SET action='X'"));
  assertThrows(DataAccessException.class,()->rt.update("DELETE FROM synqoria_offline.reconciliations"));
  assertEquals(0L,rt.queryForObject("SELECT count(*) FROM synqoria_offline.commands",Long.class));       // forced RLS: no scope, no rows
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_offline.commands SET payload='{}'::jsonb"));
 }
 @Test void historyListsEveryTerminalOutcomeWithReviewerAndFinalVersions(){
  baseline();UUID dev=device(nurse);UUID cf=staleConflict(dev,"n1",false);
  long v=ver(sync.conflict(resolver,cf));
  ok(sync.resolve(resolver,k(),cf,new Resolution(v,"ACCEPT_LOCAL","CLINICAL_REVIEW",null,2)));
  ok(submit(nurse,dev,cmd("surgery.plan","n1",1,1)));
  @SuppressWarnings("unchecked") List<Map<String,Object>> items=(List<Map<String,Object>>)sync.history(resolver,null,0,50).get("items");
  Set<Object> outcomes=new HashSet<>();for(Map<String,Object> i:items)outcomes.add(i.get("outcome"));
  assertTrue(outcomes.containsAll(Set.of("RESOLVED_LOCAL","REJECTED")),outcomes.toString());
  Map<String,Object> res=items.stream().filter(i->"RESOLVED_LOCAL".equals(i.get("outcome"))).findFirst().orElseThrow();
  assertEquals(resolver.id().toString(),String.valueOf(res.get("reviewer")));assertEquals(3L,((Number)res.get("finalVersion")).longValue());
  assertEquals("permission_denied",code(()->sync.history(nurse,null,0,10)));
  assertEquals("invalid_pagination",code(()->sync.history(resolver,null,-1,10)));
  assertTrue(count("SELECT count(*) FROM synqoria_offline.audit_events")>=4);
  assertEquals(count("SELECT count(*) FROM synqoria_offline.audit_events"),count("SELECT count(*) FROM synqoria_offline.outbox"));
 }
}
