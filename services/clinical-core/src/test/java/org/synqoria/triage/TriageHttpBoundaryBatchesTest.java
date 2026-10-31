package org.synqoria.triage;

import com.sun.net.httpserver.HttpServer;
import org.synqoria.http.BearerAdmission;
import org.synqoria.security.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Loopback HTTP tests with synthetic identity verifier and atomic store. Not production OIDC or PostgreSQL. */
public final class TriageHttpBoundaryBatchesTest {
 static final UUID T=TriageWorkflowBatchesTest.T,F=TriageWorkflowBatchesTest.F,P=TriageWorkflowBatchesTest.P,
  A=TriageWorkflowBatchesTest.A,C=TriageWorkflowBatchesTest.CASE,OTHER=TriageWorkflowBatchesTest.OTHER;
 static final Instant NOW=TriageWorkflowBatchesTest.NOW;
 static final String TOKEN="syntheticVerifiedToken_12345";
 static final String PATH="/api/v1/triage/patients/"+P+"/cases/"+C+"/commands";
 static final String COMMAND="00000000-0000-0000-0000-000000000123";
 static final String BODY="{\"commandId\":\""+COMMAND+"\",\"expectedRevision\":1,\"action\":\"ARRIVAL_REASON\",\"text\":\"Headache\"}";
 static int checks;
 static void ok(boolean b){checks++;if(!b)throw new AssertionError("HTTP boundary assertion "+checks);}
 static final class Fixture implements AutoCloseable {
  final TriageFakeAtomicStore store=new TriageFakeAtomicStore();
  final HttpServer server;
  final HttpClient client=HttpClient.newHttpClient();
  final URI url;
  boolean validToken=true,validProof=true,wrongPatientProof=false,grant=true,storageUnavailable=false;
  Fixture()throws Exception{
   store.seed(TriageWorkflowBatchesTest.base(TriageCase.Stage.ARRIVED));
   var sessions=new TrustedSessionAdmission(bearer->validToken&&TOKEN.equals(bearer)?Optional.of(
      new TrustedSessionAdmission.VerifiedIdentity("test-issuer","test-audience",A,T,F,NOW.minusSeconds(60),NOW.plusSeconds(300),true,false)):Optional.empty(),
    identity->Optional.of(new TrustedSessionAdmission.Privileges(grant?TriageWorkflowBatchesTest.CAPS:Set.of("triage:read"),Set.of(P),true,true)),
    "test-issuer","test-audience",(id,at)->true);
   TriagePersistencePort port=new TriagePersistencePort(){
    public <R> R transaction(java.util.function.Function<Unit,R> work){
     if(storageUnavailable)throw new IllegalStateException("private JDBC password should never leak");
     return store.transaction(work);
    }
   };
   var handler=new TriageCommandHttpHandler(new BearerAdmission(sessions),(actor,patient,capability,now)->validProof?
    Optional.of(new TrustedClinicalAccessEvidence(T,F,A,wrongPatientProof?OTHER:patient,NOW.minusSeconds(60),NOW.plusSeconds(300),true,true,true,true,true,true,true,TriageWorkflowBatchesTest.CAPS)):Optional.empty(),
    new TriageAuthenticatedCommandGateway(new TriageCommandCoordinator(port)),Clock.fixed(NOW,ZoneOffset.UTC));
   server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
   server.createContext("/",handler);server.start();
   url=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+PATH);
  }
  HttpResponse<String> send(String body,String path,String method,Map<String,String> headers)throws Exception{
   var b=HttpRequest.newBuilder(path==null?url:URI.create("http://127.0.0.1:"+server.getAddress().getPort()+path));
   if(body!=null)b.method(method,HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8));
   else b.method(method,HttpRequest.BodyPublishers.noBody());
   b.header("Authorization","Bearer "+TOKEN);
   if(body!=null)b.header("Content-Type","application/json");
   for(var entry:headers.entrySet())b.header(entry.getKey(),entry.getValue());
   return client.send(b.build(),HttpResponse.BodyHandlers.ofString());
  }
  HttpResponse<String> send(String body)throws Exception{return send(body,null,"POST",Map.of());}
  void unchanged(){ok(store.cases.get(new TriagePersistencePort.Scope(T,F,P,C)).revision()==1);ok(store.receipts.isEmpty());}
  @Override public void close(){server.stop(0);client.close();}
 }
 static void status(Fixture f,String body,int expected)throws Exception{
  var response=f.send(body);ok(response.statusCode()==expected);ok(!response.body().contains("Headache"));
  if(expected!=200)f.unchanged();
 }
 static void scenario(String id)throws Exception{
  try(var f=new Fixture()){
   switch(id){
    case "P06-B0241" -> {status(f,BODY,200);ok(f.store.cases.values().iterator().next().revision()==2);ok(f.store.audits.size()==1&&f.store.outbox.size()==1);}
    case "P06-B0242" -> {status(f,BODY,200);var response=f.send(BODY);ok(response.statusCode()==200&&response.body().contains("\"replay\":true"));ok(f.store.outbox.size()==1);}
    case "P06-B0243" -> {f.validToken=false;status(f,BODY,401);}
    case "P06-B0244" -> {f.validProof=false;status(f,BODY,404);}
    case "P06-B0245" -> {f.wrongPatientProof=true;status(f,BODY,404);}
    case "P06-B0246" -> {f.grant=false;status(f,BODY,404);}
    case "P06-B0247" -> {var r=f.send(null,null,"GET",Map.of());ok(r.statusCode()==405);f.unchanged();}
    case "P06-B0248" -> {var r=f.send(BODY,"/api/v1/triage/patients/"+P+"/cases/"+C+"/commands?tenant="+T,"POST",Map.of());ok(r.statusCode()==404);f.unchanged();}
    case "P06-B0249" -> {var r=f.send(BODY,PATH.replace(P.toString(),OTHER.toString()),"POST",Map.of());ok(r.statusCode()==404);f.unchanged();}
    case "P06-B0250" -> {var r=f.send(BODY,null,"POST",Map.of("X-Tenant-Id",T.toString()));ok(r.statusCode()==400);f.unchanged();}
    case "P06-B0251" -> {var r=f.send(BODY,null,"POST",Map.of("Origin","https://evil.example"));ok(r.statusCode()==400);f.unchanged();}
    case "P06-B0252" -> {var r=f.send(BODY,null,"POST",Map.of("Accept","text/html"));ok(r.statusCode()==406);f.unchanged();}
    case "P06-B0253" -> {status(f,BODY.replace("Headache","Headache\\u0000bad"),422);}
    case "P06-B0254" -> {status(f,BODY.replace("\"text\":\"Headache\"","\"text\":\"Headache\",\"text\":\"Changed\""),422);}
    case "P06-B0255" -> {status(f,BODY.replace("\"text\":\"Headache\"","\"tenantId\":\""+T+"\",\"text\":\"Headache\""),422);}
    case "P06-B0256" -> {status(f,BODY.replace("\"expectedRevision\":1","\"expectedRevision\":\"1\""),422);}
    case "P06-B0257" -> {status(f,BODY.replace("\"expectedRevision\":1","\"expectedRevision\":0"),422);}
    case "P06-B0258" -> {status(f,BODY.replace(COMMAND,"not-a-uuid"),422);}
    case "P06-B0259" -> {status(f,BODY.replace("ARRIVAL_REASON","NOT_A_TRIAGE_COMMAND"),422);}
    case "P06-B0260" -> {status(f,BODY.replace("Headache","H".repeat(4001)),422);}
    case "P06-B0261" -> {status(f,BODY.replace("Headache","Headache").replace("}",",\"reference\":\""+OTHER+"\"}"),422);}
    case "P06-B0262" -> {var r=f.send(BODY.replace("\"expectedRevision\":1","\"expectedRevision\":2"));ok(r.statusCode()==412);f.unchanged();}
    case "P06-B0263" -> {var r=f.send(BODY+" "+BODY);ok(r.statusCode()==422);f.unchanged();}
    case "P06-B0264" -> {var r=f.send(BODY.replace("Headache","\\uD800"));ok(r.statusCode()==422);f.unchanged();}
    case "P06-B0265" -> {var r=f.send(BODY.replace("\"action\":\"ARRIVAL_REASON\"","\"action\":{\"bad\":true}"));ok(r.statusCode()==422);f.unchanged();}
    case "P06-B0266" -> {var r=f.send(BODY.replace("Headache","\"\\n\""));ok(r.statusCode()==422);f.unchanged();}
    case "P06-B0267" -> {var r=f.send(BODY,null,"POST",Map.of("X-Actor-Id",A.toString()));ok(r.statusCode()==400);f.unchanged();}
    case "P06-B0268" -> {var r=f.send(BODY,null,"POST",Map.of("X-Clinical-Evidence","fake"));ok(r.statusCode()==400);f.unchanged();}
    case "P06-B0279" -> {var r=f.send(BODY,null,"POST",Map.of("Content-Type","text/plain"));ok(r.statusCode()==415);f.unchanged();}
    case "P06-B0280" -> {var r=f.send(null,null,"POST",Map.of());ok(r.statusCode()==415);f.unchanged();}
    case "P06-B0281" -> {var b=HttpRequest.newBuilder(f.url).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(BODY));
      var r=f.client.send(b.build(),HttpResponse.BodyHandlers.ofString());ok(r.statusCode()==401);f.unchanged();}
    case "P06-B0282" -> {var r=f.send(BODY,null,"POST",Map.of("Cookie","sid=evil"));ok(r.statusCode()==400);f.unchanged();}
    case "P06-B0283" -> {var r=f.send(BODY,null,"POST",Map.of("X-Facility-Id",F.toString()));ok(r.statusCode()==400);f.unchanged();}
    case "P06-B0284" -> {status(f,BODY.replace("\"text\":\"Headache\"","\"observation\":\"patient data\""),422);}
    case "P06-B0285" -> {var r=f.send(BODY.replace("ARRIVAL_REASON","QUEUE_ENQUEUE").replace("\"text\":\"Headache\"","\"reference\":\""+TriageWorkflowBatchesTest.ST+"\""));
      ok(r.statusCode()==200);ok(f.store.cases.values().iterator().next().stage()==TriageCase.Stage.WAITING);ok(f.store.outbox.size()==1);}
    case "P06-B0286" -> {var r=f.send(BODY,PATH.replace(C.toString(),OTHER.toString()),"POST",Map.of());ok(r.statusCode()==404);f.unchanged();}
    case "P06-B0287" -> {f.storageUnavailable=true;var r=f.send(BODY);ok(r.statusCode()==503);ok(!r.body().contains("password"));f.unchanged();}
    case "P06-B0288" -> {var r=f.send(BODY.replace("ARRIVAL_REASON","QUEUE_CALL").replace(",\"text\":\"Headache\"",""));
      ok(r.statusCode()==409);ok(!r.body().contains("Headache"));f.unchanged();}
    default -> throw new IllegalArgumentException("unknown batch "+id);
   }
  }
 }
 public static void main(String[] args)throws Exception{
  if(args.length!=1)throw new IllegalArgumentException("one batch required");scenario(args[0]);
  System.out.println(args[0]+" PASS checks="+checks);
 }
}
