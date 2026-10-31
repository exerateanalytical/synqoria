package org.synqoria.triage;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.synqoria.http.*;
import java.io.IOException;
import java.net.URI;
import java.time.*;
import java.util.*;

/** Eight bounded patient-scoped M03 worklist transports. Never a facility-wide PHI listing.
 * The caller supplies explicit case IDs (not grants); identity and proof remain server verified.
 * No production OIDC, RLS or DB performance qualification is claimed.
 */
public final class TriageWorklistHttpHandler implements HttpHandler {
  public static final Set<String> VIEWS=Set.of("FACILITY_QUEUE","STATION_QUEUE","PENDING_HANDOVERS","MANUAL_ESCALATIONS",
    "PENDING_REASSESSMENTS","SAFETY_HOLDS","CLINICIAN_WORKLIST","ARRIVALS_SINCE");
  public record Route(UUID patient,TriageAuthenticatedReadGateway.View view) {}
  private final BearerAdmission bearer;
  private final TriageCommandHttpHandler.ClinicalEvidenceSource proof;
  private final TriageAuthenticatedReadGateway reader;
  private final Clock clock;
  public TriageWorklistHttpHandler(BearerAdmission bearer,TriageCommandHttpHandler.ClinicalEvidenceSource proof,
                                   TriageAuthenticatedReadGateway reader,Clock clock){
    this.bearer=Objects.requireNonNull(bearer);this.proof=Objects.requireNonNull(proof);
    this.reader=Objects.requireNonNull(reader);this.clock=Objects.requireNonNull(clock);
  }
  public static Route route(URI uri){
    if(uri==null||uri.getRawQuery()!=null||uri.getRawFragment()!=null)return null;
    String path=uri.getRawPath();
    if(path==null||!path.matches("/api/v1/triage/patients/[0-9a-f-]{36}/views/[A-Z_]{4,40}"))return null;
    String[] p=path.split("/");
    try{
      UUID patient=TriageCommandHttpHandler.uuid(p[5]);
      if(!VIEWS.contains(p[7]))return null;
      return new Route(patient,TriageAuthenticatedReadGateway.View.valueOf(p[7]));
    }catch(IllegalArgumentException ex){return null;}
  }
  private static String quote(String s){return TriageReadHttpHandler.quoteJson(s);}
  private static String nullable(UUID u){return u==null?"null":quote(u.toString());}
  private static String result(TriageReadService.Summary s){
    return "{\"caseId\":"+quote(s.id().toString())+",\"stage\":"+quote(s.stage().name())+
       ",\"revision\":"+s.revision()+",\"station\":"+nullable(s.station())+
       ",\"assignee\":"+nullable(s.assignee())+",\"arrivedAt\":"+quote(s.arrivedAt().toString())+
       ",\"paused\":"+s.paused()+",\"safetyHold\":"+s.safetyHold()+",\"manualEscalation\":"+s.manuallyEscalated()+"}";
  }
  private static void fail(HttpExchange x,int code,String reason,UUID request)throws IOException{
    ClinicalResponse.send(x,code,ClinicalResponse.problem(code,reason),request);
  }
  @Override public void handle(HttpExchange x)throws IOException{
    UUID request=UUID.randomUUID();
    try{
      Route r=route(x.getRequestURI());if(r==null){fail(x,404,"not_found",request);return;}
      if(!"POST".equals(x.getRequestMethod())){fail(x,405,"invalid_request",request);return;}
      var h=x.getRequestHeaders();
      for(String forbidden:List.of("Origin","Cookie","Expect","Transfer-Encoding","X-Tenant-Id","X-Facility-Id","X-Patient-Id","X-Actor-Id","X-Clinical-Evidence","X-Forwarded-User"))
        if(h.containsKey(forbidden)){fail(x,400,"invalid_request",request);return;}
      if(h.get("Content-Type")==null||h.get("Content-Type").size()!=1||!"application/json".equalsIgnoreCase(h.getFirst("Content-Type"))){fail(x,415,"invalid_request",request);return;}
      if(h.containsKey("Accept")&&(h.get("Accept").size()!=1||!Set.of("application/json","*/*").contains(h.getFirst("Accept")))){fail(x,406,"invalid_request",request);return;}
      var len=h.get("Content-Length");if(len==null||len.size()!=1||!len.get(0).matches("[1-9][0-9]{0,4}")){fail(x,400,"invalid_request",request);return;}
      int declared=Integer.parseInt(len.get(0));if(declared>4096){fail(x,413,"invalid_request",request);return;}
      Instant now=clock.instant();var identity=bearer.admit(h,now);
      if(identity.isEmpty()){fail(x,401,"authentication_required",request);return;}
      byte[] bytes=x.getRequestBody().readNBytes(4097);if(bytes.length>4096){fail(x,413,"invalid_request",request);return;}
      if(bytes.length!=declared){fail(x,400,"invalid_request",request);return;}
      Map<String,Object> json=TriageStrictJson.parse(bytes);
      if(!json.containsKey("caseIds")||json.keySet().stream().anyMatch(k->!Set.of("caseIds","stationOrClinician","after").contains(k)))throw new IllegalArgumentException("invalid worklist arguments");
      @SuppressWarnings("unchecked") var cases=(List<String>)json.get("caseIds");
      List<UUID> ids=cases.stream().map(TriageCommandHttpHandler::uuid).toList();
      if(new HashSet<>(ids).size()!=ids.size())throw new IllegalArgumentException("duplicate case request");
      UUID station=json.containsKey("stationOrClinician")?TriageCommandHttpHandler.uuid(json.get("stationOrClinician")):null;
      Instant after=null;
      if(json.containsKey("after"))try{after=Instant.parse((String)json.get("after"));}catch(ClassCastException|java.time.format.DateTimeParseException e){throw new IllegalArgumentException("bad arrival window");}
      boolean requiresStation=r.view()==TriageAuthenticatedReadGateway.View.STATION_QUEUE||r.view()==TriageAuthenticatedReadGateway.View.CLINICIAN_WORKLIST;
      boolean requiresAfter=r.view()==TriageAuthenticatedReadGateway.View.ARRIVALS_SINCE;
      if(requiresStation!=(station!=null)||requiresAfter!=(after!=null))throw new IllegalArgumentException("unexpected worklist filter");
      var evidence=proof.resolve(identity.get(),r.patient(),"triage:read",now);
      if(evidence==null||evidence.isEmpty()){fail(x,404,"not_found",request);return;}
      var answer=reader.read(identity.get(),evidence.get(),r.patient(),ids,r.view(),now,station,after);
      ClinicalResponse.send(x,200,"{\"cases\":["+String.join(",",answer.summaries().stream().map(TriageWorklistHttpHandler::result).toList())+"]}",request);
    }catch(SecurityException ex){fail(x,404,"not_found",request);}
     catch(IllegalArgumentException ex){fail(x,422,"invalid_request",request);}
     catch(RuntimeException ex){fail(x,503,"service_unavailable",request);}
  }
}
