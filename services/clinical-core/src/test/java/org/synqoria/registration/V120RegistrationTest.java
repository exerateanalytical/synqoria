package org.synqoria.registration;
import java.lang.reflect.*;import java.sql.*;import java.time.*;import java.util.*;
import org.synqoria.security.TrustedAccessContext;
/** Unit test using JDBC proxies; does not exercise PostgreSQL grants or RLS. */
public final class V120RegistrationTest {
 static int checks=0;static void ok(boolean b){if(!b)throw new AssertionError("Registration check "+checks);checks++;}
 static Object def(Class<?> t){if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;return null;}
 @SuppressWarnings("unchecked")static <T>T proxy(Class<T> t,InvocationHandler h){return (T)Proxy.newProxyInstance(t.getClassLoader(),new Class<?>[]{t},h);}
 static class FakeDb {
  boolean auto=false,readOnly=false,committed=false,rolled=false,closed=false,failCommit=false;int failAt=0,count=0;List<String> sql=new ArrayList<>();
  Connection open(){return proxy(Connection.class,(self,m,a)->switch(m.getName()){
   case "getAutoCommit"->auto;case "isReadOnly"->readOnly;
   case "prepareStatement"->{sql.add((String)a[0]);yield statement();}
   case "commit"->{if(failCommit)throw new SQLException("commit");committed=true;yield null;}
   case "rollback"->{rolled=true;yield null;}case "close"->{closed=true;yield null;}
   default->def(m.getReturnType());});}
  PreparedStatement statement(){return proxy(PreparedStatement.class,(self,m,a)->switch(m.getName()){
   case "setObject","setString","setTimestamp","setDate","setBytes","setQueryTimeout","close"->null;
   case "executeUpdate"->{count++;if(count==failAt)throw new SQLException("injected write failure");yield 1;}
   default->def(m.getReturnType());});}
 }
 static void bad(Runnable fn){try{fn.run();throw new AssertionError("Should reject");}catch(IllegalArgumentException e){checks++;}}
 public static void main(String[] unused){
  Instant now=Instant.parse("2026-10-30T00:00:00Z");UUID tenant=UUID.randomUUID(),facility=UUID.randomUUID();
  var actor=new TrustedAccessContext(tenant,facility,UUID.randomUUID(),Set.of("patient:register","patient:identity-review"),Set.of());
  var draft=new RegistrationDraft(null,null,null,RegistrationDraft.BirthInformation.UNKNOWN,"en",true);
  ok(draft.legalName()==null&&draft.plausibleAt(LocalDate.parse("2026-10-30")));
  bad(()->new RegistrationDraft("",null,null,RegistrationDraft.BirthInformation.UNKNOWN,"en",true));
  bad(()->new RegistrationDraft("A",LocalDate.now(),5,RegistrationDraft.BirthInformation.KNOWN,"en",true));
  bad(()->new RegistrationDraft(null,null,131,RegistrationDraft.BirthInformation.ESTIMATED_AGE,"en",true));
  bad(()->new RegistrationDraft(null,null,null,RegistrationDraft.BirthInformation.KNOWN,"en",true));
  ok(!new RegistrationDraft(null,LocalDate.parse("2027-01-01"),null,RegistrationDraft.BirthInformation.KNOWN,"en",true).plausibleAt(LocalDate.parse("2026-10-30")));
  ok(DuplicateReviewGate.decide(false,false,false)==DuplicateReviewGate.Decision.REVIEW_REQUIRED);
  ok(DuplicateReviewGate.decide(true,true,false)==DuplicateReviewGate.Decision.REVIEW_REQUIRED);
  ok(DuplicateReviewGate.decide(true,false,true)==DuplicateReviewGate.Decision.BLOCK_AUTOMERGE);
  ok(DuplicateReviewGate.decide(true,false,false)==DuplicateReviewGate.Decision.ALLOW_PROVISIONAL);
  ok(RegistrationAuthorization.canRegister(actor,facility,true,true,true));
  ok(!RegistrationAuthorization.canRegister(actor,UUID.randomUUID(),true,true,true));
  ok(!RegistrationAuthorization.canRegister(actor,facility,false,true,true));
  ok(!RegistrationAuthorization.canRegister(actor,facility,true,false,true));
  ok(!RegistrationAuthorization.canRegister(actor,facility,true,true,false));
  var noRights=new TrustedAccessContext(tenant,facility,UUID.randomUUID(),Set.of("patient:register"),Set.of());
  ok(!RegistrationAuthorization.canRegister(noRights,facility,true,true,true));
  var cmd=new RegistrationCommand(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),new byte[32],draft,now);
  ok(RegistrationRequestPolicy.allowed(actor,cmd,true,true,true,false,false,now));
  ok(!RegistrationRequestPolicy.allowed(actor,cmd,true,true,true,true,false,now));
  ok(!RegistrationRequestPolicy.allowed(actor,cmd,true,true,true,false,true,now));
  ok(!RegistrationRequestPolicy.allowed(actor,cmd,true,true,true,false,false,now.plusSeconds(301)));
  byte[] fingerprint=cmd.fingerprintHmac();fingerprint[0]=12;ok(cmd.fingerprintHmac()[0]==0);
  bad(()->new RegistrationCommand(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),new byte[31],draft,now));
  FakeDb good=new FakeDb();var result=new RegistrationJdbc(good::open).create(actor,cmd,true,true,true,false,now);
  ok(result.state()==RegistrationJdbc.State.COMMITTED&&cmd.patientId().equals(result.patientId()));
  ok(good.committed&&!good.rolled&&good.closed&&good.count==5);
  ok(good.sql.stream().anyMatch(s->s.contains("patient_master_index")));
  ok(good.sql.stream().anyMatch(s->s.contains("patient_registration_audit")));
  ok(good.sql.stream().anyMatch(s->s.contains("clinical_outbox")));
  ok(good.sql.stream().anyMatch(s->s.contains("patient_registration_command_claim")));
  for(int i=1;i<=5;i++){FakeDb fail=new FakeDb();fail.failAt=i;ok(new RegistrationJdbc(fail::open).create(actor,cmd,true,true,true,false,now).state()==RegistrationJdbc.State.UNAVAILABLE&&fail.rolled&&!fail.committed);}
  FakeDb commit=new FakeDb();commit.failCommit=true;ok(new RegistrationJdbc(commit::open).create(actor,cmd,true,true,true,false,now).state()==RegistrationJdbc.State.UNAVAILABLE&&commit.rolled);
  FakeDb auto=new FakeDb();auto.auto=true;ok(new RegistrationJdbc(auto::open).create(actor,cmd,true,true,true,false,now).state()==RegistrationJdbc.State.UNAVAILABLE&&!auto.committed);
  FakeDb readOnly=new FakeDb();readOnly.readOnly=true;ok(new RegistrationJdbc(readOnly::open).create(actor,cmd,true,true,true,false,now).state()==RegistrationJdbc.State.UNAVAILABLE&&!readOnly.committed);
  FakeDb deny=new FakeDb();ok(new RegistrationJdbc(deny::open).create(actor,cmd,true,true,true,true,now).state()==RegistrationJdbc.State.REJECTED&&deny.count==0);
  System.out.println("PASS v0.12.0 registration: "+checks+" policy/atomicity assertions (mock JDBC only)");
 }
}
