package org.synqoria.longitudinal;

import com.sun.net.httpserver.HttpServer;
import org.synqoria.http.BearerAdmission;
import org.synqoria.security.TrustedSessionAdmission;
import org.synqoria.security.TrustedClinicalAccessEvidence;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import static org.synqoria.longitudinal.ClinicalCaseCore.*;

/** Loopback HTTP -> trusted test session -> JDBC protocol simulator. NOT a live PostgreSQL integration. */
public final class LongitudinalJDBCWireLoopbackTest {
 private static final String TOKEN="SYNQORIA_SYNTHETIC_TEST_TOKEN_12345678";
 private static final Instant NOW=Instant.parse("2026-10-31T10:45:00Z");
 private static int assertions;
 private static void ok(boolean v){assertions++;if(!v)throw new AssertionError("clinical HTTP/JDBC bridge "+assertions);}
 static void run()throws Exception{
  Scope scope=new Scope(UUID.fromString("10000000-0000-0000-0000-000000000001"),
   UUID.fromString("10000000-0000-0000-0000-000000000002"),
   UUID.fromString("10000000-0000-0000-0000-000000000003"),
   UUID.fromString("10000000-0000-0000-0000-000000000004"));
  UUID actor=UUID.fromString("10000000-0000-0000-0000-000000000005");
  var db=new LongitudinalJdbcContractTest.FakePg(new State(scope));
  Set<String> caps=Set.of("encounter.write","encounter.read","encounter.audit.read");
  var auth=new TrustedSessionAdmission(token->{
    if(!TOKEN.equals(token))return Optional.empty();
    return Optional.of(new TrustedSessionAdmission.VerifiedIdentity("test-only-issuer","test-only-audience",
      actor,scope.tenant(),scope.facility(),NOW.minusSeconds(60),NOW.plusSeconds(300),true,false));
   },identity->Optional.of(new TrustedSessionAdmission.Privileges(caps,Set.of(scope.patient()),true,true)),
   "test-only-issuer","test-only-audience");
  var handler=LongitudinalClinicalWiring.jdbcHandler(db.source(),db.keys,new BearerAdmission(auth),
   (identity,patient,cap,at)->patient.equals(scope.patient())?Optional.of(new TrustedClinicalAccessEvidence(
     scope.tenant(),scope.facility(),actor,patient,NOW.minusSeconds(60),NOW.plusSeconds(300),
     true,true,true,true,true,true,true,caps)):Optional.empty(),Clock.fixed(NOW,ZoneOffset.UTC));
  HttpServer server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
  try{
   server.createContext("/",handler);server.start();
   HttpClient client=HttpClient.newHttpClient();String root="http://127.0.0.1:"+server.getAddress().getPort()+
    "/api/v1/clinical/patients/"+scope.patient()+"/encounters/"+scope.encounter();
   String id=UUID.randomUUID().toString();String command="{\"commandId\":\""+id+"\",\"expectedRevision\":0,\"operation\":\"encounter.checkin\",\"inputs\":{}}";
   HttpRequest request=HttpRequest.newBuilder(URI.create(root+"/commands"))
     .header("Authorization","Bearer "+TOKEN).header("Content-Type","application/json")
     .POST(HttpRequest.BodyPublishers.ofString(command)).build();
   HttpResponse<String> one=client.send(request,HttpResponse.BodyHandlers.ofString());
   ok(one.statusCode()==200&&one.body().contains("\"revision\":1"));
   ok(one.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
   var second=client.send(request,HttpResponse.BodyHandlers.ofString());
   ok(second.statusCode()==200&&second.body().contains("\"replay\":true"));
   var summary=client.send(HttpRequest.newBuilder(URI.create(root+"/summary"))
      .header("Authorization","Bearer "+TOKEN).GET().build(),HttpResponse.BodyHandlers.ofString());
   ok(summary.statusCode()==200&&summary.body().contains("\"revision\":1"));
   var unauthorized=client.send(HttpRequest.newBuilder(URI.create(root+"/summary"))
      .GET().build(),HttpResponse.BodyHandlers.ofString());
   ok(unauthorized.statusCode()==401);
   ok(db.caseUpdates==1&&db.eventInserts==1&&db.receiptInserts==1&&db.outboxInserts==1);
   ok(db.current.revision==1&&auditValid(db.current));
  }finally{server.stop(0);}
 }
 public static void main(String[] args)throws Exception{run();System.out.println("PASS trusted loopback JDBC protocol wiring ("+assertions+" assertions; no live DB)");}
}
