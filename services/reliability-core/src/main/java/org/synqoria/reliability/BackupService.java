package org.synqoria.reliability;

import static org.synqoria.reliability.Db.m;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.synqoria.reliability.Db.Reply;
import org.synqoria.reliability.domain.Domain;
import org.synqoria.reliability.domain.Domain.*;

/** Backup catalogue and verification. Creation success never makes a backup VERIFIED; five independent checks do. */
@Service
public class BackupService {
 final Db db;
 public BackupService(Db db){this.db=db;}
 public record BackupRequest(String kind,String snapshotId,String databaseRef,Instant startedAt,Instant finishedAt,long walStart,long walEnd,List<String> objectRefs,long sizeBytes,String digest,String keyRef,Instant retentionUntil){}
 public record Check(String checkKey,String result,String evidenceRef,String observedDigest){}
 public record Verification(List<Check> checks){}

 static final String BK="SELECT id,kind,snapshot_id,database_ref,started_at,finished_at,wal_start,wal_end,object_refs,size_bytes,digest,key_ref,retention_until,status,recorded_by,verified_by,version,created_at FROM synqoria_reliability.backups";
 public Reply record(Actor a,UUID cmd,BackupRequest r){
  a.require(Perm.BACKUP_RECORD);ObservabilityService.req(r);
  if(!Set.of("FULL","INCREMENTAL","WAL_ARCHIVE").contains(r.kind())||r.snapshotId()==null||r.snapshotId().isBlank()||r.databaseRef()==null||r.databaseRef().isBlank()||r.startedAt()==null||r.finishedAt()==null||
     r.finishedAt().isBefore(r.startedAt())||r.walStart()<0||r.walEnd()<r.walStart()||r.sizeBytes()<=0||r.digest()==null||!r.digest().matches("[0-9a-f]{64}")||r.keyRef()==null||r.keyRef().isBlank()||
     r.retentionUntil()==null||!r.retentionUntil().isAfter(r.finishedAt())||(r.objectRefs()!=null&&r.objectRefs().size()>100))throw new Violation("invalid_backup");
  return db.command(a,cmd,"BACKUP_RECORD",r,()->{
   UUID id=UUID.randomUUID();
   db.jdbc.update(c->{
    var ps=c.prepareStatement("INSERT INTO synqoria_reliability.backups(id,cell_id,facility_id,kind,snapshot_id,database_ref,started_at,finished_at,wal_start,wal_end,object_refs,size_bytes,digest,key_ref,retention_until,status,recorded_by,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'RECORDED',?,1)");
    ps.setObject(1,id);ps.setObject(2,a.cell());ps.setObject(3,a.facility());ps.setString(4,r.kind());ps.setString(5,r.snapshotId().trim());ps.setString(6,r.databaseRef().trim());
    ps.setTimestamp(7,Timestamp.from(r.startedAt()));ps.setTimestamp(8,Timestamp.from(r.finishedAt()));ps.setLong(9,r.walStart());ps.setLong(10,r.walEnd());
    ps.setArray(11,c.createArrayOf("text",(r.objectRefs()==null?List.<String>of():r.objectRefs()).toArray()));ps.setLong(12,r.sizeBytes());ps.setString(13,r.digest());ps.setString(14,r.keyRef().trim());
    ps.setTimestamp(15,Timestamp.from(r.retentionUntil()));ps.setObject(16,a.id());return ps;});
   db.audit(a,cmd,"BACKUP",id,"RECORD",null,"RECORDED",1,m("kind",r.kind()));
   return new Reply(201,view(id));
  });
 }
 Map<String,Object> view(UUID id){
  Map<String,Object> b=new LinkedHashMap<>(db.one(BK+" WHERE id=?",id));
  b.put("verification",db.rows("SELECT check_key,result,evidence_ref,observed_digest,verified_by,at FROM synqoria_reliability.backup_verifications WHERE backup_id=? ORDER BY check_key",id));
  b.put("expired",Instant.parse((String)b.get("retentionUntil")).isBefore(db.clock.instant()));
  return b;
 }
 public Reply verify(Actor a,UUID cmd,UUID id,Verification v){
  a.require(Perm.BACKUP_VERIFY);ObservabilityService.req(v);
  if(v.checks()==null||v.checks().size()!=Domain.BACKUP_CHECKS.size()||!new HashSet<>(v.checks().stream().map(c->c==null?"":c.checkKey()).toList()).equals(new HashSet<>(Domain.BACKUP_CHECKS)))throw new Violation("invalid_verification");
  for(Check c:v.checks())if(!Set.of("PASS","FAIL").contains(c.result())||c.evidenceRef()==null||c.evidenceRef().isBlank()||c.evidenceRef().length()>160||(c.observedDigest()!=null&&!c.observedDigest().matches("[0-9a-f]{64}")))throw new Violation("invalid_verification");
  return db.command(a,cmd,"BACKUP_VERIFY|"+id,v,()->{
   Map<String,Object> b=db.rows("SELECT * FROM synqoria_reliability.backups WHERE id=? FOR UPDATE",id).stream().findFirst().orElseThrow(()->new Violation("not_found"));
   if(!"RECORDED".equals(b.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(b.get("recordedBy")))throw new Violation("verifier_must_be_independent");
   boolean all=true;
   for(Check c:v.checks()){
    String result=c.result();
    if(c.checkKey().equals("INTEGRITY")){                      // a digest recomputed from the stored artifact must equal the recorded digest; the client cannot declare integrity
     if(c.observedDigest()==null||!c.observedDigest().equals(String.valueOf(b.get("digest")).trim()))result="FAIL";
    }
    if(!result.equals("PASS"))all=false;
    db.jdbc.update("INSERT INTO synqoria_reliability.backup_verifications(id,cell_id,facility_id,backup_id,check_key,result,evidence_ref,observed_digest,verified_by) VALUES (?,?,?,?,?,?,?,?,?)",
     UUID.randomUUID(),a.cell(),a.facility(),id,c.checkKey(),result,c.evidenceRef().trim(),c.observedDigest(),a.id());
   }
   String to=all?"VERIFIED":"VERIFICATION_FAILED";
   db.jdbc.update("UPDATE synqoria_reliability.backups SET status=?,verified_by=?,version=version+1 WHERE id=?",to,a.id(),id);
   db.audit(a,cmd,"BACKUP",id,all?"VERIFY":"VERIFY_FAILED","RECORDED",to,((Number)b.get("version")).longValue()+1,m());
   return new Reply(200,view(id));
  });
 }
 public Map<String,Object> list(Actor a,String kind,String status,int page,int size){
  a.require(Perm.BACKUP_READ);ObservabilityService.paging(page,size);
  if(kind!=null&&!Set.of("FULL","INCREMENTAL","WAL_ARCHIVE").contains(kind))throw new Violation("invalid_status");
  if(status!=null&&!Set.of("RECORDED","VERIFIED","VERIFICATION_FAILED").contains(status))throw new Violation("invalid_status");
  return db.scoped(a,()->{
   StringBuilder w=new StringBuilder(" WHERE true");List<Object> args=new ArrayList<>();
   if(kind!=null){w.append(" AND kind=?");args.add(kind);}
   if(status!=null){w.append(" AND status=?");args.add(status);}
   long total=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_reliability.backups"+w,Long.class,args.toArray());
   args.add(size);args.add((long)page*size);
   Instant now=db.clock.instant();
   List<Map<String,Object>> items=new ArrayList<>();
   for(Map<String,Object> r:db.rows(BK+w+" ORDER BY finished_at DESC,id LIMIT ? OFFSET ?",args.toArray())){
    Map<String,Object> x=new LinkedHashMap<>(r);x.put("expired",Instant.parse((String)r.get("retentionUntil")).isBefore(now));items.add(x);
   }
   return db.pageOf(items,page,size,total);
  });
 }
 public Map<String,Object> get(Actor a,UUID id){a.require(Perm.BACKUP_READ);return db.scoped(a,()->view(id));}

 /** WAL continuity from a base backup to a target position over the VERIFIED archive ranges. */
 public Map<String,Object> continuity(Actor a,UUID baseId,long target){
  a.require(Perm.BACKUP_READ);
  if(target<0)throw new Violation("invalid_query");
  return db.scoped(a,()->continuityIn(baseId,target));
 }
 Map<String,Object> continuityIn(UUID baseId,long target){
  Map<String,Object> base=db.one("SELECT kind,status,wal_end FROM synqoria_reliability.backups WHERE id=?",baseId);
  if(!"FULL".equals(base.get("kind")))throw new Violation("invalid_query");
  List<WalRange> ranges=new ArrayList<>();
  for(Map<String,Object> r:db.rows("SELECT wal_start,wal_end FROM synqoria_reliability.backups WHERE kind IN ('WAL_ARCHIVE','INCREMENTAL') AND status='VERIFIED' AND retention_until>?",Timestamp.from(db.clock.instant())))
   ranges.add(new WalRange(((Number)r.get("walStart")).longValue(),((Number)r.get("walEnd")).longValue()));
  Continuity c=Domain.walContinuity(((Number)base.get("walEnd")).longValue(),target,ranges);
  return m("baseBackupId",baseId.toString(),"baseVerified","VERIFIED".equals(base.get("status")),"targetLsn",target,"continuous",c.continuous(),"coveredUpTo",c.coveredUpTo(),"gapStart",c.gapStart(),"gapEnd",c.gapEnd());
 }
}
