package org.synqoria.fleet.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.synqoria.fleet.domain.GatewayControlPlaneHttpClient.*;
import org.synqoria.fleet.domain.GatewayRouteAdmission.*;
import org.synqoria.fleet.domain.RoutingDecisionGate.Envelope;
import org.synqoria.fleet.domain.RoutingDecisionGate.Rejected;
import tools.jackson.databind.json.JsonMapper;

/** M39 HTTP boundary fault injection. Not a substitute for real workload mTLS or a gateway deployment. */
class GatewayControlPlaneHttpClientTest {
 private static final String KEY="m39-test-control-plane-key-1234567890111111111111";
 private static final String ROUTE="hospital-a.example";
 private static final URI ORIGIN=URI.create("https://fleet-control.svc.cluster.local:8443/");
 private static final Instant NOW=Instant.parse("2026-11-07T12:00:00Z");
 private static final Clock CLOCK=Clock.fixed(NOW,ZoneOffset.UTC);
 private static final UUID A=UUID.fromString("00000000-0000-4000-8000-000000000011");
 private static final UUID B=UUID.fromString("00000000-0000-4000-8000-000000000022");
 private final JsonMapper json=JsonMapper.builder().build();
 private final AtomicReference<Envelope> signed=new AtomicReference<>(sign(A,3,4,ROUTE));
 private final AtomicReference<CurrentRoute> fence=new AtomicReference<>(
    new CurrentRoute(ROUTE,A,3,4,"ACTIVE","HEALTHY",NOW.minusSeconds(5)));
 private final List<URI> requests=new ArrayList<>();
 private volatile int status=200;
 private volatile String type="application/json";
 private volatile String cache="no-store";
 private volatile URI effective=null;
 private volatile String override=null;
 private volatile boolean failNetwork=false;

