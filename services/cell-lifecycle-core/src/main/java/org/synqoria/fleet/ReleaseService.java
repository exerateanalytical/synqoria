package org.synqoria.fleet;

import static org.synqoria.fleet.Db.m;

import java.util.*;
import org.springframework.stereotype.Service;
import org.synqoria.fleet.Db.Reply;
import org.synqoria.fleet.domain.Domain;
import org.synqoria.fleet.domain.Domain.*;

/** Release manifest governance and canary rollouts with evidence, compatibility gates and automatic stop conditions. */
@Service
public class ReleaseService {
 final Db db;
 public ReleaseService(Db db){this.db=db;}
 public record ManifestRequest(String releaseKey,String artifactDigest,int minSchema,int maxSchema){}
 public record Decision(long expectedVersion,boolean approve){}
 public record RolloutRequest(UUID manifestId,List<List<UUID>> stages){}
 public record Evidence(String status,String evidenceRef,String artifactDigest){}
 public record Advance(long expectedVersion){}
 public record Stop(long expectedVersion,String reason){}

 static final String MAN="SELECT id,release_key,artifact_digest,min_schema,max_schema,status,created_by,approved_by,version,created_at FROM synqoria_fleet.release_manifests";
 Map<String,Object> manifest(UUID id){return db.one(MAN+" WHERE id=?",id);}
 public Reply createManifest(Actor a,UUID cmd,ManifestRequest r){
  a.require(Perm.RELEASE_MANAGE);a.requirePlatformWide();FleetService.req(r);
  if(r.releaseKey()==null||!r.releaseKey().matches("[A-Za-z0-9][A-Za-z0-9._-]{1,39}")||r.artifactDigest()==null||!r.artifactDigest().matches("[0-9a-f]{64}")||r.minSchema()<1||r.maxSchema()<r.minSchema())throw new Violation("invalid_release");
  return db.command(a,cmd,"MANIFEST_CREATE",r,()->{
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_fleet.release_manifests(id,cell_id,facility_id,release_key,artifact_digest,min_schema,max_schema,status,created_by,version) VALUES (?,?,?,?,?,?,?,'DRAFT',?,1)",
    id,a.cell(),a.facility(),r.releaseKey(),r.artifactDigest(),r.minSchema(),r.maxSchema(),a.id());
   db.audit(a,cmd,"MANIFEST",id,"CREATE",null,"DRAFT",1,m("release",r.releaseKey()));
   return new Reply(201,manifest(id));
  });
 }
 public Reply decideManifest(Actor a,UUID cmd,UUID id,Decision d){
  a.require(Perm.RELEASE_APPROVE);a.requirePlatformWide();FleetService.req(d);
  return db.command(a,cmd,"MANIFEST_DECIDE|"+id,d,()->{
   Map<String,Object> mf=db.rows("SELECT * FROM synqoria_fleet.release_manifests WHERE id=? FOR UPDATE",id).stream().findFirst().orElseThrow(()->new Violation("not_found"));
   if(((Number)mf.get("version")).longValue()!=d.expectedVersion())throw new Violation("stale_version");
   if(!"DRAFT".equals(mf.get("status")))throw new Violation("invalid_transition");
   if(a.id().equals(mf.get("createdBy")))throw new Violation("approver_must_be_independent");
   String to=d.approve()?"APPROVED":"REJECTED";
   db.jdbc.update("UPDATE synqoria_fleet.release_manifests SET status=?,approved_by=?,version=version+1 WHERE id=?",to,a.id(),id);
   db.audit(a,cmd,"MANIFEST",id,d.approve()?"APPROVE":"REJECT","DRAFT",to,d.expectedVersion()+1,m());
   return new Reply(200,manifest(id));
  });
 }
 public Map<String,Object> manifests(Actor a,int page,int size){
  a.require(Perm.FLEET_READ);a.requirePlatformWide();FleetService.paging(page,size);
  return db.scoped(a,()->{
   long total=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_fleet.release_manifests",Long.class);
   return db.pageOf(db.rows(MAN+" ORDER BY created_at DESC,id LIMIT ? OFFSET ?",size,(long)page*size),page,size,total);
  });
 }

 // ------------------------------------------------------------------ rollouts
 static final String ROLL="SELECT r.id,r.manifest_id,m.release_key,m.artifact_digest,r.status,r.current_stage,r.stage_count,r.stop_reason,r.started_by,r.version,r.created_at FROM synqoria_fleet.rollouts r JOIN synqoria_fleet.release_manifests m ON m.id=r.manifest_id";
 Map<String,Object> rollout(UUID id){
  Map<String,Object> r=new LinkedHashMap<>(db.one(ROLL+" WHERE r.id=?",id));
  r.put("cells",db.rows("SELECT rc.target_cell,c.cell_key,c.schema_version,rc.stage_no,rc.status,rc.evidence_ref,rc.deployed_at,"+
   "h.status AS health,h.observed_at AS health_observed_at FROM synqoria_fleet.rollout_cells rc JOIN synqoria_fleet.cells c ON c.id=rc.target_cell "+
   "LEFT JOIN LATERAL (SELECT status,observed_at FROM synqoria_fleet.cell_health x WHERE x.target_cell=c.id ORDER BY observed_at DESC LIMIT 1) h ON true WHERE rc.rollout_id=? ORDER BY rc.stage_no,c.cell_key",id));
  return r;
 }
 public Reply startRollout(Actor a,UUID cmd,RolloutRequest r){
  a.require(Perm.ROLLOUT_OPERATE);a.requirePlatformWide();FleetService.req(r);
  if(r.manifestId()==null||r.stages()==null||r.stages().isEmpty()||r.stages().size()>10||r.stages().stream().anyMatch(s->s==null||s.isEmpty()||s.size()>200))throw new Violation("invalid_rollout");
  return db.command(a,cmd,"ROLLOUT_START",r,()->{
   Map<String,Object> mf=db.rows("SELECT * FROM synqoria_fleet.release_manifests WHERE id=? FOR SHARE",r.manifestId()).stream().findFirst().orElseThrow(()->new Violation("not_found"));
   if(!"APPROVED".equals(mf.get("status")))throw new Violation("invalid_transition");
   Set<UUID> seen=new HashSet<>();List<String> incompatible=new ArrayList<>();
   for(List<UUID> stage:r.stages())for(UUID c:stage){
    if(!seen.add(c))throw new Violation("invalid_rollout");
    Map<String,Object> cell=db.rows("SELECT id,state,schema_version,cell_key FROM synqoria_fleet.cells WHERE id=?",c).stream().findFirst().orElseThrow(()->new Violation("not_found"));
    if(!"ACTIVE".equals(cell.get("state")))throw new Violation("route_unavailable");
    if(!Domain.schemaCompatible(((Number)cell.get("schemaVersion")).intValue(),((Number)mf.get("minSchema")).intValue(),((Number)mf.get("maxSchema")).intValue()))incompatible.add((String)cell.get("cellKey"));
   }
   if(!incompatible.isEmpty())throw new Violation("incompatible_release",incompatible);          // compatibility gate: nothing is started
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_fleet.rollouts(id,cell_id,facility_id,manifest_id,status,current_stage,stage_count,started_by,version) VALUES (?,?,?,?,'CANARY',0,?,?,1)",id,a.cell(),a.facility(),r.manifestId(),r.stages().size(),a.id());
   for(int s=0;s<r.stages().size();s++)for(UUID c:r.stages().get(s)){
    UUID tenant=db.jdbc.queryForObject("SELECT tenant_id FROM synqoria_fleet.cells WHERE id=?",UUID.class,c);
    db.jdbc.update("INSERT INTO synqoria_fleet.rollout_cells(id,cell_id,facility_id,tenant_id,rollout_id,target_cell,stage_no,status) VALUES (?,?,?,?,?,?,?,'PENDING')",UUID.randomUUID(),a.cell(),a.facility(),tenant,id,c,s);
   }
   db.audit(a,cmd,"ROLLOUT",id,"START",null,"CANARY",1,m("release",mf.get("releaseKey")));
   return new Reply(201,rollout(id));
  });
 }
 /** Evidence, not assertion: DEPLOYED requires the cell's reported artifact digest to equal the approved manifest digest. */
 public Reply recordEvidence(Actor a,UUID cmd,UUID rolloutId,UUID cellRow,Evidence e){
  a.require(Perm.ROLLOUT_OPERATE);a.requirePlatformWide();FleetService.req(e);
  if(!Set.of("DEPLOYED","FAILED").contains(e.status())||e.evidenceRef()==null||e.evidenceRef().isBlank()||e.evidenceRef().length()>160||e.artifactDigest()==null||!e.artifactDigest().matches("[0-9a-f]{64}"))throw new Violation("invalid_evidence");
  return db.command(a,cmd,"ROLLOUT_EVIDENCE|"+rolloutId+"|"+cellRow,e,()->{
   Map<String,Object> ro=lock(rolloutId);
   if(!Set.of("CANARY","PROGRESSING").contains(ro.get("status")))throw new Violation("rollout_stopped");
   Map<String,Object> rc=db.rows("SELECT * FROM synqoria_fleet.rollout_cells WHERE rollout_id=? AND target_cell=? FOR UPDATE",rolloutId,cellRow).stream().findFirst().orElseThrow(()->new Violation("not_found"));
   if(((Number)rc.get("stageNo")).intValue()!=((Number)ro.get("currentStage")).intValue())throw new Violation("stage_not_open");
   if(!"PENDING".equals(rc.get("status")))throw new Violation("invalid_transition");
   Map<String,Object> mf=db.one("SELECT release_key,artifact_digest FROM synqoria_fleet.release_manifests WHERE id=?",ro.get("manifestId"));
   if("DEPLOYED".equals(e.status())){
    if(!e.artifactDigest().equals(String.valueOf(mf.get("artifactDigest")).trim()))throw new Violation("evidence_mismatch");
    db.jdbc.update("UPDATE synqoria_fleet.rollout_cells SET status='DEPLOYED',evidence_ref=?,evidence_digest=?,deployed_at=? WHERE id=?",e.evidenceRef().trim(),e.artifactDigest(),java.sql.Timestamp.from(db.clock.instant()),rc.get("id"));
    db.jdbc.update("UPDATE synqoria_fleet.cells SET release_key=?,version=version+1 WHERE id=?",mf.get("releaseKey"),cellRow);
    db.audit(a,cmd,"ROLLOUT",rolloutId,"DEPLOYED",(String)ro.get("status"),(String)ro.get("status"),((Number)ro.get("version")).longValue(),m("cell",cellRow.toString()));
   }else{
    db.jdbc.update("UPDATE synqoria_fleet.rollout_cells SET status='FAILED',evidence_ref=? WHERE id=?",e.evidenceRef().trim(),rc.get("id"));
    stopRow(a,cmd,rolloutId,ro,"automatic stop: deployment failed on a cell");
   }
   return new Reply(200,rollout(rolloutId));
  });
 }
 Map<String,Object> lock(UUID id){return db.rows("SELECT * FROM synqoria_fleet.rollouts WHERE id=? FOR UPDATE",id).stream().findFirst().orElseThrow(()->new Violation("not_found"));}
 void stopRow(Actor a,UUID cmd,UUID id,Map<String,Object> ro,String reason){
  db.jdbc.update("UPDATE synqoria_fleet.rollouts SET status='STOPPED',stop_reason=?,version=version+1 WHERE id=?",reason,id);
  db.audit(a,cmd,"ROLLOUT",id,"STOP",(String)ro.get("status"),"STOPPED",((Number)ro.get("version")).longValue()+1,m());
 }
 /** Moves to the next stage only when every cell of the current stage is DEPLOYED and observed HEALTHY after its deployment. */
 public Reply advance(Actor a,UUID cmd,UUID id,Advance ad){
  a.require(Perm.ROLLOUT_OPERATE);a.requirePlatformWide();FleetService.req(ad);
  return db.command(a,cmd,"ROLLOUT_ADVANCE|"+id,ad,()->{
   Map<String,Object> ro=lock(id);
   if(((Number)ro.get("version")).longValue()!=ad.expectedVersion())throw new Violation("stale_version");
   if(!Set.of("CANARY","PROGRESSING").contains(ro.get("status")))throw new Violation("rollout_stopped");
   int stage=((Number)ro.get("currentStage")).intValue();
   List<Map<String,Object>> cells=db.rows("SELECT rc.status,rc.deployed_at,h.status AS health FROM synqoria_fleet.rollout_cells rc "+
    "LEFT JOIN LATERAL (SELECT status FROM synqoria_fleet.cell_health x WHERE x.target_cell=rc.target_cell AND x.observed_at>=rc.deployed_at ORDER BY observed_at DESC LIMIT 1) h ON true WHERE rc.rollout_id=? AND rc.stage_no=?",id,stage);
   if(!Domain.stageComplete(cells.stream().map(c->(String)c.get("status")).toList()))throw new Violation("stage_incomplete");
   if(!cells.stream().allMatch(c->"HEALTHY".equals(c.get("health"))))throw new Violation("health_gate_failed");
   boolean last=stage+1>=((Number)ro.get("stageCount")).intValue();
   if(last)db.jdbc.update("UPDATE synqoria_fleet.rollouts SET status='COMPLETED',version=version+1 WHERE id=?",id);
   else db.jdbc.update("UPDATE synqoria_fleet.rollouts SET status='PROGRESSING',current_stage=?,version=version+1 WHERE id=?",stage+1,id);
   db.audit(a,cmd,"ROLLOUT",id,last?"COMPLETE":"ADVANCE",(String)ro.get("status"),last?"COMPLETED":"PROGRESSING",ad.expectedVersion()+1,m("stage",stage));
   return new Reply(200,rollout(id));
  });
 }
 public Reply stop(Actor a,UUID cmd,UUID id,Stop s){
  a.require(Perm.ROLLOUT_OPERATE);a.requirePlatformWide();FleetService.req(s);
  if(s.reason()==null||s.reason().trim().length()<5)throw new Violation("reason_required");
  return db.command(a,cmd,"ROLLOUT_STOP|"+id,s,()->{
   Map<String,Object> ro=lock(id);
   if(((Number)ro.get("version")).longValue()!=s.expectedVersion())throw new Violation("stale_version");
   if(!Set.of("CANARY","PROGRESSING").contains(ro.get("status")))throw new Violation("invalid_transition");
   stopRow(a,cmd,id,ro,s.reason().trim());
   return new Reply(200,rollout(id));
  });
 }
 /** Called inside the health-report transaction: an unhealthy deployed cell stops its rollout. Returns the stopped rollout id or null. */
 String autoStopIfUnhealthy(Actor a,UUID cmd,UUID cellRow,String status){
  if(!"UNHEALTHY".equals(status))return null;
  List<Map<String,Object>> open=db.rows("SELECT r.* FROM synqoria_fleet.rollouts r JOIN synqoria_fleet.rollout_cells rc ON rc.rollout_id=r.id WHERE rc.target_cell=? AND rc.status='DEPLOYED' AND r.status IN ('CANARY','PROGRESSING') FOR UPDATE OF r",cellRow);
  if(open.isEmpty())return null;
  Map<String,Object> ro=open.getFirst();
  stopRow(a,cmd,(UUID)ro.get("id"),ro,"automatic stop: a deployed cell reported UNHEALTHY");
  return String.valueOf(ro.get("id"));
 }
 public Map<String,Object> rollouts(Actor a,int page,int size){
  a.require(Perm.FLEET_READ);a.requirePlatformWide();FleetService.paging(page,size);
  return db.scoped(a,()->{
   long total=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_fleet.rollouts",Long.class);
   return db.pageOf(db.rows(ROLL+" ORDER BY r.created_at DESC,r.id LIMIT ? OFFSET ?",size,(long)page*size),page,size,total);
  });
 }
 public Map<String,Object> rolloutDetail(Actor a,UUID id){a.require(Perm.FLEET_READ);a.requirePlatformWide();return db.scoped(a,()->rollout(id));}
}
