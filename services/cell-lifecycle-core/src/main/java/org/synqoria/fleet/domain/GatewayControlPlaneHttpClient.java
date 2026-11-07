package org.synqoria.fleet.domain;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import tools.jackson.databind.json.JsonMapper;

/**
 * Authenticated M39 gateway -> management-plane HTTPS adapter.
 *
 * Construct this with an independently provisioned mutual-TLS SSLContext and
 * a workload-token source (never a user/browser header). Routes and cell targets
 * are not learned from client requests. This is an embeddable data-plane client:
 * a real gateway filter, workload certificate provisioning, route stream fencing,
 * and deployment configuration are still separate required work.
 */
public final class GatewayControlPlaneHttpClient
    implements GatewayRouteAdmission.SignedDecisions, GatewayRouteAdmission.Directory {
 public static final int MAX_JSON_BYTES = 8192;

 /** Gateway workload-only token source, refreshed per HTTP request. */
 @FunctionalInterface public interface WorkloadTokens {
  String currentToken() throws Exception;
 }

 /** Exact management-plane HTTP response, including the final URL to detect redirects. */
 public record Reply(int status, URI effectiveUri, String contentType, String cacheControl, byte[] body) {
  public Reply { body=body==null?null:body.clone(); }
  @Override public byte[] body(){return body==null?null:body.clone();}
 }

 /** Injectable HTTP boundary for controlled fault-injection tests, never user-supplied. */
 @FunctionalInterface public interface Exchange {
  Reply get(URI url) throws Exception;
 }

 /**
  * Production network boundary. HttpClient is private and always uses redirect
  * NEVER, explicit endpoint-name verification, caller-supplied mutual TLS context,
  * and finite connection/request timeouts. The context and workload token MUST
  * be provisioned independently of patient requests and stored outside source.
  */
 public static final class MutualTlsExchange implements Exchange {
  private final HttpClient client;
  private final WorkloadTokens tokens;

  public MutualTlsExchange(SSLContext mutuallyAuthenticatedContext, WorkloadTokens tokens) {
   Objects.requireNonNull(mutuallyAuthenticatedContext,"gateway_mtls_required");
   this.tokens=Objects.requireNonNull(tokens);
   SSLParameters parameters=new SSLParameters();
   parameters.setEndpointIdentificationAlgorithm("HTTPS");
   this.client=HttpClient.newBuilder().sslContext(mutuallyAuthenticatedContext)
       .sslParameters(parameters).connectTimeout(Duration.ofSeconds(2))
       .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_2).build();
  }

  @Override public Reply get(URI url) throws Exception {
   String token=tokens.currentToken();
   if(token==null || token.length()<32 || token.length()>8192 ||
       !token.matches("[A-Za-z0-9._~+/-]+"))
    throw new IllegalStateException("gateway_workload_token_unavailable");
   HttpRequest request=HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(3))
       .header("Authorization","Bearer "+token).header("Accept","application/json")
       .header("Cache-Control","no-store").GET().build();
   HttpResponse<InputStream> response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
   try(InputStream stream=response.body()){
    if(response.statusCode()!=200 || !url.equals(response.uri()))
     throw new IllegalStateException("gateway_control_plane_unavailable");
    String contentType=response.headers().firstValue("Content-Type").orElse("");
    String cacheControl=response.headers().firstValue("Cache-Control").orElse("");
    if(!isJson(contentType) || !cacheControl.toLowerCase(java.util.Locale.ROOT).contains("no-store"))
     throw new IllegalStateException("gateway_control_plane_invalid_response");
    long advertised=response.headers().firstValueAsLong("Content-Length").orElse(-1);
    if(advertised>MAX_JSON_BYTES)
     throw new IllegalStateException("gateway_control_plane_response_too_large");
    byte[] payload=stream.readNBytes(MAX_JSON_BYTES+1);
    if(payload.length>MAX_JSON_BYTES)
     throw new IllegalStateException("gateway_control_plane_response_too_large");
    return new Reply(200,response.uri(),contentType,cacheControl,payload);
   }
  }
 }

 private final URI origin;
 private final Exchange exchange;
 private final JsonMapper json;

 /** expectedHost must be a trusted workload configuration value, not Host or Origin headers. */
 public GatewayControlPlaneHttpClient(URI origin, String expectedHost, Exchange exchange, JsonMapper json) {
  this.origin=Objects.requireNonNull(origin);
  this.exchange=Objects.requireNonNull(exchange);
  this.json=Objects.requireNonNull(json);
  if(expectedHost==null || !expectedHost.matches("[a-z0-9][a-z0-9.-]{2,252}") ||
     !origin.isAbsolute() || !"https".equals(origin.getScheme()) ||
     !expectedHost.equals(origin.getHost()) || origin.getRawUserInfo()!=null ||
     origin.getRawQuery()!=null || origin.getRawFragment()!=null ||
     !("".equals(origin.getRawPath()) || "/".equals(origin.getRawPath())) ||
     origin.getPort()==0 || origin.getPort()>65535)
   throw new IllegalArgumentException("invalid_trusted_control_plane_origin");
 }

 private static boolean isJson(String type){
  return type!=null && type.toLowerCase(java.util.Locale.ROOT)
   .matches("application/json(?:\\s*;\\s*charset=utf-8)?");
 }

 private static String text(Map<?,?> o,String key){
  Object v=o.get(key);
  if(!(v instanceof String s) || s.isBlank() || s.length()>512)
   throw new IllegalArgumentException("invalid_control_plane_field");
  return s;
 }
 private static long integer(Map<?,?> o,String key){
  Object v=o.get(key);
  // Never coerce a fractional or scientific-notation number into a fence epoch.
  if(!(v instanceof Long || v instanceof Integer || v instanceof Short ||
       v instanceof Byte || v instanceof java.math.BigInteger))
   throw new IllegalArgumentException("invalid_control_plane_field");
  try{
   return new java.math.BigInteger(v.toString()).longValueExact();
  }catch(ArithmeticException e){throw new IllegalArgumentException("invalid_control_plane_field",e);}
 }
 private static UUID uuid(Map<?,?> o,String key){
  return UUID.fromString(text(o,key));
 }
 private static Instant epoch(Map<?,?> o,String key){
  return Instant.ofEpochSecond(integer(o,key));
 }

 private Map<?,?> load(String routeKey, boolean fence) throws Exception {
  if(routeKey==null || !routeKey.matches("[a-z0-9][a-z0-9.-]{2,119}"))
   throw new IllegalArgumentException("route_binding_denied");
  URI url=origin.resolve("/api/v1/fleet/routing/"+routeKey+(fence?"/fence":""));
  Reply r=exchange.get(url);
  if(r==null || r.status()!=200 || !url.equals(r.effectiveUri()) ||
     !isJson(r.contentType()) ||
     !r.cacheControl().toLowerCase(java.util.Locale.ROOT).contains("no-store") ||
     r.body()==null || r.body().length==0 || r.body().length>MAX_JSON_BYTES)
   throw new IllegalStateException("gateway_control_plane_invalid_response");
  final String encoded;
  try{
   encoded=StandardCharsets.UTF_8.newDecoder()
      .onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT)
      .decode(ByteBuffer.wrap(r.body())).toString();
  }catch(CharacterCodingException e){
   throw new IllegalStateException("gateway_control_plane_invalid_response",e);
  }
  Object decoded=json.readValue(encoded,Object.class);
  if(!(decoded instanceof Map<?,?> fields))
   throw new IllegalArgumentException("invalid_control_plane_field");
  if(!routeKey.equals(text(fields,"routeKey")))
   throw new IllegalArgumentException("route_binding_denied");
  return fields;
 }

 @Override public RoutingDecisionGate.Envelope fetch(String routeKey) throws Exception {
  Map<?,?> d=load(routeKey,false);
  String signature=text(d,"signature");
  if(!signature.matches("[0-9a-f]{64}"))
   throw new IllegalArgumentException("invalid_control_plane_signature");
  return new RoutingDecisionGate.Envelope(routeKey,uuid(d,"cellId"),
   integer(d,"placementEpoch"),integer(d,"directoryVersion"),
   epoch(d,"issuedAt"),epoch(d,"expiresAt"),signature);
 }

 @Override public GatewayRouteAdmission.CurrentRoute snapshot(String routeKey) throws Exception {
  Map<?,?> d=load(routeKey,true);
  return new GatewayRouteAdmission.CurrentRoute(
   routeKey,uuid(d,"cellId"),integer(d,"placementEpoch"),integer(d,"directoryVersion"),
   text(d,"cellState"),text(d,"health"),Instant.parse(text(d,"observedAt")));
 }
}
