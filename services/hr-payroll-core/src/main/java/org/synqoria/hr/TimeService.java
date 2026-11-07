package org.synqoria.hr;

import static org.synqoria.hr.Db.m;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.synqoria.hr.Db.Reply;
import org.synqoria.hr.domain.Domain;
import org.synqoria.hr.domain.Domain.*;

/** M24-S03/S04: shift templates, rosters (revisions, coverage, rest, overtime, independent review), attendance and leave. */
@Service
public class TimeService {
 private final Db db;
 private final Duration minRest;
 public TimeService(Db db,@org.springframework.beans.factory.annotation.Value("${synqoria.hr.min-rest-hours:11}")long restHours){
  this.db=db;this.minRest=Duration.ofHours(restHours);
 }

 public record TemplateRequest(String code,String startTime,int durationMinutes){}
 public record CoverageRequest(UUID departmentId,UUID templateId,int minStaff){}
 public record RosterRequest(UUID departmentId,LocalDate periodStart,LocalDate periodEnd){}
 public record ShiftRequest(UUID templateId,UUID staffId,LocalDate date,boolean overtime){}
 public record Transition(long expectedVersion){}
 public record Decision(long expectedVersion,boolean approve,String note){}
 public record AttendanceRequest(UUID staffId,LocalDate workDate,Instant clockIn,Instant clockOut,String source,String importRef){}
 public record ImportRequest(List<AttendanceRequest> records){}
 public record CorrectionRequest(long expectedVersion,Instant clockIn,Instant clockOut,String note){}
 public record DisputeRequest(long expectedVersion,String note){}
 public record LeaveTypeRequest(String code,boolean paid,String accrualPerMonth,String maxBalance){}
 public record HolidayRequest(LocalDate date,String name){}
 public record LeaveRequestIn(UUID staffId,UUID leaveTypeId,LocalDate fromDate,LocalDate toDate){}

 LocalDate today(){return LocalDate.ofInstant(db.clock.instant(),db.zone);}

 // ---------------------------------------------------------------- templates, coverage
 public Reply createTemplate(Actor a,UUID cmd,TemplateRequest r){
  a.require(Perm.ROSTER_MANAGE);
  return db.command(a,cmd,"TEMPLATE",r,()->{
   LocalTime t;
   try{t=LocalTime.parse(r.startTime());}catch(RuntimeException e){throw new Violation("invalid_template");}
   if(r==null||!Domain.validCode(r.code())||r.durationMinutes()<30||r.durationMinutes()>960)throw new Violation("invalid_template");
   if(!db.rows("SELECT 1 FROM synqoria_hr.shift_templates WHERE code=?",r.code()).isEmpty())throw new Violation("duplicate_record");
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_hr.shift_templates(id,cell_id,facility_id,code,start_time,duration_minutes) VALUES (?,?,?,?,?,?)",id,a.cell(),a.facility(),r.code(),java.sql.Time.valueOf(t),r.durationMinutes());
   db.audit(a,cmd,"TEMPLATE",id,"CREATE",null,"ACTIVE",1,m("code",r.code()));
   return new Reply(201,m("id",id.toString(),"code",r.code(),"startTime",t.toString(),"durationMinutes",r.durationMinutes()));
  });
 }
 public Reply setCoverage(Actor a,UUID cmd,CoverageRequest r){
  a.require(Perm.ROSTER_MANAGE);
  return db.command(a,cmd,"COVERAGE",r,()->{
   if(r==null||r.departmentId()==null||r.templateId()==null||r.minStaff()<0||r.minStaff()>500)throw new Violation("invalid_coverage");
   db.jdbc.update("INSERT INTO synqoria_hr.coverage_requirements(cell_id,facility_id,department_id,template_id,min_staff) VALUES (?,?,?,?,?) "+
    "ON CONFLICT (cell_id,facility_id,department_id,template_id) DO UPDATE SET min_staff=EXCLUDED.min_staff",a.cell(),a.facility(),r.departmentId(),r.templateId(),r.minStaff());
   db.audit(a,cmd,"COVERAGE",r.templateId(),"SET","SET","SET",1,m("departmentId",r.departmentId().toString(),"min",r.minStaff()));
   return new Reply(200,m("departmentId",r.departmentId().toString(),"templateId",r.templateId().toString(),"minStaff",r.minStaff()));
  });
 }
 public List<Map<String,Object>> templates(Actor a){
  a.require(Perm.ROSTER_READ);
  return db.scoped(a,()->db.rows("SELECT id,code,start_time,duration_minutes FROM synqoria_hr.shift_templates ORDER BY code"));
 }

