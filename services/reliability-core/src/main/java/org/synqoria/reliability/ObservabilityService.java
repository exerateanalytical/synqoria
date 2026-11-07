package org.synqoria.reliability;

import static org.synqoria.reliability.Db.m;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.synqoria.reliability.Db.Reply;
import org.synqoria.reliability.domain.Domain;
import org.synqoria.reliability.domain.Domain.*;

/** Service-health contracts, signals, derived health and capacity, and redacted telemetry with audited access. */
@Service
public class ObservabilityService {
 final Db db;
 static final Pattern KEY=Pattern.compile("[a-z][a-z0-9-]{2,47}"),CORR=Pattern.compile("[A-Za-z0-9._-]{8,64}"),TRACE=Pattern.compile("[0-9a-f]{16,64}"),EVT=Pattern.compile("[a-z][a-z0-9_.-]{2,63}");
 public ObservabilityService(Db db){this.db=db;}

 public record Dependency(String serviceKey,String kind){}
 public record ServiceRequest(String serviceKey,String name,String tier,String ownerRef,String recoveryBoundary,double sloTargetPct,List<Dependency> dependencies){}
 public record SignalRequest(String kind,double value,Instant observedAt,String traceId){}
 public record EventIn(String correlationId,String traceId,String spanId,String serviceKey,String level,String eventType,String message,Map<String,Object> attributes,Instant occurredAt){}
 public record EventBatch(List<EventIn> events){}

 static <T> T req(T v){if(v==null)throw new Violation("malformed_request");return v;}
 static void paging(int page,int size){if(page<0||size<1||size>200)throw new Violation("invalid_pagination");}

