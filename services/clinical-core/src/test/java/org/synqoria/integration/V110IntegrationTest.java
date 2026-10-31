package org.synqoria.integration;
import com.sun.net.httpserver.HttpServer;
import org.synqoria.http.*;
import org.synqoria.security.*;
import org.synqoria.persistence.*;
import org.synqoria.facility.*;
import java.lang.reflect.*;import java.net.*;import java.net.http.*;import java.sql.*;import java.time.*;import java.util.*;
/** Dependency-free integration tests. JDBC is proxied; NO live PostgreSQL/OIDC/device testing. */
public final class V110IntegrationTest {
 static int count=0;
 static void check(boolean ok,String description){if(!ok)throw new AssertionError(description);count++;}
 static <T extends Throwable> void throwsType(Class<T> c,Runnable r){try{r.run();throw new AssertionError("Expected "+c);}catch(RuntimeException e){check(c.isInstance(e),"expected exception "+c);}}
 static Object fallback(Class<?> t){if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;return null;}
 @SuppressWarnings("unchecked")static <T>T proxy(Class<T> type,InvocationHandler handler){return(T)java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);}
 static final UUID TENANT=UUID.randomUUID(),FACILITY=UUID.randomUUID(),CELL=UUID.randomUUID(),ACTOR=UUID.randomUUID(),PATIENT=UUID.randomUUID();
 static final Instant NOW=Instant.parse("2026-10-30T00:00:00Z");
 static final String TOKEN="VERIFIED_TEST_ONLY_TOKEN_123";
 static final TrustedAccessContext ACTX=new TrustedAccessContext(TENANT,FACILITY,ACTOR,Set.of("patient:read","facility:view"),Set.of(PATIENT));
 static final class MockDb {
  boolean failInsert=false,failCommit=false,wrongRow=false,missing=false,committed=false,rolled=false;
  UUID auditCorrelation=null;int inserted=0;
  Connection connection(){return proxy(Connection.class,(o,m,a)->switch(m.getName()){
   case "getAutoCommit","isReadOnly"->false;
   case "prepareStatement"->statement((String)a[0]);
   case "commit"->{if(failCommit)throw new SQLException("failed");committed=true;yield null;}
   case "rollback"->{rolled=true;yield null;}
   default->fallback(m.getReturnType());
  });}
  PreparedStatement statement(String sql){boolean audit=sql.contains("INSERT INTO clinical_read_audit");return proxy(PreparedStatement.class,(o,m,a)->switch(m.getName()){
   case "setObject"->{if(audit&&((int)a[0])==8)auditCorrelation=(UUID)a[1];yield null;}
   case "executeQuery"->rows();
   case "executeUpdate"->{inserted++;if(failInsert)throw new SQLException("audit failed");yield 1;}
   default->fallback(m.getReturnType());
  });}
  ResultSet rows(){return proxy(ResultSet.class,new InvocationHandler(){int n=0;public Object invoke(Object o,Method m,Object[] a){return switch(m.getName()){
   case "next"->!missing&&++n==1;
   case "getObject"->switch((String)a[0]){case "tenant_id"->wrongRow?UUID.randomUUID():TENANT;case "facility_id"->FACILITY;case "patient_id"->PATIENT;default->null;};
   case "getString"->"verified";case "getLong"->1L;default->fallback(m.getReturnType());
  };}});}
 }
 static JdbcPatientReadPort port(MockDb db){return new JdbcPatientReadPort(new TransactionalPatientReadJdbc(db::connection,(a,p,now)->true));}
 static TrustedSessionAdmission session(){return new TrustedSessionAdmission(token->TOKEN.equals(token)?Optional.of(new TrustedSessionAdmission.VerifiedIdentity("issuer","audience",ACTOR,TENANT,FACILITY,NOW.minusSeconds(20),NOW.plusSeconds(100),true,false)):Optional.empty(),id->Optional.of(new TrustedSessionAdmission.Privileges(Set.of("patient:read","facility:view"),Set.of(PATIENT),true,true)),"issuer","audience",(id,at)->true);}
 static void b24(){var db=new MockDb();var result=port(db).read(ACTX,PATIENT,UUID.randomUUID(),NOW);check(result.status()==AuditedPatientAccess.Status.OK,"committed JDBC port");check(db.committed&&db.inserted==1,"same transaction audit before response");db=new MockDb();db.failCommit=true;check(port(db).read(ACTX,PATIENT,UUID.randomUUID(),NOW).status()==AuditedPatientAccess.Status.UNAVAILABLE&&!db.committed&&db.rolled,"commit fail-closed");db=new MockDb();db.failInsert=true;check(port(db).read(ACTX,PATIENT,UUID.randomUUID(),NOW).status()==AuditedPatientAccess.Status.UNAVAILABLE&&!db.committed,"audit fail-closed");db=new MockDb();check(port(db).read(new TrustedAccessContext(TENANT,FACILITY,ACTOR,Set.of(),Set.of()),PATIENT,UUID.randomUUID(),NOW).status()==AuditedPatientAccess.Status.NOT_DISCLOSED&&db.inserted==0,"no pre-authenticated DB query");}
 static void b25() throws Exception {
  var db=new MockDb();var gateway=new ClinicalHttpGateway(new BearerAdmission(session()),ctx->Optional.of(new FacilityBinding(CELL,TENANT,FACILITY,"ACTIVE",3)),port(db),Clock.fixed(NOW,ZoneOffset.UTC),IntegrationReadiness.unavailable());
  var server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);server.createContext("/",gateway);server.start();
  try{var uri=URI.create("http://localhost:"+server.getAddress().getPort()+"/api/v1/patients/"+PATIENT);var request=HttpRequest.newBuilder(uri).header("Authorization","Bearer "+TOKEN).GET().build();var res=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());check(res.statusCode()==200,"HTTP JDBC adapter 200");check(db.committed,"commit before response");check(UUID.fromString(res.headers().firstValue("X-Request-ID").orElseThrow()).equals(db.auditCorrelation),"HTTP header matches database audit correlation");}finally{server.stop(0);}
 }
 static void b26(){throwsType(IllegalArgumentException.class,()->new IntegrationReadiness(List.of(()->true)));check(!IntegrationReadiness.unavailable().ready(),"no implicit readiness");check(new IntegrationReadiness(List.of(()->true,()->true,()->true)).ready(),"all fresh probes pass");check(!new IntegrationReadiness(List.of(()->true,()->false,()->true)).ready(),"failed DB denies");check(!new IntegrationReadiness(List.of(()->true,()->{throw new IllegalStateException();},()->true)).ready(),"probe error denies");}
 static void b27(){check(ClinicalPayload.patient(new PatientRecordEnvelope(TENANT,FACILITY,PATIENT,"verified",1)).contains("\"revision\":1"),"validated JSON");check(ClinicalPayload.facility(new FacilityBinding(CELL,TENANT,FACILITY,"ACTIVE",2)).contains("\"revision\":2"),"facility DTO");throwsType(IllegalArgumentException.class,()->ClinicalPayload.facility(new FacilityBinding(CELL,TENANT,FACILITY,"SUSPENDED",2)));}
 static void b15(){throwsType(NullPointerException.class,()->new ProductionAdmissionBoundary(t->Optional.empty(),i->Optional.empty(),null,"i","a"));var noEpoch=new ProductionAdmissionBoundary(t->Optional.of(new TrustedSessionAdmission.VerifiedIdentity("issuer","audience",ACTOR,TENANT,FACILITY,NOW.minusSeconds(10),NOW.plusSeconds(100),true,false)),id->Optional.of(new TrustedSessionAdmission.Privileges(Set.of("patient:read"),Set.of(PATIENT),true,true)),(id,n)->false,"issuer","audience");check(noEpoch.admit(TOKEN,NOW).isEmpty(),"revocation denied");var admitted=new ProductionAdmissionBoundary(t->t.equals(TOKEN)?Optional.of(new TrustedSessionAdmission.VerifiedIdentity("issuer","audience",ACTOR,TENANT,FACILITY,NOW.minusSeconds(10),NOW.plusSeconds(100),true,false)):Optional.empty(),id->Optional.of(new TrustedSessionAdmission.Privileges(Set.of("patient:read"),Set.of(PATIENT),true,true)),(id,n)->true,"issuer","audience");check(admitted.admit(TOKEN,NOW).isPresent(),"injected test verifier only");check(admitted.admit("wrong-credential-token",NOW).isEmpty(),"forged token rejected");}
 static void b16(){var ok=new AuditedPatientAccess.Result(AuditedPatientAccess.Status.OK,new PatientRecordEnvelope(TENANT,FACILITY,PATIENT,"verified",1));check(PatientDisclosureFence.release(ACTX,PATIENT,ok).isPresent(),"committed scoped record");check(PatientDisclosureFence.release(ACTX,UUID.randomUUID(),ok).isEmpty(),"requested ID mismatch withheld");check(PatientDisclosureFence.release(new TrustedAccessContext(TENANT,FACILITY,ACTOR,Set.of(),Set.of()),PATIENT,ok).isEmpty(),"no grants withheld");check(PatientDisclosureFence.release(ACTX,PATIENT,new AuditedPatientAccess.Result(AuditedPatientAccess.Status.UNAVAILABLE,null)).isEmpty(),"no disclosure on audit failure");}
 static void b11(){
  final boolean[] wrong={false},duplicate={false},missing={false};
  JdbcFacilityGate.Connections pool=()->proxy(Connection.class,(o,m,a)->switch(m.getName()){
   case "prepareStatement"->{check(JdbcFacilityGate.SQL.contains("JOIN hospital_tenants")&&JdbcFacilityGate.SQL.contains("JOIN hospital_cells"),"hierarchical statuses joined");yield proxy(PreparedStatement.class,(op,mp,ap)->switch(mp.getName()){
    case "setObject","setQueryTimeout","setMaxRows"->null;
    case "executeQuery"->proxy(ResultSet.class,new InvocationHandler(){int n=0;public Object invoke(Object or,Method mr,Object[] ar){return switch(mr.getName()){
     case "next"->!missing[0]&&++n<=(duplicate[0]?2:1);
     case "getObject"->switch((String)ar[0]){case "tenant_id"->wrong[0]?UUID.randomUUID():TENANT;case "facility_id"->FACILITY;case "cell_id"->CELL;default->null;};
     case "getString"->"ACTIVE";case "getLong"->7L;default->fallback(mr.getReturnType());
    };}});
    default->fallback(mp.getReturnType());});}
   default->fallback(m.getReturnType());});
  var gate=new JdbcFacilityGate(pool,CELL);check(gate.resolve(ACTX).isPresent(),"active current DB binding");wrong[0]=true;check(gate.resolve(ACTX).isEmpty(),"cross-tenant row withheld");wrong[0]=false;duplicate[0]=true;check(gate.resolve(ACTX).isEmpty(),"duplicate row withheld");duplicate[0]=false;missing[0]=true;check(gate.resolve(ACTX).isEmpty(),"unknown facility withheld");
 }
 public static void main(String[] args)throws Exception {if(args.length!=1)throw new IllegalArgumentException("select one batch");switch(args[0]){
  case "P01-B0024"->b24();case "P01-B0025"->b25();case "P01-B0026"->b26();case "P01-B0027"->b27();case "P03-B0015"->b15();case "P03-B0016"->b16();case "P04-B0011"->b11();default->throw new IllegalArgumentException("Unknown batch");}
  System.out.println("PASS "+args[0]+": "+count+" assertions (JDBC mocks / injected TEST verifier only)");}
}
