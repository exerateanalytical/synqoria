package org.synqoria.fleet;

import static org.synqoria.fleet.Db.m;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.synqoria.fleet.Db.Reply;
import org.synqoria.fleet.domain.Domain;
import org.synqoria.fleet.domain.Domain.*;

/**
 * Controlled cell migration: prepare, copy, catch up, freeze, fence, verify, switch, observe, retire, with rollback.
 * This service records and governs the state machine and its evidence; the data copy itself is performed by an external executor.
 */
@Service
public class MigrationService {
 final Db db;final RoutingService routing;
 public MigrationService(Db db,RoutingService routing){this.db=db;this.routing=routing;}
 public record Plan(UUID sourceCell,UUID targetCell,UUID routeId){}
 public record StepRequest(long expectedVersion,String step,String evidence,String sourceDigest,String targetDigest,Long lagMs,Long expectedEpoch){}
 public record Rollback(long expectedVersion,String reason,Long expectedEpoch){}

 static final String MIG="SELECT g.id,g.tenant_id,g.source_cell,s.cell_key AS source_key,g.target_cell,t.cell_key AS target_key,g.route_id,r.route_key,r.placement_epoch AS route_epoch,g.state,g.source_digest,g.target_digest,"+
  "g.copy_evidence,g.catchup_lag_ms,g.fence_evidence,g.switch_epoch,g.switch_approved_by,g.rollback_reason,g.planned_by,g.version,g.created_at,g.updated_at,"+
  "s.state AS source_state,t.state AS target_state FROM synqoria_fleet.migrations g JOIN synqoria_fleet.cells s ON s.id=g.source_cell JOIN synqoria_fleet.cells t ON t.id=g.target_cell JOIN synqoria_fleet.routes r ON r.id=g.route_id";
 Map<String,Object> view(UUID id){return db.one(MIG+" WHERE g.id=?",id);}
 public Reply plan(Actor a,UUID cmd,Plan p){
  a.require(Perm.MIGRATION_PLAN);a.requirePlatformWide();FleetService.req(p);
  if(p.sourceCell()==null||p.targetCell()==null||p.routeId()==null||p.sourceCell().equals(p.targetCell()))throw new Violation("invalid_migration");
  return db.command(a,cmd,"MIGRATION_PLAN",p,()->{
   Map<String,Object> src=cell(p.sourceCell()),tgt=cell(p.targetCell());
   Map<String,Object> rt=db.rows("SELECT * FROM synqoria_fleet.routes WHERE id=? FOR UPDATE",p.routeId()).stream().findFirst().orElseThrow(()->new Violation("not_found"));
   if(!src.get("tenantId").equals(tgt.get("tenantId"))||!rt.get("tenantId").equals(src.get("tenantId")))throw new Violation("tenant_scope_denied");
   if(!"ACTIVE".equals(src.get("state"))||!p.sourceCell().equals(rt.get("targetCell")))throw new Violation("invalid_transition");
   if(!"QUALIFIED".equals(tgt.get("state")))throw new Violation("not_qualified");
   String residency=db.jdbc.queryForObject("SELECT residency_policy FROM synqoria_fleet.tenants WHERE id=?",String.class,src.get("tenantId"));
   if("IN_REGION_ONLY".equals(residency)&&!Objects.equals(src.get("region"),tgt.get("region")))throw new Violation("residency_violation");
   UUID id=UUID.randomUUID();
   db.jdbc.update("INSERT INTO synqoria_fleet.migrations(id,cell_id,facility_id,tenant_id,source_cell,target_cell,route_id,state,planned_by,version) VALUES (?,?,?,?,?,?,?,'PREPARED',?,1)",
    id,a.cell(),a.facility(),src.get("tenantId"),p.sourceCell(),p.targetCell(),p.routeId(),a.id());             // unique index: one open migration per route
   db.audit(a,cmd,"MIGRATION",id,"PLAN",null,"PREPARED",1,m());
   return new Reply(201,view(id));
  });
 }
 Map<String,Object> cell(UUID id){return db.rows("SELECT * FROM synqoria_fleet.cells WHERE id=? FOR UPDATE",id).stream().findFirst().orElseThrow(()->new Violation("not_found"));}
 Map<String,Object> lock(UUID id,long expected){
  Map<String,Object> g=db.rows("SELECT * FROM synqoria_fleet.migrations WHERE id=? FOR UPDATE",id).stream().findFirst().orElseThrow(()->new Violation("not_found"));
  if(((Number)g.get("version")).longValue()!=expected)throw new Violation("stale_version");
  return g;
 }
 void cellState(UUID id,String to,String reason){
  db.jdbc.update("UPDATE synqoria_fleet.cells SET state=?,state_reason=?,version=version+1 WHERE id=?",to,reason,id);
 }
 public Reply step(Actor a,UUID cmd,UUID id,StepRequest r){
  FleetService.req(r);
  if(r.step()==null||!Domain.STEPS.contains(r.step()))throw new Violation("invalid_step");
  a.require("SWITCH".equals(r.step())?Perm.MIGRATION_APPROVE:Perm.MIGRATION_OPERATE);a.requirePlatformWide();
  return db.command(a,cmd,"MIGRATION_STEP|"+id,r,()->{
   Map<String,Object> g=lock(id,r.expectedVersion());
   if(!Domain.stepFrom(r.step()).equals(g.get("state")))throw new Violation("invalid_transition");
   UUID src=(UUID)g.get("sourceCell"),tgt=(UUID)g.get("targetCell");
   String to=Domain.stepTo(r.step());
   String set="state=?";List<Object> args=new ArrayList<>();args.add(to);
   switch(r.step()){
    case "COPY"->{need(r.evidence());set+=",copy_evidence=?";args.add(r.evidence().trim());}
    case "CATCH_UP"->{
     if(r.lagMs()==null||r.lagMs()<0)throw new Violation("invalid_evidence");
     if(r.lagMs()>Domain.MAX_CATCHUP_LAG_MS)throw new Violation("lag_too_high");
     set+=",catchup_lag_ms=?";args.add(r.lagMs());}
    case "FREEZE"->cellState(src,"DRAINING","migration freeze");
    case "FENCE"->{need(r.evidence());cellState(src,"FENCED","migration fence");set+=",fence_evidence=?";args.add(r.evidence().trim());}
    case "VERIFY"->{
     if(!validDigest(r.sourceDigest())||!validDigest(r.targetDigest()))throw new Violation("invalid_evidence");
     if(!r.sourceDigest().equals(r.targetDigest()))throw new Violation("verification_failed");           // nothing is switched on a mismatch
     cellState(src,"MIGRATING","migration verified");set+=",source_digest=?,target_digest=?";args.add(r.sourceDigest());args.add(r.targetDigest());}
    case "SWITCH"->{
     if(a.id().equals(g.get("plannedBy")))throw new Violation("approver_must_be_independent");
     Map<String,Object> rt=db.rows("SELECT * FROM synqoria_fleet.routes WHERE id=? FOR UPDATE",g.get("routeId")).getFirst();
     if(r.expectedEpoch()==null||r.expectedEpoch()!=((Number)rt.get("placementEpoch")).longValue())throw new Violation("stale_placement_epoch");
     long next=r.expectedEpoch()+1;
     Map<String,Object> t=cell(tgt);
     if(!"QUALIFIED".equals(t.get("state")))throw new Violation("not_qualified");
     db.jdbc.update("UPDATE synqoria_fleet.cells SET state='ACTIVE',state_reason='migration switch',placement_epoch=?,version=version+1 WHERE id=?",next,tgt);
     db.jdbc.update("UPDATE synqoria_fleet.routes SET target_cell=?,placement_epoch=?,version=version+1,updated_at=? WHERE id=?",tgt,next,java.sql.Timestamp.from(db.clock.instant()),g.get("routeId"));
     routing.bumpDirectory(a);
     set+=",switch_epoch=?,switch_approved_by=?";args.add(next);args.add(a.id());}
    case "OBSERVE"->{}
    case "RETIRE"->{
     Map<String,Object> rt=db.one("SELECT updated_at FROM synqoria_fleet.routes WHERE id=?",g.get("routeId"));
     List<Map<String,Object>> h=db.rows("SELECT status FROM synqoria_fleet.cell_health WHERE target_cell=? AND observed_at>=? ORDER BY observed_at DESC LIMIT 1",tgt,java.sql.Timestamp.from(Instant.parse((String)rt.get("updatedAt"))));
     if(h.isEmpty()||!"HEALTHY".equals(h.getFirst().get("status")))throw new Violation("health_gate_failed");     // observe: the target must prove itself before the source goes
     cellState(src,"RETIRED","migration retire");}
    default->throw new Violation("invalid_step");
   }
   args.add(id);
   db.jdbc.update("UPDATE synqoria_fleet.migrations SET "+set+",version=version+1,updated_at=clock_timestamp() WHERE id=?",args.toArray());
   db.audit(a,cmd,"MIGRATION",id,r.step(),(String)g.get("state"),to,r.expectedVersion()+1,m());
   return new Reply(200,view(id));
  });
 }
 static void need(String e){if(e==null||e.isBlank()||e.length()>160)throw new Violation("evidence_required");}
 static boolean validDigest(String d){return d!=null&&d.matches("[0-9a-f]{64}");}

