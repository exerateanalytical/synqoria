package org.synqoria.analytics;

import static org.synqoria.analytics.Db.m;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.synqoria.analytics.Db.Reply;
import org.synqoria.analytics.domain.Domain;
import org.synqoria.analytics.domain.Domain.*;

/** M28-B01/B02: source registry (independently verified), idempotent ordered ingestion, quarantine, retry and projection rebuild. */
@Service
public class IngestService {
 private final Db db;
 public IngestService(Db db){this.db=db;}

 public record SourceRequest(String sourceKey,String name,String domain,String ownerRef,int contractVersion,List<String> requiredFields,int maxLatenessMinutes,int maxStalenessMinutes){}
 public record Decision(long expectedVersion,boolean approve,String note){}
 public record Transition(long expectedVersion){}
 public record EventIn(String sourceEventId,String entityKey,long sourceVersion,Instant occurredAt,Map<String,Object> payload){}
 public record IngestRequest(List<EventIn> events){}
 public record DiscardRequest(String reason){}

 private Map<String,Object> lockSource(UUID id,long expected){
  List<Map<String,Object>> r=db.rows("SELECT * FROM synqoria_analytics.sources WHERE id=? FOR UPDATE",id);
  if(r.isEmpty())throw new Violation("not_found");
  if(((Number)r.getFirst().get("version")).longValue()!=expected)throw new Violation("stale_version");
  return r.getFirst();
 }
 private Map<String,Object> source(UUID id){return db.one("SELECT * FROM synqoria_analytics.sources WHERE id=?",id);}

