package org.synqoria.hr;

import static org.synqoria.hr.Db.m;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.synqoria.hr.Db.Reply;
import org.synqoria.hr.domain.Domain;
import org.synqoria.hr.domain.Domain.*;

/** M24-S01/S02: organisation, workforce records, dated lifecycle changes (maker-checker) and employment contracts. */
@Service
public class StaffService {
 private final Db db;
 public StaffService(Db db){this.db=db;}

 public record DeptRequest(String code,String name){}
 public record PositionRequest(String code,String title,UUID departmentId){}
 public record OnboardRequest(String staffNumber,String givenName,String familyName,UUID departmentId,UUID positionId,LocalDate hireDate,String classification,UUID userRef){}
 public record ChangeRequest(long expectedVersion,String kind,LocalDate effectiveDate,String reason,UUID toDepartmentId,UUID toPositionId){}
 public record Decision(long expectedVersion,boolean approve,String note){}
 public record ContractRequest(String employerCode,String countryCode,String currency,String payBasis,String baseAmount,UUID positionId,LocalDate effectiveFrom,LocalDate effectiveTo){}
 public record AccountRequest(String accountToken,String accountMask){}

 LocalDate today(){return LocalDate.ofInstant(db.clock.instant(),db.zone);}

 public Reply createDepartment(Actor a,UUID cmd,DeptRequest r){
  a.require(Perm.STAFF_MANAGE);
  return db.command(a,cmd,"DEPT",r,()->{
   if(r==null||!Domain.validCode(r.code())||r.name()==null||r.name().isBlank()||r.name().length()>120)throw new Violation("invalid_department");
   if(!db.rows("SELECT 1 FROM synqoria_hr.departments WHERE code=?",r.code()).isEmpty())throw new Violation("duplicate_record");
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_hr.departments(id,cell_id,facility_id,code,name) VALUES (?,?,?,?,?)",id,a.cell(),a.facility(),r.code(),r.name().strip());
   db.audit(a,cmd,"DEPARTMENT",id,"CREATE",null,"ACTIVE",1,m("code",r.code()));
   return new Reply(201,db.one("SELECT * FROM synqoria_hr.departments WHERE id=?",id));
  });
 }
 public Reply createPosition(Actor a,UUID cmd,PositionRequest r){
  a.require(Perm.STAFF_MANAGE);
  return db.command(a,cmd,"POSITION",r,()->{
   if(r==null||!Domain.validCode(r.code())||r.title()==null||r.title().isBlank()||r.title().length()>120||r.departmentId()==null)throw new Violation("invalid_position");
   if(db.rows("SELECT 1 FROM synqoria_hr.departments WHERE id=?",r.departmentId()).isEmpty())throw new Violation("not_found");
   if(!db.rows("SELECT 1 FROM synqoria_hr.positions WHERE code=?",r.code()).isEmpty())throw new Violation("duplicate_record");
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_hr.positions(id,cell_id,facility_id,department_id,code,title) VALUES (?,?,?,?,?,?)",id,a.cell(),a.facility(),r.departmentId(),r.code(),r.title().strip());
   db.audit(a,cmd,"POSITION",id,"CREATE",null,"ACTIVE",1,m("code",r.code()));
   return new Reply(201,db.one("SELECT * FROM synqoria_hr.positions WHERE id=?",id));
  });
 }
 public Map<String,Object> organisation(Actor a){
  a.require(Perm.STAFF_READ);
  return db.scoped(a,()->m("departments",db.rows("SELECT id,code,name FROM synqoria_hr.departments ORDER BY code"),
   "positions",db.rows("SELECT id,code,title,department_id FROM synqoria_hr.positions ORDER BY code")));
 }

