package org.synqoria.offline;

import static org.synqoria.offline.Db.m;

import java.util.*;
import org.springframework.stereotype.Service;
import org.synqoria.offline.Db.Reply;
import org.synqoria.offline.domain.Domain.*;

/**
 * Ownership transfer of one record between cells: propose, freeze, positive fencing evidence, independent approval, complete.
 * The epoch rises by exactly one at completion. Until then the destination cannot write and the source is frozen.
 */
@Service
public class TransferService {
 final Db db;final SyncService sync;
 public TransferService(Db db,SyncService sync){this.db=db;this.sync=sync;}

 public record Propose(UUID headId,UUID toOwner,String reason){}
 public record Step(long expectedVersion){}
 public record Fence(long expectedVersion,String evidenceRef,long lastSourceVersion){}
 public record Decide(long expectedVersion,boolean approve,String reason){}
 public record Abort(long expectedVersion,String reason){}

 static final String VIEW="SELECT t.id,t.head_id,h.record_type,h.record_key,t.from_owner,t.to_owner,t.from_epoch,t.status,t.reason,t.fence_evidence_ref,t.fence_last_version,t.fenced_by,t.proposed_by,t.approved_by,"+
  "t.completed_epoch,t.abort_reason,t.version,t.created_at,t.updated_at,h.head_version AS current_head_version,h.ownership_epoch AS current_epoch,h.frozen "+
  "FROM synqoria_offline.ownership_transfers t JOIN synqoria_offline.record_heads h ON h.id=t.head_id";
 Map<String,Object> view(UUID id){return db.one(VIEW+" WHERE t.id=?",id);}
 Map<String,Object> lock(UUID id,long expected){
  Map<String,Object> t=db.rows("SELECT * FROM synqoria_offline.ownership_transfers WHERE id=? FOR UPDATE",id).stream().findFirst().orElseThrow(()->new Violation("not_found"));
  if(((Number)t.get("version")).longValue()!=expected)throw new Violation("stale_version");
  return t;
 }
 void move(Actor a,UUID cmd,UUID id,Map<String,Object> t,String to,String action,String extraSql,Object... extra){
  Object[] args=new Object[extra.length+2];System.arraycopy(extra,0,args,0,extra.length);args[extra.length]=to;args[extra.length+1]=id;
  db.jdbc.update("UPDATE synqoria_offline.ownership_transfers SET "+extraSql+(extraSql.isEmpty()?"":",")+"status=?,version=version+1,updated_at=clock_timestamp() WHERE id=?",args);
  db.audit(a,cmd,"TRANSFER",id,action,(String)t.get("status"),to,((Number)t.get("version")).longValue()+1,m());
 }

