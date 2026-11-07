package org.synqoria.analytics;

import static org.synqoria.analytics.Db.m;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.synqoria.analytics.Db.Reply;
import org.synqoria.analytics.domain.Domain;
import org.synqoria.analytics.domain.Domain.*;

/** M28-B03..B06: versioned metric registry, controlled computation, domain-gated reads, series, dashboard and cell-level aggregates. */
@Service
public class MetricService {
 private final Db db;
 public MetricService(Db db){this.db=db;}

 public record MetricRequest(String metricKey,String nameEn,String nameFr,String domain,String unit,Map<String,Object> definition,int minCell,LocalDate effectiveFrom){}
 public record Decision(long expectedVersion,boolean approve,String note){}
 public record ComputeRequest(LocalDate periodStart,LocalDate periodEnd){}
 public static final int MIN_FACILITIES=3;

 String canon(Object o){
  if(o instanceof Map<?,?> mp){StringBuilder b=new StringBuilder("{");boolean f=true;
   for(Object k:new TreeSet<>(mp.keySet().stream().map(String::valueOf).toList())){if(!f)b.append(',');f=false;b.append(db.encode(k)).append(':').append(canon(mp.get(k)));}return b.append('}').toString();}
  if(o instanceof List<?> l){StringBuilder b=new StringBuilder("[");for(int i=0;i<l.size();i++){if(i>0)b.append(',');b.append(canon(l.get(i)));}return b.append(']').toString();}
  return db.encode(o);
 }
 private Map<String,Object> metric(UUID id){return db.one("SELECT * FROM synqoria_analytics.metric_definitions WHERE id=?",id);}

