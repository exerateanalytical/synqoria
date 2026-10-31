package org.synqoria.triage;

import com.sun.net.httpserver.HttpServer;
import org.synqoria.http.BearerAdmission;
import org.synqoria.security.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;

/** Real loopback HTTP with a synthetic verified session and atomic test store. No live OIDC/RLS claims. */
public final class TriageReadHttpIntegrationTest {
  static int assertions;
  static void ok(boolean v){assertions++;if(!v)throw new AssertionError("read HTTP check "+assertions);}
  static final UUID T=TriageWorkflowBatchesTest.T,F=TriageWorkflowBatchesTest.F,P=TriageWorkflowBatchesTest.P,
    A=TriageWorkflowBatchesTest.A,C=TriageWorkflowBatchesTest.CASE,OTHER=TriageWorkflowBatchesTest.OTHER;
  static final Instant NOW=TriageWorkflowBatchesTest.NOW;
  static final String BASE="/api/v1/triage/patients/"+P+"/cases/"+C;
  static final String TOKEN="syntheticReadSession_12345";
  static final class Fixture implements AutoCloseable {
    final HttpServer server;final HttpClient client=HttpClient.newHttpClient();
    final TriageFakeAtomicStore store=new TriageFakeAtomicStore();
    boolean validToken=true,validProof=true,wrongPatient=false,readGrant=true,failStore=false;
    Fixture()throws Exception{
      var session=new TrustedSessionAdmission(
       token->validToken&&TOKEN.equals(token)?Optional.of(new TrustedSessionAdmission.VerifiedIdentity(
          "synthetic-issuer","synthetic-audience",A,T,F,NOW.minusSeconds(60),NOW.plusSeconds(300),true,false)):Optional.empty(),
       identity->Optional.of(new TrustedSessionAdmission.Privileges(readGrant?TriageWorkflowBatchesTest.CAPS:Set.of("triage:queue"),Set.of(P),true,true)),
       "synthetic-issuer","synthetic-audience",(actor,now)->true);
      var coordinator=new TriageCommandCoordinator(store);
      coordinator.arrive(TriageWorkflowBatchesTest.a,TriageWorkflowBatchesTest.e,
        new TriagePersistencePort.Scope(T,F,P,C),new TriageCommand(UUID.randomUUID(),A,NOW,0,"synthetic intake"),null);
      var port=new TriagePersistencePort(){
       public <V> V transaction(java.util.function.Function<Unit,V> work){
        if(failStore)throw new IllegalStateException("secret-internal-SQL-password");
        return store.transaction(work);
       }
      };
      var handler=new TriageReadHttpHandler(new BearerAdmission(session),(a,patient,capability,now)->validProof?
        Optional.of(new TrustedClinicalAccessEvidence(T,F,A,wrongPatient?OTHER:patient,NOW.minusSeconds(60),NOW.plusSeconds(300),
          true,true,true,true,true,true,true,TriageWorkflowBatchesTest.CAPS)):Optional.empty(),
        new TriageAuthenticatedReadGateway(port),Clock.fixed(NOW,ZoneOffset.UTC));
      server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",handler);server.start();
    }
    HttpResponse<String> fetch(String path,String method,Map<String,String> extra,boolean bearer)throws Exception{
      var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+path))
        .method(method,HttpRequest.BodyPublishers.noBody());
      if(bearer)builder.header("Authorization","Bearer "+TOKEN);
      for(var e:extra.entrySet())builder.header(e.getKey(),e.getValue());
      return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> fetch(String path)throws Exception{return fetch(path,"GET",Map.of("Accept","application/json"),true);}
    void notLeaking(HttpResponse<String> response){ok(!response.body().contains("secret-internal-SQL-password")&&
      !response.body().contains("synthetic intake"));}
    @Override public void close(){server.stop(0);client.close();}
  }
  public static void main(String[] args)throws Exception{
    ok(TriageReadHttpHandler.route(URI.create(BASE))!=null);
    ok(TriageReadHttpHandler.route(URI.create(BASE+"/history")).history());
    ok(TriageReadHttpHandler.route(URI.create(BASE+"?debug=true"))==null);
    ok(TriageReadHttpHandler.route(URI.create(BASE+"/history?afterRevision=0")).afterRevision()==0);
    ok(TriageReadHttpHandler.route(URI.create(BASE+"/history?afterRevision=500")).afterRevision()==500);
    ok(TriageReadHttpHandler.route(URI.create(BASE+"/history?afterRevision=01"))==null);
    ok(TriageReadHttpHandler.route(URI.create(BASE+"/history?afterRevision=1&afterRevision=2"))==null);
    ok(TriageReadHttpHandler.route(URI.create(BASE+"/history?afterRevision=2147483648"))==null);
    ok(TriageReadHttpHandler.quoteJson("hello\"\\world\n").equals("\"hello\\\"\\\\world\\n\""));
    ok(TriageReadHttpHandler.route(URI.create(BASE.replace(C.toString(),"00000000-0000-0000-0000-00000000000X")))==null);
    try(var f=new Fixture()){
      var summary=f.fetch(BASE);ok(summary.statusCode()==200);
      ok(summary.body().contains("\"revision\":1")&&summary.body().contains("\"ARRIVED\""));
      ok(summary.body().contains(C.toString())&&!summary.body().contains(P.toString()));
      ok("no-store, private".equals(summary.headers().firstValue("Cache-Control").orElse("")));
      ok(summary.headers().firstValue("X-Request-ID").isPresent());
      f.notLeaking(summary);
      var history=f.fetch(BASE+"/history");ok(history.statusCode()==200);
      ok(history.body().contains("\"ARRIVAL\"")&&history.body().contains("\"revision\":1"));
      ok(history.body().contains("\"digest\"")&&!history.body().contains(A.toString()));
      f.notLeaking(history);
      var after=f.store.cases.get(new TriagePersistencePort.Scope(T,F,P,C));
      var cmd=new TriageCommand(UUID.randomUUID(),A,NOW.plusSeconds(1),1,"notes are never returned");
      f.store.seed(new TriageIntakeService().recordArrivalReason(TriageWorkflowBatchesTest.a,TriageWorkflowBatchesTest.e,after,cmd,"secret clinical note"));
      var next=f.fetch(BASE+"/history");ok(next.statusCode()==200&&next.body().contains("\"revision\":2"));
      ok(next.body().contains("ARRIVAL_REASON")&&!next.body().contains("secret clinical note"));
      f.notLeaking(next);
      ok(f.fetch(BASE.replace(P.toString(),OTHER.toString())).statusCode()==404);
      ok(f.fetch(BASE.replace(C.toString(),OTHER.toString())).statusCode()==404);
      ok(f.fetch(BASE+"/history?user="+A).statusCode()==404);
      ok(f.fetch(BASE+"/edit").statusCode()==404);
      ok(f.fetch(BASE,"POST",Map.of(),true).statusCode()==405);
      ok(f.fetch(BASE,"GET",Map.of("Cookie","session=evil"),true).statusCode()==400);
      ok(f.fetch(BASE,"GET",Map.of("Origin","https://evil.invalid"),true).statusCode()==400);
      ok(f.fetch(BASE,"GET",Map.of("Accept","text/html"),true).statusCode()==406);
      ok(f.fetch(BASE,"GET",Map.of(),false).statusCode()==401);
      f.validToken=false;ok(f.fetch(BASE).statusCode()==401);f.validToken=true;
      f.validProof=false;ok(f.fetch(BASE).statusCode()==404);f.validProof=true;
      f.wrongPatient=true;ok(f.fetch(BASE).statusCode()==404);f.wrongPatient=false;
      f.readGrant=false;ok(f.fetch(BASE).statusCode()==404);f.readGrant=true;
      var current=f.store.cases.get(new TriagePersistencePort.Scope(T,F,P,C));
      for(int n=0;n<120;n++){
        var command=new TriageCommand(UUID.randomUUID(),A,NOW.plusSeconds(n+2),current.revision(),"synthetic amendment");
        current=new TriageIntakeService().recordArrivalReason(TriageWorkflowBatchesTest.a,TriageWorkflowBatchesTest.e,current,command,"repeated correction");
      }
      f.store.seed(current);
      var page1=f.fetch(BASE+"/history");ok(page1.statusCode()==200&&page1.body().contains("\"nextAfterRevision\":100"));
      ok(page1.body().split("\"revision\":",-1).length-1==100);
      var page2=f.fetch(BASE+"/history?afterRevision=100");ok(page2.statusCode()==200&&page2.body().contains("\"nextAfterRevision\":null"));
      ok(page2.body().split("\"revision\":",-1).length-1==22);
      ok(f.fetch(BASE+"/history?afterRevision=122").body().contains("\"history\":[]"));
      ok(f.fetch(BASE+"/history?afterRevision=001").statusCode()==404);
      ok(f.fetch(BASE+"/history?afterRevision=1&afterRevision=2").statusCode()==404);
      f.failStore=true;var service=f.fetch(BASE);ok(service.statusCode()==503);f.notLeaking(service);
    }
    System.out.println("TRIAGE_READ_HTTP PASS assertions="+assertions);
  }
}
