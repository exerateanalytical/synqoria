package org.synqoria.longitudinal;

import com.sun.net.httpserver.HttpServer;
import org.synqoria.http.BearerAdmission;
import org.synqoria.security.TrustedSessionAdmission;
import org.synqoria.security.TrustedClinicalAccessEvidence;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.synqoria.longitudinal.ClinicalCaseCore.*;

/** Independent synthetic loopback HTTP tests. Real OIDC and PostgreSQL are NOT provided here. */
public final class LongitudinalHttpBoundaryTest {
 static final Instant NOW=Instant.parse("2026-10-31T10:45:00Z");
 static final UUID TENANT=UUID.fromString("10000000-0000-0000-0000-000000000001");
 static final UUID FACILITY=UUID.fromString("10000000-0000-0000-0000-000000000002");
 static final UUID PATIENT=UUID.fromString("10000000-0000-0000-0000-000000000003");
 static final UUID WRONG=UUID.fromString("10000000-0000-0000-0000-000000000004");
 static final UUID CASE=UUID.fromString("10000000-0000-0000-0000-000000000005");
 static final UUID A=UUID.fromString("10000000-0000-0000-0000-000000000006");
 static final UUID B=UUID.fromString("10000000-0000-0000-0000-000000000007");
 static final UUID NOTE=UUID.fromString("10000000-0000-0000-0000-000000000008");
 static final UUID OBS=UUID.fromString("10000000-0000-0000-0000-000000000009");
 static final UUID OBS2=UUID.fromString("10000000-0000-0000-0000-00000000000a");
 static final String TOKEN_A="SYNQORIA_VALID_TEST_TOKEN_A_123456789", TOKEN_B="SYNQORIA_VALID_TEST_TOKEN_B_123456789";
 static final Set<String> CAPS=Set.of("encounter.write","encounter.read","encounter.audit.read","encounter.assign",
    "encounter.admission.approve","encounter.transfer.accept","encounter.discharge.authorize","note.write","note.read",
    "note.read.confidential","note.sign","note.cosign","note.error.flag","observation.write","observation.read",
    "observation.verify","observation.invalidate");
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static void status(HttpResponse<String> r,int expect){check(r.statusCode()==expect,"expected "+expect+" got "+r.statusCode()+" "+r.body());}
 static String command(UUID id,long revision,String op,Map<String,String> arguments){
  var b=new StringBuilder("{\"commandId\":").append(LongitudinalHttpHandler.json(id.toString()))
    .append(",\"expectedRevision\":").append(revision)
    .append(",\"operation\":").append(LongitudinalHttpHandler.json(op)).append(",\"inputs\":{");
  int i=0;for(var e:arguments.entrySet()){
    if(i++>0)b.append(',');b.append(LongitudinalHttpHandler.json(e.getKey())).append(':')
       .append(LongitudinalHttpHandler.json(e.getValue()));
  }
  return b.append("}}").toString();
 }
 static final class F implements AutoCloseable {
  final Scope scope=new Scope(TENANT,FACILITY,PATIENT,CASE);
  final UnitTestClinicalStore store=new UnitTestClinicalStore(scope);
  final HttpServer server;
  final HttpClient client=HttpClient.newHttpClient();
  final URI base;
  boolean allowProof=true;
  boolean allowConfidential=true;
  F() throws Exception{
    var session=new TrustedSessionAdmission(token->{
      UUID actor=token.equals(TOKEN_A)?A:token.equals(TOKEN_B)?B:null;
      return actor==null?Optional.empty():Optional.of(new TrustedSessionAdmission.VerifiedIdentity("synthetic-issuer",
       "synqoria-tests",actor,TENANT,FACILITY,NOW.minusSeconds(60),NOW.plusSeconds(300),true,false));
    },identity->Optional.of(new TrustedSessionAdmission.Privileges(allowConfidential?CAPS:
       CAPS.stream().filter(c->!c.equals("note.read.confidential")).collect(java.util.stream.Collectors.toSet()),
       Set.of(PATIENT),true,true)),"synthetic-issuer","synqoria-tests");
    var handler=new LongitudinalHttpHandler(new BearerAdmission(session),(identity,patient,cap,at)->{
      if(!allowProof || !PATIENT.equals(patient))return Optional.empty();
      return Optional.of(new TrustedClinicalAccessEvidence(TENANT,FACILITY,identity.actorId(),patient,
        NOW.minusSeconds(60),NOW.plusSeconds(300),true,true,true,true,true,true,true,
        allowConfidential?CAPS:CAPS.stream().filter(c->!c.equals("note.read.confidential")).collect(java.util.stream.Collectors.toSet())));
    },new LongitudinalAuthenticatedGateway(store),Clock.fixed(NOW,ZoneOffset.UTC));
    server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
    server.createContext("/",handler);server.start();
    base=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1/clinical/patients/"+PATIENT+"/encounters/"+CASE);
  }
  HttpResponse<String> get(String relative)throws Exception{return getWith(TOKEN_A,base.resolve(base.toString()+relative));}
  HttpResponse<String> getWith(String token,URI address)throws Exception{
    var b=HttpRequest.newBuilder(address).GET();if(token!=null)b.header("Authorization","Bearer "+token);
    return client.send(b.build(),HttpResponse.BodyHandlers.ofString());
  }
  HttpResponse<String> post(String token,String payload)throws Exception{return postTo(base.toString()+"/commands",token,payload,false);}
  HttpResponse<String> postTo(String url,String token,String payload,boolean spoof)throws Exception{
    var b=HttpRequest.newBuilder(URI.create(url)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(payload));
    if(token!=null)b.header("Authorization","Bearer "+token);
    if(spoof)b.header("X-Tenant-Id",UUID.randomUUID().toString());
    return client.send(b.build(),HttpResponse.BodyHandlers.ofString());
  }
  HttpResponse<String> write(String token,String op,Map<String,String> args)throws Exception{
    return post(token,command(UUID.randomUUID(),store.snapshot().revision,op,args));
  }
  void active()throws Exception{
    status(write(TOKEN_A,"encounter.checkin",Map.of()),200);
    status(write(TOKEN_A,"encounter.activate",Map.of()),200);
  }
  @Override public void close(){server.stop(0);}
 }
 static void b0061()throws Exception{
  var c=command(UUID.randomUUID(),0,"encounter.checkin",Map.of());
  check(LongitudinalStrictJson.decode(c.getBytes(StandardCharsets.UTF_8)).inputs().isEmpty(),"valid strict command");
  var invalid=List.of(c.replace("\"operation\"","\"operation\":\"note.sign\",\"operation\""),
   c.replace("\"inputs\":{}","\"inputs\":{\"noteId\":\"a\",\"noteId\":\"b\"}"),
   c.replace("\"expectedRevision\":0","\"expectedRevision\":-1"),
   c+" 1",c.replace("\"operation\":\"encounter.checkin\"","\"operation\":\"unknown.command\""),
   c.replace("\"inputs\":{}","\"inputs\":{\"duplicate\":\"x\",\"duplicate\":\"y\"}"));
  int count=0;for(String v:invalid){try{LongitudinalStrictJson.decode(v.getBytes(StandardCharsets.UTF_8));
      throw new AssertionError("accepted malformed command "+count);}catch(IllegalArgumentException expected){count++;}}
  check(count==6,"strict decoder adversarial coverage");
 }
 static void b0062()throws Exception{
  try(F f=new F()){
   var a=f.write(TOKEN_A,"encounter.checkin",Map.of());status(a,200);
   check(a.body().contains("\"revision\":1"),"check-in persisted");
   var b=f.write(TOKEN_A,"encounter.activate",Map.of());status(b,200);
   check(b.body().contains("\"revision\":2"),"activate persisted");
   var summary=f.get("/summary");status(summary,200);
   check(summary.body().contains("\"stage\":\"ACTIVE\""),"session read state");
   check(summary.headers().firstValue("Cache-Control").orElse("").contains("no-store"),"private no store");
  }
 }
 static void b0063()throws Exception{
  try(F f=new F()){
   f.active();status(f.write(TOKEN_A,"note.create_draft",Map.of("noteId",NOTE.toString())),200);
   status(f.write(TOKEN_A,"note.write_draft",Map.of("noteId",NOTE.toString(),"body","Synthetic patient narrative")),200);
   status(f.write(TOKEN_A,"note.mark_confidential",Map.of("noteId",NOTE.toString(),"confidential","true")),200);
   var view=f.get("/notes/"+NOTE);status(view,200);
   check(view.body().contains("Synthetic patient narrative"),"authorized full note");
   f.allowConfidential=false;status(f.get("/notes/"+NOTE),404);
   check(f.store.snapshot().notes.get(NOTE).confidential,"note confidentiality persisted");
  }
 }
 static void b0064()throws Exception{
  try(F f=new F()){
   f.active();var data=Map.of("observationId",OBS.toString(),"code","local:pulse",
       "unit","/min","value","82","measuredAt","2026-10-31T10:44:00Z");
   status(f.write(TOKEN_A,"observation.capture_numeric",data),200);
   status(f.write(TOKEN_A,"observation.request_verification",Map.of("observationId",OBS.toString())),200);
   status(f.write(TOKEN_B,"observation.verify",Map.of("observationId",OBS.toString())),200);
   var obs=f.get("/observations?code=local%3Apulse&limit=20");status(obs,200);
   check(obs.body().contains("\"value\":\"82\""),"verified observation read");
   check(obs.body().contains("\"status\":\"VERIFIED\""),"observation verification");
  }
 }
 static void b0065()throws Exception{
  try(F f=new F()){
   f.active();status(f.get("/summary"),200);
   status(f.getWith(TOKEN_A,URI.create(f.base.toString().replace(PATIENT.toString(),WRONG.toString())+"/summary")),404);
   status(f.getWith(null,URI.create(f.base+"/summary")),401);
   f.allowProof=false;status(f.get("/summary"),404);
   check(f.store.snapshot().revision==2,"denied reads cannot write");
  }
 }
 static void b0066()throws Exception{
  try(F f=new F()){
   f.active();status(f.write(TOKEN_A,"encounter.record_room",Map.of("room","Synthetic ward")),200);
   var first=f.get("/audit?limit=2");status(first,200);
   check(first.body().contains("\"nextRevision\":2"),"pagination cursor");
   check(!first.body().contains("Synthetic ward"),"audit excludes clinical free text");
   var next=f.get("/audit?afterRevision=2&limit=2");status(next,200);
   check(next.body().contains("\"revision\":3"),"second page");
   check(next.body().contains("\"nextRevision\":null"),"terminal page");
   status(f.get("/audit?limit=101"),422);
  }
 }
 static void b0067()throws Exception{
  try(F f=new F()){
   var id=UUID.randomUUID();var cmd=command(id,0,"encounter.checkin",Map.of());
   var first=f.post(TOKEN_A,cmd);status(first,200);check(first.body().contains("\"replay\":false"),"first write");
   var replay=f.post(TOKEN_A,cmd);status(replay,200);check(replay.body().contains("\"replay\":true"),"safe replay");
   status(f.post(TOKEN_A,command(id,0,"encounter.activate",Map.of())),404);
   status(f.post(TOKEN_A,command(UUID.randomUUID(),0,"encounter.activate",Map.of())),409);
   check(f.store.snapshot().revision==1,"one committed command");
  }
 }
 static void b0068()throws Exception{
  try(F f=new F()){
   var cmd=command(UUID.randomUUID(),0,"encounter.checkin",Map.of());
   status(f.postTo(f.base+"/commands",TOKEN_A,cmd,true),400);
   status(f.postTo(f.base+"/commands?x=1",TOKEN_A,cmd,false),404);
   status(f.post(TOKEN_A,cmd.replace("\"inputs\":{}","\"inputs\":{\"tenantId\":\"unsafe\"}")),422);
   var get=f.getWith(TOKEN_A,URI.create(f.base+"/notes"));status(get,404);
   check(f.store.snapshot().revision==0,"invalid inputs cannot mutate");
  }
 }
 public static void main(String[] args)throws Exception{
  if(args.length!=1)throw new IllegalArgumentException("one batch id required");
  switch(args[0]){
    case "P07-B0061" -> b0061();case "P07-B0062" -> b0062();
    case "P07-B0063" -> b0063();case "P07-B0064" -> b0064();
    case "P07-B0065" -> b0065();case "P07-B0066" -> b0066();
    case "P07-B0067" -> b0067();case "P07-B0068" -> b0068();
    default -> throw new IllegalArgumentException("unknown batch");
  }
  System.out.println("PASS "+args[0]);
 }
}
