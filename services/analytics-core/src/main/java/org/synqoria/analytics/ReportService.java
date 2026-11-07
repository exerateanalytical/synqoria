package org.synqoria.analytics;

import static org.synqoria.analytics.Db.m;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.synqoria.analytics.Db.Reply;
import org.synqoria.analytics.domain.Domain;
import org.synqoria.analytics.domain.Domain.*;

/** M28-B07/B08: approved report definitions, asynchronous runs over stored metric values only, schedules, governed CSV exports. */
@Service
public class ReportService {
 private final Db db;
 private final Duration exportTtl;
 public ReportService(Db db,@Value("${synqoria.analytics.export-ttl-hours:24}")long ttlHours){
  if(ttlHours<1||ttlHours>168)throw new IllegalStateException("export_ttl_out_of_range");
  this.db=db;this.exportTtl=Duration.ofHours(ttlHours);
 }

 public record ReportRequest(String reportKey,String nameEn,String nameFr,List<String> metricKeys,int maxPeriodDays,String requiredPermission){}
 public record Decision(long expectedVersion,boolean approve,String note){}
 public record RunRequest(LocalDate periodStart,LocalDate periodEnd){}
 public record ScheduleRequest(int everyMinutes,int lookbackDays){}
 public record ExportRequest(String format,String purpose){}
 public record ExportDecision(long expectedVersion,boolean approve,String note){}

 private Map<String,Object> report(UUID id){return db.one("SELECT * FROM synqoria_analytics.report_definitions WHERE id=?",id);}

