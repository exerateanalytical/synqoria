package org.synqoria.longitudinal;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.synqoria.http.BearerAdmission;
import org.synqoria.http.ClinicalResponse;
import org.synqoria.security.TrustedAccessContext;
import org.synqoria.security.TrustedClinicalAccessEvidence;
import org.synqoria.longitudinal.ClinicalCaseCore.*;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/** Opt-in, authenticated M04-M06 HTTP adapter. No production OIDC/database wiring is shipped.
 * Patient/encounter URL is the sole caller-provided scope; tenant, facility, actor, proof and time
 * are resolved only from verified backend dependencies. A production bootstrap must use real RLS.
 */
public final class LongitudinalHttpHandler implements HttpHandler {
  @FunctionalInterface public interface ClinicalEvidenceSource {
    Optional<TrustedClinicalAccessEvidence> resolve(TrustedAccessContext actor,UUID patient,String capability,Instant now);
  }
  public record Route(UUID patient,UUID encounter,String resource,UUID member,Map<String,String> query) {}
  private static final Pattern ROOT=Pattern.compile(
    "/api/v1/clinical/patients/([0-9a-f-]{36})/encounters/([0-9a-f-]{36})/(commands|summary|audit|note-index|observations(?:/[0-9a-f-]{36}/lineage)?|notes(?:/[0-9a-f-]{36})?)");
  private final BearerAdmission bearer;
  private final ClinicalEvidenceSource evidence;
  private final LongitudinalAuthenticatedGateway gateway;
  private final Clock clock;
  public LongitudinalHttpHandler(BearerAdmission bearer,ClinicalEvidenceSource evidence,
                                  LongitudinalAuthenticatedGateway gateway,Clock clock){
    this.bearer=Objects.requireNonNull(bearer);this.evidence=Objects.requireNonNull(evidence);
    this.gateway=Objects.requireNonNull(gateway);this.clock=Objects.requireNonNull(clock);
  }
  private static UUID canonicalUuid(String text){
    UUID v=UUID.fromString(text);if(!v.toString().equals(text))throw new IllegalArgumentException("noncanonical id");return v;
  }
  public static Route route(URI uri){
    if(uri==null||uri.getRawFragment()!=null||uri.getRawPath()==null)return null;
    var match=ROOT.matcher(uri.getRawPath());if(!match.matches())return null;
    try{
      UUID patient=canonicalUuid(match.group(1)),encounter=canonicalUuid(match.group(2));
      String resource=match.group(3);UUID member=null;
      if(resource.equals("notes"))return null; // individual note reads require an explicit note ID
      if(resource.startsWith("notes/")){member=canonicalUuid(resource.substring(6));resource="notes";}
      if(resource.startsWith("observations/")){
        member=canonicalUuid(resource.substring("observations/".length(),resource.length()-"/lineage".length()));
        resource="observation-lineage";
      }
      String query=uri.getRawQuery();Map<String,String> options=new LinkedHashMap<>();
      if(query!=null){
        if(resource.equals("commands")||resource.equals("summary")||resource.equals("notes")||resource.equals("observation-lineage"))return null;
        for(String item:query.split("&",-1)){
          int i=item.indexOf('=');if(i<1||i!=item.lastIndexOf('='))return null;
          String key=item.substring(0,i),value=item.substring(i+1);
          if(!(resource.equals("audit")&&Set.of("afterRevision","limit").contains(key))&&
             !(resource.equals("observations")&&Set.of("code","limit").contains(key))&&
             !(resource.equals("note-index")&&Set.of("afterId","limit").contains(key)))return null;
          if(options.containsKey(key)||value.length()>180||value.isEmpty())return null;
          if(resource.equals("observations")&&key.equals("code")){
            value=URLDecoder.decode(value,StandardCharsets.UTF_8);
            if(value.indexOf('%')>=0||value.indexOf('+')>=0||value.length()>128||value.isBlank())return null;
          }
          options.put(key,value);
        }
      }
      if(resource.equals("observations")&&!options.containsKey("code"))return null;
      if(resource.equals("note-index")&&options.containsKey("afterId"))canonicalUuid(options.get("afterId"));
      return new Route(patient,encounter,resource,member,Map.copyOf(options));
    }catch(RuntimeException e){return null;}
  }
  private static long boundedLong(Map<String,String> q,String field,long fallback,long max){
    String value=q.get(field);if(value==null)return fallback;
    if(!value.matches("(0|[1-9][0-9]{0,17})"))throw new IllegalArgumentException("invalid page");
    long n=Long.parseLong(value);if(n>max)throw new IllegalArgumentException("invalid page");return n;
  }
  private static int envelope(HttpExchange exchange,Route route){
    boolean write=route.resource.equals("commands");
    if(!exchange.getRequestMethod().equals(write?"POST":"GET"))return 405;
    var headers=exchange.getRequestHeaders();
    for(String name:List.of("Origin","Cookie","Expect","Transfer-Encoding","X-HTTP-Method-Override",
       "X-Tenant-Id","X-Facility-Id","X-Patient-Id","X-Actor-Id","X-Role","X-Capabilities",
       "X-Clinical-Evidence","X-Forwarded-User"))if(headers.containsKey(name))return 400;
    var accept=headers.get("Accept");
    if(accept!=null&&(accept.size()!=1||!Set.of("application/json","*/*").contains(
      accept.get(0).trim().toLowerCase(Locale.ROOT))))return 406;
    var contentType=headers.get("Content-Type");var size=headers.get("Content-Length");
    if(write){
      if(contentType==null||contentType.size()!=1||!"application/json".equals(contentType.get(0).trim().toLowerCase(Locale.ROOT)))return 415;
      if(size==null||size.size()!=1||!size.get(0).matches("[1-9][0-9]{0,4}"))return 400;
      if(Integer.parseInt(size.get(0))>32768)return 413;
    }else{
      if(contentType!=null||size!=null&&(size.size()!=1||!"0".equals(size.get(0))))return 400;
    }
    return 0;
  }
  static String json(String s){
    if(s==null)return "null";
    StringBuilder b=new StringBuilder("\"");
    for(int i=0;i<s.length();i++){
      char c=s.charAt(i);
      switch(c){
        case '"' -> b.append("\\\"");case '\\' -> b.append("\\\\");
        case '\n' -> b.append("\\n");case '\r' -> b.append("\\r");case '\t' -> b.append("\\t");
        default -> {if(c<0x20)b.append(String.format("\\u%04x",(int)c));else b.append(c);}
      }
    }
    return b.append('"').toString();
  }
  private static void reject(HttpExchange x,int status,String reason,UUID requestId)throws IOException{
    ClinicalResponse.send(x,status,ClinicalResponse.problem(status,reason),requestId);
  }
  @Override public void handle(HttpExchange x)throws IOException{
    UUID requestId=UUID.randomUUID();
    try{
      Route route=route(x.getRequestURI());if(route==null){reject(x,404,"not_found",requestId);return;}
      int invalid=envelope(x,route);if(invalid!=0){reject(x,invalid,"invalid_request",requestId);return;}
      Instant now=clock.instant();Optional<TrustedAccessContext> identity=bearer.admit(x.getRequestHeaders(),now);
      if(identity.isEmpty()){reject(x,401,"authentication_required",requestId);return;}
      String capability=switch(route.resource){
        case "commands" -> "encounter.write"; // operation-specific proof is resolved after strict parsing
        case "summary" -> "encounter.read";case "audit" -> "encounter.audit.read";
        case "notes","note-index" -> "note.read";case "observations","observation-lineage" -> "observation.read";
        default -> throw new IllegalArgumentException("unknown route");
      };
      LongitudinalStrictJson.Body body=null;
      if(route.resource.equals("commands")){
        byte[] bytes=x.getRequestBody().readNBytes(32769);
        if(bytes.length>32768){reject(x,413,"invalid_request",requestId);return;}
        if(bytes.length!=Integer.parseInt(x.getRequestHeaders().getFirst("Content-Length"))){reject(x,400,"invalid_request",requestId);return;}
        body=LongitudinalStrictJson.decode(bytes);
        capability=body.operation().startsWith("note.")?"note.write":body.operation().startsWith("observation.")?
          "observation.write":"encounter.write";
      }
      var proof=evidence.resolve(identity.get(),route.patient(),capability,now);
      if(proof==null||proof.isEmpty()){reject(x,404,"not_found",requestId);return;}
      var scope=new Scope(identity.get().tenantId(),identity.get().facilityId(),route.patient(),route.encounter());
      String response;
      if(body!=null){
        var result=gateway.command(identity.get(),proof.get(),scope,new Command(body.commandId(),body.expectedRevision(),now),
           body.operation(),body.inputs(),now);
        response="{\"revision\":"+result.revision()+",\"auditDigest\":"+json(result.auditHash())+
                 ",\"replay\":"+result.replay()+"}";
      }else if(route.resource.equals("summary")){
        var summary=gateway.summary(identity.get(),proof.get(),scope,now);
        response="{\"encounterId\":"+json(summary.encounterId().toString())+
           ",\"revision\":"+summary.revision()+",\"stage\":"+json(summary.stage().name())+
           ",\"onLeave\":"+summary.onLeave()+",\"notes\":"+summary.notes()+
           ",\"observations\":"+summary.observations()+",\"auditHead\":"+json(summary.auditHead())+"}";
      }else if(route.resource.equals("audit")){
        long after=boundedLong(route.query(),"afterRevision",0,Integer.MAX_VALUE);
        int limit=(int)boundedLong(route.query(),"limit",50,100);
        var page=gateway.history(identity.get(),proof.get(),scope,after,limit,now);
        String rows=String.join(",",page.rows().stream().map(row->"{\"revision\":"+row.revision()+
           ",\"operation\":"+json(row.operation())+",\"at\":"+json(row.at().toString())+
           ",\"digest\":"+json(row.digest())+"}").toList());
        response="{\"history\":["+rows+"],\"nextRevision\":"+page.nextRevision()+"}";
      }else if(route.resource.equals("note-index")){
        UUID after=route.query().containsKey("afterId")?canonicalUuid(route.query().get("afterId")):null;
        int limit=(int)boundedLong(route.query(),"limit",50,100);
        var page=gateway.noteIndex(identity.get(),proof.get(),scope,after,limit,now);
        response="{\"notes\":["+String.join(",",page.rows().stream().map(n->"{\"id\":"+json(n.id().toString())+
           ",\"status\":"+json(n.status().name())+",\"title\":"+json(n.title())+
           ",\"confidential\":"+n.confidential()+"}").toList())+
           "],\"nextId\":"+(page.nextId()==null?"null":json(page.nextId().toString()))+"}";
      }else if(route.resource.equals("observation-lineage")){
        var rows=gateway.observationLineage(identity.get(),proof.get(),scope,route.member(),now);
        response="{\"lineage\":["+String.join(",",rows.stream().map(o->"{\"id\":"+json(o.id().toString())+
         ",\"code\":"+json(o.code())+",\"value\":"+json(o.value())+
         ",\"unit\":"+json(o.unit())+",\"measuredAt\":"+json(o.measuredAt().toString())+
         ",\"status\":"+json(o.status().name())+
         ",\"replaces\":"+(o.replaces()==null?"null":json(o.replaces().toString()))+"}").toList())+"]}";
      }else if(route.resource.equals("notes")){
        var n=gateway.note(identity.get(),proof.get(),scope,route.member(),now);
        response="{\"id\":"+json(n.id().toString())+",\"status\":"+json(n.status().name())+
          ",\"author\":"+json(n.author().toString())+",\"title\":"+json(n.title())+
          ",\"body\":"+json(n.body())+",\"confidential\":"+n.confidential()+"}";
      }else{
        String code=route.query().get("code");int limit=(int)boundedLong(route.query(),"limit",50,100);
        var rows=gateway.observationTimeline(identity.get(),proof.get(),scope,code,limit,now);
        response="{\"observations\":["+String.join(",",rows.stream().map(o->"{\"id\":"+json(o.id().toString())+
          ",\"code\":"+json(o.code())+",\"value\":"+json(o.value())+
          ",\"unit\":"+json(o.unit())+",\"measuredAt\":"+json(o.measuredAt().toString())+
          ",\"status\":"+json(o.status().name())+"}").toList())+"]}";
      }
      ClinicalResponse.send(x,200,response,requestId);
    }catch(SecurityException ex){reject(x,404,"not_found",requestId);}
     catch(IllegalArgumentException ex){reject(x,422,"invalid_request",requestId);}
     catch(IllegalStateException ex){reject(x,409,"state_conflict",requestId);}
     catch(RuntimeException ex){reject(x,503,"service_unavailable",requestId);}
  }
}
