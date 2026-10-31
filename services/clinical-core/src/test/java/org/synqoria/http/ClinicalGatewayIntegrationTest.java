package org.synqoria.http;
import com.sun.net.httpserver.*;
import org.synqoria.security.*;
import org.synqoria.facility.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
/** Real loopback HTTP tests; injected verifier is test-only and NOT OIDC. */
public final class ClinicalGatewayIntegrationTest {
 private static int checks=0;
 static void ok(boolean result,String message){if(!result)throw new AssertionError(message);checks++;}
 static final UUID TENANT=UUID.randomUUID(),FACILITY=UUID.randomUUID(),CELL=UUID.randomUUID(),ACTOR=UUID.randomUUID(),PATIENT=UUID.randomUUID();
 static final Instant NOW=Instant.parse("2026-10-29T20:00:00Z");
 static final String TOKEN="TEST_VERIFIER_TOKEN_12345";
 static final String PATIENT_PATH="/api/v1/patients/"+PATIENT;
 static void testPath(){
  ok(CanonicalRequestPath.parse(URI.create(PATIENT_PATH)).id().equals(PATIENT),"canonical uuid");
  for(var path:List.of("/api/v1/patients/nope",PATIENT_PATH+"/",PATIENT_PATH+"?test=1","/api/v1/patients/%2F", "/api/v1/patients/../facility/context", "/api/v1//patients/"+PATIENT))
   ok(CanonicalRequestPath.parse(URI.create(path))==null,"ambiguous path "+path);
  ok(CanonicalRequestPath.parse(URI.create("/health/ready")).resource().equals("ready"),"ready route");
  System.out.println("PASS P01-B0014: canonical URI assertions");
 }
 static TrustedSessionAdmission admissions(){
  return new TrustedSessionAdmission(token-> TOKEN.equals(token)?Optional.of(new TrustedSessionAdmission.VerifiedIdentity("test-issuer","test-audience",ACTOR,TENANT,FACILITY,NOW.minusSeconds(60),NOW.plusSeconds(900),true,false)):Optional.empty(),
   identity->Optional.of(new TrustedSessionAdmission.Privileges(Set.of("patient:read","facility:view"),Set.of(PATIENT),true,true)),"test-issuer","test-audience");
 }
 static void testBearer(){
  var bearer=new BearerAdmission(admissions());
  var headers=new Headers();headers.add("X-Tenant-ID",TENANT.toString());
  ok(bearer.admit(headers,NOW).isEmpty(),"forged tenant header not auth");
  headers.add("Authorization","Basic abc");ok(bearer.admit(headers,NOW).isEmpty(),"basic rejected");
  headers.remove("Authorization");headers.add("Authorization","Bearer "+TOKEN);
  ok(bearer.admit(headers,NOW).isPresent(),"validated bearer admitted");
  headers.add("Authorization","Bearer "+TOKEN);ok(bearer.admit(headers,NOW).isEmpty(),"duplicate bearer rejected");
  headers.remove("Authorization");headers.add("Authorization","Bearer WRONG_TOKEN_012345");ok(bearer.admit(headers,NOW).isEmpty(),"unknown bearer rejected");
  System.out.println("PASS P03-B0009: verifier admission assertions");
 }
 static void testFacility(){
  var gate=new CellFacilityGate(new FacilityDirectory(List.of(new FacilityBinding(CELL,TENANT,FACILITY,"ACTIVE",2))),CELL);
  var ctx=new TrustedAccessContext(TENANT,FACILITY,ACTOR,Set.of("facility:view"),Set.of());
  ok(gate.resolve(ctx).isPresent(),"active matching cell");
  ok(new CellFacilityGate(new FacilityDirectory(List.of(new FacilityBinding(CELL,TENANT,FACILITY,"SUSPENDED",2))),CELL).resolve(ctx).isEmpty(),"inactive denied");
  ok(new CellFacilityGate(new FacilityDirectory(List.of(new FacilityBinding(UUID.randomUUID(),TENANT,FACILITY,"ACTIVE",2))),CELL).resolve(ctx).isEmpty(),"wrong cell denied");
  ok(gate.resolve(new TrustedAccessContext(UUID.randomUUID(),FACILITY,ACTOR,Set.of(),Set.of())).isEmpty(),"wrong tenant denied");
  System.out.println("PASS P04-B0007: facility-cell assertions");
 }
 static void testAuditedPatient(){
  var ctx=new TrustedAccessContext(TENANT,FACILITY,ACTOR,Set.of("patient:read"),Set.of(PATIENT));
  PatientReadService service=new PatientReadService((t,id)->Optional.of(new PatientRecordEnvelope(TENANT,FACILITY,PATIENT,"verified",4)),(caller,p,op)->new TrustedClinicalAccessEvidence(TENANT,FACILITY,ACTOR,PATIENT,NOW.minusSeconds(1),NOW.plusSeconds(60),true,true,true,true,true,true,true,Set.of("patient:read")));
  var auditCalls=new AtomicInteger();
  var audited=new AuditedPatientAccess(service,(a,t,f,p,o,at)->{auditCalls.incrementAndGet();return true;});
  ok(audited.read(ctx,PATIENT,NOW).status()==AuditedPatientAccess.Status.OK,"authorized and audited");
  ok(auditCalls.get()==1,"audit committed");
  ok(new AuditedPatientAccess(service,(a,t,f,p,o,at)->false).read(ctx,PATIENT,NOW).status()==AuditedPatientAccess.Status.UNAVAILABLE,"audit failure denies release");
  ok(audited.read(ctx,UUID.randomUUID(),NOW).status()==AuditedPatientAccess.Status.NOT_DISCLOSED,"not authorized hidden");
  ok(auditCalls.get()==1,"denied not recorded as successful read");
  System.out.println("PASS P03-B0010: audited patient read assertions");
 }
 static HttpResponse<String> send(HttpClient client,URI uri,String path,String token,String method,boolean spoof) throws Exception {
  var b=HttpRequest.newBuilder(uri.resolve(path)).timeout(Duration.ofSeconds(3)).header("Accept","application/json");
  if(token!=null)b.header("Authorization","Bearer "+token);
  if(spoof)b.header("X-Tenant-ID",UUID.randomUUID().toString());
  b.method(method,HttpRequest.BodyPublishers.noBody());
  return client.send(b.build(),HttpResponse.BodyHandlers.ofString());
 }
 static void testHttp() throws Exception {
  var access=admissions();
  var directory=new FacilityDirectory(List.of(new FacilityBinding(CELL,TENANT,FACILITY,"ACTIVE",2)));
  var patientService=new PatientReadService((tenant,patient)->Optional.of(new PatientRecordEnvelope(TENANT,FACILITY,PATIENT,"provisional",3)),
   (caller,patient,op)->new TrustedClinicalAccessEvidence(TENANT,FACILITY,ACTOR,PATIENT,NOW.minusSeconds(3),NOW.plusSeconds(60),true,true,true,true,true,true,true,Set.of("patient:read")));
  var gateway=new ClinicalHttpGateway(new BearerAdmission(access),new CellFacilityGate(directory,CELL),new AuditedPatientAccess(patientService,(a,t,f,p,o,n)->true),Clock.fixed(NOW,ZoneOffset.UTC));
  var server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);server.createContext("/",gateway);server.start();
  try {
   var base=new URI("http://localhost:"+server.getAddress().getPort());var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
   var okPatient=send(client,base,PATIENT_PATH,TOKEN,"GET",true);
   ok(okPatient.statusCode()==200,"authenticated patient HTTP success");
   ok(okPatient.headers().firstValue("X-Request-ID").orElse("").matches("[0-9a-f-]{36}"),"server generated trace id");
   var secondPatient=send(client,base,PATIENT_PATH,TOKEN,"GET",false);
   ok(!okPatient.headers().firstValue("X-Request-ID").equals(secondPatient.headers().firstValue("X-Request-ID")),"trace ids unique");
   var invalidAccept=HttpRequest.newBuilder(base.resolve(PATIENT_PATH)).header("Accept","text/html").header("Authorization","Bearer "+TOKEN).GET().build();
   ok(client.send(invalidAccept,HttpResponse.BodyHandlers.ofString()).statusCode()==406,"non-json accept rejected");
   var crossSite=HttpRequest.newBuilder(base.resolve(PATIENT_PATH)).header("Origin","https://malicious.invalid").header("Authorization","Bearer "+TOKEN).GET().build();
   ok(client.send(crossSite,HttpResponse.BodyHandlers.ofString()).statusCode()==400,"cross origin request rejected");
   var bodyGet=HttpRequest.newBuilder(base.resolve(PATIENT_PATH)).header("Authorization","Bearer "+TOKEN).method("GET",HttpRequest.BodyPublishers.ofString("payload")).build();
   ok(client.send(bodyGet,HttpResponse.BodyHandlers.ofString()).statusCode()==400,"GET with body rejected");

   ok(okPatient.body().contains(PATIENT.toString()),"patient response id");
   ok(okPatient.headers().firstValue("Cache-Control").orElse("").contains("no-store"),"patient response no store");
   ok("nosniff".equals(okPatient.headers().firstValue("X-Content-Type-Options").orElse("")),"no sniff");
   ok(okPatient.headers().firstValue("Access-Control-Allow-Origin").isEmpty(),"no wildcard CORS");
   var unauth=send(client,base,PATIENT_PATH,null,"GET",true);
   ok(unauth.statusCode()==401,"spoof header without bearer rejected");
   var unknown=send(client,base,PATIENT_PATH,TOKEN,"GET",false);
   var missing=send(client,base,"/api/v1/patients/"+UUID.randomUUID(),TOKEN,"GET",false);
   ok(missing.statusCode()==404,"unauthorized patient hidden");
   ok(missing.body().equals(send(client,base,"/api/v1/patients/"+UUID.randomUUID(),TOKEN,"GET",false).body()),"same non-disclosure problem");
   ok(unknown.statusCode()==200,"expected patient still authorized");
   var facility=send(client,base,"/api/v1/facility/context",TOKEN,"GET",true);
   ok(facility.statusCode()==200&&facility.body().contains(FACILITY.toString()),"facility scope from verified identity");
   ok(send(client,base,PATIENT_PATH,TOKEN,"POST",false).statusCode()==405,"writes disabled");
   ok(send(client,base,PATIENT_PATH+"?include=all",TOKEN,"GET",false).statusCode()==404,"query disallowed");
   ok(send(client,base,"/health/live",null,"GET",false).statusCode()==200,"liveness != readiness");
   ok(send(client,base,"/health/ready",null,"GET",false).statusCode()==503,"readiness explicitly denied");
   System.out.println("PASS P01-B0017/B0018: request framing and trace assertions");
   System.out.println("PASS P01-B0015: protected JSON and response headers");
   System.out.println("PASS P01-B0016: real localhost HTTP routes");
  } finally {server.stop(0);}
 }
 public static void main(String[] args) throws Exception {
  testPath();testBearer();testFacility();testAuditedPatient();testHttp();
  System.out.println("PASS: "+checks+" loopback integration assertions (test verifier only; no OIDC or PostgreSQL)");
 }
}
