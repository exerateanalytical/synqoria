package org.synqoria.triage;

import com.sun.net.httpserver.HttpServer;
import org.synqoria.http.BearerAdmission;
import org.synqoria.security.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;

/** Live loopback HTTP (synthetic identity and atomic store), no real PostgreSQL or OIDC. */
public final class TriageV56HttpIntegrationTest {
 static int checks;
 static void ok(boolean b){checks++;if(!b)throw new AssertionError("M03 HTTP v56 assertion "+checks);}
 static final UUID T=TriageWorkflowBatchesTest.T,F=TriageWorkflowBatchesTest.F,P=TriageWorkflowBatchesTest.P,
  A=TriageWorkflowBatchesTest.A,C=TriageWorkflowBatchesTest.CASE,OTHER=TriageWorkflowBatchesTest.OTHER;
 static final Instant NOW=TriageWorkflowBatchesTest.NOW;
 static final String TOKEN="syntheticTriageToken_12345";
 static final String PATH="/api/v1/triage/patients/"+P+"/cases/"+C;
 static final String ID="11111111-1111-1111-1111-111111111111";
 static final String OBSID="22222222-2222-2222-2222-222222222222";
 static final class Fixture implements AutoCloseable{
  final TriageFakeAtomicStore store=new TriageFakeAtomicStore();
  final HttpServer server; final HttpClient client=HttpClient.newHttpClient();
  boolean token=true,proof=true,access=true;
  Fixture(TriageCase initial)throws Exception{
   if(initial!=null)store.seed(initial);
   var session=new TrustedSessionAdmission(
     bearer->token&&TOKEN.equals(bearer)?Optional.of(new TrustedSessionAdmission.VerifiedIdentity(
       "synthetic-issuer","synthetic-audience",A,T,F,NOW.minusSeconds(60),NOW.plusSeconds(300),true,false)):Optional.empty(),
     identity->Optional.of(new TrustedSessionAdmission.Privileges(access?TriageWorkflowBatchesTest.CAPS:Set.of("triage:read"),Set.of(P),true,true)),
     "synthetic-issuer","synthetic-audience",(id,now)->true);
   var coordinator=new TriageCommandCoordinator(store);
   TriageCommandHttpHandler.ClinicalEvidenceSource evidence=(actor,patient,capability,now)->proof?
     Optional.of(new TrustedClinicalAccessEvidence(T,F,A,patient,NOW.minusSeconds(60),NOW.plusSeconds(300),
       true,true,true,true,true,true,true,TriageWorkflowBatchesTest.CAPS)):Optional.empty();
   server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
   server.createContext(PATH+"/intake",new TriageIntakeHttpHandler(new BearerAdmission(session),evidence,coordinator,Clock.fixed(NOW,ZoneOffset.UTC)));
   server.createContext(PATH+"/commands",new TriageCommandHttpHandler(new BearerAdmission(session),evidence,
      new TriageAuthenticatedCommandGateway(coordinator),Clock.fixed(NOW,ZoneOffset.UTC)));
   server.start();
  }
  HttpResponse<String> post(String suffix,String payload,Map<String,String> headers)throws Exception{
   var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+PATH+suffix))
      .header("Authorization","Bearer "+TOKEN).header("Content-Type","application/json");
   for(var e:headers.entrySet())req.header(e.getKey(),e.getValue());
   return client.send(req.POST(HttpRequest.BodyPublishers.ofString(payload)).build(),HttpResponse.BodyHandlers.ofString());
  }
  HttpResponse<String> post(String suffix,String payload)throws Exception{return post(suffix,payload,Map.of());}
  TriageCase current(){return store.cases.get(new TriagePersistencePort.Scope(T,F,P,C));}
  void notLeaking(HttpResponse<String> r){ok(!r.body().contains("private clinical narrative")&&!r.body().contains(A.toString()));}
  @Override public void close(){server.stop(0);client.close();}
 }
 static String observation(String action,String obsId,String replaces){
  return "{\"commandId\":\""+ID+"\",\"expectedRevision\":1,\"action\":\""+action+"\",\"observation\":{\"id\":\""+obsId+"\",\"code\":\"staff-recorded-code\",\"value\":\"37.2\",\"unit\":\"unit-recorded\",\"takenAt\":\"2026-10-30T14:30:00Z\""+
    (replaces==null?"":",\"replaces\":\""+replaces+"\"")+"}}";
 }
 public static void main(String[] args)throws Exception{
  ok(TriageIntakeHttpHandler.route(URI.create(PATH+"/intake"))!=null);
  ok(TriageIntakeHttpHandler.route(URI.create(PATH+"/intake?debug=1"))==null);
  ok(TriageIntakeHttpHandler.route(URI.create(PATH+"/commands"))==null);
  if(args.length==0||args.length==1&&args[0].equals("intake"))try(var f=new Fixture(null)){
   String intake="{\"commandId\":\""+ID+"\",\"expectedRevision\":0}";
   var created=f.post("/intake",intake);ok(created.statusCode()==201);ok(created.body().contains("\"revision\":1")&&created.body().contains("\"replay\":false"));
   ok(f.current()!=null&&f.current().genesis()!=null&&f.store.audits.size()==1&&f.store.outbox.size()==1&&f.store.receipts.size()==1);
   ok("no-store, private".equals(created.headers().firstValue("Cache-Control").orElse("")));
   var replay=f.post("/intake",intake);ok(replay.statusCode()==200&&replay.body().contains("\"replay\":true"));
   ok(f.store.audits.size()==1&&f.store.outbox.size()==1);
   var conflict=f.post("/intake",intake.replace(ID,"33333333-3333-3333-3333-333333333333"));ok(conflict.statusCode()==409);
   ok(f.current().revision()==1&&f.store.receipts.size()==1);
   var invalid=f.post("/intake",intake.replace("\"expectedRevision\":0","\"expectedRevision\":1"));ok(invalid.statusCode()==422);
   var extra=f.post("/intake",intake.replace("}",",\"action\":\"ARRIVAL_REASON\"}"));ok(extra.statusCode()==422);
   var foreign=f.post("/intake",intake,Map.of("X-Actor-Id",A.toString()));ok(foreign.statusCode()==400);
   f.token=false;ok(f.post("/intake",intake).statusCode()==401);f.token=true;
   f.proof=false;ok(f.post("/intake",intake).statusCode()==404);f.proof=true;
   f.access=false;ok(f.post("/intake",intake).statusCode()==404);f.access=true;
   f.notLeaking(created);
  }
  if(args.length==0||args.length==1&&args[0].equals("observation-record"))try(var f=new Fixture(TriageWorkflowBatchesTest.base(TriageCase.Stage.ASSESSING))){
   String record=observation("OBSERVATION_RECORD",OBSID,null);
   var response=f.post("/commands",record);ok(response.statusCode()==200);ok(f.current().revision()==2);
   ok(f.current().observations().containsKey(UUID.fromString(OBSID)));
   ok(A.equals(f.current().observations().get(UUID.fromString(OBSID)).recordedBy()));
   ok(f.store.audits.size()==1&&f.store.outbox.size()==1&&f.store.receipts.size()==1);
   f.notLeaking(response);
   var replay=f.post("/commands",record);ok(replay.statusCode()==200&&replay.body().contains("\"replay\":true"));
   ok(f.store.outbox.size()==1);
  }
  if(args.length==0||args.length==1&&args[0].equals("observation-correction")){
   var original=new TriageCase.Observation(OTHER,"staff-recorded-code","37.2","unit-recorded",NOW.minusSeconds(30),A,null);
   var c=TriageWorkflowBatchesTest.special(TriageCase.Stage.ASSESSING,Map.of(),null,false,false,Map.of(OTHER,original));
   try(var f=new Fixture(c)){
    String correction=observation("OBSERVATION_CORRECTION",OBSID,OTHER.toString()).replace("37.2","37.3");
    var wrote=f.post("/commands",correction);ok(wrote.statusCode()==200);
    ok(f.current().revision()==2&&f.current().observations().size()==2);
    ok(f.current().observations().get(UUID.fromString(OBSID)).replaces().equals(OTHER));
    ok(f.current().observations().get(OTHER).equals(original));
    var replay=f.post("/commands",correction);ok(replay.statusCode()==200&&replay.body().contains("\"replay\":true"));
    ok(f.store.audits.size()==1&&f.store.outbox.size()==1&&f.store.receipts.size()==1);
    f.notLeaking(wrote);
   }
  }
  if(args.length==0||args.length==1&&args[0].equals("observation-invalid"))  try(var f=new Fixture(TriageWorkflowBatchesTest.base(TriageCase.Stage.ASSESSING))){
   String record=observation("OBSERVATION_RECORD",OBSID,null);
   var wrong=f.post("/commands",record.replace("\"unit\":\"unit-recorded\"","\"unit\":7"));ok(wrong.statusCode()==422);ok(f.current().revision()==1);
   var forged=f.post("/commands",record.replace("\"takenAt\"", "\"recordedBy\":\""+OTHER+"\",\"takenAt\""));ok(forged.statusCode()==422);
   var duplicate=f.post("/commands",record.replace("\"value\":\"37.2\"","\"value\":\"37.2\",\"value\":\"39\""));ok(duplicate.statusCode()==422);
   var nested=f.post("/commands",record.replace("\"code\":\"staff-recorded-code\"","\"code\":{\"nested\":true}"));ok(nested.statusCode()==422);
   var future=f.post("/commands",record.replace("14:30:00","16:30:00"));ok(future.statusCode()==422);ok(f.current().revision()==1);
   var early=f.post("/commands",record.replace("2026-10-30T14:30:00Z","2026-10-29T14:30:00Z"));ok(early.statusCode()==422);
   var unknown=f.post("/commands",record.replace("\"observation\"","\"unexpected\""));ok(unknown.statusCode()==422);
   var forbidden=f.post("/commands",record.replace("\"action\":\"OBSERVATION_RECORD\"","\"action\":\"ASSESSMENT_BEGIN\""));ok(forbidden.statusCode()==422);
   var appointment=f.post("/commands",record.replace("}",",\"appointment\":\""+C+"\"}"));ok(appointment.statusCode()==422);
   ok(f.current().revision()==1&&f.store.receipts.isEmpty()&&f.store.outbox.isEmpty());
  }
  System.out.println("TRIAGE_V56_HTTP PASS mode="+(args.length==0?"ALL":args[0])+" assertions="+checks);
 }
}