 private static Envelope sign(UUID cell,long epoch,long version,String route){
  var d=new Domain.Decision(route,cell,epoch,version,NOW.minusSeconds(1),NOW.plusSeconds(59));
  return new Envelope(d.routeKey(),d.cellId(),d.placementEpoch(),d.directoryVersion(),
   d.issuedAt(),d.expiresAt(),Domain.sign(KEY,d));
 }
 private String encoded(boolean isFence){
  if(override!=null)return override;
  if(isFence){
   CurrentRoute c=fence.get();
   return json.writeValueAsString(Map.of("routeKey",c.routeKey(),"cellId",c.cellId().toString(),
    "placementEpoch",c.placementEpoch(),"directoryVersion",c.directoryVersion(),
    "cellState",c.cellState(),"health",c.health(),"observedAt",c.observedAt().toString()));
  }
  Envelope e=signed.get();
  return json.writeValueAsString(Map.of("routeKey",e.routeKey(),"cellId",e.cellId().toString(),
   "placementEpoch",e.placementEpoch(),"directoryVersion",e.directoryVersion(),
   "issuedAt",e.issuedAt().getEpochSecond(),"expiresAt",e.expiresAt().getEpochSecond(),
   "signature",e.signature(),"health","HEALTHY"));
 }
 private GatewayControlPlaneHttpClient client(){
  return new GatewayControlPlaneHttpClient(ORIGIN,"fleet-control.svc.cluster.local",url->{
   requests.add(url);
   if(failNetwork)throw new java.io.IOException("synthetic control plane failure");
   byte[] body=encoded(url.toString().endsWith("/fence")).getBytes(StandardCharsets.UTF_8);
   return new Reply(status,effective==null?url:effective,type,cache,body);
  },json);
 }
 private GatewayRouteAdmission admission(){
  GatewayControlPlaneHttpClient c=client();
  return new GatewayRouteAdmission(KEY,CLOCK,c,c,
   cell->cell.equals(A)?URI.create("https://a.cell.svc.cluster.local:8443/"):
     cell.equals(B)?URI.create("https://b.cell.svc.cluster.local:8443/"):null,
   ".svc.cluster.local");
 }
 private static void denied(String code,Runnable op){
  var e=assertThrows(Rejected.class,op::run);
  assertEquals(code,e.getMessage());
 }
 @Test void readsRealResponseFieldsAndChecksBothExactManagementPlanePaths() throws Exception{
  var c=client();
  assertEquals(A,c.fetch(ROUTE).cellId());
  assertEquals(3,c.snapshot(ROUTE).placementEpoch());
  assertEquals(List.of(ORIGIN.resolve("/api/v1/fleet/routing/"+ROUTE),
    ORIGIN.resolve("/api/v1/fleet/routing/"+ROUTE+"/fence")),requests);
 }
 @Test void forwardsOnlyToMappedHospitalAfterSignedDecisionAndLiveFence(){
  Target target=admission().admit(ROUTE);
  assertEquals(A,target.cellId());
  assertEquals("a.cell.svc.cluster.local",target.destination().getHost());
  assertEquals(3,requests.size()); // decision, live monotonic fence, independent second read
 }
 @Test void switchToSecondCellUsesOnlyTheSecondCellTrustedDestination(){
  var gate=admission();
  assertEquals(A,gate.admit(ROUTE).cellId());
  signed.set(sign(B,4,5,ROUTE));
  fence.set(new CurrentRoute(ROUTE,B,4,5,"ACTIVE","HEALTHY",NOW));
  Target next=gate.admit(ROUTE);
  assertEquals(B,next.cellId());
  assertEquals("b.cell.svc.cluster.local",next.destination().getHost());
  signed.set(sign(A,3,4,ROUTE));
  fence.set(new CurrentRoute(ROUTE,A,3,4,"ACTIVE","HEALTHY",NOW));
  denied("routing_decision_stale",()->gate.admit(ROUTE));
 }
 @Test void signedTargetMismatchIsDeniedEvenIfEpochMatches(){
  fence.set(new CurrentRoute(ROUTE,B,3,4,"ACTIVE","HEALTHY",NOW));
  denied("routing_target_mismatch",()->admission().admit(ROUTE));
 }
 @Test void wrongHospitalAndMalformedRouteNeverResultInNetworkCall() throws Exception{
  var c=client();
  assertThrows(IllegalArgumentException.class,()->c.fetch("../other"));
  assertThrows(IllegalArgumentException.class,()->c.snapshot("hospital/?fake=yes"));
  assertTrue(requests.isEmpty());
  denied("route_binding_denied",()->admission().admit("../other"));
  assertTrue(requests.isEmpty());
 }
 @Test void deniesUnavailableControlPlaneWithoutCachedTarget(){
  failNetwork=true;
  denied("routing_admission_unavailable",()->admission().admit(ROUTE));
  assertEquals(1,requests.size());
 }
 @Test void rejectsRedirectsAndNonOkOrUncacheableResponses() throws Exception{
  for(int code:new int[]{301,302,307,401,403,404,429,500,503}){
   status=code;
   assertThrows(IllegalStateException.class,()->client().fetch(ROUTE));
  }
  status=200;
  effective=URI.create("https://untrusted.example/api/v1/fleet/routing/"+ROUTE);
  assertThrows(IllegalStateException.class,()->client().fetch(ROUTE));
  effective=null;cache="public, max-age=60";
  assertThrows(IllegalStateException.class,()->client().snapshot(ROUTE));
  cache="no-store";type="text/html";
  assertThrows(IllegalStateException.class,()->client().snapshot(ROUTE));
 }
 @Test void rejectsTamperedAndMaltypedFenceOrSignature(){
  override=encoded(false).replace("\"placementEpoch\":3","\"placementEpoch\":3.9");
  assertThrows(IllegalArgumentException.class,()->client().fetch(ROUTE));
  override=encoded(false).replace("\"cellId\":\""+A+"\"","\"cellId\":\"bad\"");
  assertThrows(IllegalArgumentException.class,()->client().fetch(ROUTE));
  override=encoded(false).replace("\"routeKey\":\""+ROUTE+"\"","\"routeKey\":\"hospital-b.example\"");
  assertThrows(IllegalArgumentException.class,()->client().fetch(ROUTE));
  override="{\"routeKey\":\""+ROUTE+"\",\"signature\":\"bad\"}";
  assertThrows(IllegalArgumentException.class,()->client().fetch(ROUTE));
  override="{\"routeKey\":\""+ROUTE+"\",\"cellId\":\""+A+"\",\"placementEpoch\":3.3}";
  assertThrows(IllegalArgumentException.class,()->client().snapshot(ROUTE));
 }
 @Test void rejectsMalformedOrOversizedAndInvalidUtf8WithoutFallback(){
  override="[";assertThrows(RuntimeException.class,()->client().fetch(ROUTE));
  override="x".repeat(MAX_JSON_BYTES+1);
  assertThrows(IllegalStateException.class,()->client().fetch(ROUTE));
  var c=new GatewayControlPlaneHttpClient(ORIGIN,"fleet-control.svc.cluster.local",
    u->new Reply(200,u,"application/json","no-store",new byte[]{(byte)0xff}),json);
  assertThrows(IllegalStateException.class,()->c.fetch(ROUTE));
 }
 @Test void trustedControlPlaneOriginCannotBeSelectedByCaller(){
  for(String unsafe:new String[]{
   "http://fleet-control.svc.cluster.local","https://fleet-control.svc.cluster.local.evil.example",
   "https://operator@fleet-control.svc.cluster.local",
   "https://fleet-control.svc.cluster.local/path","https://fleet-control.svc.cluster.local/?a=1",
   "https://fleet-control.svc.cluster.local/#fragment"
  }){
   assertThrows(IllegalArgumentException.class,()->new GatewayControlPlaneHttpClient(
     URI.create(unsafe),"fleet-control.svc.cluster.local",u->null,json),unsafe);
  }
 }
 @Test void transportRejectsMissingMutualTlsContextOrTokenProvider(){
  assertThrows(NullPointerException.class,()->new MutualTlsExchange(null,()->"x".repeat(48)));
  assertThrows(NullPointerException.class,()->new MutualTlsExchange(javax.net.ssl.SSLContext.getDefault(),null));
 }
 @Test void returnedRawHttpReplyDefensivelyCopiesBody(){
  byte[] b="{}".getBytes(StandardCharsets.UTF_8);
  Reply r=new Reply(200,ORIGIN,"application/json","no-store",b);
  b[0]=0;
  assertEquals('{',(char)r.body()[0]);
  byte[] x=r.body();x[0]=0;
  assertEquals('{',(char)r.body()[0]);
 }
}
