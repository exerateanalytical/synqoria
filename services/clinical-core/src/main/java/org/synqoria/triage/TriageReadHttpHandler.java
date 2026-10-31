package org.synqoria.triage;

import com.sun.net.httpserver.*;
import org.synqoria.http.*;
import org.synqoria.security.*;
import java.io.IOException;
import java.net.URI;
import java.time.*;
import java.util.*;

/** Opt-in patient-scoped GET transport; no production bootstrap/OIDC/RLS certification provided.
 * Never accepts caller-supplied tenant/facility/actor/clinical evidence or exposes free-text notes.
 */
public final class TriageReadHttpHandler implements HttpHandler {
  public record Route(UUID patient,UUID caseId,boolean history,int afterRevision) {}
  static final int HISTORY_PAGE_SIZE=100;
  private final BearerAdmission admission;
  private final TriageCommandHttpHandler.ClinicalEvidenceSource evidence;
  private final TriageAuthenticatedReadGateway reader;
  private final Clock clock;
  public TriageReadHttpHandler(BearerAdmission admission,TriageCommandHttpHandler.ClinicalEvidenceSource evidence,
                               TriageAuthenticatedReadGateway reader,Clock clock){
    this.admission=Objects.requireNonNull(admission);this.evidence=Objects.requireNonNull(evidence);
    this.reader=Objects.requireNonNull(reader);this.clock=Objects.requireNonNull(clock);
  }
  public static Route route(URI uri){
    if(uri==null||uri.getRawFragment()!=null)return null;
    String path=uri.getRawPath();if(path==null)return null;
    var matcher=java.util.regex.Pattern.compile("/api/v1/triage/patients/([0-9a-f-]{36})/cases/([0-9a-f-]{36})(/history)?").matcher(path);
    if(!matcher.matches())return null;
    try{
      var patient=UUID.fromString(matcher.group(1));var caseId=UUID.fromString(matcher.group(2));
      if(!patient.toString().equals(matcher.group(1))||!caseId.toString().equals(matcher.group(2)))return null;
      boolean history=matcher.group(3)!=null;int afterRevision=0;
      if(uri.getRawQuery()!=null){
        if(!history||!uri.getRawQuery().matches("afterRevision=(0|[1-9][0-9]{0,9})"))return null;
        afterRevision=Integer.parseInt(uri.getRawQuery().substring("afterRevision=".length()));
      }
      return new Route(patient,caseId,history,afterRevision);
    }catch(IllegalArgumentException ex){return null;}
  }
  private static int validate(HttpExchange x){
    if(!"GET".equals(x.getRequestMethod()))return 405;
    var h=x.getRequestHeaders();
    for(String forbidden:List.of("Origin","Cookie","Expect","Transfer-Encoding","X-Tenant-Id","X-Facility-Id",
      "X-Patient-Id","X-Actor-Id","X-Role","X-Capabilities","X-Clinical-Evidence","X-Forwarded-User"))
      if(h.containsKey(forbidden))return 400;
    if(h.containsKey("Content-Type"))return 400;
    var lengths=h.get("Content-Length");
    if(lengths!=null&&(lengths.size()!=1||!"0".equals(lengths.get(0))))return 400;
    var accept=h.get("Accept");
    if(accept!=null && (accept.size()!=1||!Set.of("application/json","*/*").contains(accept.get(0).trim().toLowerCase(Locale.ROOT))))return 406;
    return 0;
  }
  static String quoteJson(String value){
    if(value==null)return "null";
    StringBuilder out=new StringBuilder("\"");
    for(int index=0;index<value.length();index++){
      char c=value.charAt(index);
      switch(c){
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {if(c<0x20)out.append(String.format("\\u%04x",(int)c));else out.append(c);}
      }
    }
    return out.append('"').toString();
  }
  private static String safeId(UUID value){return value==null?"null":quoteJson(value.toString());}
  private static String summary(TriageReadService.Summary s){
    return "{\"caseId\":"+safeId(s.id())+",\"stage\":\""+s.stage().name()+"\",\"revision\":"+s.revision()+
      ",\"station\":"+safeId(s.station())+",\"assignee\":"+safeId(s.assignee())+
      ",\"arrivedAt\":\""+s.arrivedAt()+"\",\"paused\":"+s.paused()+
      ",\"safetyHold\":"+s.safetyHold()+",\"manualEscalation\":"+s.manuallyEscalated()+"}";
  }
  private static String history(TriageCase.Change change){
    return "{\"revision\":"+change.revision()+",\"operation\":"+quoteJson(change.operation())+
       ",\"occurredAt\":"+quoteJson(change.at().toString())+",\"digest\":"+quoteJson(change.digest())+"}";
  }
  @Override public void handle(HttpExchange x)throws IOException{
    UUID requestId=UUID.randomUUID();
    try{
      Route r=route(x.getRequestURI());if(r==null){reply(x,404,"not_found",requestId);return;}
      int invalid=validate(x);if(invalid!=0){reply(x,invalid,"invalid_request",requestId);return;}
      Instant now=clock.instant();var actor=admission.admit(x.getRequestHeaders(),now);
      if(actor.isEmpty()){reply(x,401,"authentication_required",requestId);return;}
      var proof=evidence.resolve(actor.get(),r.patient(),"triage:read",now);
      if(proof==null||proof.isEmpty()){reply(x,404,"not_found",requestId);return;}
      var view=r.history()?TriageAuthenticatedReadGateway.View.CASE_HISTORY:TriageAuthenticatedReadGateway.View.CASE_SUMMARY;
      var response=reader.read(actor.get(),proof.get(),r.patient(),List.of(r.caseId()),view,now,null,null);
      String payload;
      if(r.history()){
        var page=response.history().stream().filter(e->e.revision()>r.afterRevision()).limit(HISTORY_PAGE_SIZE+1L).toList();
        boolean more=page.size()>HISTORY_PAGE_SIZE;
        var visible=more?page.subList(0,HISTORY_PAGE_SIZE):page;
        int next=more?(int)visible.get(visible.size()-1).revision():0;
        payload="{\"history\":["+String.join(",",visible.stream().map(TriageReadHttpHandler::history).toList())+
           "],\"nextAfterRevision\":"+(more?String.valueOf(next):"null")+"}";
      }else payload="{\"case\":"+summary(response.summaries().get(0))+"}";
      ClinicalResponse.send(x,200,payload,requestId);
    }catch(SecurityException ex){reply(x,404,"not_found",requestId);}
     catch(IllegalArgumentException ex){reply(x,400,"invalid_request",requestId);}
     catch(RuntimeException ex){reply(x,503,"service_unavailable",requestId);}
  }
  private static void reply(HttpExchange x,int code,String reason,UUID id)throws IOException{
    ClinicalResponse.send(x,code,ClinicalResponse.problem(code,reason),id);
  }
}