 public Reply create(Actor a,UUID cmd,MetricRequest r){
  a.require(Perm.METRIC_MANAGE);
  return db.command(a,cmd,"METRIC_CREATE",r,()->{
   if(r==null||r.metricKey()==null||!r.metricKey().matches("[a-z][a-z0-9_.]{1,63}")||r.nameEn()==null||r.nameEn().isBlank()||r.nameFr()==null||r.nameFr().isBlank()||
      r.nameEn().length()>160||r.nameFr().length()>160||r.effectiveFrom()==null)throw new Violation("invalid_metric");
   List<String> problems=Domain.validateMetric(r.definition(),r.domain(),r.unit(),r.minCell());
   if(!problems.isEmpty())throw new Violation("invalid_metric",problems);
   String source=(String)r.definition().get("source");
   List<Map<String,Object>> s=db.rows("SELECT domain FROM synqoria_analytics.sources WHERE source_key=?",source);
   if(s.isEmpty())throw new Violation("invalid_metric",List.of("UNKNOWN_SOURCE:"+source));
   if(!r.domain().equals(s.getFirst().get("domain")))throw new Violation("invalid_metric",List.of("DOMAIN_DIFFERS_FROM_SOURCE"));
   int next=db.jdbc.queryForObject("SELECT coalesce(max(version_no),0)+1 FROM synqoria_analytics.metric_definitions WHERE metric_key=?",Integer.class,r.metricKey());
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_analytics.metric_definitions(id,cell_id,facility_id,metric_key,version_no,name_en,name_fr,domain,unit,source_key,definition,definition_hash,min_cell,effective_from,status,created_by,version) "+
    "VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?,'DRAFT',?,1)",id,a.cell(),a.facility(),r.metricKey(),next,r.nameEn().strip(),r.nameFr().strip(),r.domain(),r.unit(),source,db.encode(r.definition()),Domain.sha256(canon(r.definition())),r.minCell(),Date.valueOf(r.effectiveFrom()),a.id());
   db.audit(a,cmd,"METRIC",id,"CREATE",null,"DRAFT",1,m("key",r.metricKey(),"versionNo",next));
   return new Reply(201,metric(id));
  });
 }
 public Reply decide(Actor a,UUID cmd,UUID id,Decision d){
  a.require(Perm.METRIC_APPROVE);
  return db.command(a,cmd,"METRIC_DECIDE|"+id,d,()->{
   List<Map<String,Object>> rows=db.rows("SELECT * FROM synqoria_analytics.metric_definitions WHERE id=? FOR UPDATE",id);
   if(rows.isEmpty())throw new Violation("not_found");
   Map<String,Object> x=rows.getFirst();
   if(((Number)x.get("version")).longValue()!=d.expectedVersion())throw new Violation("stale_version");
   if(!"DRAFT".equals(x.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(x.get("createdBy")))throw new Violation("approver_must_be_independent");
   if(!d.approve()&&(d.note()==null||d.note().isBlank()))throw new Violation("reason_required");
   if(d.approve())
    for(Map<String,Object> old:db.rows("SELECT id,version FROM synqoria_analytics.metric_definitions WHERE metric_key=? AND status='APPROVED' FOR UPDATE",x.get("metricKey"))){
     db.jdbc.update("UPDATE synqoria_analytics.metric_definitions SET status='RETIRED',version=version+1 WHERE id=?",old.get("id"));
     db.audit(a,cmd,"METRIC",(UUID)old.get("id"),"RETIRE","APPROVED","RETIRED",((Number)old.get("version")).longValue()+1,m("by",id.toString()));
    }
   String to=d.approve()?"APPROVED":"REJECTED";
   db.jdbc.update("UPDATE synqoria_analytics.metric_definitions SET status=?,approved_by=?,approved_at=clock_timestamp(),version=version+1 WHERE id=?",to,a.id(),id);
   db.audit(a,cmd,"METRIC",id,d.approve()?"APPROVE":"REJECT","DRAFT",to,d.expectedVersion()+1,m());
   return new Reply(200,metric(id));
  });
 }
 public Map<String,Object> list(Actor a,String domain,String status,int page,int size){
  a.require(Perm.REPORT_READ);
  if(page<0||size<1||size>100)throw new Violation("invalid_pagination");
  return db.scoped(a,()->{
   List<Object> args=new ArrayList<>();StringBuilder w=new StringBuilder(" WHERE true");
   if(domain!=null){Perm.readFor(domain);w.append(" AND domain=?");args.add(domain);}
   if(status!=null){w.append(" AND status=?");args.add(status);}
   long total=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_analytics.metric_definitions"+w,Long.class,args.toArray());
   args.add(size);args.add((long)page*size);
   return db.pageOf(db.rows("SELECT id,metric_key,version_no,name_en,name_fr,domain,unit,source_key,definition,definition_hash,min_cell,effective_from,status,created_by,approved_by,version FROM synqoria_analytics.metric_definitions"+w+
    " ORDER BY metric_key,version_no DESC LIMIT ? OFFSET ?",args.toArray()),page,size,total);
  });
 }

 // ---------------------------------------------------------------- computation
 public Reply compute(Actor a,UUID cmd,UUID metricId,ComputeRequest r){
  a.require(Perm.COMPUTE);
  return db.command(a,cmd,"COMPUTE|"+metricId,r,()->{
   if(r==null||r.periodStart()==null||r.periodEnd()==null||r.periodEnd().isBefore(r.periodStart())||r.periodEnd().toEpochDay()-r.periodStart().toEpochDay()>366)throw new Violation("invalid_period");
   Map<String,Object> md=metric(metricId);
   if(!"APPROVED".equals(md.get("status")))throw new Violation("metric_not_approved");
   if(LocalDate.parse((String)md.get("effectiveFrom")).isAfter(r.periodEnd()))throw new Violation("invalid_period",List.of("BEFORE_EFFECTIVE_DATE"));
   List<Map<String,Object>> sr=db.rows("SELECT * FROM synqoria_analytics.sources WHERE source_key=?",md.get("sourceKey"));
   if(sr.isEmpty())throw new Violation("not_found");
   Map<String,Object> src=sr.getFirst();
   db.jdbc.update("DELETE FROM synqoria_analytics.metric_values WHERE metric_id=? AND period_start=? AND period_end=?",metricId,Date.valueOf(r.periodStart()),Date.valueOf(r.periodEnd()));
   List<GroupResult> results;Instant watermark=null;boolean stale=true;
   if(!"VERIFIED".equals(src.get("status"))){
    results=List.of(new GroupResult("ALL",null,"NO_DATA",null,null,null,0,0));          // unverified source: nothing is published
   }else{
    List<Fact> facts=new ArrayList<>();
    for(Map<String,Object> f:db.rows("SELECT entity_key,occurred_at,data FROM synqoria_analytics.facts WHERE source_id=? AND occurred_at>=? AND occurred_at<?",src.get("id"),
      Timestamp.from(r.periodStart().minusDays(1).atStartOfDay(db.zone).toInstant()),Timestamp.from(r.periodEnd().plusDays(2).atStartOfDay(db.zone).toInstant()))){
     @SuppressWarnings("unchecked") Map<String,Object> data=(Map<String,Object>)f.get("data");
     facts.add(new Fact((String)f.get("entityKey"),Instant.parse((String)f.get("occurredAt")),data));
    }
    String wm=db.one("SELECT max(occurred_at) AS wm FROM synqoria_analytics.facts WHERE source_id=?",src.get("id")).get("wm") instanceof String s?s:null;
    if(wm!=null){watermark=Instant.parse(wm);stale=watermark.plusSeconds(((Number)src.get("maxStalenessMinutes")).longValue()*60).isBefore(db.clock.instant());}
    @SuppressWarnings("unchecked") Map<String,Object> def=(Map<String,Object>)md.get("definition");
    results=Domain.compute(def,facts,r.periodStart(),r.periodEnd(),db.zone,((Number)md.get("minCell")).intValue());
   }
   int written=0;
   for(GroupResult g:results){
    db.jdbc.update("INSERT INTO synqoria_analytics.metric_values(id,cell_id,facility_id,metric_id,metric_key,domain,unit,metric_version,period_start,period_end,dimension_key,currency,state,numerator,denominator,value,cohort_size,unknown_count,source_watermark,stale) "+
     "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),a.cell(),a.facility(),metricId,md.get("metricKey"),md.get("domain"),md.get("unit"),md.get("versionNo"),
     Date.valueOf(r.periodStart()),Date.valueOf(r.periodEnd()),g.dimension(),g.currency(),g.state(),g.numerator(),g.denominator(),g.value(),g.cohort(),g.unknown(),watermark==null?null:Timestamp.from(watermark),stale);
    written++;
   }
   db.audit(a,cmd,"METRIC",metricId,"COMPUTE","APPROVED","APPROVED",((Number)md.get("version")).longValue(),m("rows",written,"period",r.periodStart()+".."+r.periodEnd()));
   return new Reply(200,m("rows",written,"values",values(metricId,r.periodStart(),r.periodEnd())));
  });
 }
 List<Map<String,Object>> values(UUID metricId,LocalDate from,LocalDate to){
  return db.rows("SELECT id,metric_key,metric_version,domain,unit,period_start,period_end,dimension_key,currency,state,numerator,denominator,value,cohort_size,unknown_count,source_watermark,stale,computed_at "+
   "FROM synqoria_analytics.metric_values WHERE metric_id=? AND period_start=? AND period_end=? ORDER BY dimension_key,currency NULLS FIRST",metricId,Date.valueOf(from),Date.valueOf(to));
 }

 /** Reads stored values for ONE domain; the caller needs that domain's read permission. Facility-scoped by RLS. */
 public Map<String,Object> read(Actor a,String domain,String metricKey,LocalDate from,LocalDate to,int page,int size){
  a.require(Perm.readFor(domain));
  if(page<0||size<1||size>200)throw new Violation("invalid_pagination");
  if(metricKey!=null&&!metricKey.matches("[a-z][a-z0-9_.]{1,63}"))throw new Violation("invalid_query");
  return db.scoped(a,()->{
   List<Object> args=new ArrayList<>(List.of(domain));StringBuilder w=new StringBuilder(" WHERE v.domain=? AND v.facility_id=?");args.add(a.facility());
   if(metricKey!=null){w.append(" AND v.metric_key=?");args.add(metricKey);}
   if(from!=null){w.append(" AND v.period_start>=?");args.add(Date.valueOf(from));}
   if(to!=null){w.append(" AND v.period_end<=?");args.add(Date.valueOf(to));}
   String f=" FROM synqoria_analytics.metric_values v JOIN synqoria_analytics.metric_definitions d ON d.id=v.metric_id";
   long total=db.jdbc.queryForObject("SELECT count(*)"+f+w,Long.class,args.toArray());
   args.add(size);args.add((long)page*size);
   return db.pageOf(db.rows("SELECT v.id,v.metric_key,v.metric_version,d.name_en,d.name_fr,v.domain,v.unit,v.period_start,v.period_end,v.dimension_key,v.currency,v.state,v.numerator,v.denominator,v.value,v.cohort_size,v.unknown_count,v.source_watermark,v.stale,v.computed_at"+f+w+
    " ORDER BY v.period_start DESC,v.metric_key,v.dimension_key,v.currency NULLS FIRST LIMIT ? OFFSET ?",args.toArray()),page,size,total);
  });
 }
 /** S01: every OPERATIONS value of the latest computed period per metric, with source freshness. */
 public Map<String,Object> dashboard(Actor a,IngestService ingest){
  a.require(Perm.READ_OPERATIONS);
  Map<String,Object> out=db.scoped(a,()->m("tiles",db.rows("SELECT DISTINCT ON (v.metric_key) v.metric_key,d.name_en,d.name_fr,v.unit,v.period_start,v.period_end,v.state,v.numerator,v.denominator,v.value,v.cohort_size,v.unknown_count,v.source_watermark,v.stale,v.computed_at "+
    "FROM synqoria_analytics.metric_values v JOIN synqoria_analytics.metric_definitions d ON d.id=v.metric_id WHERE v.domain='OPERATIONS' AND v.dimension_key='ALL' AND v.facility_id=? "+
    "ORDER BY v.metric_key,v.period_end DESC,v.computed_at DESC",a.facility())));
  Map<String,Object> res=new LinkedHashMap<>(out);
  res.put("freshness",ingest.freshness(withRead(a)));
  return res;
 }
 private Actor withRead(Actor a){Set<String> g=new HashSet<>(a.grants());g.add(Perm.REPORT_READ);return new Actor(a.id(),a.cell(),a.facility(),g);}

 /** Cross-facility aggregate inside the caller's own hospital cell. Never exposes a single facility's value. */
 public Map<String,Object> cellAggregate(Actor a,String metricKey,LocalDate from,LocalDate to){
  a.require(Perm.CELL_AGGREGATE_READ);
  if(metricKey==null||!metricKey.matches("[a-z][a-z0-9_.]{1,63}")||from==null||to==null)throw new Violation("invalid_query");
  return db.scoped(a,()->{
   List<Map<String,Object>> rows=db.rows("SELECT facility_id AS contributing_facility,domain,unit,dimension_key,currency,numerator,denominator FROM synqoria_analytics.metric_values WHERE cell_id=? AND metric_key=? AND period_start=? AND period_end=? AND state='OK'",
    a.cell(),metricKey,Date.valueOf(from),Date.valueOf(to));
   if(rows.isEmpty())return m("metricKey",metricKey,"state","NO_DATA","items",List.of(),"minFacilities",MIN_FACILITIES);
   String domain=(String)rows.getFirst().get("domain");
   a.require(Perm.readFor(domain));
   boolean rate=rows.getFirst().get("denominator")!=null;
   Map<String,List<Part>> by=new TreeMap<>();
   for(Map<String,Object> r:rows)by.computeIfAbsent(r.get("dimensionKey")+"\u0000"+(r.get("currency")==null?"":String.valueOf(r.get("currency")).trim()),k->new ArrayList<>())
    .add(new Part((UUID)r.get("contributingFacility"),(BigDecimal)r.get("numerator"),(BigDecimal)r.get("denominator")));
   List<Map<String,Object>> items=new ArrayList<>();
   for(var e:by.entrySet()){
    String[] k=e.getKey().split("\u0000",-1);
    Optional<GroupResult> g=Domain.combine(e.getValue(),MIN_FACILITIES,rate);
    items.add(g.map(x->m("dimension",k[0],"currency",k[1].isEmpty()?null:k[1],"state","OK","value",x.value(),"numerator",x.numerator(),"denominator",x.denominator(),"facilities",x.cohort()))
     .orElse(m("dimension",k[0],"currency",k[1].isEmpty()?null:k[1],"state","SUPPRESSED","value",null,"facilities",null)));
   }
   return m("metricKey",metricKey,"state","OK","items",items,"minFacilities",MIN_FACILITIES);
  });
 }
}
