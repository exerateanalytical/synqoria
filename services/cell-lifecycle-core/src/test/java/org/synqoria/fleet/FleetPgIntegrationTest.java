package org.synqoria.fleet;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.synqoria.fleet.Db.Reply;
import org.synqoria.fleet.domain.Domain;
import org.synqoria.fleet.domain.Domain.*;
import tools.jackson.databind.json.JsonMapper;

/** Real PostgreSQL integration (disposable 18.x, restricted runtime role, forced RLS). Opt-in: SYNQORIA_FLEET_PG_TEST=1. No real infrastructure is touched: executors are simulated by test input. */
class FleetPgIntegrationTest {
 static String url,adminUrl,adminUser,adminPassword,runtimeUser="synqoria_fleet_runtime",runtimePassword="m39-ci-only";
 static final String ROUTING_KEY="r".repeat(48);
 static class MutableClock extends Clock {
  volatile Instant now=Instant.parse("2026-11-07T06:00:00Z");
  public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now;}
 }
 MutableClock clock;Db db;ReleaseService releases;FleetService fleet;RoutingService routing;MigrationService migrations;JdbcTemplate admin;DataSource runtimeDs;
 final UUID plane=UUID.randomUUID(),unit=UUID.randomUUID();
 Actor ops,approver,gateway;

 static Actor actor(UUID plane,UUID unit,Set<UUID> tenants,String... p){return new Actor(UUID.randomUUID(),plane,unit,Set.of(p),tenants);}
 @BeforeAll static void env(){
  assumeTrue("1".equals(System.getenv("SYNQORIA_FLEET_PG_TEST")),"set SYNQORIA_FLEET_PG_TEST=1 with disposable PostgreSQL");
  url=System.getenv("SYNQORIA_FLEET_TEST_JDBC_URL");adminUrl=System.getenv().getOrDefault("SYNQORIA_FLEET_TEST_ADMIN_JDBC_URL",url);
  adminUser=System.getenv("SYNQORIA_FLEET_TEST_ADMIN_USER");adminPassword=System.getenv("SYNQORIA_FLEET_TEST_ADMIN_PASSWORD");
 }
 @BeforeEach void setUp()throws Exception{
  DriverManagerDataSource ads=new DriverManagerDataSource(adminUrl,adminUser,adminPassword);admin=new JdbcTemplate(ads);
  admin.execute("DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_fleet_runtime') THEN CREATE ROLE synqoria_fleet_runtime LOGIN NOINHERIT NOSUPERUSER NOCREATEROLE NOCREATEDB NOREPLICATION NOBYPASSRLS PASSWORD '"+runtimePassword+"'; END IF; END $$");
  admin.execute("DROP SCHEMA IF EXISTS synqoria_fleet CASCADE");
  try(Connection c=ads.getConnection();Statement s=c.createStatement()){s.execute(Files.readString(Path.of("db","migration","V001__m39_fleet.sql")));}
  DriverManagerDataSource rds=new DriverManagerDataSource(url,runtimeUser,runtimePassword);runtimeDs=rds;
  clock=new MutableClock();
  db=new Db(new JdbcTemplate(rds),JsonMapper.builder().build(),new DataSourceTransactionManager(rds),clock,"synqoria_fleet_runtime","UTC");
  releases=new ReleaseService(db);fleet=new FleetService(db,releases);routing=new RoutingService(db,ROUTING_KEY);migrations=new MigrationService(db,routing);
  ops=actor(plane,unit,Set.of(),Perm.TENANT_REQUEST,Perm.CELL_OPERATE,Perm.QUALIFY_RECORD,Perm.HEALTH_REPORT,Perm.ROUTE_MANAGE,Perm.FLEET_READ,Perm.RELEASE_MANAGE,Perm.ROLLOUT_OPERATE,Perm.MIGRATION_PLAN,Perm.MIGRATION_OPERATE);
  approver=actor(plane,unit,Set.of(),Perm.TENANT_APPROVE,Perm.QUALIFY_COMPLETE,Perm.RELEASE_APPROVE,Perm.MIGRATION_APPROVE,Perm.FLEET_READ,Perm.ROUTE_READ);
  gateway=actor(plane,unit,Set.of(),Perm.ROUTE_READ);
 }
 static UUID k(){return UUID.randomUUID();}
 @SuppressWarnings("unchecked") Map<String,Object> body(Reply r){return (Map<String,Object>)r.body();}
 Reply ok(Reply r){assertTrue(r.status()<300,r.status()+" "+r.body());return r;}
 static UUID id(Map<String,Object> m){return m.get("id") instanceof UUID u?u:UUID.fromString((String)m.get("id"));}
 static long ver(Map<String,Object> m){return ((Number)m.get("version")).longValue();}
 String code(Runnable r){try{r.run();return "NO_ERROR";}catch(Violation v){return v.code();}}
 long count(String sql,Object...a){return admin.queryForObject(sql,Long.class,a);}
 String state(UUID cell){return admin.queryForObject("SELECT state FROM synqoria_fleet.cells WHERE id=?",String.class,cell);}
 long epoch(UUID route){return count("SELECT placement_epoch FROM synqoria_fleet.routes WHERE id=?",route);}

 FleetService.ProvisionRequest prov(String key){
  return new FleetService.ProvisionRequest("req-"+key,key,"Synthetic Hospital "+key,"eu-west","STANDARD",20,List.of("clinical-core","operations"),"DEDICATED_DATABASE","IN_REGION_ONLY",Map.of("undo","drop-synthetic"));
 }
 /** request -> approve -> apply(OK); returns [tenantId, cellId] with the cell PROVISIONING */
 UUID[] provision(String key){
  Map<String,Object> p=body(ok(fleet.requestTenant(ops,k(),prov(key))));
  p=body(ok(fleet.decideProvisioning(approver,k(),id(p),new FleetService.Decision(ver(p),true,null))));
  p=body(ok(fleet.applyProvisioning(ops,k(),id(p),new FleetService.ApplyRequest(ver(p),"OK",null))));
  UUID tenant=UUID.fromString(String.valueOf(p.get("tenantId")));
  UUID cell=admin.queryForObject("SELECT id FROM synqoria_fleet.cells WHERE tenant_id=?",UUID.class,tenant);
  return new UUID[]{tenant,cell};
 }
 void qualify(UUID cell){
  Map<String,Object> q=body(ok(fleet.startQualification(ops,k(),cell)));
  for(String c:Domain.QUALIFICATION_CHECKS)q=body(ok(fleet.recordCheck(ops,k(),id(q),new FleetService.CheckRequest(c,"PASS","ev-"+c,null))));
  ok(fleet.completeQualification(approver,k(),id(q),new FleetService.Complete(ver(q))));
 }
 void activate(UUID cell){
  Map<String,Object> c=fleet.cell(ops,cell);
  ok(fleet.transition(ops,k(),cell,new FleetService.Transition(ver(c),"ACTIVE","go live after qualification")));
 }
 void healthy(UUID cell){ok(fleet.reportHealth(ops,k(),cell,new FleetService.HealthReport("HEALTHY",clock.instant(),null)));}
 UUID[] liveCell(String key){UUID[] t=provision(key);qualify(t[1]);activate(t[1]);healthy(t[1]);return t;}
 UUID route(String rk,UUID cell){return id(body(ok(routing.createRoute(ops,k(),new RoutingService.RouteRequest(rk,cell)))));}

 // ------------------------------------------------------------------ B02 provisioning
 @Test void provisioningIsMakerCheckerIdempotentAndDeduplicated(){
  Map<String,Object> p=body(ok(fleet.requestTenant(ops,k(),prov("acme"))));
  assertEquals("approver_must_be_independent",code(()->fleet.decideProvisioning(new Actor(ops.id(),plane,unit,Set.of(Perm.TENANT_APPROVE),Set.of()),k(),id(p),new FleetService.Decision(ver(p),true,null))));
  assertEquals("permission_denied",code(()->fleet.decideProvisioning(ops,k(),id(p),new FleetService.Decision(ver(p),true,null))));
  UUID key=k();
  Reply a=fleet.requestTenant(ops,key,prov("beta")),b=fleet.requestTenant(ops,key,prov("beta"));
  assertEquals(id(body(a)),id(body(b)),"an idempotent replay returns the same request");
  assertThrows(DataAccessException.class,()->fleet.requestTenant(ops,k(),prov("beta")),"a second request for the same tenant key is a duplicate");
  assertEquals("idempotency_collision",code(()->fleet.requestTenant(ops,key,prov("gamma"))));
  assertEquals(2L,count("SELECT count(*) FROM synqoria_fleet.provisioning_requests"));
  FleetService.ProvisionRequest bad=new FleetService.ProvisionRequest("req-bad","bad-one","X","eu-west","STANDARD",20,List.of("a-b"),"SHARED_SCHEMA","IN_REGION_ONLY",Map.of("u","1"));
  assertEquals("invalid_provisioning",code(()->fleet.requestTenant(ops,k(),bad)));            // shared storage is not an option
 }
 @Test void executorFailureCreatesNothingAndRollbackIsRefusedOnceActive(){
  Map<String,Object> p=body(ok(fleet.requestTenant(ops,k(),prov("acme"))));
  p=body(ok(fleet.decideProvisioning(approver,k(),id(p),new FleetService.Decision(ver(p),true,null))));
  p=body(ok(fleet.applyProvisioning(ops,k(),id(p),new FleetService.ApplyRequest(ver(p),"FAILED","database_unreachable"))));
  assertEquals("FAILED",p.get("status"));assertEquals(0L,count("SELECT count(*) FROM synqoria_fleet.tenants"));assertEquals(0L,count("SELECT count(*) FROM synqoria_fleet.cells"));
  Map<String,Object> q=body(ok(fleet.requestTenant(ops,k(),prov("beta"))));
  q=body(ok(fleet.decideProvisioning(approver,k(),id(q),new FleetService.Decision(ver(q),true,null))));
  q=body(ok(fleet.applyProvisioning(ops,k(),id(q),new FleetService.ApplyRequest(ver(q),"OK",null))));
  Map<String,Object> rb=body(ok(fleet.rollbackProvisioning(ops,k(),id(q),new FleetService.RollbackRequest(ver(q),"Customer cancelled before go-live"))));
  assertEquals("ROLLED_BACK",rb.get("status"));assertEquals("RETIRED",admin.queryForObject("SELECT state FROM synqoria_fleet.cells",String.class));
  UUID[] live=liveCell("gamma");
  Map<String,Object> g=fleet.provisioningList(ops,"APPLIED",0,10).get("items") instanceof List<?> l?(Map<String,Object>)l.getFirst():null;
  assertEquals("irreversible",code(()->fleet.rollbackProvisioning(ops,k(),id(g),new FleetService.RollbackRequest(ver(g),"Too late to roll back"))));
  assertEquals("ACTIVE",state(live[1]));
 }

 // ------------------------------------------------------------------ B03 isolation
 @Test void tenantScopedOperatorsSeeOnlyTheirTenantAndPlatformOperationsAreDenied(){
  UUID[] a=liveCell("acme"),b=liveCell("beta");
  Actor scoped=actor(plane,unit,Set.of(a[0]),Perm.FLEET_READ,Perm.ROUTE_READ,Perm.ROUTE_MANAGE,Perm.CELL_OPERATE,Perm.QUALIFY_RECORD,Perm.RELEASE_MANAGE,Perm.TENANT_REQUEST);
  @SuppressWarnings("unchecked") List<Map<String,Object>> cells=(List<Map<String,Object>>)fleet.cells(scoped,null,null,0,50).get("items");
  assertEquals(1,cells.size());assertEquals("acme-prod",cells.getFirst().get("cellKey"));
  assertEquals("not_found",code(()->fleet.cell(scoped,b[1])));
  assertEquals(1,((List<?>)fleet.tenants(scoped,0,10).get("items")).size());
  assertEquals("tenant_scope_denied",code(()->fleet.requestTenant(scoped,k(),prov("evil"))));
  assertEquals("tenant_scope_denied",code(()->releases.createManifest(scoped,k(),new ReleaseService.ManifestRequest("r1","a".repeat(64),1,2))));
  assertEquals("not_found",code(()->routing.createRoute(scoped,k(),new RoutingService.RouteRequest("beta.example",b[1]))));
  assertEquals("not_found",code(()->fleet.transition(scoped,k(),b[1],new FleetService.Transition(1,"DRAINING","trying another tenant"))));
  Actor otherPlane=actor(UUID.randomUUID(),UUID.randomUUID(),Set.of(),Perm.FLEET_READ);
  assertEquals(0,((List<?>)fleet.cells(otherPlane,null,null,0,10).get("items")).size());       // a different management plane sees nothing
 }
 @Test void databaseNeverLetsTwoCellsShareADatabaseOrCredential(){
  UUID[] a=provision("acme");
  assertThrows(DataAccessException.class,()->admin.update("INSERT INTO synqoria_fleet.cells(id,cell_id,facility_id,tenant_id,cell_key,environment,region,tier,capacity_units,state,database_ref,credential_ref,created_by,version) "+
   "SELECT gen_random_uuid(),cell_id,facility_id,tenant_id,'acme-dr','DR',region,tier,capacity_units,'PROVISIONING',database_ref,'cred/other',created_by,1 FROM synqoria_fleet.cells WHERE id=?",a[1]));
  assertThrows(DataAccessException.class,()->admin.update("INSERT INTO synqoria_fleet.cells(id,cell_id,facility_id,tenant_id,cell_key,environment,region,tier,capacity_units,state,database_ref,credential_ref,created_by,version) "+
   "SELECT gen_random_uuid(),cell_id,facility_id,tenant_id,'acme-dr','DR',region,tier,capacity_units,'PROVISIONING','db/other',credential_ref,created_by,1 FROM synqoria_fleet.cells WHERE id=?",a[1]));
 }

 // ------------------------------------------------------------------ B04/B05 qualification and lifecycle
 @Test void qualificationNeedsAllEightPassingChecksAndAnIndependentCompleter(){
  UUID[] t=provision("acme");
  Map<String,Object> q=body(ok(fleet.startQualification(ops,k(),t[1])));
  assertEquals("duplicate_record",code(()->fleet.startQualification(ops,k(),t[1])));
  q=body(ok(fleet.recordCheck(ops,k(),id(q),new FleetService.CheckRequest("DATABASE","PASS","ev-1",null))));
  Map<String,Object> open=q;
  assertEquals("qualification_incomplete",code(()->fleet.completeQualification(approver,k(),id(open),new FleetService.Complete(ver(open)))));
  assertEquals("invalid_check",code(()->fleet.recordCheck(ops,k(),id(open),new FleetService.CheckRequest("DATABASE","PASS","",null))));
  assertThrows(DataAccessException.class,()->fleet.recordCheck(ops,k(),id(open),new FleetService.CheckRequest("DATABASE","PASS","ev-again",null)));    // one result per check
  for(String c:Domain.QUALIFICATION_CHECKS.subList(1,8))q=body(ok(fleet.recordCheck(ops,k(),id(q),new FleetService.CheckRequest(c,"PASS","ev-"+c,null))));
  Map<String,Object> full=q;
  assertEquals("completer_must_be_independent",code(()->fleet.completeQualification(new Actor(ops.id(),plane,unit,Set.of(Perm.QUALIFY_COMPLETE),Set.of()),k(),id(full),new FleetService.Complete(ver(full)))));
  assertEquals("invalid_transition",code(()->activate(t[1])));                                       // cannot go live before qualifying
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_fleet.cells SET state='ACTIVE' WHERE id=?",t[1]));
  ok(fleet.completeQualification(approver,k(),id(full),new FleetService.Complete(ver(full))));
  assertEquals("QUALIFIED",state(t[1]));
  activate(t[1]);assertEquals("ACTIVE",state(t[1]));
 }
 @Test void failedChecksFailTheCellAndTheDatabaseRefusesAFalsePass(){
  UUID[] t=provision("acme");
  Map<String,Object> q=body(ok(fleet.startQualification(ops,k(),t[1])));
  for(String c:Domain.QUALIFICATION_CHECKS)q=body(ok(fleet.recordCheck(ops,k(),id(q),new FleetService.CheckRequest(c,"BACKUP".equals(c)?"FAIL":"PASS","ev-"+c,null))));
  Map<String,Object> done=body(ok(fleet.completeQualification(approver,k(),id(q),new FleetService.Complete(ver(q)))));
  assertEquals("FAILED",done.get("status"));assertEquals("FAILED",state(t[1]));
  UUID[] u=provision("beta");Map<String,Object> q2=body(ok(fleet.startQualification(ops,k(),u[1])));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_fleet.qualifications SET status='PASSED',completed_by=gen_random_uuid(),completed_at=now() WHERE id=?",id(q2)));
 }
 @Test void lifecycleTransitionsFollowTheTableEvenInTheDatabase(){
  UUID[] t=liveCell("acme");
  Map<String,Object> c0=fleet.cell(ops,t[1]);Map<String,Object> c=c0;
  assertEquals("reason_required",code(()->fleet.transition(ops,k(),t[1],new FleetService.Transition(ver(c0),"DRAINING","no"))));
  assertEquals("invalid_transition",code(()->fleet.transition(ops,k(),t[1],new FleetService.Transition(ver(c0),"RETIRED","skip the drain and retire"))));
  assertEquals("invalid_transition",code(()->fleet.transition(ops,k(),t[1],new FleetService.Transition(ver(c0),"MIGRATING","manual migrate is not allowed"))));
  assertEquals("stale_version",code(()->fleet.transition(ops,k(),t[1],new FleetService.Transition(99,"DRAINING","stale version attempt"))));
  c=body(ok(fleet.transition(ops,k(),t[1],new FleetService.Transition(ver(c),"DRAINING","maintenance window"))));
  c=body(ok(fleet.transition(ops,k(),t[1],new FleetService.Transition(ver(c),"FENCED","isolating the cell"))));
  assertEquals("FENCED",c.get("state"));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_fleet.cells SET state='QUALIFIED' WHERE id=?",t[1]));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_fleet.cells SET database_ref='db/stolen' WHERE id=?",t[1]));
 }

 // ------------------------------------------------------------------ B06 routing
 @SuppressWarnings("unchecked") Decision decision(Map<String,Object> m){
  return new Decision((String)m.get("routeKey"),UUID.fromString((String)m.get("cellId")),((Number)m.get("placementEpoch")).longValue(),((Number)m.get("directoryVersion")).longValue(),
   Instant.ofEpochSecond(((Number)m.get("issuedAt")).longValue()),Instant.ofEpochSecond(((Number)m.get("expiresAt")).longValue()));
 }
 @Test void routingLookupIsSignedHealthAwareAndFailsClosed(){
  UUID[] t=liveCell("acme");route("acme.prod",t[1]);
  Map<String,Object> r=routing.lookup(gateway,"acme.prod");
  assertTrue(Domain.verify(ROUTING_KEY,decision(r),(String)r.get("signature"),clock.instant()));
  assertEquals(1L,((Number)r.get("placementEpoch")).longValue());assertEquals(1L,((Number)r.get("directoryVersion")).longValue());
  assertEquals("not_found",code(()->routing.lookup(gateway,"missing.prod")));
  assertEquals("permission_denied",code(()->routing.lookup(ops,"acme.prod")));
  clock.now=clock.now.plusSeconds(1);ok(fleet.reportHealth(ops,k(),t[1],new FleetService.HealthReport("UNHEALTHY",clock.instant(),null)));
  assertEquals("route_unavailable",code(()->routing.lookup(gateway,"acme.prod")));                       // unhealthy: refuse
  clock.now=clock.now.plusSeconds(5);healthy(t[1]);assertDoesNotThrow(()->routing.lookup(gateway,"acme.prod"));
  clock.now=clock.now.plusSeconds(Domain.HEALTH_MAX_AGE_SECONDS+10);
  assertEquals("route_unavailable",code(()->routing.lookup(gateway,"acme.prod")));                       // stale health: refuse
  healthy(t[1]);assertDoesNotThrow(()->routing.lookup(gateway,"acme.prod"));
  Map<String,Object> c=fleet.cell(ops,t[1]);
  ok(fleet.transition(ops,k(),t[1],new FleetService.Transition(ver(c),"DRAINING","draining for maintenance")));
  assertEquals("route_unavailable",code(()->routing.lookup(gateway,"acme.prod")));                       // not ACTIVE: refuse
 }
 @Test void routingFenceReadsActualPostgresTargetVersionAndFreshHealthUnderRls(){
  UUID[] acme=liveCell("acme"),beta=liveCell("beta");
  route("acme.prod",acme[1]);route("beta.prod",beta[1]);
  Map<String,Object> f=routing.routingFence(gateway,"acme.prod");
  assertEquals("acme.prod",f.get("routeKey"));
  assertEquals(acme[1].toString(),f.get("cellId"));
  assertEquals(1L,((Number)f.get("placementEpoch")).longValue());
  assertEquals(2L,((Number)f.get("directoryVersion")).longValue());
  assertEquals("ACTIVE",f.get("cellState"));
  assertEquals("HEALTHY",f.get("health"));
  assertTrue(f.get("observedAt") instanceof String);
  assertEquals("permission_denied",code(()->routing.routingFence(ops,"acme.prod")));
  Actor acmeOperator=actor(plane,unit,Set.of(acme[0]),Perm.ROUTE_READ);
  assertEquals("not_found",code(()->routing.routingFence(acmeOperator,"beta.prod")));
  assertEquals("not_found",code(()->routing.routingFence(gateway,"missing.prod")));
  clock.now=clock.now.plusSeconds(1);
  ok(fleet.reportHealth(ops,k(),acme[1],new FleetService.HealthReport("UNHEALTHY",clock.instant(),null)));
  assertEquals("route_unavailable",code(()->routing.routingFence(gateway,"acme.prod")));
  healthy(acme[1]);
  assertDoesNotThrow(()->routing.routingFence(gateway,"acme.prod"));
  clock.now=clock.now.plusSeconds(Domain.HEALTH_MAX_AGE_SECONDS+10);
  assertEquals("route_unavailable",code(()->routing.routingFence(gateway,"acme.prod")));
 }

 @Test void routeSwitchesRefuseStaleEpochsCrossTenantTargetsAndNeverGoBackwards(){
  UUID[] a=liveCell("acme"),b=liveCell("beta");UUID route=route("acme.prod",a[1]);
  Map<String,Object> a2=body(ok(fleet.requestTenant(ops,k(),prov("acme-two"))));          // a second cell for the SAME tenant
  admin.update("INSERT INTO synqoria_fleet.cells(id,cell_id,facility_id,tenant_id,cell_key,environment,region,tier,capacity_units,state,database_ref,credential_ref,created_by,version) "+
   "SELECT gen_random_uuid(),cell_id,facility_id,tenant_id,'acme-dr','DR',region,tier,capacity_units,'ACTIVE','db/acme/dr','cred/acme/dr',created_by,1 FROM synqoria_fleet.cells WHERE id=?",a[1]);
  UUID dr=admin.queryForObject("SELECT id FROM synqoria_fleet.cells WHERE cell_key='acme-dr'",UUID.class);
  Map<String,Object> rt=routing.routes(ops,0,10).get("items") instanceof List<?> l?(Map<String,Object>)l.getFirst():null;
  assertEquals("stale_placement_epoch",code(()->routing.switchRoute(ops,k(),route,new RoutingService.Switch(ver(rt),7,dr,"failover to the DR cell"))));
  assertEquals("tenant_scope_denied",code(()->routing.switchRoute(ops,k(),route,new RoutingService.Switch(ver(rt),1,b[1],"cross tenant switch attempt"))));
  assertEquals("reason_required",code(()->routing.switchRoute(ops,k(),route,new RoutingService.Switch(ver(rt),1,dr,"no"))));
  ok(routing.switchRoute(ops,k(),route,new RoutingService.Switch(ver(rt),1,dr,"failover to the DR cell")));
  assertEquals(2L,epoch(route));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_fleet.routes SET placement_epoch=1 WHERE id=?",route));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_fleet.routes SET target_cell=? WHERE id=?",a[1],route));    // owner change without an epoch bump
  assertEquals(2L,count("SELECT directory_version FROM synqoria_fleet.directory_state"));
  assertNotNull(a2);
 }

 // ------------------------------------------------------------------ B07 releases
 static final String DIGEST="d".repeat(64);
 UUID approvedManifest(String key,int min,int max){
  Map<String,Object> m=body(ok(releases.createManifest(ops,k(),new ReleaseService.ManifestRequest(key,DIGEST,min,max))));
  assertEquals("approver_must_be_independent",code(()->releases.decideManifest(new Actor(ops.id(),plane,unit,Set.of(Perm.RELEASE_APPROVE),Set.of()),k(),id(m),new ReleaseService.Decision(ver(m),true))));
  ok(releases.decideManifest(approver,k(),id(m),new ReleaseService.Decision(ver(m),true)));
  return id(m);
 }
 @Test void canaryProgressionNeedsEvidenceHealthAndOrder(){
  UUID[] a=liveCell("acme"),b=liveCell("beta"),c=liveCell("gamma");UUID man=approvedManifest("2026.11.1",1,3);
  Map<String,Object> ro=body(ok(releases.startRollout(ops,k(),new ReleaseService.RolloutRequest(man,List.of(List.of(a[1]),List.of(b[1],c[1]))))));
  assertEquals("CANARY",ro.get("status"));
  assertThrows(DataAccessException.class,()->releases.startRollout(ops,k(),new ReleaseService.RolloutRequest(man,List.of(List.of(a[1])))),"only one active rollout");
  assertEquals("stage_not_open",code(()->releases.recordEvidence(ops,k(),id(ro),b[1],new ReleaseService.Evidence("DEPLOYED","dep-2",DIGEST))));       // no skipping the canary
  assertEquals("evidence_mismatch",code(()->releases.recordEvidence(ops,k(),id(ro),a[1],new ReleaseService.Evidence("DEPLOYED","dep-1","e".repeat(64)))));
  assertEquals("stage_incomplete",code(()->releases.advance(ops,k(),id(ro),new ReleaseService.Advance(ver(ro)))));
  clock.now=clock.now.plusSeconds(1);Map<String,Object> r1=body(ok(releases.recordEvidence(ops,k(),id(ro),a[1],new ReleaseService.Evidence("DEPLOYED","dep-1",DIGEST))));
  assertEquals("health_gate_failed",code(()->releases.advance(ops,k(),id(ro),new ReleaseService.Advance(ver(r1)))));          // no health observation after deployment yet
  clock.now=clock.now.plusSeconds(30);healthy(a[1]);
  Map<String,Object> r2=body(ok(releases.advance(ops,k(),id(ro),new ReleaseService.Advance(ver(r1)))));
  assertEquals("PROGRESSING",r2.get("status"));
  ok(releases.recordEvidence(ops,k(),id(ro),b[1],new ReleaseService.Evidence("DEPLOYED","dep-2",DIGEST)));
  ok(releases.recordEvidence(ops,k(),id(ro),c[1],new ReleaseService.Evidence("DEPLOYED","dep-3",DIGEST)));
  clock.now=clock.now.plusSeconds(30);healthy(b[1]);healthy(c[1]);
  Map<String,Object> cur=releases.rolloutDetail(ops,id(ro));
  Map<String,Object> done=body(ok(releases.advance(ops,k(),id(ro),new ReleaseService.Advance(ver(cur)))));
  assertEquals("COMPLETED",done.get("status"));assertEquals("2026.11.1",admin.queryForObject("SELECT release_key FROM synqoria_fleet.cells WHERE id=?",String.class,c[1]));
  assertEquals("rollout_stopped",code(()->releases.advance(ops,k(),id(ro),new ReleaseService.Advance(ver(done)))));
 }
 @Test void incompatibleSchemaBlocksTheRolloutAndUnhealthyOrFailedDeploymentsStopIt(){
  UUID[] a=liveCell("acme"),b=liveCell("beta");
  UUID old=approvedManifest("2026.11.0",5,6);
  Violation v=assertThrows(Violation.class,()->releases.startRollout(ops,k(),new ReleaseService.RolloutRequest(old,List.of(List.of(a[1])))));
  assertEquals("incompatible_release",v.code());assertEquals(List.of("acme-prod"),v.details());
  assertEquals(0L,count("SELECT count(*) FROM synqoria_fleet.rollouts"));
  UUID man=approvedManifest("2026.11.2",1,2);
  Map<String,Object> ro=body(ok(releases.startRollout(ops,k(),new ReleaseService.RolloutRequest(man,List.of(List.of(a[1]),List.of(b[1]))))));
  ok(releases.recordEvidence(ops,k(),id(ro),a[1],new ReleaseService.Evidence("DEPLOYED","dep-1",DIGEST)));
  Reply h=ok(fleet.reportHealth(ops,k(),a[1],new FleetService.HealthReport("UNHEALTHY",clock.instant(),null)));
  assertNotNull(body(h).get("rolloutStopped"));
  assertEquals("STOPPED",releases.rolloutDetail(ops,id(ro)).get("status"));
  assertEquals("rollout_stopped",code(()->releases.recordEvidence(ops,k(),id(ro),b[1],new ReleaseService.Evidence("DEPLOYED","dep-2",DIGEST))));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_fleet.rollouts SET status='CANARY',stop_reason=NULL WHERE id=?",id(ro)));
  Map<String,Object> ro2=body(ok(releases.startRollout(ops,k(),new ReleaseService.RolloutRequest(man,List.of(List.of(b[1]))))));
  Map<String,Object> failed=body(ok(releases.recordEvidence(ops,k(),id(ro2),b[1],new ReleaseService.Evidence("FAILED","dep-fail",DIGEST))));
  assertEquals("STOPPED",failed.get("status"));
 }

 // ------------------------------------------------------------------ B08 migration
 UUID[] migrationFixture(){
  UUID[] src=liveCell("acme");UUID route=route("acme.prod",src[1]);
  UUID[] t=src;
  admin.update("INSERT INTO synqoria_fleet.cells(id,cell_id,facility_id,tenant_id,cell_key,environment,region,tier,capacity_units,state,database_ref,credential_ref,created_by,version) "+
   "SELECT gen_random_uuid(),cell_id,facility_id,tenant_id,'acme-next','PROD',region,tier,capacity_units,'PROVISIONING','db/acme/next','cred/acme/next',created_by,1 FROM synqoria_fleet.cells WHERE id=?",src[1]);
  UUID target=admin.queryForObject("SELECT id FROM synqoria_fleet.cells WHERE cell_key='acme-next'",UUID.class);
  qualify(target);
  return new UUID[]{t[0],src[1],target,route};
 }
 Map<String,Object> step(Map<String,Object> g,Actor who,String step,String evidence,String sd,String td,Long lag,Long epoch){
  return body(ok(migrations.step(who,k(),id(g),new MigrationService.StepRequest(ver(g),step,evidence,sd,td,lag,epoch))));
 }
 static final String DG="a".repeat(64);
 Map<String,Object> upTo(Map<String,Object> g,String last){
  for(String s:List.of("COPY","CATCH_UP","FREEZE","FENCE","VERIFY")){
   g=step(g,ops,s,"ev-"+s,DG,DG,100L,null);
   if(s.equals(last))return g;
  }
  return g;
 }
 @Test void migrationRunsEveryStepWithEvidenceAndSwitchesTheRouteWithANewEpoch(){
  UUID[] f=migrationFixture();
  Map<String,Object> g0=body(ok(migrations.plan(ops,k(),new MigrationService.Plan(f[1],f[2],f[3]))));
  Map<String,Object> g=g0;
  assertThrows(DataAccessException.class,()->migrations.plan(ops,k(),new MigrationService.Plan(f[1],f[2],f[3])),"one open migration per route");
  assertEquals("invalid_transition",code(()->migrations.step(ops,k(),id(g0),new MigrationService.StepRequest(ver(g0),"FREEZE","x",null,null,null,null))));    // no skipping
  assertEquals("evidence_required",code(()->migrations.step(ops,k(),id(g0),new MigrationService.StepRequest(ver(g0),"COPY","",null,null,null,null))));
  g=step(g,ops,"COPY","copy-job-1",null,null,null,null);
  Map<String,Object> copying=g;
  assertEquals("lag_too_high",code(()->migrations.step(ops,k(),id(copying),new MigrationService.StepRequest(ver(copying),"CATCH_UP",null,null,null,60000L,null))));
  g=step(g,ops,"CATCH_UP",null,null,null,100L,null);
  g=step(g,ops,"FREEZE",null,null,null,null,null);assertEquals("DRAINING",state(f[1]));
  g=step(g,ops,"FENCE","fence-ack",null,null,null,null);assertEquals("FENCED",state(f[1]));
  Map<String,Object> fenced=g;
  assertEquals("verification_failed",code(()->migrations.step(ops,k(),id(fenced),new MigrationService.StepRequest(ver(fenced),"VERIFY",null,DG,"b".repeat(64),null,null))));
  assertEquals("FENCED",state(f[1]),"a failed verification changes nothing");
  g=step(g,ops,"VERIFY",null,DG,DG,null,null);assertEquals("MIGRATING",state(f[1]));
  Map<String,Object> verified=g;
  assertEquals("permission_denied",code(()->migrations.step(ops,k(),id(verified),new MigrationService.StepRequest(ver(verified),"SWITCH",null,null,null,null,1L))));
  assertEquals("approver_must_be_independent",code(()->migrations.step(new Actor(ops.id(),plane,unit,Set.of(Perm.MIGRATION_APPROVE),Set.of()),k(),id(verified),new MigrationService.StepRequest(ver(verified),"SWITCH",null,null,null,null,1L))));
  assertEquals("stale_placement_epoch",code(()->migrations.step(approver,k(),id(verified),new MigrationService.StepRequest(ver(verified),"SWITCH",null,null,null,null,9L))));
  g=step(g,approver,"SWITCH",null,null,null,null,1L);
  assertEquals(2L,epoch(f[3]));assertEquals("ACTIVE",state(f[2]));assertEquals(f[2].toString(),admin.queryForObject("SELECT target_cell::text FROM synqoria_fleet.routes WHERE id=?",String.class,f[3]));
  g=step(g,ops,"OBSERVE",null,null,null,null,null);
  Map<String,Object> obs=g;
  assertEquals("health_gate_failed",code(()->migrations.step(ops,k(),id(obs),new MigrationService.StepRequest(ver(obs),"RETIRE",null,null,null,null,null))));   // the target must prove itself first
  clock.now=clock.now.plusSeconds(20);healthy(f[2]);
  g=step(g,ops,"RETIRE",null,null,null,null,null);
  assertEquals("RETIRED",g.get("state"));assertEquals("RETIRED",state(f[1]));
  Map<String,Object> done=g;
  assertEquals("irreversible",code(()->migrations.rollback(approver,k(),id(done),new MigrationService.Rollback(ver(done),"Too late to roll back now",2L))));
  assertDoesNotThrow(()->routing.lookup(gateway,"acme.prod"));
 }
 @Test void rollbackBeforeSwitchRestoresTheSourceAndAfterSwitchReversesTheRoute(){
  UUID[] f=migrationFixture();
  Map<String,Object> g=body(ok(migrations.plan(ops,k(),new MigrationService.Plan(f[1],f[2],f[3]))));
  g=upTo(g,"FENCE");assertEquals("FENCED",state(f[1]));
  Map<String,Object> rb=body(ok(migrations.rollback(ops,k(),id(g),new MigrationService.Rollback(ver(g),"Partner window cancelled",null))));
  assertEquals("ROLLED_BACK",rb.get("state"));assertEquals("ACTIVE",state(f[1]));assertEquals(1L,epoch(f[3]));
  Map<String,Object> g2=body(ok(migrations.plan(ops,k(),new MigrationService.Plan(f[1],f[2],f[3]))));
  g2=upTo(g2,"VERIFY");g2=step(g2,approver,"SWITCH",null,null,null,null,1L);
  Map<String,Object> sw=g2;
  assertEquals("permission_denied",code(()->migrations.rollback(ops,k(),id(sw),new MigrationService.Rollback(ver(sw),"Target cell misbehaving",2L))));       // after the switch an approver is needed
  assertEquals("stale_placement_epoch",code(()->migrations.rollback(approver,k(),id(sw),new MigrationService.Rollback(ver(sw),"Target cell misbehaving",1L))));
  Map<String,Object> back=body(ok(migrations.rollback(approver,k(),id(sw),new MigrationService.Rollback(ver(sw),"Target cell misbehaving",2L))));
  assertEquals("ROLLED_BACK",back.get("state"));assertEquals(3L,epoch(f[3]));assertEquals("ACTIVE",state(f[1]));assertEquals("FENCED",state(f[2]));
  assertEquals(f[1].toString(),admin.queryForObject("SELECT target_cell::text FROM synqoria_fleet.routes WHERE id=?",String.class,f[3]));
 }

 // ------------------------------------------------------------------ B10 concurrency, privilege, audit
 @Test void concurrentDuplicateProvisioningAndRouteSwitchesHaveOneWinner() throws Exception{
  ExecutorService ex=Executors.newFixedThreadPool(4);CyclicBarrier barrier=new CyclicBarrier(4);List<Future<String>> fs=new ArrayList<>();
  for(int i=0;i<4;i++)fs.add(ex.submit(()->{barrier.await();try{fleet.requestTenant(ops,k(),prov("acme"));return "OK";}catch(RuntimeException e){return "REFUSED";}}));
  int okc=0;for(Future<String> f:fs)if(f.get().equals("OK"))okc++;
  assertEquals(1,okc);assertEquals(1L,count("SELECT count(*) FROM synqoria_fleet.provisioning_requests"));
  UUID[] a=liveCell("beta");UUID route=route("beta.prod",a[1]);
  admin.update("INSERT INTO synqoria_fleet.cells(id,cell_id,facility_id,tenant_id,cell_key,environment,region,tier,capacity_units,state,database_ref,credential_ref,created_by,version) "+
   "SELECT gen_random_uuid(),cell_id,facility_id,tenant_id,'beta-dr','DR',region,tier,capacity_units,'ACTIVE','db/beta/dr','cred/beta/dr',created_by,1 FROM synqoria_fleet.cells WHERE id=?",a[1]);
  UUID dr=admin.queryForObject("SELECT id FROM synqoria_fleet.cells WHERE cell_key='beta-dr'",UUID.class);
  CyclicBarrier b2=new CyclicBarrier(2);long v=count("SELECT version FROM synqoria_fleet.routes WHERE id=?",route);List<Future<String>> sw=new ArrayList<>();
  for(int i=0;i<2;i++)sw.add(ex.submit(()->{b2.await();try{routing.switchRoute(ops,k(),route,new RoutingService.Switch(v,1,dr,"concurrent failover request"));return "OK";}catch(Violation e){return e.code();}}));
  List<String> res=new ArrayList<>();for(Future<String> f:sw)res.add(f.get());Collections.sort(res);
  ex.shutdown();
  assertTrue(res.contains("OK"),res.toString());assertEquals(1,res.stream().filter("OK"::equals).count());
  assertEquals(2L,epoch(route));
 }
 @Test void leastPrivilegeImmutableAuditAndOutboxConsistency(){
  UUID[] t=liveCell("acme");route("acme.prod",t[1]);
  JdbcTemplate rt=new JdbcTemplate(runtimeDs);
  assertThrows(DataAccessException.class,()->rt.update("DELETE FROM synqoria_fleet.cells"));
  assertThrows(DataAccessException.class,()->rt.update("DELETE FROM synqoria_fleet.tenants"));
  assertThrows(DataAccessException.class,()->rt.update("UPDATE synqoria_fleet.audit_events SET action='X'"));
  assertThrows(DataAccessException.class,()->rt.update("DELETE FROM synqoria_fleet.cell_health"));
  assertEquals(0L,rt.queryForObject("SELECT count(*) FROM synqoria_fleet.cells",Long.class),"forced RLS: no scope, no rows");
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_fleet.cell_health SET status='HEALTHY'"));
  assertTrue(count("SELECT count(*) FROM synqoria_fleet.audit_events")>=10);
  assertEquals(count("SELECT count(*) FROM synqoria_fleet.audit_events"),count("SELECT count(*) FROM synqoria_fleet.outbox"));
  assertEquals(0L,count("SELECT count(*) FROM synqoria_fleet.audit_events WHERE details::text ILIKE '%secret%' OR details::text ILIKE '%password%'"));
 }
}
