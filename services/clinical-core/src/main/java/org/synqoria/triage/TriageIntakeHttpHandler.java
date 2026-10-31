package org.synqoria.triage;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.synqoria.http.*;
import java.io.IOException;
import java.net.URI;
import java.time.*;
import java.util.*;

/** Separate authenticated M03 first-arrival creation transport. Opt-in wiring only.
 * Every identity and clinical-access claim is supplied by trusted server-side services.
 */
public final class TriageIntakeHttpHandler implements HttpHandler {
  private final BearerAdmission bearer;
  private final TriageCommandHttpHandler.ClinicalEvidenceSource evidence;
  private final TriageCommandCoordinator coordinator;
  private final Clock clock;
  public TriageIntakeHttpHandler(BearerAdmission bearer,TriageCommandHttpHandler.ClinicalEvidenceSource evidence,
                                 TriageCommandCoordinator coordinator,Clock clock){
    this.bearer=Objects.requireNonNull(bearer);this.evidence=Objects.requireNonNull(evidence);
    this.coordinator=Objects.requireNonNull(coordinator);this.clock=Objects.requireNonNull(clock);
  }
  public static TriageCommandHttpHandler.Route route(URI uri){
    if(uri==null||uri.getRawQuery()!=null||uri.getRawFragment()!=null)return null;
    String path=uri.getRawPath();
    if(path==null||!path.matches("/api/v1/triage/patients/[0-9a-f-]{36}/cases/[0-9a-f-]{36}/intake"))return null;
    String[] pieces=path.split("/");
    try{
      UUID patient=TriageCommandHttpHandler.uuid(pieces[5]),id=TriageCommandHttpHandler.uuid(pieces[7]);
      return new TriageCommandHttpHandler.Route(patient,id);
    }catch(IllegalArgumentException e){return null;}
  }
  private static void reply(HttpExchange x,int status,String code,UUID requestId)throws IOException{
    ClinicalResponse.send(x,status,ClinicalResponse.problem(status,code),requestId);
  }
  @Override public void handle(HttpExchange x)throws IOException{
    UUID requestId=UUID.randomUUID();
    try{
      var route=route(x.getRequestURI());if(route==null){reply(x,404,"not_found",requestId);return;}
      if(!"POST".equals(x.getRequestMethod())){reply(x,405,"invalid_request",requestId);return;}
      var headers=x.getRequestHeaders();
      for(var field:List.of("Origin","Cookie","Expect","Transfer-Encoding","X-Tenant-Id","X-Facility-Id","X-Patient-Id","X-Actor-Id","X-Clinical-Evidence","X-Forwarded-User"))
        if(headers.containsKey(field)){reply(x,400,"invalid_request",requestId);return;}
      if(!"application/json".equalsIgnoreCase(headers.getFirst("Content-Type"))){reply(x,415,"invalid_request",requestId);return;}
      if(headers.get("Content-Type")==null||headers.get("Content-Type").size()!=1){reply(x,415,"invalid_request",requestId);return;}
      if(headers.get("Accept")!=null&&(headers.get("Accept").size()!=1||!Set.of("application/json","*/*").contains(headers.getFirst("Accept")))){reply(x,406,"invalid_request",requestId);return;}
      var lengths=headers.get("Content-Length");if(lengths==null||lengths.size()!=1||!lengths.get(0).matches("[1-9][0-9]{0,4}")){reply(x,400,"invalid_request",requestId);return;}
      int size=Integer.parseInt(lengths.get(0));if(size>8192){reply(x,413,"invalid_request",requestId);return;}
      Instant now=clock.instant();var actor=bearer.admit(headers,now);
      if(actor.isEmpty()){reply(x,401,"authentication_required",requestId);return;}
      byte[] bytes=x.getRequestBody().readNBytes(8193);
      if(bytes.length>8192){reply(x,413,"invalid_request",requestId);return;}
      if(bytes.length!=size){reply(x,400,"invalid_request",requestId);return;}
      var body=TriageStrictJson.parse(bytes);
      if(!body.keySet().stream().allMatch(Set.of("commandId","expectedRevision","appointment")::contains) || !body.containsKey("commandId") || !Integer.valueOf(0).equals(body.get("expectedRevision")))throw new IllegalArgumentException("invalid intake envelope");
      UUID commandId=TriageCommandHttpHandler.uuid(body.get("commandId"));
      UUID appointment=body.containsKey("appointment")?TriageCommandHttpHandler.uuid(body.get("appointment")):null;
      var proof=evidence.resolve(actor.get(),route.patient(),"triage:intake",now);
      if(proof==null||proof.isEmpty()){reply(x,404,"not_found",requestId);return;}
      var scope=new TriagePersistencePort.Scope(actor.get().tenantId(),actor.get().facilityId(),route.patient(),route.caseId());
      var command=new TriageCommand(commandId,actor.get().actorId(),now,0,null);
      var result=coordinator.arrive(actor.get(),proof.get(),scope,command,appointment);
      ClinicalResponse.send(x,result.replay()?200:201,"{\"revision\":"+result.revision()+",\"eventDigest\":\""+result.auditDigest()+"\",\"replay\":"+result.replay()+"}",requestId);
    }catch(IllegalArgumentException e){reply(x,422,"invalid_request",requestId);}
     catch(SecurityException e){reply(x,404,"not_found",requestId);}
     catch(TriageCommandConflict e){reply(x,409,"state_conflict",requestId);}
     catch(RuntimeException e){reply(x,503,"service_unavailable",requestId);}
  }
}
