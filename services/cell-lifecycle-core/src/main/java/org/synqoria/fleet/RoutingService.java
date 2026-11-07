package org.synqoria.fleet;

import static org.synqoria.fleet.Db.m;

import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.synqoria.fleet.Db.Reply;
import org.synqoria.fleet.domain.Domain;
import org.synqoria.fleet.domain.Domain.*;

/** Versioned authoritative routing directory. Decisions are signed by the control plane and FAIL CLOSED. */
@Service
public class RoutingService {
 final Db db;final String key;
 public RoutingService(Db db,@Value("${synqoria.fleet.routing-key}")String key){
  if(key==null||key.length()<32)throw new IllegalStateException("routing_key_not_configured");
  this.db=db;this.key=key;
 }
 public record RouteRequest(String routeKey,UUID cellId){}
 public record Switch(long expectedVersion,long expectedEpoch,UUID toCell,String reason){}

 static final String ROUTE="SELECT r.id,r.tenant_id,r.target_cell,c.cell_key AS target_cell_key,r.route_key,r.placement_epoch,r.version,r.updated_at FROM synqoria_fleet.routes r JOIN synqoria_fleet.cells c ON c.id=r.target_cell";
 long bumpDirectory(Actor a){
  return db.jdbc.queryForObject("INSERT INTO synqoria_fleet.directory_state(cell_id,facility_id,directory_version) VALUES (?,?,1) ON CONFLICT (cell_id,facility_id) DO UPDATE SET directory_version=synqoria_fleet.directory_state.directory_version+1 RETURNING directory_version",Long.class,a.cell(),a.facility());
 }
 long directoryVersion(Actor a){
  List<Long> v=db.jdbc.queryForList("SELECT directory_version FROM synqoria_fleet.directory_state WHERE cell_id=? AND facility_id=?",Long.class,a.cell(),a.facility());
  return v.isEmpty()?0:v.getFirst();
 }
 public Reply createRoute(Actor a,UUID cmd,RouteRequest r){
  a.require(Perm.ROUTE_MANAGE);FleetService.req(r);
  if(r.routeKey()==null||!r.routeKey().matches("[a-z0-9][a-z0-9.-]{2,119}")||r.cellId()==null)throw new Violation("invalid_route");
  return db.command(a,cmd,"ROUTE_CREATE",r,()->{
   Map<String,Object> c=db.rows("SELECT id,tenant_id,state,placement_epoch FROM synqoria_fleet.cells WHERE id=? FOR UPDATE",r.cellId()).stream().findFirst().orElseThrow(()->new Violation("not_found"));
   a.requireTenant((UUID)c.get("tenantId"));
   if(!"ACTIVE".equals(c.get("state")))throw new Violation("route_unavailable");
   UUID id=UUID.randomUUID();long epoch=((Number)c.get("placementEpoch")).longValue();
   db.jdbc.update("INSERT INTO synqoria_fleet.routes(id,cell_id,facility_id,tenant_id,target_cell,route_key,placement_epoch,version) VALUES (?,?,?,?,?,?,?,1)",id,a.cell(),a.facility(),c.get("tenantId"),r.cellId(),r.routeKey(),epoch);
   long dv=bumpDirectory(a);
   db.audit(a,cmd,"ROUTE",id,"CREATE",null,"ACTIVE",1,m("directoryVersion",dv));
   return new Reply(201,db.one(ROUTE+" WHERE r.id=?",id));
  });
 }
 /** Manual failover switch. The caller must present the epoch it saw; a stale epoch is refused. */
 public Reply switchRoute(Actor a,UUID cmd,UUID id,Switch s){
  a.require(Perm.ROUTE_MANAGE);FleetService.req(s);
  if(s.toCell()==null||s.reason()==null||s.reason().trim().length()<5)throw new Violation("reason_required");
  return db.command(a,cmd,"ROUTE_SWITCH|"+id,s,()->{
   Map<String,Object> rt=db.rows("SELECT * FROM synqoria_fleet.routes WHERE id=? FOR UPDATE",id).stream().findFirst().orElseThrow(()->new Violation("not_found"));
   a.requireTenant((UUID)rt.get("tenantId"));
   if(((Number)rt.get("version")).longValue()!=s.expectedVersion())throw new Violation("stale_version");
   if(((Number)rt.get("placementEpoch")).longValue()!=s.expectedEpoch())throw new Violation("stale_placement_epoch");
   if(db.jdbc.queryForObject("SELECT count(*) FROM synqoria_fleet.migrations WHERE route_id=? AND state NOT IN ('RETIRED','ROLLED_BACK','FAILED')",Long.class,id)>0)throw new Violation("route_in_migration");
   Map<String,Object> to=db.rows("SELECT id,tenant_id,state FROM synqoria_fleet.cells WHERE id=? FOR UPDATE",s.toCell()).stream().findFirst().orElseThrow(()->new Violation("not_found"));
   if(!to.get("tenantId").equals(rt.get("tenantId")))throw new Violation("tenant_scope_denied");        // a route can never cross tenants
   if(!"ACTIVE".equals(to.get("state")))throw new Violation("route_unavailable");
   long next=((Number)rt.get("placementEpoch")).longValue()+1;
   db.jdbc.update("UPDATE synqoria_fleet.routes SET target_cell=?,placement_epoch=?,version=version+1,updated_at=? WHERE id=?",s.toCell(),next,java.sql.Timestamp.from(db.clock.instant()),id);
   db.jdbc.update("UPDATE synqoria_fleet.cells SET placement_epoch=GREATEST(placement_epoch,?),version=version+1 WHERE id=?",next,s.toCell());
   long dv=bumpDirectory(a);
   db.audit(a,cmd,"ROUTE",id,"SWITCH","ACTIVE","ACTIVE",s.expectedVersion()+1,m("epoch",next,"directoryVersion",dv));
   return new Reply(200,db.one(ROUTE+" WHERE r.id=?",id));
  });
 }
 /**
  * Independently authenticated, live directory snapshot for a hospital gateway.
  * One scoped SQL statement observes target, epoch, directory version and health
  * at the same PostgreSQL statement snapshot. This is NOT a patient-access grant.
  */
 public Map<String,Object> routingFence(Actor a,String routeKey){
  a.require(Perm.ROUTE_READ);
  if(routeKey==null||!routeKey.matches("[a-z0-9][a-z0-9.-]{2,119}"))throw new Violation("invalid_route");
  return db.scoped(a,()->{
   Instant now=db.clock.instant();
   Map<String,Object> r=db.one("SELECT r.target_cell,r.placement_epoch,ds.directory_version,c.state,"+
    "h.status AS health,h.observed_at FROM synqoria_fleet.routes r "+
    "JOIN synqoria_fleet.cells c ON c.id=r.target_cell "+
    "JOIN synqoria_fleet.directory_state ds ON ds.cell_id=r.cell_id AND ds.facility_id=r.facility_id "+
    "LEFT JOIN LATERAL (SELECT status,observed_at FROM synqoria_fleet.cell_health x "+
    "WHERE x.target_cell=c.id ORDER BY observed_at DESC,recorded_at DESC,id DESC LIMIT 1) h ON true "+
    "WHERE r.route_key=?",routeKey);
   Instant observed=r.get("observedAt")==null?null:Instant.parse((String)r.get("observedAt"));
   if(!Domain.routable((String)r.get("state"),(String)r.get("health"),observed,now))
    throw new Violation("route_unavailable");
   return m("routeKey",routeKey,"cellId",r.get("targetCell").toString(),
    "placementEpoch",((Number)r.get("placementEpoch")).longValue(),
    "directoryVersion",((Number)r.get("directoryVersion")).longValue(),
    "cellState",r.get("state"),"health",r.get("health"),"observedAt",observed.toString());
  });
 }