 public Reply createReport(Actor a,UUID cmd,ReportRequest r){
  a.require(Perm.REPORT_MANAGE);
  return db.command(a,cmd,"REPORT_CREATE",r,()->{
   if(r==null||r.reportKey()==null||!r.reportKey().matches("[a-z][a-z0-9_.]{1,63}")||r.nameEn()==null||r.nameEn().isBlank()||r.nameFr()==null||r.nameFr().isBlank()||
      r.metricKeys()==null||r.metricKeys().isEmpty()||r.metricKeys().size()>20||r.metricKeys().stream().anyMatch(k->k==null||!k.matches("[a-z][a-z0-9_.]{1,63}"))||
      r.maxPeriodDays()<1||r.maxPeriodDays()>800||r.requiredPermission()==null||!Perm.ALL.contains(r.requiredPermission()))throw new Violation("invalid_report");
   int next=db.jdbc.queryForObject("SELECT coalesce(max(version_no),0)+1 FROM synqoria_analytics.report_definitions WHERE report_key=?",Integer.class,r.reportKey());
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_analytics.report_definitions(id,cell_id,facility_id,report_key,version_no,name_en,name_fr,metric_keys,max_period_days,required_permission,status,created_by,version) VALUES (?,?,?,?,?,?,?,?,?,?,'DRAFT',?,1)",
    id,a.cell(),a.facility(),r.reportKey(),next,r.nameEn().strip(),r.nameFr().strip(),new TreeSet<>(r.metricKeys()).toArray(new String[0]),r.maxPeriodDays(),r.requiredPermission(),a.id());
   db.audit(a,cmd,"REPORT",id,"CREATE",null,"DRAFT",1,m("key",r.reportKey(),"versionNo",next));
   return new Reply(201,report(id));
  });
 }
 public Reply decideReport(Actor a,UUID cmd,UUID id,Decision d){
  a.require(Perm.REPORT_APPROVE);
  return db.command(a,cmd,"REPORT_DECIDE|"+id,d,()->{
   List<Map<String,Object>> rows=db.rows("SELECT * FROM synqoria_analytics.report_definitions WHERE id=? FOR UPDATE",id);
   if(rows.isEmpty())throw new Violation("not_found");
   Map<String,Object> x=rows.getFirst();
   if(((Number)x.get("version")).longValue()!=d.expectedVersion())throw new Violation("stale_version");
   if(!"DRAFT".equals(x.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(x.get("createdBy")))throw new Violation("approver_must_be_independent");
   if(!d.approve()&&(d.note()==null||d.note().isBlank()))throw new Violation("reason_required");
   if(d.approve())
    for(Map<String,Object> old:db.rows("SELECT id,version FROM synqoria_analytics.report_definitions WHERE report_key=? AND status='APPROVED' FOR UPDATE",x.get("reportKey"))){
     db.jdbc.update("UPDATE synqoria_analytics.report_definitions SET status='RETIRED',version=version+1 WHERE id=?",old.get("id"));
     db.audit(a,cmd,"REPORT",(UUID)old.get("id"),"RETIRE","APPROVED","RETIRED",((Number)old.get("version")).longValue()+1,m());
    }
   String to=d.approve()?"APPROVED":"REJECTED";
   db.jdbc.update("UPDATE synqoria_analytics.report_definitions SET status=?,approved_by=?,approved_at=clock_timestamp(),version=version+1 WHERE id=?",to,a.id(),id);
   db.audit(a,cmd,"REPORT",id,d.approve()?"APPROVE":"REJECT","DRAFT",to,d.expectedVersion()+1,m());
   return new Reply(200,report(id));
  });
 }
 public Map<String,Object> listReports(Actor a,String status,int page,int size){
  a.require(Perm.REPORT_READ);
  if(page<0||size<1||size>100)throw new Violation("invalid_pagination");
  return db.scoped(a,()->{
   String w=status==null?"":" WHERE status=?";Object[] base=status==null?new Object[0]:new Object[]{status};
   long total=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_analytics.report_definitions"+w,Long.class,base);
   Object[] args=Arrays.copyOf(base,base.length+2);args[base.length]=size;args[base.length+1]=(long)page*size;
   return db.pageOf(db.rows("SELECT id,report_key,version_no,name_en,name_fr,metric_keys,max_period_days,required_permission,status,created_by,approved_by,version FROM synqoria_analytics.report_definitions"+w+" ORDER BY report_key,version_no DESC LIMIT ? OFFSET ?",args),page,size,total);
  });
 }

 // ---------------------------------------------------------------- runs
 public Reply requestRun(Actor a,UUID cmd,UUID reportId,RunRequest r){
  a.require(Perm.REPORT_RUN);
  return db.command(a,cmd,"RUN_REQUEST|"+reportId,r,()->{
   Map<String,Object> rep=db.rows("SELECT * FROM synqoria_analytics.report_definitions WHERE id=?",reportId).stream().findFirst().orElseThrow(()->new Violation("not_found"));
   if(!"APPROVED".equals(rep.get("status")))throw new Violation("report_not_approved");
   a.require((String)rep.get("requiredPermission"));                              // the report decides who may run it
   if(r==null||r.periodStart()==null||r.periodEnd()==null||r.periodEnd().isBefore(r.periodStart()))throw new Violation("invalid_period");
   if(r.periodEnd().toEpochDay()-r.periodStart().toEpochDay()+1>((Number)rep.get("maxPeriodDays")).longValue())throw new Violation("invalid_period",List.of("EXCEEDS_MAX_PERIOD"));
   return new Reply(202,runView(enqueue(a,reportId,r.periodStart(),r.periodEnd(),"MANUAL")));
  });
 }
 private UUID enqueue(Actor a,UUID reportId,LocalDate from,LocalDate to,String trigger){
  UUID id=UUID.randomUUID();
  db.jdbc.update("INSERT INTO synqoria_analytics.report_runs(id,cell_id,facility_id,report_id,period_start,period_end,trigger,status,requested_by,version) VALUES (?,?,?,?,?,?,?,'QUEUED',?,1)",
   id,a.cell(),a.facility(),reportId,Date.valueOf(from),Date.valueOf(to),trigger,a.id());
  db.audit(a,UUID.randomUUID(),"RUN",id,"QUEUE",null,"QUEUED",1,m("trigger",trigger));
  return id;
 }
 Map<String,Object> runView(UUID id){
  return db.one("SELECT r.id,r.report_id,d.report_key,d.version_no AS report_version,d.name_en,d.name_fr,r.period_start,r.period_end,r.trigger,r.status,r.requested_by,r.result,r.error_code,r.created_at,r.started_at,r.finished_at,r.version "+
   "FROM synqoria_analytics.report_runs r JOIN synqoria_analytics.report_definitions d ON d.id=r.report_id WHERE r.id=?",id);
 }
 /** Worker tick: executes queued runs. Reads ONLY stored, already-disclosure-controlled metric values; no ad-hoc queries. */
 public Reply processQueued(Actor a,UUID cmd,int limit){
  a.require(Perm.REPORT_RUN);
  return db.command(a,cmd,"RUN_PROCESS",m("limit",limit),()->{
   if(limit<1||limit>50)throw new Violation("invalid_batch");
   int ok=0,failed=0;
   for(Map<String,Object> q:db.rows("SELECT r.id,r.report_id,r.period_start,r.period_end,r.version FROM synqoria_analytics.report_runs r WHERE r.status='QUEUED' ORDER BY r.created_at,r.id LIMIT ? FOR UPDATE",limit)){
    UUID id=(UUID)q.get("id");
    db.jdbc.update("UPDATE synqoria_analytics.report_runs SET status='RUNNING',started_at=clock_timestamp(),version=version+1 WHERE id=?",id);
    try{
     Map<String,Object> rep=report((UUID)q.get("reportId"));
     LocalDate from=LocalDate.parse((String)q.get("periodStart")),to=LocalDate.parse((String)q.get("periodEnd"));
     List<Object> sections=new ArrayList<>();
     @SuppressWarnings("unchecked") List<String> keys=(List<String>)rep.get("metricKeys");
     for(String key:keys){
      List<Map<String,Object>> md=db.rows("SELECT id,version_no,definition_hash,domain,name_en,name_fr FROM synqoria_analytics.metric_definitions WHERE metric_key=? AND status='APPROVED'",key);
      if(md.isEmpty()){sections.add(m("metricKey",key,"state","NOT_AVAILABLE","rows",List.of()));continue;}
      List<Map<String,Object>> vals=db.rows("SELECT dimension_key,currency,state,numerator,denominator,value,cohort_size,unknown_count,source_watermark,stale,computed_at FROM synqoria_analytics.metric_values "+
       "WHERE metric_id=? AND period_start=? AND period_end=? AND facility_id=? ORDER BY dimension_key,currency NULLS FIRST",md.getFirst().get("id"),Date.valueOf(from),Date.valueOf(to),a.facility());
      sections.add(m("metricKey",key,"metricVersion",md.getFirst().get("versionNo"),"definitionHash",md.getFirst().get("definitionHash"),"domain",md.getFirst().get("domain"),
       "state",vals.isEmpty()?"NOT_COMPUTED":"AVAILABLE","rows",vals));
     }
     Map<String,Object> result=m("reportKey",rep.get("reportKey"),"reportVersion",rep.get("versionNo"),"periodStart",from.toString(),"periodEnd",to.toString(),"generatedAt",db.clock.instant().toString(),"sections",sections);
     db.jdbc.update("UPDATE synqoria_analytics.report_runs SET status='SUCCEEDED',result=?::jsonb,finished_at=clock_timestamp(),version=version+1 WHERE id=?",db.encode(result),id);
     db.audit(a,cmd,"RUN",id,"SUCCEED","RUNNING","SUCCEEDED",((Number)q.get("version")).longValue()+2,m());ok++;
    }catch(Violation v){
     db.jdbc.update("UPDATE synqoria_analytics.report_runs SET status='FAILED',error_code=?,finished_at=clock_timestamp(),version=version+1 WHERE id=?",v.code(),id);
     db.audit(a,cmd,"RUN",id,"FAIL","RUNNING","FAILED",((Number)q.get("version")).longValue()+2,m("code",v.code()));failed++;
    }
   }
   return new Reply(200,m("succeeded",ok,"failed",failed));
  });
 }
 public Map<String,Object> getRun(Actor a,UUID id){
  a.require(Perm.REPORT_READ);
  return db.scoped(a,()->runView(id));
 }
 public Map<String,Object> listRuns(Actor a,String status,int page,int size){
  a.require(Perm.REPORT_READ);
  if(page<0||size<1||size>100)throw new Violation("invalid_pagination");
  return db.scoped(a,()->{
   String w=status==null?"":" WHERE r.status=?";Object[] base=status==null?new Object[0]:new Object[]{status};
   String f=" FROM synqoria_analytics.report_runs r JOIN synqoria_analytics.report_definitions d ON d.id=r.report_id";
   long total=db.jdbc.queryForObject("SELECT count(*)"+f+w,Long.class,base);
   Object[] args=Arrays.copyOf(base,base.length+2);args[base.length]=size;args[base.length+1]=(long)page*size;
   return db.pageOf(db.rows("SELECT r.id,d.report_key,d.version_no AS report_version,d.name_en,d.name_fr,r.period_start,r.period_end,r.trigger,r.status,r.requested_by,r.error_code,r.created_at,r.finished_at,r.version"+f+w+" ORDER BY r.created_at DESC,r.id LIMIT ? OFFSET ?",args),page,size,total);
  });
 }

 // ---------------------------------------------------------------- schedules
 public Reply schedule(Actor a,UUID cmd,UUID reportId,ScheduleRequest r){
  a.require(Perm.SCHEDULE_MANAGE);
  return db.command(a,cmd,"SCHEDULE|"+reportId,r,()->{
   Map<String,Object> rep=db.rows("SELECT status FROM synqoria_analytics.report_definitions WHERE id=?",reportId).stream().findFirst().orElseThrow(()->new Violation("not_found"));
   if(!"APPROVED".equals(rep.get("status")))throw new Violation("report_not_approved");
   if(r==null||r.everyMinutes()<15||r.everyMinutes()>44640||r.lookbackDays()<1||r.lookbackDays()>800)throw new Violation("invalid_schedule");
   db.jdbc.update("INSERT INTO synqoria_analytics.schedules(id,cell_id,facility_id,report_id,every_minutes,lookback_days,next_run_at,enabled,created_by) VALUES (?,?,?,?,?,?,?,true,?) "+
    "ON CONFLICT (cell_id,facility_id,report_id) DO UPDATE SET every_minutes=EXCLUDED.every_minutes,lookback_days=EXCLUDED.lookback_days,next_run_at=EXCLUDED.next_run_at,enabled=true",
    UUID.randomUUID(),a.cell(),a.facility(),reportId,r.everyMinutes(),r.lookbackDays(),Timestamp.from(db.clock.instant().plus(Duration.ofMinutes(r.everyMinutes()))),a.id());
   db.audit(a,cmd,"REPORT",reportId,"SCHEDULE","APPROVED","APPROVED",1,m("everyMinutes",r.everyMinutes()));
   return new Reply(200,m("reportId",reportId.toString(),"everyMinutes",r.everyMinutes(),"lookbackDays",r.lookbackDays()));
  });
 }
 /** Enqueues a run for each due schedule (idempotent per due slot) and advances it. */
 public Reply runDue(Actor a,UUID cmd){
  a.require(Perm.SCHEDULE_MANAGE);
  return db.command(a,cmd,"SCHEDULE_DUE",m("t",db.clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MINUTES).toString()),()->{
   Instant now=db.clock.instant();int n=0;
   for(Map<String,Object> s:db.rows("SELECT * FROM synqoria_analytics.schedules WHERE enabled AND next_run_at<=? ORDER BY next_run_at FOR UPDATE",Timestamp.from(now))){
    Map<String,Object> rep=report((UUID)s.get("reportId"));
    if(!"APPROVED".equals(rep.get("status"))){db.jdbc.update("UPDATE synqoria_analytics.schedules SET enabled=false WHERE id=?",s.get("id"));continue;}
    LocalDate to=LocalDate.ofInstant(now,db.zone).minusDays(1),from=to.minusDays(((Number)s.get("lookbackDays")).longValue()-1);
    if(to.toEpochDay()-from.toEpochDay()+1>((Number)rep.get("maxPeriodDays")).longValue())from=to.minusDays(((Number)rep.get("maxPeriodDays")).longValue()-1);
    enqueue(a,(UUID)s.get("reportId"),from,to,"SCHEDULE");n++;
    db.jdbc.update("UPDATE synqoria_analytics.schedules SET next_run_at=? WHERE id=?",Timestamp.from(now.plus(Duration.ofMinutes(((Number)s.get("everyMinutes")).longValue()))),s.get("id"));
   }
   return new Reply(200,m("enqueued",n));
  });
 }

 // ---------------------------------------------------------------- exports
 public Reply requestExport(Actor a,UUID cmd,UUID runId,ExportRequest r){
  a.require(Perm.EXPORT_REQUEST);
  return db.command(a,cmd,"EXPORT_REQUEST|"+runId,r,()->{
   if(r==null||r.purpose()==null||r.purpose().isBlank()||r.purpose().length()>300)throw new Violation("invalid_export");
   if(!"CSV".equals(r.format()))throw new Violation("unsupported_format",List.of("ONLY_CSV_IS_IMPLEMENTED"));
   List<Map<String,Object>> run=db.rows("SELECT r.status,d.required_permission FROM synqoria_analytics.report_runs r JOIN synqoria_analytics.report_definitions d ON d.id=r.report_id WHERE r.id=?",runId);
   if(run.isEmpty())throw new Violation("not_found");
   if(!"SUCCEEDED".equals(run.getFirst().get("status")))throw new Violation("invalid_transition");
   a.require((String)run.getFirst().get("requiredPermission"));
   if(!db.rows("SELECT 1 FROM synqoria_analytics.exports WHERE run_id=? AND requested_by=? AND status='REQUESTED'",runId,a.id()).isEmpty())throw new Violation("duplicate_record");
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_analytics.exports(id,cell_id,facility_id,run_id,format,status,purpose,requested_by,version) VALUES (?,?,?,?,'CSV','REQUESTED',?,?,1)",id,a.cell(),a.facility(),runId,r.purpose().strip(),a.id());
   db.audit(a,cmd,"EXPORT",id,"REQUEST",null,"REQUESTED",1,m("runId",runId.toString()));
   return new Reply(201,exportView(id));
  });
 }
 Map<String,Object> exportView(UUID id){
  return db.one("SELECT id,run_id,format,status,purpose,requested_by,approved_by,approved_at,artifact_sha256,artifact_bytes,expires_at,watermark,version,created_at FROM synqoria_analytics.exports WHERE id=?",id);
 }
 /** Approval generates the artifact: CSV with formula-injection defence, a provenance watermark, a SHA-256 and an expiry. */
 @SuppressWarnings("unchecked")
 public Reply decideExport(Actor a,UUID cmd,UUID id,ExportDecision d){
  a.require(Perm.EXPORT_APPROVE);
  return db.command(a,cmd,"EXPORT_DECIDE|"+id,d,()->{
   List<Map<String,Object>> rows=db.rows("SELECT * FROM synqoria_analytics.exports WHERE id=? FOR UPDATE",id);
   if(rows.isEmpty())throw new Violation("not_found");
   Map<String,Object> x=rows.getFirst();
   if(((Number)x.get("version")).longValue()!=d.expectedVersion())throw new Violation("stale_version");
   if(!"REQUESTED".equals(x.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(x.get("requestedBy")))throw new Violation("approver_must_be_independent");
   if(!d.approve()){
    if(d.note()==null||d.note().isBlank())throw new Violation("reason_required");
    db.jdbc.update("UPDATE synqoria_analytics.exports SET status='REJECTED',version=version+1 WHERE id=?",id);
    db.audit(a,cmd,"EXPORT",id,"REJECT","REQUESTED","REJECTED",d.expectedVersion()+1,m());
    return new Reply(200,exportView(id));
   }
   Map<String,Object> run=runView((UUID)x.get("runId"));
   Map<String,Object> result=(Map<String,Object>)run.get("result");
   Instant now=db.clock.instant();
   String watermark="SYNQORIA M28 export | run "+x.get("runId")+" | report "+run.get("reportKey")+" v"+run.get("reportVersion")+" | requested by "+x.get("requestedBy")+" | approved by "+a.id()+" | "+now+" | purpose: "+x.get("purpose");
   StringBuilder csv=new StringBuilder();
   csv.append("# ").append(watermark.replace("\r"," ").replace("\n"," ")).append("\r\n");
   csv.append("metric_key,metric_version,domain,period_start,period_end,dimension,currency,state,numerator,denominator,value,cohort_size,unknown_count,source_watermark,stale\r\n");
   for(Map<String,Object> sec:(List<Map<String,Object>>)result.get("sections"))
    for(Map<String,Object> v:(List<Map<String,Object>>)sec.getOrDefault("rows",List.of()))
     csv.append(String.join(",",Domain.csvCell((String)sec.get("metricKey")),String.valueOf(sec.get("metricVersion")),Domain.csvCell((String)sec.get("domain")),(String)result.get("periodStart"),(String)result.get("periodEnd"),
      Domain.csvCell((String)v.get("dimensionKey")),Domain.csvCell(v.get("currency")==null?"":String.valueOf(v.get("currency")).trim()),Domain.csvCell((String)v.get("state")),
      v.get("numerator")==null?"":String.valueOf(v.get("numerator")),v.get("denominator")==null?"":String.valueOf(v.get("denominator")),v.get("value")==null?"":String.valueOf(v.get("value")),
      String.valueOf(v.get("cohortSize")),String.valueOf(v.get("unknownCount")),v.get("sourceWatermark")==null?"":String.valueOf(v.get("sourceWatermark")),String.valueOf(v.get("stale")))).append("\r\n");
   byte[] bytes=csv.toString().getBytes(StandardCharsets.UTF_8);
   if(bytes.length>5_242_880)throw new Violation("export_too_large");
   db.jdbc.update("UPDATE synqoria_analytics.exports SET status='READY',approved_by=?,approved_at=?,artifact=?,artifact_sha256=?,artifact_bytes=?,expires_at=?,watermark=?,version=version+1 WHERE id=?",
    a.id(),Timestamp.from(now),bytes,Domain.sha256(csv.toString()),bytes.length,Timestamp.from(now.plus(exportTtl)),watermark.length()>300?watermark.substring(0,300):watermark,id);
   db.audit(a,cmd,"EXPORT",id,"APPROVE","REQUESTED","READY",d.expectedVersion()+1,m("bytes",bytes.length));
   return new Reply(200,exportView(id));
  });
 }
 /** Audited download. Only the requester or an export approver, only while READY and unexpired. */
 public Reply download(Actor a,UUID cmd,UUID id){
  a.require(Perm.EXPORT_DOWNLOAD);
  return db.command(a,cmd,"EXPORT_DOWNLOAD|"+id,m("id",id.toString()),()->{
   List<Map<String,Object>> rows=db.rows("SELECT * FROM synqoria_analytics.exports WHERE id=? FOR UPDATE",id);
   if(rows.isEmpty())throw new Violation("not_found");
   Map<String,Object> x=rows.getFirst();
   if(!a.id().equals(x.get("requestedBy"))&&!a.has(Perm.EXPORT_APPROVE))throw new Violation("permission_denied");
   if(!"READY".equals(x.get("status")))throw new Violation("export_not_ready");
   if(!Instant.parse((String)x.get("expiresAt")).isAfter(db.clock.instant()))throw new Violation("export_expired");
   byte[] bytes=db.jdbc.queryForObject("SELECT artifact FROM synqoria_analytics.exports WHERE id=?",byte[].class,id);
   String content=new String(bytes,StandardCharsets.UTF_8);
   String sha=x.get("artifactSha256").toString().trim();
   if(!Domain.sha256(content).equals(sha))throw new Violation("evidence_mismatch");
   db.jdbc.update("INSERT INTO synqoria_analytics.export_downloads(id,cell_id,facility_id,export_id,actor_id,artifact_sha256) VALUES (?,?,?,?,?,?)",UUID.randomUUID(),a.cell(),a.facility(),id,a.id(),sha);
   db.audit(a,cmd,"EXPORT",id,"DOWNLOAD","READY","READY",((Number)x.get("version")).longValue(),m("sha256",sha));
   return new Reply(200,m("filename","m28-export-"+id.toString().substring(0,8)+".csv","sha256",sha,"watermark",x.get("watermark"),"content",content));
  });
 }
 /** Purges expired artifacts (evidence of the export is kept; the bytes are not). */
 public Reply expireDue(Actor a,UUID cmd){
  a.require(Perm.EXPORT_APPROVE);
  return db.command(a,cmd,"EXPORT_EXPIRE",m("t",db.clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MINUTES).toString()),()->{
   int n=db.jdbc.update("UPDATE synqoria_analytics.exports SET status='EXPIRED',artifact=NULL,artifact_bytes=NULL,version=version+1 WHERE status='READY' AND expires_at<=?",Timestamp.from(db.clock.instant()));
   return new Reply(200,m("expired",n));
  });
 }
 public Map<String,Object> listExports(Actor a,String status,int page,int size){
  a.require(Perm.REPORT_READ);
  if(page<0||size<1||size>100)throw new Violation("invalid_pagination");
  return db.scoped(a,()->{
   String w=status==null?"":" WHERE e.status=?";Object[] base=status==null?new Object[0]:new Object[]{status};
   String f=" FROM synqoria_analytics.exports e JOIN synqoria_analytics.report_runs r ON r.id=e.run_id JOIN synqoria_analytics.report_definitions d ON d.id=r.report_id";
   long total=db.jdbc.queryForObject("SELECT count(*)"+f+w,Long.class,base);
   Object[] args=Arrays.copyOf(base,base.length+2);args[base.length]=size;args[base.length+1]=(long)page*size;
   return db.pageOf(db.rows("SELECT e.id,e.run_id,d.report_key,r.period_start,r.period_end,e.format,e.status,e.purpose,e.requested_by,e.approved_by,e.artifact_sha256,e.artifact_bytes,e.expires_at,e.version,e.created_at,"+
    "(SELECT count(*) FROM synqoria_analytics.export_downloads x WHERE x.export_id=e.id) AS downloads"+f+w+" ORDER BY e.created_at DESC,e.id LIMIT ? OFFSET ?",args),page,size,total);
  });
 }
}