 /** Rollback until the source is retired. After the switch it reverses the route with a new epoch and needs an independent approver; after RETIRE it is impossible. */
 public Reply rollback(Actor a,UUID cmd,UUID id,Rollback r){
  FleetService.req(r);
  if(r.reason()==null||r.reason().trim().length()<5)throw new Violation("reason_required");
  a.requirePlatformWide();
  return db.command(a,cmd,"MIGRATION_ROLLBACK|"+id,r,()->{
   Map<String,Object> g=lock(id,r.expectedVersion());
   String state=(String)g.get("state");
   if(!Domain.rollbackPossible(state))throw new Violation("irreversible");
   boolean switched=Domain.rollbackNeedsApproval(state);
   a.require(switched?Perm.MIGRATION_APPROVE:Perm.MIGRATION_OPERATE);
   if(switched&&a.id().equals(g.get("plannedBy")))throw new Violation("approver_must_be_independent");
   UUID src=(UUID)g.get("sourceCell"),tgt=(UUID)g.get("targetCell");
   Map<String,Object> s=cell(src);
   if(switched){
    Map<String,Object> rt=db.rows("SELECT * FROM synqoria_fleet.routes WHERE id=? FOR UPDATE",g.get("routeId")).getFirst();
    if(r.expectedEpoch()==null||r.expectedEpoch()!=((Number)rt.get("placementEpoch")).longValue())throw new Violation("stale_placement_epoch");
    long next=r.expectedEpoch()+1;
    cellState(src,"ACTIVE","migration rollback");                                  // MIGRATING -> ACTIVE
    cellState(tgt,"FENCED","migration rollback");                                  // the target stops serving
    db.jdbc.update("UPDATE synqoria_fleet.cells SET placement_epoch=GREATEST(placement_epoch,?) WHERE id=?",next,src);
    db.jdbc.update("UPDATE synqoria_fleet.routes SET target_cell=?,placement_epoch=?,version=version+1,updated_at=? WHERE id=?",src,next,java.sql.Timestamp.from(db.clock.instant()),g.get("routeId"));
    routing.bumpDirectory(a);
   }else if(Set.of("DRAINING","FENCED","MIGRATING").contains(s.get("state")))cellState(src,"ACTIVE","migration rollback");
   db.jdbc.update("UPDATE synqoria_fleet.migrations SET state='ROLLED_BACK',rollback_reason=?,version=version+1,updated_at=clock_timestamp() WHERE id=?",r.reason().trim(),id);
   db.audit(a,cmd,"MIGRATION",id,"ROLLBACK",state,"ROLLED_BACK",r.expectedVersion()+1,m());
   return new Reply(200,view(id));
  });
 }
 public Map<String,Object> list(Actor a,int page,int size){
  a.require(Perm.FLEET_READ);FleetService.paging(page,size);
  return db.scoped(a,()->{
   long total=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_fleet.migrations",Long.class);
   return db.pageOf(db.rows(MIG+" ORDER BY g.created_at DESC,g.id LIMIT ? OFFSET ?",size,(long)page*size),page,size,total);
  });
 }
 public Map<String,Object> get(Actor a,UUID id){a.require(Perm.FLEET_READ);return db.scoped(a,()->view(id));}
}
