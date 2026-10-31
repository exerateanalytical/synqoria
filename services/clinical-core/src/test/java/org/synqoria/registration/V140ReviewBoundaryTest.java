package org.synqoria.registration;
import org.synqoria.security.TrustedAccessContext;
import java.lang.reflect.*;import java.sql.*;import java.time.*;import java.util.*;
/** Unit and JDBC proxy tests only: does not certify independent reviewer signoff, real PostgreSQL or release. */
public final class V140ReviewBoundaryTest {
 static int assertions=0;
 static void ok(boolean yes){assertions++;if(!yes)throw new AssertionError("assertion "+assertions);}
 static void invalid(Runnable r){try{r.run();throw new AssertionError("expected invalid");}catch(IllegalArgumentException x){assertions++;}}
 static final Instant NOW=Instant.parse("2026-10-30T02:00:00Z");
 static final UUID TENANT=UUID.randomUUID(),FACILITY=UUID.randomUUID(),REGISTRAR=UUID.randomUUID(),REVIEWER=UUID.randomUUID(),CANDIDATE=UUID.randomUUID();
 static final byte[] KEY=new byte[48];
 static {for(int i=0;i<KEY.length;i++)KEY[i]=(byte)(i+11);}
 static final RegistrationDraft DRAFT=new RegistrationDraft(null,null,null,RegistrationDraft.BirthInformation.UNKNOWN,"en",true);
 static TrustedAccessContext actor(){return new TrustedAccessContext(TENANT,FACILITY,REGISTRAR,Set.of("patient:register","patient:identity-review"),Set.of());}
 static RegistrationCommand command(){return new RegistrationCommand(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),RegistrationFingerprint.sign(KEY,TENANT,FACILITY,DRAFT),DRAFT,NOW);}
 static ReviewDecisionAuthority authority(){return new ReviewDecisionAuthority("review-test-1",KEY);}
 static ReviewDecisionProof proof(RegistrationCommand command){return authority().issue(TENANT,FACILITY,REVIEWER,REGISTRAR,CANDIDATE,command.commandId(),command.patientId(),3,command.fingerprintHmac(),NOW,Duration.ofSeconds(90));}
 static void proofData(){
  var c=command();var p=proof(c);
  ok(p.reviewId().version()==4&&p.fingerprint().length==32&&p.mac().length==32);
  invalid(()->new ReviewDecisionProof(TENANT,FACILITY,UUID.randomUUID(),REGISTRAR,REGISTRAR,CANDIDATE,UUID.randomUUID(),UUID.randomUUID(),0,new byte[32],NOW,NOW.plusSeconds(1),"NO_UNRESOLVED_MATCH","test-key",new byte[32]));
  invalid(()->new ReviewDecisionProof(TENANT,FACILITY,UUID.randomUUID(),REVIEWER,REGISTRAR,CANDIDATE,UUID.randomUUID(),UUID.randomUUID(),-1,new byte[32],NOW,NOW.plusSeconds(1),"NO_UNRESOLVED_MATCH","test-key",new byte[32]));
  invalid(()->new ReviewDecisionProof(TENANT,FACILITY,UUID.randomUUID(),REVIEWER,REGISTRAR,CANDIDATE,UUID.randomUUID(),UUID.randomUUID(),0,new byte[32],NOW,NOW.plusSeconds(1),"MERGE_APPROVED","test-key",new byte[32]));
  byte[] copied=p.mac();copied[0]^=1;ok(authority().verify(p,NOW));
  byte[] fingerprint=p.fingerprint();fingerprint[0]^=1;ok(authority().verify(p,NOW));
 }
 static void cryptography(){
  var c=command();var p=proof(c);ok(authority().verify(p,NOW));ok(!authority().verify(p,NOW.minusSeconds(1)));ok(!authority().verify(p,p.expiresAt()));
  byte[] other=KEY.clone();other[5]^=13;ok(!new ReviewDecisionAuthority("review-test-1",other).verify(p,NOW));
  ok(!new ReviewDecisionAuthority("review-test-2",KEY).verify(p,NOW));
  var altered=new ReviewDecisionProof(p.tenantId(),p.facilityId(),p.reviewId(),p.reviewerId(),p.registrarId(),UUID.randomUUID(),p.commandId(),p.patientId(),p.candidateRevision(),p.fingerprint(),p.decidedAt(),p.expiresAt(),p.outcome(),p.keyId(),p.mac());
  ok(!authority().verify(altered,NOW));
  invalid(()->new ReviewDecisionAuthority("bad",new byte[32]));
  invalid(()->authority().issue(TENANT,FACILITY,REVIEWER,REGISTRAR,CANDIDATE,c.commandId(),c.patientId(),3,c.fingerprintHmac(),NOW,Duration.ofMinutes(11)));
 }
 @SuppressWarnings("unchecked")static <T>T proxy(Class<T> kind,InvocationHandler handler){return (T)Proxy.newProxyInstance(kind.getClassLoader(),new Class<?>[]{kind},handler);}
 static void repository() throws Exception {
  List<String> sqls=new ArrayList<>();List<Object> bindValues=new ArrayList<>();boolean[] hasRow={true};boolean[] auto={false},readOnly={false};
  ResultSet rs=proxy(ResultSet.class,(o,m,a)->m.getName().equals("next")?hasRow[0]:null);
  PreparedStatement ps=proxy(PreparedStatement.class,(o,m,a)->switch(m.getName()){
   case "setObject","setLong","setBytes"->{bindValues.add(a[1]);yield null;}case "executeQuery"->rs;default->null;});
  Connection conn=proxy(Connection.class,(o,m,a)->switch(m.getName()){
   case "getAutoCommit"->auto[0];case "isReadOnly"->readOnly[0];case "prepareStatement"->{sqls.add((String)a[0]);yield ps;}default->null;});
  var c=command();var pr=proof(c);var b=new ReviewDecisionRepository.Binding(TENANT,FACILITY,pr.reviewId(),REVIEWER,REGISTRAR,CANDIDATE,c.commandId(),c.patientId(),c.fingerprintHmac(),pr.mac(),3);
  ok(ReviewDecisionRepository.committed(conn,b));ok(sqls.get(0).contains("revoked_at IS NULL")&&sqls.get(0).contains("LIMIT 1")&&sqls.get(0).contains("tenant_id=?"));
  ok(bindValues.size()==11&&bindValues.get(0).equals(TENANT)&&bindValues.get(6).equals(3L)&&bindValues.get(7).equals(c.commandId()));
  hasRow[0]=false;ok(!ReviewDecisionRepository.committed(conn,b));
  auto[0]=true;ok(!ReviewDecisionRepository.committed(conn,b));auto[0]=false;readOnly[0]=true;ok(!ReviewDecisionRepository.committed(conn,b));
  ok(!ReviewDecisionRepository.committed(conn,null));
 }
 static void admission(){
  var c=command();var p=proof(c);var au=authority();
  ok(RegistrationReviewAdmission.reviewed(actor(),c,p,au,CANDIDATE,3,true,true,true,true,NOW));
  ok(!RegistrationReviewAdmission.reviewed(actor(),c,p,au,CANDIDATE,3,false,true,true,true,NOW));
  ok(!RegistrationReviewAdmission.reviewed(actor(),c,p,au,CANDIDATE,4,true,true,true,true,NOW));
  ok(!RegistrationReviewAdmission.reviewed(actor(),c,p,au,UUID.randomUUID(),3,true,true,true,true,NOW));
  ok(!RegistrationReviewAdmission.reviewed(actor(),c,p,au,CANDIDATE,3,true,false,true,true,NOW));
  ok(!RegistrationReviewAdmission.reviewed(actor(),c,p,au,CANDIDATE,3,true,true,false,true,NOW));
  ok(!RegistrationReviewAdmission.reviewed(actor(),c,p,au,CANDIDATE,3,true,true,true,false,NOW));
  ok(!RegistrationReviewAdmission.reviewed(actor(),c,p,au,CANDIDATE,3,true,true,true,true,NOW.plusSeconds(100)));
  ok(!RegistrationReviewAdmission.reviewed(actor(),command(),p,au,CANDIDATE,3,true,true,true,true,NOW));
  var other=new TrustedAccessContext(UUID.randomUUID(),FACILITY,REGISTRAR,Set.of("patient:register","patient:identity-review"),Set.of());
  ok(!RegistrationReviewAdmission.reviewed(other,c,p,au,CANDIDATE,3,true,true,true,true,NOW));
 }
 static void outcome(){
  var id=UUID.randomUUID();
  var good=RegistrationOutcomeEnvelope.from(new RegistrationJdbc.Result(RegistrationJdbc.State.COMMITTED,id),true);
  ok(good.status()==RegistrationOutcomeEnvelope.Status.COMMITTED&&!good.retrySameCommandOnly());
  ok(RegistrationOutcomeEnvelope.from(new RegistrationJdbc.Result(RegistrationJdbc.State.COMMITTED,id),false).status()==RegistrationOutcomeEnvelope.Status.UNKNOWN_OUTCOME);
  ok(RegistrationOutcomeEnvelope.from(new RegistrationJdbc.Result(RegistrationJdbc.State.UNAVAILABLE,null),false).retrySameCommandOnly());
  ok(RegistrationOutcomeEnvelope.from(new RegistrationJdbc.Result(RegistrationJdbc.State.REJECTED,null),false).status()==RegistrationOutcomeEnvelope.Status.REJECTED);
  ok(RegistrationOutcomeEnvelope.from(null,false).status()==RegistrationOutcomeEnvelope.Status.UNKNOWN_OUTCOME);
  ok(!good.code().contains(id.toString()));
 }
 public static void main(String[] args) throws Exception {
  if(args.length!=1)throw new IllegalArgumentException("one batch required");
  switch(args[0]){case "P05-B0011"->proofData();case "P05-B0012"->cryptography();case "P05-B0013"->repository();case "P05-B0014"->admission();case "P05-B0015"->outcome();default->throw new IllegalArgumentException("unknown batch");}
  System.out.println("PASS "+args[0]+" "+assertions+" assertions; pure Java/JDBC proxy only");
 }
}