 public Map<String,Object> routes(Actor a,int page,int size){
  a.require(Perm.FLEET_READ);FleetService.paging(page,size);
  return db.scoped(a,()->{
   long total=db.jdbc.queryForObject("SELECT count(*) FROM synqoria_fleet.routes",Long.class);
   Map<String,Object> p=db.pageOf(db.rows(ROUTE+" ORDER BY r.route_key LIMIT ? OFFSET ?",size,(long)page*size),page,size,total);
   p.put("directoryVersion",directoryVersion(a));
   return p;
  });
 }
 /** Health-aware lookup returning a signed, short-lived decision. Anything not provably routable is refused (fail closed). */
 public Map<String,Object> lookup(Actor a,String routeKey){
  a.require(Perm.ROUTE_READ);
  if(routeKey==null||!routeKey.matches("[a-z0-9][a-z0-9.-]{2,119}"))throw new Violation("invalid_route");
  return db.scoped(a,()->{
   Instant now=db.clock.instant();
   List<Map<String,Object>> rows=db.rows("SELECT r.target_cell,r.placement_epoch,c.state,h.status AS health,h.observed_at FROM synqoria_fleet.routes r JOIN synqoria_fleet.cells c ON c.id=r.target_cell "+
    "LEFT JOIN LATERAL (SELECT status,observed_at FROM synqoria_fleet.cell_health x WHERE x.target_cell=c.id ORDER BY observed_at DESC,recorded_at DESC,id DESC LIMIT 1) h ON true WHERE r.route_key=?",routeKey);
   if(rows.isEmpty())throw new Violation("not_found");
   Map<String,Object> r=rows.getFirst();
   Instant observed=r.get("observedAt")==null?null:Instant.parse((String)r.get("observedAt"));
   if(!Domain.routable((String)r.get("state"),(String)r.get("health"),observed,now))throw new Violation("route_unavailable");
   Decision d=new Decision(routeKey,(UUID)r.get("targetCell"),((Number)r.get("placementEpoch")).longValue(),directoryVersion(a),now,now.plusSeconds(Domain.DECISION_TTL_SECONDS));
   return m("routeKey",routeKey,"cellId",d.cellId().toString(),"placementEpoch",d.placementEpoch(),"directoryVersion",d.directoryVersion(),
    "issuedAt",d.issuedAt().getEpochSecond(),"expiresAt",d.expiresAt().getEpochSecond(),"health",r.get("health"),"signature",Domain.sign(key,d));
  });
 }
}