 // ---------------------------------------------------------------- rosters
 private Map<String,Object> lockRoster(UUID id,long expected){
  List<Map<String,Object>> r=db.rows("SELECT * FROM synqoria_hr.rosters WHERE id=? FOR UPDATE",id);
  if(r.isEmpty())throw new Violation("not_found");
  if(((Number)r.getFirst().get("version")).longValue()!=expected)throw new Violation("stale_version");
  return r.getFirst();
 }
 private Map<String,Object> roster(UUID id){return db.one("SELECT * FROM synqoria_hr.rosters WHERE id=?",id);}
 public Reply createRoster(Actor a,UUID cmd,RosterRequest r){
  a.require(Perm.ROSTER_MANAGE);
  return db.command(a,cmd,"ROSTER",r,()->{
   if(r==null||r.departmentId()==null||r.periodStart()==null||r.periodEnd()==null||r.periodEnd().isBefore(r.periodStart())||r.periodEnd().toEpochDay()-r.periodStart().toEpochDay()>62)throw new Violation("invalid_roster");
   if(db.rows("SELECT 1 FROM synqoria_hr.departments WHERE id=?",r.departmentId()).isEmpty())throw new Violation("not_found");
   if(!db.rows("SELECT 1 FROM synqoria_hr.rosters WHERE department_id=? AND period_start=? AND status IN ('DRAFT','SUBMITTED')",r.departmentId(),Date.valueOf(r.periodStart())).isEmpty())throw new Violation("duplicate_record");
   int rev=db.jdbc.queryForObject("SELECT coalesce(max(revision),0)+1 FROM synqoria_hr.rosters WHERE department_id=? AND period_start=?",Integer.class,r.departmentId(),Date.valueOf(r.periodStart()));
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_hr.rosters(id,cell_id,facility_id,department_id,period_start,period_end,revision,status,created_by,version) VALUES (?,?,?,?,?,?,?,'DRAFT',?,1)",
    id,a.cell(),a.facility(),r.departmentId(),Date.valueOf(r.periodStart()),Date.valueOf(r.periodEnd()),rev,a.id());
   db.audit(a,cmd,"ROSTER",id,"CREATE",null,"DRAFT",1,m("revision",rev));
   return new Reply(201,roster(id));
  });
 }
 /** New DRAFT revision pre-filled from the currently published roster. */
 public Reply revise(Actor a,UUID cmd,UUID id,Transition t){
  a.require(Perm.ROSTER_MANAGE);
  return db.command(a,cmd,"ROSTER_REVISE|"+id,t,()->{
   Map<String,Object> p=lockRoster(id,t.expectedVersion());
   if(!"PUBLISHED".equals(p.get("status")))throw new Violation("invalid_transition");
   if(!db.rows("SELECT 1 FROM synqoria_hr.rosters WHERE department_id=? AND period_start=? AND status IN ('DRAFT','SUBMITTED')",p.get("departmentId"),Date.valueOf((String)p.get("periodStart"))).isEmpty())throw new Violation("duplicate_record");
   UUID nid=UUID.randomUUID();
   int rev=((Number)p.get("revision")).intValue()+1;
   db.jdbc.update("INSERT INTO synqoria_hr.rosters(id,cell_id,facility_id,department_id,period_start,period_end,revision,status,created_by,version) VALUES (?,?,?,?,?,?,?,'DRAFT',?,1)",
    nid,a.cell(),a.facility(),p.get("departmentId"),Date.valueOf((String)p.get("periodStart")),Date.valueOf((String)p.get("periodEnd")),rev,a.id());
   db.jdbc.update("INSERT INTO synqoria_hr.roster_shifts(id,cell_id,facility_id,roster_id,template_id,staff_id,starts_at,ends_at,overtime) "+
    "SELECT gen_random_uuid(),cell_id,facility_id,?,template_id,staff_id,starts_at,ends_at,overtime FROM synqoria_hr.roster_shifts WHERE roster_id=?",nid,id);
   db.audit(a,cmd,"ROSTER",nid,"REVISE","PUBLISHED","DRAFT",1,m("fromRoster",id.toString(),"revision",rev));
   return new Reply(201,roster(nid));
  });
 }
 public Reply addShift(Actor a,UUID cmd,UUID rosterId,ShiftRequest r){
  a.require(Perm.ROSTER_MANAGE);
  return db.command(a,cmd,"SHIFT_ADD|"+rosterId,r,()->{
   List<Map<String,Object>> rr=db.rows("SELECT * FROM synqoria_hr.rosters WHERE id=? FOR UPDATE",rosterId);
   if(rr.isEmpty())throw new Violation("not_found");
   Map<String,Object> ro=rr.getFirst();
   if(!"DRAFT".equals(ro.get("status")))throw new Violation("invalid_transition");
   if(r==null||r.templateId()==null||r.date()==null)throw new Violation("invalid_shift");
   if(r.date().isBefore(LocalDate.parse((String)ro.get("periodStart")))||r.date().isAfter(LocalDate.parse((String)ro.get("periodEnd"))))throw new Violation("roster_conflict",List.of("OUTSIDE_PERIOD"));
   List<Map<String,Object>> tp=db.rows("SELECT start_time,duration_minutes FROM synqoria_hr.shift_templates WHERE id=?",r.templateId());
   if(tp.isEmpty())throw new Violation("not_found");
   Instant start=LocalDateTime.of(r.date(),LocalTime.parse(String.valueOf(tp.getFirst().get("startTime")))).atZone(db.zone).toInstant();
   Instant end=start.plus(Duration.ofMinutes(((Number)tp.getFirst().get("durationMinutes")).longValue()));
   if(r.staffId()!=null){
    List<Map<String,Object>> st=db.rows("SELECT status FROM synqoria_hr.staff WHERE id=?",r.staffId());
    if(st.isEmpty())throw new Violation("not_found");
    if(!"ACTIVE".equals(st.getFirst().get("status")))throw new Violation("roster_conflict",List.of("STAFF_NOT_ACTIVE"));
    if(!db.rows("SELECT 1 FROM synqoria_hr.leave_requests WHERE staff_id=? AND status='APPROVED' AND from_date<=? AND to_date>=?",r.staffId(),Date.valueOf(r.date()),Date.valueOf(r.date())).isEmpty())
     throw new Violation("roster_conflict",List.of("ON_APPROVED_LEAVE"));
   }
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_hr.roster_shifts(id,cell_id,facility_id,roster_id,template_id,staff_id,starts_at,ends_at,overtime) VALUES (?,?,?,?,?,?,?,?,?)",
    id,a.cell(),a.facility(),rosterId,r.templateId(),r.staffId(),Timestamp.from(start),Timestamp.from(end),r.overtime());
   db.audit(a,cmd,"ROSTER",rosterId,"ADD_SHIFT","DRAFT","DRAFT",((Number)ro.get("version")).longValue(),m("shiftId",id.toString(),"vacancy",r.staffId()==null));
   return new Reply(201,db.one("SELECT * FROM synqoria_hr.roster_shifts WHERE id=?",id));
  });
 }
 public Reply removeShift(Actor a,UUID cmd,UUID shiftId){
  a.require(Perm.ROSTER_MANAGE);
  return db.command(a,cmd,"SHIFT_REMOVE|"+shiftId,m("id",shiftId.toString()),()->{
   List<Map<String,Object>> s=db.rows("SELECT s.roster_id,r.status,r.version FROM synqoria_hr.roster_shifts s JOIN synqoria_hr.rosters r ON r.id=s.roster_id WHERE s.id=? FOR UPDATE OF r",shiftId);
   if(s.isEmpty())throw new Violation("not_found");
   if(!"DRAFT".equals(s.getFirst().get("status")))throw new Violation("invalid_transition");
   db.jdbc.update("DELETE FROM synqoria_hr.roster_shifts WHERE id=?",shiftId);
   db.audit(a,cmd,"ROSTER",(UUID)s.getFirst().get("rosterId"),"REMOVE_SHIFT","DRAFT","DRAFT",((Number)s.getFirst().get("version")).longValue(),m("shiftId",shiftId.toString()));
   return new Reply(200,m("removed",shiftId.toString()));
  });
 }
 /** Overtime must be authorised by someone other than the person who built the roster. */
 public Reply authorizeOvertime(Actor a,UUID cmd,UUID shiftId){
  a.require(Perm.OVERTIME_AUTHORIZE);
  return db.command(a,cmd,"OT_AUTH|"+shiftId,m("id",shiftId.toString()),()->{
   List<Map<String,Object>> s=db.rows("SELECT s.overtime,s.overtime_authorized_by,r.created_by,r.status,r.id AS rid FROM synqoria_hr.roster_shifts s JOIN synqoria_hr.rosters r ON r.id=s.roster_id WHERE s.id=? FOR UPDATE OF s",shiftId);
   if(s.isEmpty())throw new Violation("not_found");
   Map<String,Object> x=s.getFirst();
   if(!Boolean.TRUE.equals(x.get("overtime"))||x.get("overtimeAuthorizedBy")!=null||!List.of("DRAFT","SUBMITTED").contains(x.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(x.get("createdBy")))throw new Violation("approver_must_be_independent");
   db.jdbc.update("UPDATE synqoria_hr.roster_shifts SET overtime_authorized_by=?,overtime_authorized_at=clock_timestamp() WHERE id=?",a.id(),shiftId);
   db.audit(a,cmd,"ROSTER",(UUID)x.get("rid"),"AUTHORIZE_OVERTIME",(String)x.get("status"),(String)x.get("status"),1,m("shiftId",shiftId.toString()));
   return new Reply(200,m("shiftId",shiftId.toString(),"authorized",true));
  });
 }
 /** All blocking problems for a roster; used by submit and by the read-only check. */
 List<String> problems(UUID rosterId,Map<String,Object> ro){
  List<Domain.Shift> shifts=new ArrayList<>();
  UUID dep=(UUID)ro.get("departmentId");
  LocalDate from=LocalDate.parse((String)ro.get("periodStart")),to=LocalDate.parse((String)ro.get("periodEnd"));
  // rest and overlap are evaluated against ALL of the staff member's shifts around the period, including other rosters
  for(Map<String,Object> s:db.rows("SELECT s.staff_id,s.starts_at,s.ends_at FROM synqoria_hr.roster_shifts s JOIN synqoria_hr.rosters r ON r.id=s.roster_id "+
    "WHERE s.staff_id IN (SELECT staff_id FROM synqoria_hr.roster_shifts WHERE roster_id=? AND staff_id IS NOT NULL) AND (s.roster_id=? OR (r.status='PUBLISHED' AND NOT (r.department_id=? AND r.period_start=?))) "+
    "AND s.starts_at>=?::timestamptz-interval '2 days' AND s.starts_at<=?::timestamptz+interval '3 days'",rosterId,rosterId,dep,Date.valueOf(from),
    Timestamp.from(from.atStartOfDay(db.zone).toInstant()),Timestamp.from(to.atStartOfDay(db.zone).toInstant())))
   shifts.add(new Domain.Shift((UUID)s.get("staffId"),Instant.parse((String)s.get("startsAt")),Instant.parse((String)s.get("endsAt"))));
  List<String> out=new ArrayList<>(Domain.rosterProblems(shifts,minRest));
  for(Map<String,Object> s:db.rows("SELECT id FROM synqoria_hr.roster_shifts WHERE roster_id=? AND overtime AND overtime_authorized_by IS NULL",rosterId))out.add("OVERTIME_NOT_AUTHORIZED:"+s.get("id"));
  Map<UUID,Integer> req=new HashMap<>();
  for(Map<String,Object> c:db.rows("SELECT template_id,min_staff FROM synqoria_hr.coverage_requirements WHERE department_id=?",dep))req.put((UUID)c.get("templateId"),((Number)c.get("minStaff")).intValue());
  List<Object[]> assigned=new ArrayList<>();
  for(Map<String,Object> s:db.rows("SELECT template_id,starts_at FROM synqoria_hr.roster_shifts WHERE roster_id=? AND staff_id IS NOT NULL",rosterId))
   assigned.add(new Object[]{s.get("templateId"),Instant.parse((String)s.get("startsAt")).atZone(db.zone).toLocalDate()});
  out.addAll(Domain.coverageGaps(req,assigned,from,to));
  Collections.sort(out);
  return out;
 }
 public Map<String,Object> check(Actor a,UUID id){
  a.require(Perm.ROSTER_READ);
  return db.scoped(a,()->{
   Map<String,Object> ro=roster(id);
   List<String> p=problems(id,ro);
   return m("rosterId",id.toString(),"ok",p.isEmpty(),"problems",p,"vacancies",db.jdbc.queryForObject("SELECT count(*) FROM synqoria_hr.roster_shifts WHERE roster_id=? AND staff_id IS NULL",Long.class,id));
  });
 }
 /** Most serious problem decides the error code: rest > overlap/overtime/other > coverage gap. */
 static String codeFor(List<String> p){
  if(p.stream().anyMatch(x->x.startsWith("REST")))return "rest_violation";
  if(p.stream().anyMatch(x->!x.startsWith("GAP")))return "roster_conflict";
  return "coverage_gap";
 }
 public Reply submit(Actor a,UUID cmd,UUID id,Transition t){
  a.require(Perm.ROSTER_MANAGE);
  return db.command(a,cmd,"ROSTER_SUBMIT|"+id,t,()->{
   Map<String,Object> ro=lockRoster(id,t.expectedVersion());
   if(!"DRAFT".equals(ro.get("status")))throw new Violation("invalid_transition");
   List<String> p=problems(id,ro);
   if(!p.isEmpty()){
    throw new Violation(codeFor(p),p);
   }
   db.jdbc.update("UPDATE synqoria_hr.rosters SET status='SUBMITTED',version=version+1 WHERE id=?",id);
   db.audit(a,cmd,"ROSTER",id,"SUBMIT","DRAFT","SUBMITTED",t.expectedVersion()+1,m());
   return new Reply(200,roster(id));
  });
 }
 public Reply review(Actor a,UUID cmd,UUID id,Decision d){
  a.require(Perm.ROSTER_REVIEW);
  return db.command(a,cmd,"ROSTER_REVIEW|"+id,d,()->{
   Map<String,Object> ro=lockRoster(id,d.expectedVersion());
   if(!"SUBMITTED".equals(ro.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(ro.get("createdBy")))throw new Violation("reviewer_must_be_independent");
   if(!d.approve()&&(d.note()==null||d.note().isBlank()))throw new Violation("reason_required");
   if(d.approve()){
    List<String> p=problems(id,ro);                        // re-checked at the moment of publication
    if(!p.isEmpty())throw new Violation(codeFor(p),p);
    for(Map<String,Object> old:db.rows("SELECT id,version FROM synqoria_hr.rosters WHERE department_id=? AND period_start=? AND status='PUBLISHED' FOR UPDATE",ro.get("departmentId"),Date.valueOf((String)ro.get("periodStart")))){
     db.jdbc.update("UPDATE synqoria_hr.rosters SET status='SUPERSEDED',version=version+1 WHERE id=?",old.get("id"));
     db.audit(a,cmd,"ROSTER",(UUID)old.get("id"),"SUPERSEDE","PUBLISHED","SUPERSEDED",((Number)old.get("version")).longValue()+1,m("by",id.toString()));
    }
   }
   String to=d.approve()?"PUBLISHED":"REJECTED";
   db.jdbc.update("UPDATE synqoria_hr.rosters SET status=?,reviewed_by=?,reviewed_at=clock_timestamp(),review_note=?,version=version+1 WHERE id=?",to,a.id(),d.note(),id);
   db.audit(a,cmd,"ROSTER",id,d.approve()?"PUBLISH":"REJECT","SUBMITTED",to,d.expectedVersion()+1,m());
   return new Reply(200,roster(id));
  });
 }
 public Map<String,Object> getRoster(Actor a,UUID id){
  a.require(Perm.ROSTER_READ);
  return db.scoped(a,()->{
   Map<String,Object> r=new LinkedHashMap<>(roster(id));
   r.put("shifts",db.rows("SELECT s.id,s.template_id,t.code AS template_code,s.staff_id,st.staff_number,st.family_name,s.starts_at,s.ends_at,s.overtime,s.overtime_authorized_by "+
    "FROM synqoria_hr.roster_shifts s JOIN synqoria_hr.shift_templates t ON t.id=s.template_id LEFT JOIN synqoria_hr.staff st ON st.id=s.staff_id WHERE s.roster_id=? ORDER BY s.starts_at,st.family_name NULLS LAST",id));
   return r;
  });
 }
 public Map<String,Object> listRosters(Actor a,UUID department,String status,int page,int size){
  a.require(Perm.ROSTER_READ);
  if(page<0||size<1||size>100)throw new Violation("invalid_pagination");
  return db.scoped(a,()->{
   List<Object> args=new ArrayList<>();StringBuilder w=new StringBuilder(" WHERE true");
   if(department!=null){w.append(" AND r.department_id=?");args.add(department);}
   if(status!=null){w.append(" AND r.status=?");args.add(status);}
   String from=" FROM synqoria_hr.rosters r JOIN synqoria_hr.departments d ON d.id=r.department_id";
   long total=db.jdbc.queryForObject("SELECT count(*)"+from+w,Long.class,args.toArray());
   args.add(size);args.add((long)page*size);
   return db.pageOf(db.rows("SELECT r.id,d.code AS department_code,r.period_start,r.period_end,r.revision,r.status,r.created_by,r.reviewed_by,r.version"+from+w+" ORDER BY r.period_start DESC,d.code,r.revision DESC LIMIT ? OFFSET ?",args.toArray()),page,size,total);
  });
 }

 // ---------------------------------------------------------------- attendance
 private UUID insertAttendance(Actor a,UUID cmd,AttendanceRequest r,UUID corrects){
  if(r==null||r.staffId()==null||r.workDate()==null||r.clockIn()==null||(r.clockOut()!=null&&!r.clockOut().isAfter(r.clockIn())))throw new Violation("invalid_attendance");
  String src=r.source()==null?"MANUAL":r.source();
  if(!List.of("DEVICE","IMPORT","MANUAL").contains(src))throw new Violation("invalid_attendance");
  if(r.importRef()!=null&&r.importRef().length()>80)throw new Violation("invalid_attendance");
  if(db.rows("SELECT 1 FROM synqoria_hr.staff WHERE id=?",r.staffId()).isEmpty())throw new Violation("not_found");
  UUID id=UUID.randomUUID();
  db.jdbc.update("INSERT INTO synqoria_hr.attendance_records(id,cell_id,facility_id,staff_id,work_date,clock_in,clock_out,source,import_ref,status,corrects_id,captured_by,version) VALUES (?,?,?,?,?,?,?,?,?,'CAPTURED',?,?,1)",
   id,a.cell(),a.facility(),r.staffId(),Date.valueOf(r.workDate()),Timestamp.from(r.clockIn()),r.clockOut()==null?null:Timestamp.from(r.clockOut()),src,r.importRef(),corrects,a.id());
  db.audit(a,cmd,"ATTENDANCE",id,corrects==null?"CAPTURE":"CORRECT",null,"CAPTURED",1,m("source",src));
  return id;
 }
 public Reply capture(Actor a,UUID cmd,AttendanceRequest r){
  a.require(Perm.ATTENDANCE_CAPTURE);
  return db.command(a,cmd,"ATT_CAPTURE",r,()->new Reply(201,db.one("SELECT * FROM synqoria_hr.attendance_records WHERE id=?",insertAttendance(a,cmd,r,null))));
 }
 /** Idempotent bulk import: a repeated importRef is skipped, never duplicated. */
 public Reply importBatch(Actor a,UUID cmd,ImportRequest r){
  a.require(Perm.ATTENDANCE_CAPTURE);
  return db.command(a,cmd,"ATT_IMPORT",r,()->{
   if(r==null||r.records()==null||r.records().isEmpty()||r.records().size()>1000)throw new Violation("invalid_import");
   int inserted=0,skipped=0;
   for(AttendanceRequest x:r.records()){
    if(x==null||x.importRef()==null||x.importRef().isBlank())throw new Violation("invalid_import",List.of("IMPORT_REF_REQUIRED"));
    if(!db.rows("SELECT 1 FROM synqoria_hr.attendance_records WHERE source='IMPORT' AND import_ref=?",x.importRef()).isEmpty()){skipped++;continue;}
    insertAttendance(a,cmd,new AttendanceRequest(x.staffId(),x.workDate(),x.clockIn(),x.clockOut(),"IMPORT",x.importRef()),null);inserted++;
   }
   return new Reply(200,m("inserted",inserted,"skipped",skipped));
  });
 }
 private Map<String,Object> lockAtt(UUID id,long expected){
  List<Map<String,Object>> r=db.rows("SELECT * FROM synqoria_hr.attendance_records WHERE id=? FOR UPDATE",id);
  if(r.isEmpty())throw new Violation("not_found");
  if(((Number)r.getFirst().get("version")).longValue()!=expected)throw new Violation("stale_version");
  return r.getFirst();
 }
 public Reply dispute(Actor a,UUID cmd,UUID id,DisputeRequest d){
  a.require(Perm.ATTENDANCE_CAPTURE);
  return db.command(a,cmd,"ATT_DISPUTE|"+id,d,()->{
   Map<String,Object> x=lockAtt(id,d.expectedVersion());
   if(!"CAPTURED".equals(x.get("status")))throw new Violation("invalid_transition");
   if(d.note()==null||d.note().isBlank()||d.note().length()>300)throw new Violation("reason_required");
   db.jdbc.update("UPDATE synqoria_hr.attendance_records SET status='DISPUTED',note=?,version=version+1 WHERE id=?",d.note().strip(),id);
   db.audit(a,cmd,"ATTENDANCE",id,"DISPUTE","CAPTURED","DISPUTED",d.expectedVersion()+1,m());
   return new Reply(200,db.one("SELECT * FROM synqoria_hr.attendance_records WHERE id=?",id));
  });
 }
 /** A correction is a NEW record linked to the original; the original is marked CORRECTED and kept. */
 public Reply correct(Actor a,UUID cmd,UUID id,CorrectionRequest c){
  a.require(Perm.ATTENDANCE_CAPTURE);
  return db.command(a,cmd,"ATT_CORRECT|"+id,c,()->{
   Map<String,Object> x=lockAtt(id,c.expectedVersion());
   if(!List.of("CAPTURED","DISPUTED").contains(x.get("status")))throw new Violation("invalid_transition");
   if(c.note()==null||c.note().isBlank()||c.note().length()>300)throw new Violation("reason_required");
   UUID nid=insertAttendance(a,cmd,new AttendanceRequest((UUID)x.get("staffId"),LocalDate.parse((String)x.get("workDate")),c.clockIn(),c.clockOut(),"MANUAL",null),id);
   db.jdbc.update("UPDATE synqoria_hr.attendance_records SET status='CORRECTED',version=version+1 WHERE id=?",id);
   db.jdbc.update("UPDATE synqoria_hr.attendance_records SET note=? WHERE id=?",c.note().strip(),nid);
   db.audit(a,cmd,"ATTENDANCE",id,"CORRECTED",(String)x.get("status"),"CORRECTED",c.expectedVersion()+1,m("replacedBy",nid.toString()));
   return new Reply(201,db.one("SELECT * FROM synqoria_hr.attendance_records WHERE id=?",nid));
  });
 }
 public Reply decideAttendance(Actor a,UUID cmd,UUID id,Decision d){
  a.require(Perm.ATTENDANCE_REVIEW);
  return db.command(a,cmd,"ATT_DECIDE|"+id,d,()->{
   Map<String,Object> x=lockAtt(id,d.expectedVersion());
   if(!"CAPTURED".equals(x.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(x.get("capturedBy")))throw new Violation("reviewer_must_be_independent");
   if(x.get("clockOut")==null)throw new Violation("invalid_attendance",List.of("OPEN_RECORD"));
   if(!d.approve()){
    if(d.note()==null||d.note().isBlank())throw new Violation("reason_required");
    db.jdbc.update("UPDATE synqoria_hr.attendance_records SET status='DISPUTED',note=?,decided_by=?,decided_at=clock_timestamp(),version=version+1 WHERE id=?",d.note().strip(),a.id(),id);
    db.audit(a,cmd,"ATTENDANCE",id,"RETURN","CAPTURED","DISPUTED",d.expectedVersion()+1,m());
   }else{
    db.jdbc.update("UPDATE synqoria_hr.attendance_records SET status='APPROVED',decided_by=?,decided_at=clock_timestamp(),version=version+1 WHERE id=?",a.id(),id);
    db.audit(a,cmd,"ATTENDANCE",id,"APPROVE","CAPTURED","APPROVED",d.expectedVersion()+1,m());
   }
   return new Reply(200,db.one("SELECT * FROM synqoria_hr.attendance_records WHERE id=?",id));
  });
 }
 public Map<String,Object> listAttendance(Actor a,UUID staff,String status,LocalDate from,LocalDate to,int page,int size){
  a.require(Perm.ATTENDANCE_REVIEW);
  if(page<0||size<1||size>100)throw new Violation("invalid_pagination");
  return db.scoped(a,()->{
   List<Object> args=new ArrayList<>();StringBuilder w=new StringBuilder(" WHERE true");
   if(staff!=null){w.append(" AND r.staff_id=?");args.add(staff);}
   if(status!=null){w.append(" AND r.status=?");args.add(status);}
   if(from!=null){w.append(" AND r.work_date>=?");args.add(Date.valueOf(from));}
   if(to!=null){w.append(" AND r.work_date<=?");args.add(Date.valueOf(to));}
   String f=" FROM synqoria_hr.attendance_records r JOIN synqoria_hr.staff s ON s.id=r.staff_id";
   long total=db.jdbc.queryForObject("SELECT count(*)"+f+w,Long.class,args.toArray());
   args.add(size);args.add((long)page*size);
   return db.pageOf(db.rows("SELECT r.id,r.staff_id,s.staff_number,s.family_name,r.work_date,r.clock_in,r.clock_out,r.source,r.status,r.note,r.corrects_id,r.captured_by,r.version"+f+w+
    " ORDER BY r.work_date DESC,s.family_name,r.id LIMIT ? OFFSET ?",args.toArray()),page,size,total);
  });
 }

 // ---------------------------------------------------------------- leave
 public Reply createLeaveType(Actor a,UUID cmd,LeaveTypeRequest r){
  a.require(Perm.LEAVE_DECIDE);
  return db.command(a,cmd,"LEAVE_TYPE",r,()->{
   if(r==null||!Domain.validCode(r.code())||r.accrualPerMonth()==null||!r.accrualPerMonth().matches("\\d{1,4}(\\.\\d{1,4})?")||r.maxBalance()==null||!r.maxBalance().matches("\\d{1,6}(\\.\\d{1,2})?"))throw new Violation("invalid_leave_type");
   if(!db.rows("SELECT 1 FROM synqoria_hr.leave_types WHERE code=?",r.code()).isEmpty())throw new Violation("duplicate_record");
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_hr.leave_types(id,cell_id,facility_id,code,paid,accrual_per_month,max_balance) VALUES (?,?,?,?,?,?,?)",id,a.cell(),a.facility(),r.code(),r.paid(),new BigDecimal(r.accrualPerMonth()),new BigDecimal(r.maxBalance()));
   db.audit(a,cmd,"LEAVETYPE",id,"CREATE",null,"ACTIVE",1,m("code",r.code()));
   return new Reply(201,db.one("SELECT * FROM synqoria_hr.leave_types WHERE id=?",id));
  });
 }
 public Reply addHoliday(Actor a,UUID cmd,HolidayRequest r){
  a.require(Perm.LEAVE_DECIDE);
  return db.command(a,cmd,"HOLIDAY",r,()->{
   if(r==null||r.date()==null||r.name()==null||r.name().isBlank()||r.name().length()>120)throw new Violation("invalid_holiday");
   db.jdbc.update("INSERT INTO synqoria_hr.holidays(cell_id,facility_id,holiday_date,name) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",a.cell(),a.facility(),Date.valueOf(r.date()),r.name().strip());
   db.audit(a,cmd,"HOLIDAY",UUID.nameUUIDFromBytes(r.date().toString().getBytes()),"ADD",null,"ACTIVE",1,m("date",r.date().toString()));
   return new Reply(201,m("date",r.date().toString(),"name",r.name().strip()));
  });
 }
 Set<LocalDate> holidays(LocalDate from,LocalDate to){
  Set<LocalDate> s=new HashSet<>();
  for(Map<String,Object> h:db.rows("SELECT holiday_date FROM synqoria_hr.holidays WHERE holiday_date BETWEEN ? AND ?",Date.valueOf(from),Date.valueOf(to)))s.add(LocalDate.parse((String)h.get("holidayDate")));
  return s;
 }
 /** accrued (whole months since hire x rate, capped) minus approved paid days; unpaid types have no balance limit. */
 BigDecimal balance(UUID staff,Map<String,Object> type,LocalDate asOf){
  Map<String,Object> s=db.one("SELECT hire_date FROM synqoria_hr.staff WHERE id=?",staff);
  BigDecimal acc=Domain.accrued(LocalDate.parse((String)s.get("hireDate")),asOf,(BigDecimal)type.get("accrualPerMonth"),(BigDecimal)type.get("maxBalance"));
  BigDecimal used=db.jdbc.queryForObject("SELECT coalesce(sum(days),0) FROM synqoria_hr.leave_requests WHERE staff_id=? AND leave_type_id=? AND status IN ('APPROVED','SUBMITTED')",BigDecimal.class,staff,type.get("id"));
  return acc.subtract(used);
 }
 public Reply requestLeave(Actor a,UUID cmd,LeaveRequestIn r){
  a.require(Perm.LEAVE_REQUEST);
  return db.command(a,cmd,"LEAVE_REQUEST",r,()->{
   if(r==null||r.staffId()==null||r.leaveTypeId()==null||r.fromDate()==null||r.toDate()==null||r.toDate().isBefore(r.fromDate())||r.toDate().toEpochDay()-r.fromDate().toEpochDay()>365)throw new Violation("invalid_leave");
   List<Map<String,Object>> st=db.rows("SELECT status FROM synqoria_hr.staff WHERE id=? FOR SHARE",r.staffId());
   if(st.isEmpty())throw new Violation("not_found");
   if(!"ACTIVE".equals(st.getFirst().get("status")))throw new Violation("invalid_transition");
   List<Map<String,Object>> lt=db.rows("SELECT * FROM synqoria_hr.leave_types WHERE id=?",r.leaveTypeId());
   if(lt.isEmpty())throw new Violation("not_found");
   if(!db.rows("SELECT 1 FROM synqoria_hr.leave_requests WHERE staff_id=? AND status IN ('SUBMITTED','APPROVED') AND from_date<=? AND to_date>=?",r.staffId(),Date.valueOf(r.toDate()),Date.valueOf(r.fromDate())).isEmpty())throw new Violation("duplicate_record");
   int days=Domain.workingDays(r.fromDate(),r.toDate(),holidays(r.fromDate(),r.toDate()));
   if(days<1)throw new Violation("invalid_leave",List.of("NO_WORKING_DAYS"));
   if(Boolean.TRUE.equals(lt.getFirst().get("paid"))&&balance(r.staffId(),lt.getFirst(),r.fromDate()).compareTo(BigDecimal.valueOf(days))<0)throw new Violation("insufficient_leave");
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_hr.leave_requests(id,cell_id,facility_id,staff_id,leave_type_id,from_date,to_date,days,status,requested_by,version) VALUES (?,?,?,?,?,?,?,?,'SUBMITTED',?,1)",
    id,a.cell(),a.facility(),r.staffId(),r.leaveTypeId(),Date.valueOf(r.fromDate()),Date.valueOf(r.toDate()),BigDecimal.valueOf(days),a.id());
   db.audit(a,cmd,"LEAVE",id,"REQUEST",null,"SUBMITTED",1,m("days",days));
   return new Reply(201,db.one("SELECT * FROM synqoria_hr.leave_requests WHERE id=?",id));
  });
 }
 public Reply decideLeave(Actor a,UUID cmd,UUID id,Decision d){
  a.require(Perm.LEAVE_DECIDE);
  return db.command(a,cmd,"LEAVE_DECIDE|"+id,d,()->{
   List<Map<String,Object>> rows=db.rows("SELECT * FROM synqoria_hr.leave_requests WHERE id=? FOR UPDATE",id);
   if(rows.isEmpty())throw new Violation("not_found");
   Map<String,Object> x=rows.getFirst();
   if(((Number)x.get("version")).longValue()!=d.expectedVersion())throw new Violation("stale_version");
   if(!"SUBMITTED".equals(x.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(x.get("requestedBy")))throw new Violation("decider_must_be_independent");
   if(!d.approve()&&(d.note()==null||d.note().isBlank()))throw new Violation("reason_required");
   String to=d.approve()?"APPROVED":"REJECTED";
   db.jdbc.update("UPDATE synqoria_hr.leave_requests SET status=?,decided_by=?,decided_at=clock_timestamp(),decision_note=?,version=version+1 WHERE id=?",to,a.id(),d.note(),id);
   db.audit(a,cmd,"LEAVE",id,d.approve()?"APPROVE":"REJECT","SUBMITTED",to,d.expectedVersion()+1,m());
   return new Reply(200,db.one("SELECT * FROM synqoria_hr.leave_requests WHERE id=?",id));
  });
 }
 public Map<String,Object> listLeave(Actor a,UUID staff,String status,int page,int size){
  a.require(Perm.LEAVE_READ);
  if(page<0||size<1||size>100)throw new Violation("invalid_pagination");
  return db.scoped(a,()->{
   List<Object> args=new ArrayList<>();StringBuilder w=new StringBuilder(" WHERE true");
   if(staff!=null){w.append(" AND l.staff_id=?");args.add(staff);}
   if(status!=null){w.append(" AND l.status=?");args.add(status);}
   String f=" FROM synqoria_hr.leave_requests l JOIN synqoria_hr.staff s ON s.id=l.staff_id JOIN synqoria_hr.leave_types t ON t.id=l.leave_type_id";
   long total=db.jdbc.queryForObject("SELECT count(*)"+f+w,Long.class,args.toArray());
   args.add(size);args.add((long)page*size);
   return db.pageOf(db.rows("SELECT l.id,l.staff_id,s.staff_number,s.family_name,t.code AS leave_type,t.paid,l.from_date,l.to_date,l.days,l.status,l.requested_by,l.decided_by,l.decision_note,l.version"+f+w+
    " ORDER BY l.from_date DESC,l.id LIMIT ? OFFSET ?",args.toArray()),page,size,total);
  });
 }
 public List<Map<String,Object>> balances(Actor a,UUID staff){
  a.require(Perm.LEAVE_READ);
  return db.scoped(a,()->{
   db.one("SELECT id FROM synqoria_hr.staff WHERE id=?",staff);
   List<Map<String,Object>> out=new ArrayList<>();
   for(Map<String,Object> t:db.rows("SELECT * FROM synqoria_hr.leave_types ORDER BY code"))
    out.add(m("leaveType",t.get("code"),"paid",t.get("paid"),"balance",balance(staff,t,today()).toPlainString()));
   return out;
  });
 }
 public Map<String,Object> leaveTypesAndHolidays(Actor a){
  a.require(Perm.LEAVE_READ);
  return db.scoped(a,()->m("leaveTypes",db.rows("SELECT id,code,paid,accrual_per_month,max_balance FROM synqoria_hr.leave_types ORDER BY code"),
   "holidays",db.rows("SELECT holiday_date,name FROM synqoria_hr.holidays ORDER BY holiday_date")));
 }
}