 // ---------------------------------------------------------------- workforce records
 public Reply onboard(Actor a,UUID cmd,OnboardRequest r){
  a.require(Perm.STAFF_MANAGE);
  return db.command(a,cmd,"ONBOARD",r,()->{
   if(r==null||!Domain.validCode(r.staffNumber())||r.givenName()==null||r.givenName().isBlank()||r.givenName().length()>120||r.familyName()==null||r.familyName().isBlank()||
      r.familyName().length()>120||r.departmentId()==null||r.positionId()==null||r.hireDate()==null)throw new Violation("invalid_staff");
   String cls=r.classification()==null?"STANDARD":r.classification();
   if(!List.of("STANDARD","CONFIDENTIAL").contains(cls))throw new Violation("invalid_classification");
   if(cls.equals("CONFIDENTIAL"))a.require(Perm.CONFIDENTIAL_READ);
   if(r.hireDate().isAfter(today().plusDays(366)))throw new Violation("effective_date_conflict",List.of("TOO_FAR_AHEAD"));
   if(db.rows("SELECT 1 FROM synqoria_hr.positions WHERE id=? AND department_id=?",r.positionId(),r.departmentId()).isEmpty())throw new Violation("not_found");
   if(!db.rows("SELECT 1 FROM synqoria_hr.staff WHERE staff_number=?",r.staffNumber()).isEmpty())throw new Violation("duplicate_record");
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_hr.staff(id,cell_id,facility_id,staff_number,given_name,family_name,status,classification,department_id,position_id,user_ref,hire_date,last_effective_date,version,created_by) "+
    "VALUES (?,?,?,?,?,?,'ONBOARDING',?,?,?,?,?,?,1,?)",id,a.cell(),a.facility(),r.staffNumber(),r.givenName().strip(),r.familyName().strip(),cls,r.departmentId(),r.positionId(),r.userRef(),
    Date.valueOf(r.hireDate()),Date.valueOf(r.hireDate()),a.id());
   db.audit(a,cmd,"STAFF",id,"ONBOARD",null,"ONBOARDING",1,m("staffNumber",r.staffNumber(),"classification",cls));
   return new Reply(201,staff(a,id));
  });
 }
 /** Minimised projection: never includes compensation. Confidential rows are filtered by row-level security. */
 private Map<String,Object> staff(Actor a,UUID id){
  return db.one("SELECT s.id,s.staff_number,s.given_name,s.family_name,s.status,s.classification,s.department_id,d.code AS department_code,s.position_id,p.title AS position_title,"+
   "s.hire_date,s.termination_date,s.last_effective_date,s.version,s.updated_at FROM synqoria_hr.staff s JOIN synqoria_hr.departments d ON d.id=s.department_id "+
   "JOIN synqoria_hr.positions p ON p.id=s.position_id WHERE s.id=?",id);
 }
 public Map<String,Object> list(Actor a,String q,UUID department,String status,int page,int size){
  a.require(Perm.STAFF_READ);
  if(page<0||size<1||size>100)throw new Violation("invalid_pagination");
  if(status!=null)Domain.parse(StaffStatus.class,status,"status");
  if(q!=null&&q.length()>80)throw new Violation("invalid_query");
  return db.scoped(a,()->{
   List<Object> args=new ArrayList<>();StringBuilder w=new StringBuilder(" WHERE true");
   if(q!=null&&!q.isBlank()){String like="%"+q.strip().toLowerCase().replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
    w.append(" AND (lower(s.family_name) LIKE ? OR lower(s.given_name) LIKE ? OR lower(s.staff_number) LIKE ?)");args.add(like);args.add(like);args.add(like);}
   if(department!=null){w.append(" AND s.department_id=?");args.add(department);}
   if(status!=null){w.append(" AND s.status=?");args.add(status);}
   String from=" FROM synqoria_hr.staff s JOIN synqoria_hr.departments d ON d.id=s.department_id JOIN synqoria_hr.positions p ON p.id=s.position_id";
   long total=db.jdbc.queryForObject("SELECT count(*)"+from+w,Long.class,args.toArray());
   args.add(size);args.add((long)page*size);
   return db.pageOf(db.rows("SELECT s.id,s.staff_number,s.given_name,s.family_name,s.status,s.classification,d.code AS department_code,p.title AS position_title,s.hire_date,s.version"+from+w+
    " ORDER BY s.family_name,s.given_name,s.id LIMIT ? OFFSET ?",args.toArray()),page,size,total);
  });
 }
 public Map<String,Object> get(Actor a,UUID id){
  a.require(Perm.STAFF_READ);
  return db.scoped(a,()->{
   Map<String,Object> s=new LinkedHashMap<>(staff(a,id));
   s.put("changes",db.rows("SELECT id,kind,effective_date,status,reason,requested_by,decided_by,applied_at FROM synqoria_hr.staff_changes WHERE staff_id=? ORDER BY created_at DESC LIMIT 50",id));
   s.put("history",db.rows("SELECT event_id,action,from_status,to_status,aggregate_version,actor_id,occurred_at FROM synqoria_hr.audit_events WHERE aggregate_type='STAFF' AND aggregate_id=? ORDER BY occurred_at,event_id",id));
   if(a.has(Perm.COMP_READ))
    s.put("contracts",db.rows("SELECT id,contract_version,employer_code,country_code,currency,pay_basis,base_amount,effective_from,effective_to,status,created_by,approved_by FROM synqoria_hr.contracts WHERE staff_id=? ORDER BY contract_version DESC",id));
   return s;
  });
 }

 // ---------------------------------------------------------------- lifecycle
 private Map<String,Object> lockStaff(UUID id,long expected){
  List<Map<String,Object>> r=db.rows("SELECT * FROM synqoria_hr.staff WHERE id=? FOR UPDATE",id);
  if(r.isEmpty())throw new Violation("not_found");
  if(((Number)r.getFirst().get("version")).longValue()!=expected)throw new Violation("stale_version");
  return r.getFirst();
 }
 public Reply requestChange(Actor a,UUID cmd,UUID staffId,ChangeRequest r){
  a.require(Perm.STAFF_MANAGE);
  return db.command(a,cmd,"CHANGE|"+staffId,r,()->{
   Map<String,Object> s=lockStaff(staffId,r.expectedVersion());
   ChangeKind kind=Domain.parse(ChangeKind.class,r.kind(),"kind");
   if(r.reason()==null||r.reason().isBlank()||r.reason().length()>300)throw new Violation("reason_required");
   StaffStatus st=StaffStatus.valueOf((String)s.get("status"));
   if(!st.canApply(kind))throw new Violation("invalid_transition");
   Domain.checkEffectiveDate(kind,r.effectiveDate(),LocalDate.parse((String)s.get("hireDate")),LocalDate.parse((String)s.get("lastEffectiveDate")),
    s.get("terminationDate")==null?null:LocalDate.parse((String)s.get("terminationDate")),today());
   if(kind==ChangeKind.TRANSFER&&(r.toDepartmentId()==null||r.toPositionId()==null||
      db.rows("SELECT 1 FROM synqoria_hr.positions WHERE id=? AND department_id=?",r.toPositionId(),r.toDepartmentId()).isEmpty()))throw new Violation("invalid_transfer");
   if(!db.rows("SELECT 1 FROM synqoria_hr.staff_changes WHERE staff_id=? AND status IN ('REQUESTED','SCHEDULED')",staffId).isEmpty())throw new Violation("duplicate_record");
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_hr.staff_changes(id,cell_id,facility_id,staff_id,kind,effective_date,to_department_id,to_position_id,reason,status,requested_by,version) VALUES (?,?,?,?,?,?,?,?,?,'REQUESTED',?,1)",
    id,a.cell(),a.facility(),staffId,kind.name(),Date.valueOf(r.effectiveDate()),r.toDepartmentId(),r.toPositionId(),r.reason().strip(),a.id());
   db.audit(a,cmd,"STAFFCHANGE",id,"REQUEST",null,"REQUESTED",1,m("kind",kind.name(),"staffId",staffId.toString()));
   return new Reply(201,db.one("SELECT * FROM synqoria_hr.staff_changes WHERE id=?",id));
  });
 }
 public Reply decideChange(Actor a,UUID cmd,UUID changeId,Decision d){
  a.require(Perm.STAFF_APPROVE);
  return db.command(a,cmd,"CHANGE_DECIDE|"+changeId,d,()->{
   List<Map<String,Object>> rows=db.rows("SELECT * FROM synqoria_hr.staff_changes WHERE id=? FOR UPDATE",changeId);
   if(rows.isEmpty())throw new Violation("not_found");
   Map<String,Object> c=rows.getFirst();
   if(((Number)c.get("version")).longValue()!=d.expectedVersion())throw new Violation("stale_version");
   if(!"REQUESTED".equals(c.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(c.get("requestedBy")))throw new Violation("approver_must_be_independent");
   if(!d.approve()){
    if(d.note()==null||d.note().isBlank())throw new Violation("reason_required");
    db.jdbc.update("UPDATE synqoria_hr.staff_changes SET status='REJECTED',decided_by=?,decided_at=clock_timestamp(),version=version+1 WHERE id=?",a.id(),changeId);
    db.audit(a,cmd,"STAFFCHANGE",changeId,"REJECT","REQUESTED","REJECTED",d.expectedVersion()+1,m("note",d.note()));
    return new Reply(200,db.one("SELECT * FROM synqoria_hr.staff_changes WHERE id=?",changeId));
   }
   boolean due=!LocalDate.parse((String)c.get("effectiveDate")).isAfter(today());
   db.jdbc.update("UPDATE synqoria_hr.staff_changes SET status='SCHEDULED',decided_by=?,decided_at=clock_timestamp(),version=version+1 WHERE id=?",a.id(),changeId);
   db.audit(a,cmd,"STAFFCHANGE",changeId,"APPROVE","REQUESTED","SCHEDULED",d.expectedVersion()+1,m());
   if(due)apply(a,cmd,changeId);
   return new Reply(200,db.one("SELECT * FROM synqoria_hr.staff_changes WHERE id=?",changeId));
  });
 }
 /** Applies one SCHEDULED change that has reached its effective date, atomically with the workforce record update. */
 private void apply(Actor a,UUID cmd,UUID changeId){
  Map<String,Object> c=db.one("SELECT * FROM synqoria_hr.staff_changes WHERE id=?",changeId);
  Map<String,Object> s=db.one("SELECT * FROM synqoria_hr.staff WHERE id=? FOR UPDATE",c.get("staffId"));
  ChangeKind kind=ChangeKind.valueOf((String)c.get("kind"));
  StaffStatus from=StaffStatus.valueOf((String)s.get("status"));
  if(!from.canApply(kind))throw new Violation("invalid_transition");
  StaffStatus to=from.after(kind);
  LocalDate eff=LocalDate.parse((String)c.get("effectiveDate"));
  UUID dep=kind==ChangeKind.TRANSFER?(UUID)c.get("toDepartmentId"):(UUID)s.get("departmentId");
  UUID pos=kind==ChangeKind.TRANSFER?(UUID)c.get("toPositionId"):(UUID)s.get("positionId");
  Date term=kind==ChangeKind.TERMINATE?Date.valueOf(eff):(kind==ChangeKind.REHIRE?null:(s.get("terminationDate")==null?null:Date.valueOf((String)s.get("terminationDate"))));
  long v=((Number)s.get("version")).longValue()+1;
  db.jdbc.update("UPDATE synqoria_hr.staff SET status=?,department_id=?,position_id=?,termination_date=?,last_effective_date=?,version=version+1,updated_at=clock_timestamp() WHERE id=?",
   to.name(),dep,pos,term,Date.valueOf(eff),s.get("id"));
  if(kind==ChangeKind.TERMINATE)
   db.jdbc.update("UPDATE synqoria_hr.contracts SET status='ENDED',effective_to=?,version=version+1 WHERE staff_id=? AND status='ACTIVE'",Date.valueOf(eff),s.get("id"));
  db.jdbc.update("UPDATE synqoria_hr.staff_changes SET status='APPLIED',applied_at=clock_timestamp(),version=version+1 WHERE id=?",changeId);
  db.audit(a,cmd,"STAFF",(UUID)s.get("id"),kind.name(),from.name(),to.name(),v,m("effectiveDate",eff.toString(),"changeId",changeId.toString()));
 }
 public Reply applyDue(Actor a,UUID cmd){
  a.require(Perm.STAFF_APPROVE);
  return db.command(a,cmd,"APPLY_DUE",m("day",today().toString()),()->{
   int n=0;
   for(Map<String,Object> c:db.rows("SELECT id FROM synqoria_hr.staff_changes WHERE status='SCHEDULED' AND effective_date<=? ORDER BY effective_date,id FOR UPDATE",Date.valueOf(today()))){
    apply(a,cmd,(UUID)c.get("id"));n++;
   }
   return new Reply(200,m("applied",n));
  });
 }
 public Map<String,Object> listChanges(Actor a,String status,int page,int size){
  a.require(Perm.STAFF_READ);
  if(page<0||size<1||size>100)throw new Violation("invalid_pagination");
  return db.scoped(a,()->{
   String w=status==null?"":" WHERE c.status=?";Object[] base=status==null?new Object[0]:new Object[]{status};
   String from=" FROM synqoria_hr.staff_changes c JOIN synqoria_hr.staff s ON s.id=c.staff_id";
   long total=db.jdbc.queryForObject("SELECT count(*)"+from+w,Long.class,base);
   Object[] args=Arrays.copyOf(base,base.length+2);args[base.length]=size;args[base.length+1]=(long)page*size;
   return db.pageOf(db.rows("SELECT c.id,c.staff_id,s.staff_number,s.family_name,c.kind,c.effective_date,c.status,c.reason,c.requested_by,c.decided_by,c.version"+from+w+" ORDER BY c.created_at DESC,c.id LIMIT ? OFFSET ?",args),page,size,total);
  });
 }

 // ---------------------------------------------------------------- contracts (compensation: COMP_READ / CONTRACT_*)
 public Reply draftContract(Actor a,UUID cmd,UUID staffId,ContractRequest r){
  a.require(Perm.CONTRACT_MANAGE);
  return db.command(a,cmd,"CONTRACT|"+staffId,r,()->{
   List<Map<String,Object>> s=db.rows("SELECT status FROM synqoria_hr.staff WHERE id=? FOR SHARE",staffId);
   if(s.isEmpty())throw new Violation("not_found");
   if("TERMINATED".equals(s.getFirst().get("status")))throw new Violation("invalid_transition");
   if(r==null||!Domain.validCode(r.employerCode())||r.countryCode()==null||!r.countryCode().matches("[A-Z]{2}")||r.currency()==null||!r.currency().matches("[A-Z]{3}")||
      !List.of("MONTHLY","HOURLY").contains(r.payBasis())||r.effectiveFrom()==null||r.positionId()==null||r.baseAmount()==null||!r.baseAmount().matches("\\d{1,14}(\\.\\d{1,4})?"))throw new Violation("invalid_contract");
   if(r.effectiveTo()!=null&&r.effectiveTo().isBefore(r.effectiveFrom()))throw new Violation("invalid_effective_window");
   if(db.rows("SELECT 1 FROM synqoria_hr.positions WHERE id=?",r.positionId()).isEmpty())throw new Violation("not_found");
   int next=db.jdbc.queryForObject("SELECT coalesce(max(contract_version),0)+1 FROM synqoria_hr.contracts WHERE staff_id=?",Integer.class,staffId);
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_hr.contracts(id,cell_id,facility_id,staff_id,contract_version,employer_code,country_code,currency,pay_basis,base_amount,position_id,effective_from,effective_to,status,created_by,version) "+
    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT',?,1)",id,a.cell(),a.facility(),staffId,next,r.employerCode(),r.countryCode(),r.currency(),r.payBasis(),new BigDecimal(r.baseAmount()),r.positionId(),
    Date.valueOf(r.effectiveFrom()),r.effectiveTo()==null?null:Date.valueOf(r.effectiveTo()),a.id());
   db.audit(a,cmd,"CONTRACT",id,"DRAFT",null,"DRAFT",1,m("staffId",staffId.toString(),"contractVersion",next,"currency",r.currency()));  // amount deliberately not in audit/outbox
   return new Reply(201,db.one("SELECT * FROM synqoria_hr.contracts WHERE id=?",id));
  });
 }
 public Reply decideContract(Actor a,UUID cmd,UUID id,Decision d){
  a.require(Perm.CONTRACT_APPROVE);
  return db.command(a,cmd,"CONTRACT_DECIDE|"+id,d,()->{
   List<Map<String,Object>> rows=db.rows("SELECT * FROM synqoria_hr.contracts WHERE id=? FOR UPDATE",id);
   if(rows.isEmpty())throw new Violation("not_found");
   Map<String,Object> c=rows.getFirst();
   if(((Number)c.get("version")).longValue()!=d.expectedVersion())throw new Violation("stale_version");
   if(!"DRAFT".equals(c.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(c.get("createdBy")))throw new Violation("approver_must_be_independent");
   if(!d.approve()){
    db.jdbc.update("UPDATE synqoria_hr.contracts SET status='REJECTED',version=version+1 WHERE id=?",id);
    db.audit(a,cmd,"CONTRACT",id,"REJECT","DRAFT","REJECTED",d.expectedVersion()+1,m());
   }else{
    for(Map<String,Object> old:db.rows("SELECT id,version FROM synqoria_hr.contracts WHERE staff_id=? AND status='ACTIVE' FOR UPDATE",c.get("staffId"))){
     db.jdbc.update("UPDATE synqoria_hr.contracts SET status='SUPERSEDED',effective_to=?,version=version+1 WHERE id=?",
      Date.valueOf(LocalDate.parse((String)c.get("effectiveFrom")).minusDays(1)),old.get("id"));
     db.audit(a,cmd,"CONTRACT",(UUID)old.get("id"),"SUPERSEDE","ACTIVE","SUPERSEDED",((Number)old.get("version")).longValue()+1,m("by",id.toString()));
    }
    db.jdbc.update("UPDATE synqoria_hr.contracts SET status='ACTIVE',approved_by=?,approved_at=clock_timestamp(),version=version+1 WHERE id=?",a.id(),id);
    db.audit(a,cmd,"CONTRACT",id,"ACTIVATE","DRAFT","ACTIVE",d.expectedVersion()+1,m());
   }
   return new Reply(200,db.one("SELECT * FROM synqoria_hr.contracts WHERE id=?",id));
  });
 }
 public Reply setPaymentAccount(Actor a,UUID cmd,UUID staffId,AccountRequest r){
  a.require(Perm.CONTRACT_MANAGE);
  return db.command(a,cmd,"ACCOUNT|"+staffId,r,()->{
   if(r==null||r.accountToken()==null||r.accountToken().length()<8||r.accountToken().length()>120||!r.accountToken().matches("[A-Za-z0-9._:/+=-]+")||
      r.accountMask()==null||!r.accountMask().matches("[*A-Za-z0-9 -]{4,20}"))throw new Violation("invalid_account");
   if(db.rows("SELECT 1 FROM synqoria_hr.staff WHERE id=?",staffId).isEmpty())throw new Violation("not_found");
   db.jdbc.update("INSERT INTO synqoria_hr.payment_accounts(cell_id,facility_id,staff_id,account_token,account_mask,set_by) VALUES (?,?,?,?,?,?) "+
    "ON CONFLICT (cell_id,facility_id,staff_id) DO UPDATE SET account_token=EXCLUDED.account_token,account_mask=EXCLUDED.account_mask,set_by=EXCLUDED.set_by,set_at=clock_timestamp()",
    a.cell(),a.facility(),staffId,r.accountToken(),r.accountMask(),a.id());
   db.audit(a,cmd,"STAFF",staffId,"SET_PAYMENT_ACCOUNT","SET","SET",1,m());   // token never audited
   return new Reply(200,m("staffId",staffId.toString(),"accountMask",r.accountMask()));
  });
 }
}