 public Reply propose(Actor a,UUID cmd,Propose p){
  a.require(Perm.TRANSFER_PROPOSE);SyncService.req(p);
  if(p.headId()==null||p.toOwner()==null||p.reason()==null||p.reason().trim().length()<5)throw new Violation("invalid_transfer");
  return db.command(a,cmd,"TRANSFER_PROPOSE",p,()->{
   Map<String,Object> h=sync.lockHead(p.headId());
   if(p.toOwner().equals(h.get("ownerRef")))throw new Violation("already_owner");
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_offline.ownership_transfers(id,cell_id,facility_id,head_id,from_owner,to_owner,from_epoch,status,reason,proposed_by,version) VALUES (?,?,?,?,?,?,?,'PROPOSED',?,?,1)",
    id,a.cell(),a.facility(),p.headId(),h.get("ownerRef"),p.toOwner(),h.get("ownershipEpoch"),p.reason().trim(),a.id());   // unique index => one open transfer per record
   db.audit(a,cmd,"TRANSFER",id,"PROPOSE",null,"PROPOSED",1,m());
   return new Reply(201,view(id));
  });
 }
 /** Freezing stops every writer (offline commands answer record_frozen) before any evidence is collected. */
 public Reply freeze(Actor a,UUID cmd,UUID id,Step s){
  a.require(Perm.TRANSFER_OPERATE);SyncService.req(s);
  return db.command(a,cmd,"TRANSFER_FREEZE|"+id,s,()->{
   Map<String,Object> t=lock(id,s.expectedVersion());
   if(!"PROPOSED".equals(t.get("status")))throw new Violation("invalid_transition");
   Map<String,Object> h=sync.lockHead((UUID)t.get("headId"));
   if(((Number)h.get("ownershipEpoch")).longValue()!=((Number)t.get("fromEpoch")).longValue())throw new Violation("epoch_changed");
   db.jdbc.update("UPDATE synqoria_offline.record_heads SET frozen=true,version=version+1 WHERE id=?",t.get("headId"));
   move(a,cmd,id,t,"FROZEN","FREEZE","");
   return new Reply(200,view(id));
  });
 }
 /** Positive evidence: the source confirms its last version, and it must equal the authoritative head. Absence of evidence never fences. */
 public Reply fence(Actor a,UUID cmd,UUID id,Fence f){
  a.require(Perm.TRANSFER_OPERATE);SyncService.req(f);
  if(f.evidenceRef()==null||f.evidenceRef().isBlank()||f.evidenceRef().length()>160)throw new Violation("evidence_required");
  return db.command(a,cmd,"TRANSFER_FENCE|"+id,f,()->{
   Map<String,Object> t=lock(id,f.expectedVersion());
   if(!"FROZEN".equals(t.get("status")))throw new Violation("invalid_transition");
   Map<String,Object> h=sync.lockHead((UUID)t.get("headId"));
   if(f.lastSourceVersion()!=((Number)h.get("headVersion")).longValue())throw new Violation("fence_version_mismatch");
   move(a,cmd,id,t,"FENCED","FENCE","fence_evidence_ref=?,fence_last_version=?,fenced_by=?",f.evidenceRef().trim(),f.lastSourceVersion(),a.id());
   return new Reply(200,view(id));
  });
 }
 public Reply decide(Actor a,UUID cmd,UUID id,Decide d){
  a.require(Perm.TRANSFER_APPROVE);SyncService.req(d);
  if(!d.approve()&&(d.reason()==null||d.reason().trim().length()<5))throw new Violation("reason_required");
  return db.command(a,cmd,"TRANSFER_DECIDE|"+id,d,()->{
   Map<String,Object> t=lock(id,d.expectedVersion());
   if(!"FENCED".equals(t.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(t.get("proposedBy")))throw new Violation("approver_must_be_independent");
   if(d.approve())move(a,cmd,id,t,"APPROVED","APPROVE","approved_by=?",a.id());
   else{abort(a,cmd,id,t,d.reason());}
   return new Reply(200,view(id));
  });
 }
 void abort(Actor a,UUID cmd,UUID id,Map<String,Object> t,String reason){
  db.jdbc.update("UPDATE synqoria_offline.record_heads SET frozen=false,version=version+1 WHERE id=? AND frozen",t.get("headId"));
  move(a,cmd,id,t,"ABORTED","ABORT","abort_reason=?",reason.trim());
 }
 public Reply abort(Actor a,UUID cmd,UUID id,Abort r){
  a.require(Perm.TRANSFER_OPERATE);SyncService.req(r);
  if(r.reason()==null||r.reason().trim().length()<5)throw new Violation("reason_required");
  return db.command(a,cmd,"TRANSFER_ABORT|"+id,r,()->{
   Map<String,Object> t=lock(id,r.expectedVersion());
   if(Set.of("COMPLETED","ABORTED").contains(t.get("status")))throw new Violation("invalid_transition");
   sync.lockHead((UUID)t.get("headId"));
   abort(a,cmd,id,t,r.reason());
   return new Reply(200,view(id));
  });
 }
 /** Completion is the only place ownership changes: owner switches and the epoch rises by exactly one, atomically. */
 public Reply complete(Actor a,UUID cmd,UUID id,Step s){
  a.require(Perm.TRANSFER_OPERATE);SyncService.req(s);
  return db.command(a,cmd,"TRANSFER_COMPLETE|"+id,s,()->{
   Map<String,Object> t=lock(id,s.expectedVersion());
   if(!"APPROVED".equals(t.get("status")))throw new Violation("invalid_transition");
   Map<String,Object> h=sync.lockHead((UUID)t.get("headId"));
   long epoch=((Number)h.get("ownershipEpoch")).longValue();
   if(epoch!=((Number)t.get("fromEpoch")).longValue()||!h.get("ownerRef").equals(t.get("fromOwner")))throw new Violation("epoch_changed");
   if(((Number)h.get("headVersion")).longValue()!=((Number)t.get("fenceLastVersion")).longValue())throw new Violation("fence_version_mismatch");
   db.jdbc.update("UPDATE synqoria_offline.record_heads SET owner_ref=?,ownership_epoch=?,frozen=false,version=version+1,updated_at=clock_timestamp() WHERE id=?",t.get("toOwner"),epoch+1,t.get("headId"));
   move(a,cmd,id,t,"COMPLETED","COMPLETE","completed_epoch=?",epoch+1);
   sync.recon(a,null,id,(UUID)t.get("headId"),"OWNERSHIP_TRANSFERRED",a.id(),m("transferId",id.toString(),"fromEpoch",epoch,"evidence",t.get("fenceEvidenceRef")),((Number)h.get("headVersion")).longValue(),epoch+1);
   return new Reply(200,view(id));
  });
 }
 public Map<String,Object> list(Actor a,String status,int page,int size){
  a.require(Perm.SYNC_READ);
  if(page<0||size<1||size>100)throw new Violation("invalid_pagination");
  if(status!=null&&!Set.of("PROPOSED","FROZEN","FENCED","APPROVED","COMPLETED","ABORTED").contains(status))throw new Violation("invalid_status");
  return db.scoped(a,()->{
   String w=status==null?"":" WHERE t.status=?";Object[] base=status==null?new Object[0]:new Object[]{status};
   long total=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_offline.ownership_transfers t"+w,Long.class,base);
   Object[] args=Arrays.copyOf(base,base.length+2);args[base.length]=size;args[base.length+1]=(long)page*size;
   return db.pageOf(db.rows(VIEW+w+" ORDER BY t.created_at DESC,t.id LIMIT ? OFFSET ?",args),page,size,total);
  });
 }
}