 public Reply register(Actor a,UUID cmd,SourceRequest r){
  a.require(Perm.SOURCE_MANAGE);
  return db.command(a,cmd,"SOURCE_REGISTER",r,()->{
   if(r==null||!Domain.validKey(r.sourceKey())||r.name()==null||r.name().isBlank()||r.name().length()>160||r.ownerRef()==null||r.ownerRef().isBlank()||r.ownerRef().length()>120||
      r.contractVersion()<1||r.maxLatenessMinutes()<1||r.maxLatenessMinutes()>43200||r.maxStalenessMinutes()<1||r.maxStalenessMinutes()>43200||
      r.requiredFields()==null||r.requiredFields().isEmpty()||r.requiredFields().size()>40||r.requiredFields().stream().anyMatch(f->!Domain.validField(f)))throw new Violation("invalid_source");
   Perm.readFor(r.domain());
   if(!db.rows("SELECT 1 FROM synqoria_analytics.sources WHERE source_key=?",r.sourceKey()).isEmpty())throw new Violation("duplicate_record");
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_analytics.sources(id,cell_id,facility_id,source_key,name,domain,owner_ref,contract_version,required_fields,max_lateness_minutes,max_staleness_minutes,status,registered_by,version) "+
    "VALUES (?,?,?,?,?,?,?,?,?,?,?,'PENDING',?,1)",id,a.cell(),a.facility(),r.sourceKey(),r.name().strip(),r.domain(),r.ownerRef().strip(),r.contractVersion(),r.requiredFields().toArray(new String[0]),r.maxLatenessMinutes(),r.maxStalenessMinutes(),a.id());
   db.audit(a,cmd,"SOURCE",id,"REGISTER",null,"PENDING",1,m("key",r.sourceKey(),"domain",r.domain()));
   return new Reply(201,source(id));
  });
 }
 public Reply verify(Actor a,UUID cmd,UUID id,Decision d){
  a.require(Perm.SOURCE_VERIFY);
  return db.command(a,cmd,"SOURCE_VERIFY|"+id,d,()->{
   Map<String,Object> s=lockSource(id,d.expectedVersion());
   if(!"PENDING".equals(s.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(s.get("registeredBy")))throw new Violation("verifier_must_be_independent");
   if(!d.approve()&&(d.note()==null||d.note().isBlank()))throw new Violation("reason_required");
   String to=d.approve()?"VERIFIED":"SUSPENDED";
   db.jdbc.update("UPDATE synqoria_analytics.sources SET status=?,verified_by=?,verified_at=clock_timestamp(),version=version+1 WHERE id=?",to,a.id(),id);
   db.audit(a,cmd,"SOURCE",id,d.approve()?"VERIFY":"REJECT","PENDING",to,d.expectedVersion()+1,m());
   return new Reply(200,source(id));
  });
 }
 public Reply suspend(Actor a,UUID cmd,UUID id,Decision d){
  a.require(Perm.SOURCE_MANAGE);
  return db.command(a,cmd,"SOURCE_SUSPEND|"+id,d,()->{
   Map<String,Object> s=lockSource(id,d.expectedVersion());
   if(!"VERIFIED".equals(s.get("status")))throw new Violation("invalid_transition");
   if(d.note()==null||d.note().isBlank())throw new Violation("reason_required");
   db.jdbc.update("UPDATE synqoria_analytics.sources SET status='SUSPENDED',version=version+1 WHERE id=?",id);
   db.audit(a,cmd,"SOURCE",id,"SUSPEND","VERIFIED","SUSPENDED",d.expectedVersion()+1,m("note",d.note()));
   return new Reply(200,source(id));
  });
 }
 public List<Map<String,Object>> sources(Actor a){
  a.require(Perm.REPORT_READ);
  return db.scoped(a,()->db.rows("SELECT id,source_key,name,domain,owner_ref,contract_version,required_fields,max_lateness_minutes,max_staleness_minutes,status,registered_by,verified_by,version FROM synqoria_analytics.sources ORDER BY source_key"));
 }

 // ---------------------------------------------------------------- ingestion
 private static String hash(Map<String,Object> payload,Db db){
  return Domain.sha256(new TreeMap<>(payload).toString());
 }
 /** Applies one validated event under the entity's ordering rules. Returns APPLIED, SUPERSEDED or QUARANTINED. */
 private String apply(UUID sourceId,Actor a,UUID eventId,String entity,long version,Instant occurred,Map<String,Object> payload,String evHash){
  List<Map<String,Object>> cur=db.rows("SELECT source_version,event_id FROM synqoria_analytics.facts WHERE source_id=? AND entity_key=? FOR UPDATE",sourceId,entity);
  if(cur.isEmpty()){
   db.jdbc.update("INSERT INTO synqoria_analytics.facts(cell_id,facility_id,source_id,entity_key,source_version,occurred_at,data,event_id) VALUES (?,?,?,?,?,?,?::jsonb,?)",
    a.cell(),a.facility(),sourceId,entity,version,Timestamp.from(occurred),db.encode(payload),eventId);
   return "APPLIED";
  }
  long have=((Number)cur.getFirst().get("sourceVersion")).longValue();
  if(version>have){
   db.jdbc.update("UPDATE synqoria_analytics.ingest_events SET status='SUPERSEDED' WHERE id=? AND status='APPLIED'",cur.getFirst().get("eventId"));
   db.jdbc.update("UPDATE synqoria_analytics.facts SET source_version=?,occurred_at=?,data=?::jsonb,event_id=? WHERE source_id=? AND entity_key=?",
    version,Timestamp.from(occurred),db.encode(payload),eventId,sourceId,entity);
   return "APPLIED";
  }
  if(version==have){
   String curHash=db.one("SELECT payload_hash FROM synqoria_analytics.ingest_events WHERE id=?",cur.getFirst().get("eventId")).get("payloadHash").toString().trim();
   return curHash.equals(evHash)?"SUPERSEDED":"CONFLICT";
  }
  return "SUPERSEDED";                                                  // older than the current state: kept as evidence, not applied
 }

 public Reply ingest(Actor a,UUID cmd,UUID sourceId,IngestRequest r){
  a.require(Perm.INGEST);
  return db.command(a,cmd,"INGEST|"+sourceId,r,()->{
   if(r==null||r.events()==null||r.events().isEmpty()||r.events().size()>500)throw new Violation("invalid_batch");
   List<Map<String,Object>> sr=db.rows("SELECT * FROM synqoria_analytics.sources WHERE id=? FOR SHARE",sourceId);
   if(sr.isEmpty())throw new Violation("not_found");
   Map<String,Object> src=sr.getFirst();
   if(!"VERIFIED".equals(src.get("status")))throw new Violation("source_not_verified");
   @SuppressWarnings("unchecked") List<String> required=(List<String>)src.get("requiredFields");
   long late=((Number)src.get("maxLatenessMinutes")).longValue();
   Instant now=db.clock.instant();
   int applied=0,superseded=0,quarantined=0,duplicates=0,conflicts=0,lateCount=0;
   List<Map<String,Object>> quarantineList=new ArrayList<>();
   for(EventIn e:r.events()){
    if(e==null||e.sourceEventId()==null||e.sourceEventId().isBlank()||e.sourceEventId().length()>120||e.entityKey()==null||e.entityKey().isBlank()||e.entityKey().length()>120||e.sourceVersion()<1||e.payload()==null)
     throw new Violation("invalid_batch",List.of("MALFORMED_EVENT"));
    String h=hash(e.payload(),db);
    List<Map<String,Object>> ex=db.rows("SELECT payload_hash,status FROM synqoria_analytics.ingest_events WHERE source_id=? AND source_event_id=?",sourceId,e.sourceEventId());
    if(!ex.isEmpty()){
     if(ex.getFirst().get("payloadHash").toString().trim().equals(h))duplicates++;else{conflicts++;quarantineList.add(m("sourceEventId",e.sourceEventId(),"problems",List.of("SAME_ID_DIFFERENT_CONTENT")));}
     continue;
    }
    List<String> problems=Domain.validateEvent(required,e.payload(),e.occurredAt(),now,late);
    UUID id=UUID.randomUUID();
    boolean isLate=e.occurredAt()!=null&&Domain.isLate(e.occurredAt(),now,late);
    if(isLate)lateCount++;
    String outcome=problems.isEmpty()?apply(sourceId,a,id,e.entityKey(),e.sourceVersion(),e.occurredAt(),e.payload(),h):"QUARANTINED";
    if(outcome.equals("CONFLICT")){outcome="QUARANTINED";problems=List.of("VERSION_CONFLICT");}
    db.jdbc.update("INSERT INTO synqoria_analytics.ingest_events(id,cell_id,facility_id,source_id,source_event_id,entity_key,source_version,occurred_at,payload,payload_hash,status,late,problems) VALUES (?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?)",
     id,a.cell(),a.facility(),sourceId,e.sourceEventId(),e.entityKey(),e.sourceVersion(),Timestamp.from(e.occurredAt()==null?now:e.occurredAt()),db.encode(e.payload()),h,outcome,isLate,problems.toArray(new String[0]));
    switch(outcome){case "APPLIED"->applied++;case "SUPERSEDED"->superseded++;default->{quarantined++;quarantineList.add(m("sourceEventId",e.sourceEventId(),"problems",problems));}}
   }
   db.audit(a,cmd,"INGEST",sourceId,"BATCH","VERIFIED","VERIFIED",((Number)src.get("version")).longValue(),m("applied",applied,"superseded",superseded,"quarantined",quarantined,"duplicates",duplicates,"late",lateCount));
   return new Reply(200,m("applied",applied,"superseded",superseded,"quarantined",quarantined,"duplicates",duplicates,"conflicts",conflicts,"late",lateCount,"quarantine",quarantineList.stream().limit(50).toList()));
  });
 }
 public Map<String,Object> quarantine(Actor a,int page,int size){
  a.require(Perm.QUARANTINE_MANAGE);
  if(page<0||size<1||size>100)throw new Violation("invalid_pagination");
  return db.scoped(a,()->{
   long total=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_analytics.ingest_events WHERE status='QUARANTINED'",Long.class);
   return db.pageOf(db.rows("SELECT e.id,s.source_key,e.source_event_id,e.entity_key,e.source_version,e.occurred_at,e.received_at,e.problems,e.retry_count FROM synqoria_analytics.ingest_events e JOIN synqoria_analytics.sources s ON s.id=e.source_id "+
    "WHERE e.status='QUARANTINED' ORDER BY e.received_at,e.id LIMIT ? OFFSET ?",size,(long)page*size),page,size,total);
  });
 }
 /** Re-validates against the CURRENT contract; success applies the event, failure keeps it quarantined with the new reasons. */
 public Reply retry(Actor a,UUID cmd,UUID eventId){
  a.require(Perm.QUARANTINE_MANAGE);
  return db.command(a,cmd,"RETRY|"+eventId,m("id",eventId.toString()),()->{
   List<Map<String,Object>> er=db.rows("SELECT * FROM synqoria_analytics.ingest_events WHERE id=? FOR UPDATE",eventId);
   if(er.isEmpty())throw new Violation("not_found");
   Map<String,Object> e=er.getFirst();
   if(!"QUARANTINED".equals(e.get("status")))throw new Violation("invalid_transition");
   if(((Number)e.get("retryCount")).intValue()>=20)throw new Violation("retry_limit");
   Map<String,Object> src=source((UUID)e.get("sourceId"));
   if(!"VERIFIED".equals(src.get("status")))throw new Violation("source_not_verified");
   @SuppressWarnings("unchecked") List<String> required=(List<String>)src.get("requiredFields");
   @SuppressWarnings("unchecked") Map<String,Object> payload=(Map<String,Object>)e.get("payload");
   Instant occ=Instant.parse((String)e.get("occurredAt"));
   List<String> problems=Domain.validateEvent(required,payload,occ,db.clock.instant(),((Number)src.get("maxLatenessMinutes")).longValue());
   int tries=((Number)e.get("retryCount")).intValue()+1;
   if(!problems.isEmpty()){
    db.jdbc.update("UPDATE synqoria_analytics.ingest_events SET problems=?,retry_count=? WHERE id=?",problems.toArray(new String[0]),tries,eventId);
    db.audit(a,cmd,"EVENT",eventId,"RETRY_FAILED","QUARANTINED","QUARANTINED",tries,m("problems",problems.size()));
    return new Reply(409,m("error","still_invalid","details",problems));
   }
   String outcome=apply((UUID)e.get("sourceId"),a,eventId,(String)e.get("entityKey"),((Number)e.get("sourceVersion")).longValue(),occ,payload,e.get("payloadHash").toString().trim());
   if(outcome.equals("CONFLICT")){
    db.jdbc.update("UPDATE synqoria_analytics.ingest_events SET problems=?,retry_count=? WHERE id=?",new String[]{"VERSION_CONFLICT"},tries,eventId);
    return new Reply(409,m("error","still_invalid","details",List.of("VERSION_CONFLICT")));
   }
   db.jdbc.update("UPDATE synqoria_analytics.ingest_events SET status=?,problems='{}',retry_count=? WHERE id=?",outcome,tries,eventId);
   db.audit(a,cmd,"EVENT",eventId,"RETRY_APPLIED","QUARANTINED",outcome,tries,m());
   return new Reply(200,m("status",outcome));
  });
 }
 public Reply discard(Actor a,UUID cmd,UUID eventId,DiscardRequest d){
  a.require(Perm.QUARANTINE_MANAGE);
  return db.command(a,cmd,"DISCARD|"+eventId,d,()->{
   if(d==null||d.reason()==null||d.reason().isBlank()||d.reason().length()>300)throw new Violation("reason_required");
   List<Map<String,Object>> er=db.rows("SELECT status FROM synqoria_analytics.ingest_events WHERE id=? FOR UPDATE",eventId);
   if(er.isEmpty())throw new Violation("not_found");
   if(!"QUARANTINED".equals(er.getFirst().get("status")))throw new Violation("invalid_transition");
   db.jdbc.update("UPDATE synqoria_analytics.ingest_events SET status='DISCARDED' WHERE id=?",eventId);
   db.audit(a,cmd,"EVENT",eventId,"DISCARD","QUARANTINED","DISCARDED",1,m("reason",d.reason().strip()));
   return new Reply(200,m("status","DISCARDED"));
  });
 }

 /** Deterministic rebuild of the facts projection from stored events; the returned digest proves identical replay. */
 public Reply rebuild(Actor a,UUID cmd,UUID sourceId,Transition t){
  a.require(Perm.REBUILD);
  return db.command(a,cmd,"REBUILD|"+sourceId,t,()->{
   Map<String,Object> src=lockSource(sourceId,t.expectedVersion());
   String before=digest(sourceId);
   db.jdbc.update("DELETE FROM synqoria_analytics.facts WHERE source_id=?",sourceId);
   db.jdbc.update("INSERT INTO synqoria_analytics.facts(cell_id,facility_id,source_id,entity_key,source_version,occurred_at,data,event_id) "+
    "SELECT DISTINCT ON (entity_key) cell_id,facility_id,source_id,entity_key,source_version,occurred_at,payload,id FROM synqoria_analytics.ingest_events "+
    "WHERE source_id=? AND status IN ('APPLIED','SUPERSEDED') ORDER BY entity_key,source_version DESC,received_at DESC,id",sourceId);
   String after=digest(sourceId);
   long n=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_analytics.facts WHERE source_id=?",Long.class,sourceId);
   db.audit(a,cmd,"SOURCE",sourceId,"REBUILD",(String)src.get("status"),(String)src.get("status"),t.expectedVersion(),m("facts",n,"identical",before.equals(after)));
   return new Reply(200,m("facts",n,"digestBefore",before,"digestAfter",after,"identical",before.equals(after)));
  });
 }
 String digest(UUID sourceId){
  StringBuilder b=new StringBuilder();
  for(Map<String,Object> f:db.rows("SELECT entity_key,source_version,occurred_at,data FROM synqoria_analytics.facts WHERE source_id=? ORDER BY entity_key",sourceId))
   b.append(f.get("entityKey")).append('|').append(f.get("sourceVersion")).append('|').append(f.get("occurredAt")).append('|').append(new TreeMap<>((Map<String,Object>)f.get("data"))).append('\n');
  return Domain.sha256(b.toString());
 }

 /** Per-source freshness; a source with no facts or an old watermark is STALE and must be shown as such. */
 public List<Map<String,Object>> freshness(Actor a){
  a.require(Perm.REPORT_READ);
  return db.scoped(a,()->{
   Instant now=db.clock.instant();
   List<Map<String,Object>> out=new ArrayList<>();
   for(Map<String,Object> s:db.rows("SELECT id,source_key,name,domain,status,max_staleness_minutes FROM synqoria_analytics.sources ORDER BY source_key")){
    Map<String,Object> agg=db.one("SELECT max(occurred_at) AS wm,count(*) AS n FROM synqoria_analytics.facts WHERE source_id=?",s.get("id"));
    long q=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_analytics.ingest_events WHERE source_id=? AND status='QUARANTINED'",Long.class,s.get("id"));
    String wm=(String)agg.get("wm");
    boolean stale=wm==null||Instant.parse(wm).plusSeconds(((Number)s.get("maxStalenessMinutes")).longValue()*60).isBefore(now);
    out.add(m("sourceKey",s.get("sourceKey"),"name",s.get("name"),"domain",s.get("domain"),"status",s.get("status"),"watermark",wm,"factCount",agg.get("n"),"stale",stale,"quarantined",q,
     "maxStalenessMinutes",s.get("maxStalenessMinutes")));
   }
   return out;
  });
 }
}
