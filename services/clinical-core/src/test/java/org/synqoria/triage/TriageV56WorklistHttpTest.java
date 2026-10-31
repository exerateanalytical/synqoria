package org.synqoria.triage;

import com.sun.net.httpserver.HttpServer;
import org.synqoria.http.BearerAdmission;
import org.synqoria.security.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;

/** Full HTTP route checks for eight distinct M03 patient-scoped worklist projections. */
public final class TriageV56WorklistHttpTest {
 static final UUID T=TriageWorkflowBatchesTest.T,F=TriageWorkflowBatchesTest.F,P=TriageWorkflowBatchesTest.P,
  A=TriageWorkflowBatchesTest.A,C=TriageWorkflowBatchesTest.CASE,ST=TriageWorkflowBatchesTest.ST,OTHER=TriageWorkflowBatchesTest.OTHER;
 static final Instant NOW=TriageWorkflowBatchesTest.NOW;
 static final String SESSION="syntheticWorklist_12345";
 static int assertions;
 static void ok(boolean v){assertions++;if(!v)throw new AssertionError("worklist check "+assertions);}
 static final class Fixture implements AutoCloseable {
  final TriageFakeAtomicStore store=new TriageFakeAtomicStore();
  final HttpServer server;final HttpClient client=HttpClient.newHttpClient();
  boolean token=true,grant=true,proof=true;
  Fixture(TriageCase snapshot)throws Exception{
   store.seed(snapshot);
   var session=new TrustedSessionAdmission(
    bearer->token&&SESSION.equals(bearer)?Optional.of(new TrustedSessionAdmission.VerifiedIdentity(
       "synthetic-issuer","synthetic-audience",A,T,F,NOW.minusSeconds(60),NOW.plusSeconds(300),true,false)):Optional.empty(),
    identity->Optional.of(new TrustedSessionAdmission.Privileges(grant?TriageWorkflowBatchesTest.CAPS:Set.of("triage:queue"),Set.of(P),true,true)),
    "synthetic-issuer","synthetic-audience",(id,at)->true);
   var handler=new TriageWorklistHttpHandler(new BearerAdmission(session),(actor,patient,capability,now)->proof?
    Optional.of(new TrustedClinicalAccessEvidence(T,F,A,patient,NOW.minusSeconds(60),NOW.plusSeconds(300),
      true,true,true,true,true,true,true,TriageWorkflowBatchesTest.CAPS)):Optional.empty(),
    new TriageAuthenticatedReadGateway(store),Clock.fixed(NOW,ZoneOffset.UTC));
   server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",handler);server.start();
  }
  HttpResponse<String> request(String view,String body,Map<String,String> extra)throws Exception{
   var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1/triage/patients/"+P+"/views/"+view));
   b.header("Authorization","Bearer "+SESSION).header("Content-Type","application/json");
   for(var e:extra.entrySet())b.header(e.getKey(),e.getValue());
   return client.send(b.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
  }
  HttpResponse<String> request(String view,String body)throws Exception{return request(view,body,Map.of());}
  @Override public void close(){server.stop(0);client.close();}
 }
 static String json(String extra){return "{\"caseIds\":[\""+C+"\"]"+extra+"}";}
 static TriageCase example(String view){
  return switch(view){
   case "FACILITY_QUEUE","STATION_QUEUE" -> TriageWorkflowBatchesTest.base(TriageCase.Stage.WAITING);
   case "PENDING_HANDOVERS" -> TriageWorkflowBatchesTest.special(TriageCase.Stage.ASSIGNED,Map.of("handoverRequestedAt",NOW.toString()),null,false,false,Map.of());
   case "MANUAL_ESCALATIONS" -> TriageWorkflowBatchesTest.special(TriageCase.Stage.ASSESSING,Map.of(),"clinician-recorded",false,false,Map.of());
   case "PENDING_REASSESSMENTS" -> TriageWorkflowBatchesTest.special(TriageCase.Stage.PRIORITIZED,Map.of("reassessmentRequestedAt",NOW.toString()),null,false,false,Map.of());
   case "SAFETY_HOLDS" -> TriageWorkflowBatchesTest.special(TriageCase.Stage.ASSESSING,Map.of(),null,true,false,Map.of());
   case "CLINICIAN_WORKLIST" -> TriageWorkflowBatchesTest.base(TriageCase.Stage.ASSESSING);
   case "ARRIVALS_SINCE" -> TriageWorkflowBatchesTest.base(TriageCase.Stage.ARRIVED);
   default->throw new IllegalArgumentException("unknown view");
  };
 }
 static String extra(String view){
  return switch(view){
   case "STATION_QUEUE" -> ",\"stationOrClinician\":\""+ST+"\"";
   case "CLINICIAN_WORKLIST" -> ",\"stationOrClinician\":\""+A+"\"";
   case "ARRIVALS_SINCE" -> ",\"after\":\"2026-10-30T13:00:00Z\"";
   default->"";
  };
 }
 public static void main(String[] args)throws Exception{
  ok(TriageWorklistHttpHandler.VIEWS.size()==8);
  if(args.length>1||args.length==1&&!TriageWorklistHttpHandler.VIEWS.contains(args[0]))throw new IllegalArgumentException("unknown worklist batch");
  for(String view:args.length==0?TriageWorklistHttpHandler.VIEWS:List.of(args[0])){
   String payload=json(extra(view));
   try(var f=new Fixture(example(view))){
    var route=TriageWorklistHttpHandler.route(URI.create("/api/v1/triage/patients/"+P+"/views/"+view));ok(route!=null&&route.view().name().equals(view));
    var result=f.request(view,payload);ok(result.statusCode()==200);
    ok(result.body().contains(C.toString())&&result.body().contains("\"revision\":1"));
    ok(!result.body().contains(P.toString())&&!result.body().contains("clinician-recorded"));
    ok("no-store, private".equals(result.headers().firstValue("Cache-Control").orElse("")));
    ok(result.body().contains("\"cases\""));
    var badCase=f.request(view,payload.replace(C.toString(),OTHER.toString()));ok(badCase.statusCode()==200&&badCase.body().contains("\"cases\":[]"));
    var doubleCase=f.request(view,payload.replace("[\""+C+"\"]","[\""+C+"\",\""+C+"\"]"));ok(doubleCase.statusCode()==422);
    var denied=f.request(view,payload,Map.of("Cookie","u=impersonated"));ok(denied.statusCode()==400);
    f.proof=false;ok(f.request(view,payload).statusCode()==404);f.proof=true;
    f.grant=false;ok(f.request(view,payload).statusCode()==404);f.grant=true;
    f.token=false;ok(f.request(view,payload).statusCode()==401);f.token=true;
    var extraParam=f.request(view,payload.replace("}",",\"actorId\":\""+A+"\"}"));ok(extraParam.statusCode()==422);
    if(view.equals("STATION_QUEUE")||view.equals("CLINICIAN_WORKLIST")){
     var mismatch=f.request(view,payload.replace(view.equals("STATION_QUEUE")?ST.toString():A.toString(),OTHER.toString()));
     ok(mismatch.statusCode()==200&&mismatch.body().contains("\"cases\":[]"));
    }
    if(view.equals("ARRIVALS_SINCE")){
     var late=f.request(view,payload.replace("13:00:00Z","15:00:00Z"));ok(late.statusCode()==200&&late.body().contains("\"cases\":[]"));
     var future=f.request(view,payload.replace("13:00:00Z","17:00:00Z"));ok(future.statusCode()==422);
    }
   }
  }
  ok(TriageWorklistHttpHandler.route(URI.create("/api/v1/triage/patients/"+P+"/views/CASE_HISTORY"))==null);
  ok(TriageWorklistHttpHandler.route(URI.create("/api/v1/triage/patients/"+P+"/views/STATION_QUEUE?tenant=private"))==null);
  System.out.println("TRIAGE_V56_WORKLIST_HTTP PASS view="+(args.length==0?"ALL":args[0])+" assertions="+assertions);
 }
}