 public Reply registerService(Actor a,UUID cmd,ServiceRequest r){
  a.require(Perm.SERVICE_MANAGE);req(r);
  if(r.serviceKey()==null||!KEY.matcher(r.serviceKey()).matches()||r.name()==null||r.name().isBlank()||!Set.of("CRITICAL","IMPORTANT","SUPPORT").contains(r.tier())||r.ownerRef()==null||r.ownerRef().isBlank()||
     r.recoveryBoundary()==null||r.recoveryBoundary().isBlank()||r.sloTargetPct()<=0||r.sloTargetPct()>100||(r.dependencies()!=null&&r.dependencies().size()>30))throw new Violation("invalid_service");
  return db.command(a,cmd,"SERVICE_REGISTER",r,()->{
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_reliability.services(id,cell_id,facility_id,service_key,name,tier,owner_ref,recovery_boundary,slo_target_pct,version,created_by) VALUES (?,?,?,?,?,?,?,?,?,1,?)",
    id,a.cell(),a.facility(),r.serviceKey(),r.name().trim(),r.tier(),r.ownerRef().trim(),r.recoveryBoundary().trim(),r.sloTargetPct(),a.id());
   for(Dependency d:r.dependencies()==null?List.<Dependency>of():r.dependencies()){
    if(d==null||!Set.of("HARD","SOFT").contains(d.kind()))throw new Violation("invalid_service");
    UUID dep=db.rows("SELECT id FROM synqoria_reliability.services WHERE service_key=?",d.serviceKey()).stream().findFirst().map(x->(UUID)x.get("id")).orElseThrow(()->new Violation("not_found"));
    db.jdbc.update("INSERT INTO synqoria_reliability.service_dependencies(id,cell_id,facility_id,service_id,depends_on,kind) VALUES (?,?,?,?,?,?)",UUID.randomUUID(),a.cell(),a.facility(),id,dep,d.kind());
   }
   db.audit(a,cmd,"SERVICE",id,"REGISTER",null,"ACTIVE",1,m("key",r.serviceKey()));
   return new Reply(201,db.one("SELECT id,service_key,name,tier,owner_ref,recovery_boundary,slo_target_pct,version FROM synqoria_reliability.services WHERE id=?",id));
  });
 }
 public Reply reportSignal(Actor a,UUID cmd,UUID serviceId,SignalRequest s){
  a.require(Perm.SIGNAL_REPORT);req(s);
  if(s.kind()==null||!Domain.SIGNAL_KINDS.contains(s.kind())||s.observedAt()==null||s.value()<0||Double.isNaN(s.value())||(s.traceId()!=null&&!TRACE.matcher(s.traceId()).matches()))throw new Violation("invalid_signal");
  return db.command(a,cmd,"SIGNAL|"+serviceId,s,()->{
   db.one("SELECT id FROM synqoria_reliability.services WHERE id=?",serviceId);
   if(s.observedAt().isAfter(db.clock.instant().plusSeconds(60)))throw new Violation("invalid_signal");
   UUID id=UUID.randomUUID();String state=Domain.signalState(s.kind(),s.value());
   db.jdbc.update("INSERT INTO synqoria_reliability.health_signals(id,cell_id,facility_id,service_id,signal_kind,value,state,observed_at,trace_id,reported_by) VALUES (?,?,?,?,?,?,?,?,?,?)",
    id,a.cell(),a.facility(),serviceId,s.kind(),s.value(),state,Timestamp.from(s.observedAt()),s.traceId(),a.id());
   return new Reply(201,m("id",id.toString(),"state",state));
  });
 }

 /** Board: every service with derived health (UNKNOWN when nothing fresh), saturation and backlog. */
 public Map<String,Object> board(Actor a){
  a.require(Perm.HEALTH_READ);
  return db.scoped(a,()->{
   Instant now=db.clock.instant();
   List<Map<String,Object>> svcs=db.rows("SELECT id,service_key,name,tier,owner_ref,recovery_boundary,slo_target_pct FROM synqoria_reliability.services ORDER BY tier,service_key");
   Map<UUID,List<Signal>> sig=new HashMap<>();Map<UUID,Map<String,Object>> latest=new HashMap<>();
   for(Map<String,Object> r:db.rows("SELECT DISTINCT ON (service_id,signal_kind) service_id,signal_kind,value,state,observed_at FROM synqoria_reliability.health_signals ORDER BY service_id,signal_kind,observed_at DESC")){
    UUID sid=(UUID)r.get("serviceId");
    sig.computeIfAbsent(sid,x->new ArrayList<>()).add(new Signal((String)r.get("signalKind"),(String)r.get("state"),Instant.parse((String)r.get("observedAt"))));
    latest.computeIfAbsent(sid,x->new LinkedHashMap<>()).put((String)r.get("signalKind"),r.get("value"));
   }
   Map<UUID,String> base=new HashMap<>();
   for(Map<String,Object> s:svcs)base.put((UUID)s.get("id"),Domain.serviceHealth(sig.getOrDefault((UUID)s.get("id"),List.of()),List.of(),now));
   Map<UUID,List<String>> hard=new HashMap<>();
   for(Map<String,Object> d:db.rows("SELECT service_id,depends_on FROM synqoria_reliability.service_dependencies WHERE kind='HARD'"))
    hard.computeIfAbsent((UUID)d.get("serviceId"),x->new ArrayList<>()).add(base.get((UUID)d.get("dependsOn")));
   List<Map<String,Object>> out=new ArrayList<>();
   for(Map<String,Object> s:svcs){
    UUID id=(UUID)s.get("id");
    String h=Domain.serviceHealth(sig.getOrDefault(id,List.of()),hard.getOrDefault(id,List.of()),now);
    Map<String,Object> row=new LinkedHashMap<>(s);
    row.put("health",h);row.put("dependencyDegraded",h.equals("DEGRADED")&&base.get(id).equals("HEALTHY"));
    row.put("signals",latest.getOrDefault(id,Map.of()));
    out.add(row);
   }
   return m("services",out,"serverTime",now.toString(),"freshnessSeconds",Domain.SIGNAL_FRESHNESS.toSeconds());
  });
 }

 // ------------------------------------------------------------------ B02 telemetry
 public Reply ingest(Actor a,UUID cmd,EventBatch b){
  a.require(Perm.TELEMETRY_INGEST);req(b);
  if(b.events()==null||b.events().isEmpty()||b.events().size()>100)throw new Violation("invalid_batch");
  return db.command(a,cmd,"TELEMETRY_BATCH",b,()->{
   int accepted=0,redacted=0;
   for(EventIn e:b.events()){
    if(e==null||e.correlationId()==null||!CORR.matcher(e.correlationId()).matches()||!Set.of("DEBUG","INFO","WARN","ERROR").contains(e.level())||e.eventType()==null||!EVT.matcher(e.eventType()).matches()||
       e.occurredAt()==null||(e.traceId()!=null&&!TRACE.matcher(e.traceId()).matches())||(e.spanId()!=null&&!e.spanId().matches("[0-9a-f]{8,32}")))throw new Violation("invalid_event");
    Redacted<String> msg=Domain.redactText(e.message()==null?"":e.message());
    String text=msg.value().length()>400?msg.value().substring(0,400):msg.value();
    Redacted<Map<String,Object>> attrs=Domain.redactAttributes(e.attributes()==null?Map.of():e.attributes());
    UUID sid=null;
    if(e.serviceKey()!=null)sid=db.rows("SELECT id FROM synqoria_reliability.services WHERE service_key=?",e.serviceKey()).stream().findFirst().map(x->(UUID)x.get("id")).orElseThrow(()->new Violation("not_found"));
    db.jdbc.update("INSERT INTO synqoria_reliability.telemetry_events(id,cell_id,facility_id,service_id,correlation_id,trace_id,span_id,level,event_type,message,attributes,redactions,occurred_at) VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?)",
     UUID.randomUUID(),a.cell(),a.facility(),sid,e.correlationId(),e.traceId(),e.spanId(),e.level(),e.eventType(),text,db.encode(attrs.value()),msg.count()+attrs.count(),Timestamp.from(e.occurredAt()));
    accepted++;redacted+=msg.count()+attrs.count();
   }
   return new Reply(201,m("accepted",accepted,"redactions",redacted));
  });
 }
 /** Reading telemetry is itself recorded: who looked, what they asked for, how much they got. */
 public Map<String,Object> query(Actor a,String correlationId,String serviceKey,String level,Instant from,Instant to,int page,int size){
  a.require(Perm.TELEMETRY_READ);paging(page,size);
  if(correlationId!=null&&!CORR.matcher(correlationId).matches())throw new Violation("invalid_query");
  if(level!=null&&!Set.of("DEBUG","INFO","WARN","ERROR").contains(level))throw new Violation("invalid_query");
  if(from!=null&&to!=null&&to.isBefore(from))throw new Violation("invalid_query");
  return db.scoped(a,()->{
   StringBuilder w=new StringBuilder(" WHERE true");List<Object> args=new ArrayList<>();
   if(correlationId!=null){w.append(" AND t.correlation_id=?");args.add(correlationId);}
   if(serviceKey!=null){w.append(" AND s.service_key=?");args.add(serviceKey);}
   if(level!=null){w.append(" AND t.level=?");args.add(level);}
   if(from!=null){w.append(" AND t.occurred_at>=?");args.add(Timestamp.from(from));}
   if(to!=null){w.append(" AND t.occurred_at<=?");args.add(Timestamp.from(to));}
   String f=" FROM synqoria_reliability.telemetry_events t LEFT JOIN synqoria_reliability.services s ON s.id=t.service_id";
   long total=db.jdbc.queryForObject("SELECT count(*)"+f+w,Long.class,args.toArray());
   args.add(size);args.add((long)page*size);
   List<Map<String,Object>> rows=db.rows("SELECT t.id,t.correlation_id,t.trace_id,t.span_id,s.service_key,t.level,t.event_type,t.message,t.attributes,t.redactions,t.occurred_at"+f+w+" ORDER BY t.occurred_at DESC,t.id LIMIT ? OFFSET ?",args.toArray());
   Map<String,Object> q=new LinkedHashMap<>();
   if(correlationId!=null)q.put("correlationId",correlationId);if(serviceKey!=null)q.put("serviceKey",serviceKey);if(level!=null)q.put("level",level);
   if(from!=null)q.put("from",from.toString());if(to!=null)q.put("to",to.toString());
   db.jdbc.update("INSERT INTO synqoria_reliability.telemetry_access_log(id,cell_id,facility_id,actor_id,query_summary,returned) VALUES (?,?,?,?,?::jsonb,?)",UUID.randomUUID(),a.cell(),a.facility(),a.id(),db.encode(q),rows.size());
   return db.pageOf(rows,page,size,total);
  });
 }
 public Map<String,Object> accessLog(Actor a,int page,int size){
  a.require(Perm.TELEMETRY_READ);paging(page,size);
  return db.scoped(a,()->{
   long total=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_reliability.telemetry_access_log",Long.class);
   return db.pageOf(db.rows("SELECT id,actor_id,query_summary,returned,accessed_at FROM synqoria_reliability.telemetry_access_log ORDER BY accessed_at DESC,id LIMIT ? OFFSET ?",size,(long)page*size),page,size,total);
  });
 }
}
