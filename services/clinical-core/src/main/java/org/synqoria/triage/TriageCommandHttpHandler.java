package org.synqoria.triage;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.synqoria.http.*;
import org.synqoria.security.*;
import java.io.IOException;
import java.net.URI;
import java.time.*;
import java.util.*;

/** Actual HTTP transport for existing-case triage mutations. No default production wiring.
 * Verified identity and current clinical evidence must be supplied by trusted server-side dependencies.
 * The caller cannot submit tenant, facility, actor, timestamp or access proof in its JSON.
 */
public final class TriageCommandHttpHandler implements HttpHandler {
 @FunctionalInterface public interface ClinicalEvidenceSource {
  Optional<TrustedClinicalAccessEvidence> resolve(TrustedAccessContext actor,UUID patient,String capability,Instant now);
 }
 private final BearerAdmission bearer;
 private final ClinicalEvidenceSource evidence;
 private final TriageAuthenticatedCommandGateway gateway;
 private final Clock clock;
 public TriageCommandHttpHandler(BearerAdmission bearer,ClinicalEvidenceSource evidence,TriageAuthenticatedCommandGateway gateway,Clock clock){
  this.bearer=Objects.requireNonNull(bearer);this.evidence=Objects.requireNonNull(evidence);
  this.gateway=Objects.requireNonNull(gateway);this.clock=Objects.requireNonNull(clock);
 }
 public record Route(UUID patient,UUID caseId) {}
 public static Route route(URI uri){
  if(uri==null||uri.getRawQuery()!=null||uri.getRawFragment()!=null)return null;
  String path=uri.getRawPath();
  if(path==null||path.length()!=112||!path.matches("/api/v1/triage/patients/[0-9a-f-]{36}/cases/[0-9a-f-]{36}/commands"))return null;
  String[] p=path.split("/");
  try{
   UUID patient=UUID.fromString(p[5]),id=UUID.fromString(p[7]);
   if(!p[5].equals(patient.toString())||!p[7].equals(id.toString()))return null;
   return new Route(patient,id);
  }catch(IllegalArgumentException ex){return null;}
 }
 private static int envelope(HttpExchange x){
  if(!"POST".equals(x.getRequestMethod()))return 405;
  var h=x.getRequestHeaders();
  for(String forbidden:List.of("Origin","Cookie","Expect","Transfer-Encoding","X-HTTP-Method-Override",
   "X-Tenant-Id","X-Facility-Id","X-Patient-Id","X-Actor-Id","X-Role","X-Capabilities","X-Clinical-Evidence","X-Forwarded-User"))
   if(h.containsKey(forbidden))return 400;
  var type=h.get("Content-Type");
  if(type==null||type.size()!=1||!"application/json".equals(type.get(0).trim().toLowerCase(Locale.ROOT)))return 415;
  var accept=h.get("Accept");
  if(accept!=null&&(accept.size()!=1||!Set.of("application/json","*/*").contains(accept.get(0).trim().toLowerCase(Locale.ROOT))))return 406;
  var len=h.get("Content-Length");
  if(len==null||len.size()!=1||!len.get(0).matches("[1-9][0-9]{0,4}"))return 400;
  try{int n=Integer.parseInt(len.get(0));if(n>16384)return 413;}catch(NumberFormatException e){return 413;}
  return 0;
 }
 static UUID uuid(Object o){
  if(!(o instanceof String s)||!s.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw new IllegalArgumentException("invalid UUID");
  UUID id=UUID.fromString(s);if(!id.toString().equals(s))throw new IllegalArgumentException("noncanonical UUID");return id;
 }
 private static String requireText(Object input){if(!(input instanceof String v))throw new IllegalArgumentException("invalid text field");return v;}
 private static TriageAuthenticatedCommandGateway.Input input(Map<String,Object> data,UUID verifiedActor){
  if(!(data.get("action") instanceof String action))throw new IllegalArgumentException("missing action");
  String text=data.containsKey("text")?requireText(data.get("text")):null;
  UUID reference=data.containsKey("reference")?uuid(data.get("reference")):null;
  TriageCase.Observation observation=null;
  if(data.containsKey("observation")){
   @SuppressWarnings("unchecked") var values=(Map<String,Object>)data.get("observation");
   if(values.size()!=(values.containsKey("replaces")?6:5))throw new IllegalArgumentException("observation shape unavailable");
   String code=requireText(values.get("code")),value=requireText(values.get("value")),unit=requireText(values.get("unit"));
   Instant taken;try{taken=Instant.parse(requireText(values.get("takenAt")));}catch(java.time.format.DateTimeParseException ex){throw new IllegalArgumentException("observation time invalid");}
   observation=new TriageCase.Observation(uuid(values.get("id")),code,value,unit,taken,verifiedActor,
      values.containsKey("replaces")?uuid(values.get("replaces")):null);
  }
  return new TriageAuthenticatedCommandGateway.Input(action,text,reference,observation);
 }
 @Override public void handle(HttpExchange x)throws IOException{
  UUID requestId=UUID.randomUUID();
  try{
   Route r=route(x.getRequestURI());if(r==null){reply(x,404,"not_found",requestId);return;}
   int invalid=envelope(x);if(invalid!=0){reply(x,invalid,"invalid_request",requestId);return;}
   var actor=bearer.admit(x.getRequestHeaders(),clock.instant());
   if(actor.isEmpty()){reply(x,401,"authentication_required",requestId);return;}
   byte[] bytes=x.getRequestBody().readNBytes(16385);
   if(bytes.length>16384){reply(x,413,"invalid_request",requestId);return;}
   if(bytes.length!=Integer.parseInt(x.getRequestHeaders().getFirst("Content-Length"))){reply(x,400,"invalid_request",requestId);return;}
   Map<String,Object> body=TriageStrictJson.parse(bytes);
   if(body.keySet().stream().anyMatch(k->Set.of("appointment","caseIds","stationOrClinician","after").contains(k)))throw new IllegalArgumentException("field not available for commands");
   UUID commandId=uuid(body.get("commandId"));
   if(!(body.get("expectedRevision") instanceof Integer rev)||rev<1){reply(x,422,"invalid_request",requestId);return;}
   TriageAuthenticatedCommandGateway.Input input=input(body,actor.get().actorId());
   String capability=TriageAuthenticatedCommandGateway.capability(input.action());
   var proof=evidence.resolve(actor.get(),r.patient(),capability,clock.instant());
   if(proof==null||proof.isEmpty()){reply(x,404,"not_found",requestId);return;}
   var scope=new TriagePersistencePort.Scope(actor.get().tenantId(),actor.get().facilityId(),r.patient(),r.caseId());
   var command=new TriageCommand(commandId,actor.get().actorId(),clock.instant(),rev,body.containsKey("rationale")?requireText(body.get("rationale")):null);
   var result=gateway.execute(actor.get(),proof.get(),scope,command,input);
   ClinicalResponse.send(x,200,"{\"revision\":"+result.revision()+",\"eventDigest\":\""+result.auditDigest()+"\",\"replay\":"+result.replay()+"}",requestId);
  }catch(IllegalArgumentException ex){reply(x,422,"invalid_request",requestId);}
   catch(SecurityException ex){reply(x,404,"not_found",requestId);}
   catch(TriageCommandConflict ex){reply(x,ex.staleRevision()?412:409,ex.staleRevision()?"stale_revision":"state_conflict",requestId);}
   catch(RuntimeException ex){reply(x,503,"service_unavailable",requestId);}
 }
 private static void reply(HttpExchange x,int status,String code,UUID requestId)throws IOException{
  ClinicalResponse.send(x,status,ClinicalResponse.problem(status,code),requestId);
 }
}
