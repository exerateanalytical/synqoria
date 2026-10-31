package org.synqoria.registration;
import org.synqoria.facility.ScopedDbTransaction;
import org.synqoria.security.TrustedAccessContext;
import java.lang.reflect.*;import java.sql.*;import java.time.*;import java.util.*;
/** Focused executable tests. JDBC uses proxies, not PostgreSQL. Run one batch ID at a time. */
public final class V130RegistrationSafetyTest {
 static int assertions;
 static void ok(boolean b){assertions++;if(!b)throw new AssertionError("Assertion "+assertions);}
 static void bad(Runnable r){try{r.run();throw new AssertionError("Expected rejection");}catch(IllegalArgumentException e){assertions++;}}
 static final Instant NOW=Instant.parse("2026-10-30T00:00:00Z");
 static final UUID TENANT=UUID.randomUUID(),FACILITY=UUID.randomUUID(),ACTOR=UUID.randomUUID(),REVIEWER=UUID.randomUUID();
 static final byte[] SECRET=new byte[32];
 static {for(int i=0;i<SECRET.length;i++)SECRET[i]=(byte)(i+1);}
 static final RegistrationDraft DRAFT=new RegistrationDraft("Amina",null,null,RegistrationDraft.BirthInformation.UNKNOWN,"fr",true);
 static TrustedAccessContext actor(){return new TrustedAccessContext(TENANT,FACILITY,ACTOR,Set.of("patient:register","patient:identity-review"),Set.of());}
 static RegistrationCommand cmd(){return new RegistrationCommand(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),RegistrationFingerprint.sign(SECRET,TENANT,FACILITY,DRAFT),DRAFT,NOW);}
 static void fingerprint(){
  byte[] a=RegistrationFingerprint.sign(SECRET,TENANT,FACILITY,DRAFT),b=RegistrationFingerprint.sign(SECRET,TENANT,FACILITY,DRAFT);
  ok(a.length==32&&RegistrationFingerprint.same(a,b));ok(!RegistrationFingerprint.same(a,RegistrationFingerprint.sign(SECRET,TENANT,UUID.randomUUID(),DRAFT)));
  ok(!RegistrationFingerprint.same(a,RegistrationFingerprint.sign(SECRET,UUID.randomUUID(),FACILITY,DRAFT)));
  var changed=new RegistrationDraft("Amin",null,null,RegistrationDraft.BirthInformation.UNKNOWN,"fr",true);
  ok(!RegistrationFingerprint.same(a,RegistrationFingerprint.sign(SECRET,TENANT,FACILITY,changed)));
  byte[] key=SECRET.clone();key[0]=9;ok(!RegistrationFingerprint.same(a,RegistrationFingerprint.sign(key,TENANT,FACILITY,DRAFT)));
  bad(()->RegistrationFingerprint.sign(new byte[8],TENANT,FACILITY,DRAFT));bad(()->RegistrationFingerprint.sign(new byte[32],TENANT,FACILITY,DRAFT));ok(!RegistrationFingerprint.same(a,null));
 }
 static void issuance(){
  var issuer=new RegistrationCommandIssuer(SECRET);var first=issuer.issue(actor(),DRAFT,NOW);var second=issuer.issue(actor(),DRAFT,NOW);
  ok(!first.patientId().equals(second.patientId())&&!first.commandId().equals(second.commandId()));
  ok(first.patientId().version()==4&&first.commandId().version()==4&&first.correlationId().version()==4);
  ok(RegistrationFingerprint.same(first.fingerprintHmac(),second.fingerprintHmac()));
  bad(()->new RegistrationCommandIssuer(new byte[16]));bad(()->new RegistrationCommandIssuer(new byte[32]));bad(()->issuer.issue(null,DRAFT,NOW));
  bad(()->issuer.issue(actor(),new RegistrationDraft("x",LocalDate.parse("2027-01-01"),null,RegistrationDraft.BirthInformation.KNOWN,"en",true),NOW));
 }
 static void review(){
  var c=cmd();var permit=new RegistrationReviewPermit(TENANT,FACILITY,REVIEWER,ACTOR,UUID.randomUUID(),c.fingerprintHmac(),NOW.plusSeconds(60),9,false);
  ok(permit.permits(actor(),c,NOW,9));ok(!permit.permits(actor(),c,NOW.plusSeconds(61),9));
  ok(!permit.permits(actor(),c,NOW,10));ok(!permit.permits(actor(),new RegistrationCommand(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),new byte[32],DRAFT,NOW),NOW,9));
  var self=new RegistrationReviewPermit(TENANT,FACILITY,ACTOR,ACTOR,UUID.randomUUID(),c.fingerprintHmac(),NOW.plusSeconds(60),9,false);
  ok(!self.permits(actor(),c,NOW,9));
  var unresolved=new RegistrationReviewPermit(TENANT,FACILITY,REVIEWER,ACTOR,UUID.randomUUID(),c.fingerprintHmac(),NOW.plusSeconds(60),9,true);
  ok(!unresolved.permits(actor(),c,NOW,9));
  var other=new TrustedAccessContext(TENANT,UUID.randomUUID(),ACTOR,Set.of("patient:register"),Set.of());ok(!permit.permits(other,c,NOW,9));
  byte[] mutated=permit.fingerprint();mutated[0]++;ok(permit.permits(actor(),c,NOW,9));
 }
 static void replay(){
  var c=cmd();var claim=new RegistrationReplayDecision.Claim(TENANT,FACILITY,c.commandId(),c.patientId(),c.fingerprintHmac(),true);
  ok(RegistrationReplayDecision.decide(null,TENANT,FACILITY,c,true)==RegistrationReplayDecision.Decision.NEW_COMMAND);
  ok(RegistrationReplayDecision.decide(claim,TENANT,FACILITY,c,true)==RegistrationReplayDecision.Decision.SAFE_REPLAY);
  ok(RegistrationReplayDecision.decide(claim,TENANT,FACILITY,c,false)==RegistrationReplayDecision.Decision.UNAVAILABLE);
  ok(RegistrationReplayDecision.decide(claim,TENANT,UUID.randomUUID(),c,true)==RegistrationReplayDecision.Decision.CONFLICT);
  var pending=new RegistrationReplayDecision.Claim(TENANT,FACILITY,c.commandId(),c.patientId(),c.fingerprintHmac(),false);
  ok(RegistrationReplayDecision.decide(pending,TENANT,FACILITY,c,true)==RegistrationReplayDecision.Decision.UNAVAILABLE);
  var swapped=new RegistrationCommand(UUID.randomUUID(),c.commandId(),c.correlationId(),c.fingerprintHmac(),DRAFT,NOW);
  ok(RegistrationReplayDecision.decide(claim,TENANT,FACILITY,swapped,true)==RegistrationReplayDecision.Decision.CONFLICT);
 }
 static void duplicate(){
  byte[] token=new byte[32];var plan=DuplicateCandidateQuery.plan(actor(),TENANT,FACILITY,token,20);
  ok(plan.isPresent()&&plan.get().sql().contains("LIMIT ?")&&plan.get().parameters().size()==4);
  ok(DuplicateCandidateQuery.plan(actor(),TENANT,FACILITY,token,21).isEmpty());
  ok(DuplicateCandidateQuery.plan(actor(),TENANT,UUID.randomUUID(),token,5).isEmpty());
  ok(DuplicateCandidateQuery.plan(actor(),TENANT,FACILITY,new byte[16],5).isEmpty());
  var noReview=new TrustedAccessContext(TENANT,FACILITY,ACTOR,Set.of("patient:register"),Set.of());
  ok(DuplicateCandidateQuery.plan(noReview,TENANT,FACILITY,token,5).isEmpty());
  ok(!plan.get().sql().contains("Amina"));
 }
 @SuppressWarnings("unchecked")static <T>T proxy(Class<T> type,InvocationHandler h){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},h);}
 static void dbScope() throws Exception{
  List<String> statements=new ArrayList<>();List<String> binds=new ArrayList<>();boolean[] auto={false};
  ResultSet rows=proxy(ResultSet.class,(p,m,a)->m.getName().equals("next")?true:null);
  PreparedStatement stmt=proxy(PreparedStatement.class,(p,m,a)->switch(m.getName()){
   case "setString"->{binds.add(a[0]+":"+a[1]);yield null;}
   case "executeQuery"->rows;default->null;});
  Connection connection=proxy(Connection.class,(p,m,a)->switch(m.getName()){
   case "getAutoCommit"->auto[0];case "isReadOnly"->false;
   case "prepareStatement"->{statements.add((String)a[0]);yield stmt;}default->null;});
  ScopedDbTransaction.bind(connection,actor());ok(statements.size()==1&&statements.get(0).contains("set_config('app.tenant_id', ?, true)"));
  ok(binds.size()==3&&binds.get(0).endsWith(TENANT.toString())&&binds.get(1).endsWith(FACILITY.toString()));
  auto[0]=true;try{ScopedDbTransaction.bind(connection,actor());throw new AssertionError("Auto-commit allowed");}catch(SQLException expected){assertions++;}
  try{ScopedDbTransaction.bind(connection,null);throw new AssertionError("Missing identity allowed");}catch(SQLException expected){assertions++;}
 }
 public static void main(String[] args)throws Exception{
  if(args.length!=1)throw new IllegalArgumentException("Pass one batch ID");
  switch(args[0]){
   case "P05-B0006"->fingerprint();case "P05-B0007"->issuance();case "P05-B0008"->review();
   case "P05-B0009"->replay();case "P05-B0010"->duplicate();case "P04-B0012"->dbScope();
   default->throw new IllegalArgumentException("Unknown batch");
  }
  System.out.println("PASS "+args[0]+": "+assertions+" assertions (unit/proxy only)");
 }
}
