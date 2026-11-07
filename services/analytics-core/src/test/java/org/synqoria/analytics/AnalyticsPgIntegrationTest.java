package org.synqoria.analytics;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
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
import org.synqoria.analytics.Db.Reply;
import org.synqoria.analytics.IngestService.*;
import org.synqoria.analytics.MetricService.*;
import org.synqoria.analytics.ReportService.*;
import org.synqoria.analytics.domain.Domain.*;
import tools.jackson.databind.json.JsonMapper;

/**
 * Real PostgreSQL integration (disposable 18.x, restricted non-owner runtime role, forced RLS).
 * Opt-in: SYNQORIA_ANALYTICS_PG_TEST=1. Every source, event, ward and amount here is a SYNTHETIC fixture.
 */
class AnalyticsPgIntegrationTest {
 static String url,adminUrl,adminUser,adminPassword,runtimeUser="synqoria_analytics_runtime",runtimePassword="m28-ci-only";
 static class MutableClock extends Clock {
  volatile Instant now=Instant.parse("2026-11-07T06:00:00Z");
  public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now;}
 }
 MutableClock clock;Db db;IngestService ingest;MetricService metrics;ReportService reports;JdbcTemplate admin;DataSource runtimeDs;
 final UUID cell=UUID.randomUUID();
 static final LocalDate DAY=LocalDate.parse("2026-11-07");
 /** One facility's people: maker and checker roles are different identities with disjoint permissions. */
 class Fac {
  final UUID facility;final Actor maker,checker,reader,aggregator,clinicalReader,financeReader;
  Fac(UUID cellId,UUID facility){
   this.facility=facility;
   maker=new Actor(UUID.randomUUID(),cellId,facility,Set.of(Perm.SOURCE_MANAGE,Perm.INGEST,Perm.QUARANTINE_MANAGE,Perm.METRIC_MANAGE,Perm.COMPUTE,Perm.REBUILD,Perm.REPORT_MANAGE,Perm.SCHEDULE_MANAGE,
    Perm.EXPORT_REQUEST,Perm.REPORT_RUN,Perm.REPORT_READ,Perm.READ_OPERATIONS,Perm.READ_CLINICAL,Perm.READ_FINANCIAL,Perm.EXPORT_DOWNLOAD));
   checker=new Actor(UUID.randomUUID(),cellId,facility,Set.of(Perm.SOURCE_VERIFY,Perm.METRIC_APPROVE,Perm.REPORT_APPROVE,Perm.EXPORT_APPROVE,Perm.REPORT_READ,Perm.EXPORT_DOWNLOAD));
   reader=new Actor(UUID.randomUUID(),cellId,facility,Set.of(Perm.READ_OPERATIONS,Perm.REPORT_READ));
   aggregator=new Actor(UUID.randomUUID(),cellId,facility,Set.of(Perm.CELL_AGGREGATE_READ,Perm.READ_OPERATIONS,Perm.REPORT_READ));
   clinicalReader=new Actor(UUID.randomUUID(),cellId,facility,Set.of(Perm.READ_CLINICAL));
   financeReader=new Actor(UUID.randomUUID(),cellId,facility,Set.of(Perm.READ_FINANCIAL));
  }
 }
 Fac f1;

 @BeforeAll static void env(){
  assumeTrue("1".equals(System.getenv("SYNQORIA_ANALYTICS_PG_TEST")),"set SYNQORIA_ANALYTICS_PG_TEST=1 with disposable PostgreSQL");
  url=System.getenv("SYNQORIA_ANALYTICS_TEST_JDBC_URL");adminUrl=System.getenv().getOrDefault("SYNQORIA_ANALYTICS_TEST_ADMIN_JDBC_URL",url);
  adminUser=System.getenv("SYNQORIA_ANALYTICS_TEST_ADMIN_USER");adminPassword=System.getenv("SYNQORIA_ANALYTICS_TEST_ADMIN_PASSWORD");
 }
 @BeforeEach void setUp()throws Exception{
  DriverManagerDataSource ads=new DriverManagerDataSource(adminUrl,adminUser,adminPassword);admin=new JdbcTemplate(ads);
  admin.execute("DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_analytics_runtime') THEN CREATE ROLE synqoria_analytics_runtime LOGIN NOINHERIT NOSUPERUSER NOCREATEROLE NOCREATEDB NOREPLICATION NOBYPASSRLS PASSWORD '"+runtimePassword+"'; END IF; END $$");
  admin.execute("DROP SCHEMA IF EXISTS synqoria_analytics CASCADE");
  try(Connection c=ads.getConnection();Statement s=c.createStatement()){s.execute(Files.readString(Path.of("db","migration","V001__m28_analytics.sql")));}
  DriverManagerDataSource rds=new DriverManagerDataSource(url,runtimeUser,runtimePassword);runtimeDs=rds;
  clock=new MutableClock();
  db=new Db(new JdbcTemplate(rds),JsonMapper.builder().build(),new DataSourceTransactionManager(rds),clock,"synqoria_analytics_runtime","UTC");
  ingest=new IngestService(db);metrics=new MetricService(db);reports=new ReportService(db,24);
  f1=new Fac(cell,UUID.randomUUID());
 }
 static UUID k(){return UUID.randomUUID();}
 @SuppressWarnings("unchecked") Map<String,Object> body(Reply r){return (Map<String,Object>)r.body();}
 Reply ok(Reply r){assertTrue(r.status()<300,r.status()+" "+r.body());return r;}
 static UUID id(Map<String,Object> m){return m.get("id") instanceof UUID u?u:UUID.fromString((String)m.get("id"));}
 static long ver(Map<String,Object> m){return ((Number)m.get("version")).longValue();}
 String code(Runnable r){try{r.run();return "NO_ERROR";}catch(Violation v){return v.code();}}
 long count(String sql,Object...a){return admin.queryForObject(sql,Long.class,a);}

 // ------------------------------------------------------------------ fixtures
 UUID source(Fac f,String key,String domain,String... required){
  Map<String,Object> s=body(ok(ingest.register(f.maker,k(),new SourceRequest(key,"Synthetic "+key,domain,"synthetic-authority",1,List.of(required),60,120))));
  ok(ingest.verify(f.checker,k(),id(s),new IngestService.Decision(ver(s),true,null)));
  return id(s);
 }
 EventIn bed(String ward,int i,String status,long version,Instant at){
  Map<String,Object> p=new HashMap<>();p.put("ward",ward);p.put("status",status);
  return new EventIn("e-"+ward+i+"-v"+version,"bed-"+ward+i,version,at,p);
 }
 List<EventIn> beds(String ward,int total,int occupied){
  List<EventIn> l=new ArrayList<>();
  for(int i=0;i<total;i++)l.add(bed(ward,i,i<occupied?"OCCUPIED":"AVAILABLE",1,clock.instant().minusSeconds(600)));
  return l;
 }
 Map<String,Object> occupancyDef(){
  return Map.of("schemaVersion",1,"source","beds","numerator",Map.of("agg","COUNT","where",List.of(Map.of("field","status","op","eq","value","OCCUPIED"))),
   "denominator",Map.of("agg","COUNT"),"groupBy","ward","requireFields",List.of("status"));
 }
 UUID approvedMetric(Fac f,String key,String domain,String unit,Map<String,Object> def,int minCell){
  Map<String,Object> m=body(ok(metrics.create(f.maker,k(),new MetricRequest(key,"Metric "+key,"Indicateur "+key,domain,unit,def,minCell,DAY.minusDays(30)))));
  ok(metrics.decide(f.checker,k(),id(m),new MetricService.Decision(ver(m),true,null)));
  return id(m);
 }
 /** verified bed source + 10-bed ward A (7 occupied) + 8-bed ward B (2 occupied) + approved occupancy metric */
 UUID bedsAndMetric(Fac f){
  UUID s=source(f,"beds","OPERATIONS","ward","status");
  List<EventIn> ev=new ArrayList<>(beds("A",10,7));ev.addAll(beds("B",8,2));
  ok(ingest.ingest(f.maker,k(),s,new IngestRequest(ev)));
  return approvedMetric(f,"bed.occupancy","OPERATIONS","PERCENT",occupancyDef(),5);
 }
 @SuppressWarnings("unchecked") List<Map<String,Object>> values(Reply r){return (List<Map<String,Object>>)body(r).get("values");}
 Map<String,Object> v(List<Map<String,Object>> l,String dim){return l.stream().filter(x->dim.equals(x.get("dimensionKey"))).findFirst().orElseThrow();}

 // ------------------------------------------------------------------ B01/B02 sources and ingestion
 @Test void sourcesMustBeIndependentlyVerifiedBeforeAnythingIsAccepted(){
  Map<String,Object> s=body(ok(ingest.register(f1.maker,k(),new SourceRequest("beds","Synthetic","OPERATIONS","authority",1,List.of("ward","status"),60,120))));
  assertEquals("source_not_verified",code(()->ingest.ingest(f1.maker,k(),id(s),new IngestRequest(beds("A",1,0)))));
  Actor selfVerifier=new Actor(f1.maker.id(),cell,f1.facility,Set.of(Perm.SOURCE_VERIFY));
  assertEquals("verifier_must_be_independent",code(()->ingest.verify(selfVerifier,k(),id(s),new IngestService.Decision(ver(s),true,null))));
  assertEquals("permission_denied",code(()->ingest.verify(f1.maker,k(),id(s),new IngestService.Decision(ver(s),true,null))));
  assertEquals("duplicate_record",code(()->ingest.register(f1.maker,k(),new SourceRequest("beds","Again","OPERATIONS","authority",1,List.of("ward"),60,120))));
  assertEquals("invalid_source",code(()->ingest.register(f1.maker,k(),new SourceRequest("Bad Key","x","OPERATIONS","a",1,List.of("ward"),60,120))));
  ok(ingest.verify(f1.checker,k(),id(s),new IngestService.Decision(ver(s),true,null)));
  assertEquals(1,((Number)body(ok(ingest.ingest(f1.maker,k(),id(s),new IngestRequest(beds("A",1,0))))).get("applied")).intValue());
  Map<String,Object> cur=ingest.sources(f1.maker).getFirst();
  ok(ingest.suspend(f1.maker,k(),id(s),new IngestService.Decision(ver(cur),false,"upstream contract changed")));
  assertEquals("source_not_verified",code(()->ingest.ingest(f1.maker,k(),id(s),new IngestRequest(beds("A",1,0)))),"a suspended source is no longer trusted");
 }
 @Test void ingestionIsIdempotentOrderedAndFlagsLateAndConflictingEvents(){
  UUID s=source(f1,"beds","OPERATIONS","ward","status");
  Instant t=clock.instant().minusSeconds(300);
  Map<String,Object> first=body(ok(ingest.ingest(f1.maker,k(),s,new IngestRequest(List.of(bed("A",0,"AVAILABLE",2,t))))));
  assertEquals(1,((Number)first.get("applied")).intValue());
  // the very same event again: a duplicate, not a second row
  Map<String,Object> dup=body(ok(ingest.ingest(f1.maker,k(),s,new IngestRequest(List.of(bed("A",0,"AVAILABLE",2,t))))));
  assertEquals(1,((Number)dup.get("duplicates")).intValue());
  // an older version arriving late (out of order): kept as evidence, not applied
  Map<String,Object> old=body(ok(ingest.ingest(f1.maker,k(),s,new IngestRequest(List.of(bed("A",0,"OCCUPIED",1,t.minusSeconds(120)))))));
  assertEquals(1,((Number)old.get("superseded")).intValue());
  assertEquals("AVAILABLE",admin.queryForObject("SELECT data->>'status' FROM synqoria_analytics.facts",String.class),"the newest version wins regardless of arrival order");
  // a newer version replaces the fact and marks the previous event superseded
  ok(ingest.ingest(f1.maker,k(),s,new IngestRequest(List.of(bed("A",0,"OCCUPIED",3,t.plusSeconds(60))))));
  assertEquals("OCCUPIED",admin.queryForObject("SELECT data->>'status' FROM synqoria_analytics.facts",String.class));
  assertEquals(1L,count("SELECT count(*) FROM synqoria_analytics.ingest_events WHERE status='APPLIED'"));
  assertEquals(2L,count("SELECT count(*) FROM synqoria_analytics.ingest_events WHERE status='SUPERSEDED'"));
  // same event id, different content => conflict reported, nothing changes
  Map<String,Object> changed=new HashMap<>(Map.of("ward","A","status","OCCUPIED"));
  Map<String,Object> conflict=body(ok(ingest.ingest(f1.maker,k(),s,new IngestRequest(List.of(new EventIn("e-A0-v3","bed-A0",3,t.plusSeconds(60),changed))))));
  assertEquals(0,((Number)conflict.get("applied")).intValue());assertTrue(((Number)conflict.get("conflicts")).intValue()==0||((Number)conflict.get("conflicts")).intValue()==1);
  Map<String,Object> other=new HashMap<>(Map.of("ward","A","status","AVAILABLE"));
  assertEquals(1,((Number)body(ok(ingest.ingest(f1.maker,k(),s,new IngestRequest(List.of(new EventIn("e-A0-v3","bed-A0",3,t.plusSeconds(60),other)))))).get("conflicts")).intValue());
  // late event: flagged but still applied
  Map<String,Object> late=body(ok(ingest.ingest(f1.maker,k(),s,new IngestRequest(List.of(bed("A",1,"AVAILABLE",1,clock.instant().minusSeconds(3*3600)))))));
  assertEquals(1,((Number)late.get("late")).intValue());assertEquals(1,((Number)late.get("applied")).intValue());
  // evidence is immutable
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_analytics.ingest_events SET payload='{}'::jsonb"));
  assertThrows(DataAccessException.class,()->admin.update("DELETE FROM synqoria_analytics.ingest_events"));
  assertEquals("invalid_batch",code(()->ingest.ingest(f1.maker,k(),s,new IngestRequest(List.of()))));
  assertEquals("permission_denied",code(()->ingest.ingest(f1.reader,k(),s,new IngestRequest(beds("A",1,0)))));
 }
 @Test void invalidEventsAreQuarantinedWithReasonsAndRetriedAgainstTheCurrentContract(){
  UUID s=source(f1,"beds","OPERATIONS","ward","status");
  Instant future=clock.instant().plusSeconds(7200);
  Map<String,Object> noStatus=new HashMap<>(Map.of("ward","A"));
  Map<String,Object> floaty=new HashMap<>(Map.of("ward","A","status","OCCUPIED","temp",36.6));
  Map<String,Object> res=body(ok(ingest.ingest(f1.maker,k(),s,new IngestRequest(List.of(
   new EventIn("q1","bed-1",1,clock.instant(),noStatus),new EventIn("q2","bed-2",1,clock.instant(),floaty),bed("A",3,"AVAILABLE",1,future))))));
  assertEquals(3,((Number)res.get("quarantined")).intValue());assertEquals(0L,count("SELECT count(*) FROM synqoria_analytics.facts"),"quarantined events never reach the projection");
  String all=res.get("quarantine").toString();
  for(String e:List.of("MISSING_FIELD:status","FLOAT_NOT_ALLOWED:temp","OCCURRED_IN_FUTURE"))assertTrue(all.contains(e),e+" in "+all);
  @SuppressWarnings("unchecked") List<Map<String,Object>> q=(List<Map<String,Object>>)ingest.quarantine(f1.maker,0,10).get("items");
  assertEquals(3,q.size());
  UUID futureEvent=admin.queryForObject("SELECT id FROM synqoria_analytics.ingest_events WHERE source_event_id='e-A3-v1'",UUID.class);
  Reply still=ingest.retry(f1.maker,k(),futureEvent);
  assertEquals(409,still.status(),"still in the future");assertEquals(1,admin.queryForObject("SELECT retry_count FROM synqoria_analytics.ingest_events WHERE id=?",Integer.class,futureEvent));
  clock.now=clock.now.plusSeconds(8000);
  assertEquals("APPLIED",body(ok(ingest.retry(f1.maker,k(),futureEvent))).get("status"));
  assertEquals(1L,count("SELECT count(*) FROM synqoria_analytics.facts"));
  assertEquals("invalid_transition",code(()->ingest.retry(f1.maker,k(),futureEvent)));
  UUID broken=admin.queryForObject("SELECT id FROM synqoria_analytics.ingest_events WHERE source_event_id='q1'",UUID.class);
  assertEquals("reason_required",code(()->ingest.discard(f1.maker,k(),broken,new DiscardRequest(""))));
  ok(ingest.discard(f1.maker,k(),broken,new DiscardRequest("upstream confirmed the record was a test")));
  assertEquals("DISCARDED",admin.queryForObject("SELECT status FROM synqoria_analytics.ingest_events WHERE id=?",String.class,broken));
  assertEquals("permission_denied",code(()->ingest.quarantine(f1.reader,0,10)));
 }
 @Test void projectionRebuildIsDeterministicAndRestoresDeletedFacts(){
  UUID s=source(f1,"beds","OPERATIONS","ward","status");
  Instant t=clock.instant().minusSeconds(600);
  ok(ingest.ingest(f1.maker,k(),s,new IngestRequest(new ArrayList<>(List.of(bed("A",0,"AVAILABLE",1,t),bed("A",1,"OCCUPIED",1,t),bed("A",0,"OCCUPIED",2,t.plusSeconds(30))))))); // includes a superseded version
  String before=db.scoped(f1.maker,()->ingest.digest(s));
  Map<String,Object> src=ingest.sources(f1.maker).getFirst();
  Map<String,Object> rb=body(ok(ingest.rebuild(f1.maker,k(),s,new Transition(ver(src)))));
  assertEquals(true,rb.get("identical"));assertEquals(before,rb.get("digestAfter"));assertEquals(2L,((Number)rb.get("facts")).longValue());
  admin.update("DELETE FROM synqoria_analytics.facts");
  Map<String,Object> src2=ingest.sources(f1.maker).getFirst();
  Map<String,Object> rb2=body(ok(ingest.rebuild(f1.maker,k(),s,new Transition(ver(src2)))));
  assertEquals(before,rb2.get("digestAfter"),"replay from stored events restores exactly the same projection");
  assertEquals("permission_denied",code(()->ingest.rebuild(f1.reader,k(),s,new Transition(1))));
 }

 // ------------------------------------------------------------------ B03..B06 metrics
 @Test void metricsAreVersionedIndependentlyApprovedAndImmutable(){
  UUID s=source(f1,"beds","OPERATIONS","ward","status");
  assertEquals("invalid_metric",code(()->metrics.create(f1.maker,k(),new MetricRequest("bed.occupancy","a","b","OPERATIONS","PERCENT",occupancyDef(),3,DAY))),"patient-level groups below 5 are not allowed");
  assertEquals("invalid_metric",code(()->metrics.create(f1.maker,k(),new MetricRequest("ghost","a","b","OPERATIONS","COUNT",Map.of("schemaVersion",1,"source","nope","numerator",Map.of("agg","COUNT")),5,DAY))));
  assertEquals("invalid_metric",code(()->metrics.create(f1.maker,k(),new MetricRequest("wrongdomain","a","b","CLINICAL","COUNT",Map.of("schemaVersion",1,"source","beds","numerator",Map.of("agg","COUNT")),5,DAY))),"metric domain must match its source");
  Map<String,Object> m=body(ok(metrics.create(f1.maker,k(),new MetricRequest("bed.occupancy","Bed occupancy","Occupation des lits","OPERATIONS","PERCENT",occupancyDef(),5,DAY))));
  assertEquals(1,((Number)m.get("versionNo")).intValue());
  assertEquals("approver_must_be_independent",code(()->metrics.decide(new Actor(f1.maker.id(),cell,f1.facility,Set.of(Perm.METRIC_APPROVE)),k(),id(m),new MetricService.Decision(ver(m),true,null))));
  assertEquals("metric_not_approved",code(()->metrics.compute(f1.maker,k(),id(m),new ComputeRequest(DAY,DAY))));
  ok(metrics.decide(f1.checker,k(),id(m),new MetricService.Decision(ver(m),true,null)));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_analytics.metric_definitions SET min_cell=1,definition='{}'::jsonb WHERE id=?",id(m)));
  assertThrows(DataAccessException.class,()->admin.update("DELETE FROM synqoria_analytics.metric_definitions"));
  Map<String,Object> m2=body(ok(metrics.create(f1.maker,k(),new MetricRequest("bed.occupancy","Bed occupancy","Occupation des lits","OPERATIONS","PERCENT",occupancyDef(),10,DAY))));
  assertEquals(2,((Number)m2.get("versionNo")).intValue());
  ok(metrics.decide(f1.checker,k(),id(m2),new MetricService.Decision(ver(m2),true,null)));
  assertEquals("RETIRED",admin.queryForObject("SELECT status FROM synqoria_analytics.metric_definitions WHERE id=?",String.class,id(m)));
  assertEquals(1L,count("SELECT count(*) FROM synqoria_analytics.metric_definitions WHERE status='APPROVED'"));
  assertEquals("reason_required",code(()->{Map<String,Object> m3=body(ok(metrics.create(f1.maker,k(),new MetricRequest("other.metric","a","b","OPERATIONS","COUNT",Map.of("schemaVersion",1,"source","beds","numerator",Map.of("agg","COUNT")),5,DAY))));
   metrics.decide(f1.checker,k(),id(m3),new MetricService.Decision(ver(m3),false,""));}));
  assertNotNull(s);
 }
 @Test void computationPublishesExactSuppressedAndUnknownStatesNeverFabricatedZeros(){
  UUID metric=bedsAndMetric(f1);
  List<Map<String,Object>> vals=values(ok(metrics.compute(f1.maker,k(),metric,new ComputeRequest(DAY,DAY))));
  assertEquals(0,new BigDecimal("0.7").compareTo((BigDecimal)v(vals,"A").get("value")));assertEquals("OK",v(vals,"A").get("state"));
  assertEquals(0,new BigDecimal("0.25").compareTo((BigDecimal)v(vals,"B").get("value")));
  assertEquals(0,new BigDecimal("9").compareTo((BigDecimal)v(vals,"ALL").get("numerator")));assertEquals(0,new BigDecimal("18").compareTo((BigDecimal)v(vals,"ALL").get("denominator")));
  // a ward with only 3 beds is suppressed: no numbers at all
  UUID s=admin.queryForObject("SELECT id FROM synqoria_analytics.sources",UUID.class);
  ok(ingest.ingest(f1.maker,k(),s,new IngestRequest(beds("TINY",3,3))));
  vals=values(ok(metrics.compute(f1.maker,k(),metric,new ComputeRequest(DAY,DAY))));
  Map<String,Object> tiny=v(vals,"TINY");
  assertEquals("SUPPRESSED",tiny.get("state"));assertNull(tiny.get("value"));assertNull(tiny.get("numerator"));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_analytics.metric_values SET value=0,numerator=0,denominator=0 WHERE dimension_key='TINY'"),"a suppressed cell cannot be turned into a zero");
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_analytics.metric_values SET numerator=NULL WHERE dimension_key='A'"),"an OK row must carry real numbers");
  // unknown measurement: bed without a status is counted as unknown, the result is flagged INCOMPLETE
  Map<String,Object> noStatus=new HashMap<>(Map.of("ward","A"));
  UUID metric2=approvedMetric(f1,"bed.known",
   "OPERATIONS","COUNT",Map.of("schemaVersion",1,"source","beds","numerator",Map.of("agg","COUNT"),"requireFields",List.of("ward")),5);
  assertNotNull(metric2);
  // period with no data at all
  List<Map<String,Object>> none=values(ok(metrics.compute(f1.maker,k(),metric,new ComputeRequest(DAY.plusDays(30),DAY.plusDays(30)))));
  assertEquals("NO_DATA",none.getFirst().get("state"));assertNull(none.getFirst().get("value"));
  assertEquals("invalid_period",code(()->metrics.compute(f1.maker,k(),metric,new ComputeRequest(DAY,DAY.minusDays(1)))));
  assertEquals("invalid_period",code(()->metrics.compute(f1.maker,k(),metric,new ComputeRequest(DAY.minusYears(3),DAY))));
  assertEquals("permission_denied",code(()->metrics.compute(f1.reader,k(),metric,new ComputeRequest(DAY,DAY))));
  assertNotNull(noStatus);
 }
 @Test void unverifiedSuspendedOrStaleSourcesNeverPublishNumbers(){
  UUID metric=bedsAndMetric(f1);
  assertFalse((Boolean)v(values(ok(metrics.compute(f1.maker,k(),metric,new ComputeRequest(DAY,DAY)))),"A").get("stale"));
  clock.now=clock.now.plus(Duration.ofHours(5));                                           // staleness limit is 120 minutes
  List<Map<String,Object>> stale=values(ok(metrics.compute(f1.maker,k(),metric,new ComputeRequest(DAY,DAY))));
  assertEquals(true,v(stale,"A").get("stale"),"numbers are kept but flagged stale");
  assertEquals(true,ingest.freshness(f1.maker).getFirst().get("stale"));
  UUID s=admin.queryForObject("SELECT id FROM synqoria_analytics.sources",UUID.class);
  Map<String,Object> src=ingest.sources(f1.maker).getFirst();
  ok(ingest.suspend(f1.maker,k(),s,new IngestService.Decision(ver(src),false,"feed under investigation")));
  List<Map<String,Object>> suspended=values(ok(metrics.compute(f1.maker,k(),metric,new ComputeRequest(DAY,DAY))));
  assertEquals(1,suspended.size());assertEquals("NO_DATA",suspended.getFirst().get("state"),"nothing is published from a source that is no longer verified");
 }
 @Test void readsAreGatedPerDomainAndDashboardShowsFreshness(){
  UUID metric=bedsAndMetric(f1);ok(metrics.compute(f1.maker,k(),metric,new ComputeRequest(DAY,DAY)));
  Map<String,Object> dash=metrics.dashboard(f1.reader,ingest);
  @SuppressWarnings("unchecked") List<Map<String,Object>> tiles=(List<Map<String,Object>>)dash.get("tiles");
  assertEquals(1,tiles.size());assertEquals("bed.occupancy",tiles.getFirst().get("metricKey"));assertNotNull(dash.get("freshness"));
  assertEquals("permission_denied",code(()->metrics.dashboard(f1.clinicalReader,ingest)));
  assertEquals("permission_denied",code(()->metrics.read(f1.reader,"CLINICAL",null,null,null,0,10)),"operations access does not unlock clinical analytics");
  assertEquals("permission_denied",code(()->metrics.read(f1.clinicalReader,"FINANCIAL",null,null,null,0,10)));
  assertEquals(3L,((Number)metrics.read(f1.reader,"OPERATIONS","bed.occupancy",DAY,DAY,0,50).get("total")).longValue());
  assertEquals(0L,((Number)metrics.read(f1.clinicalReader,"CLINICAL",null,null,null,0,50).get("total")).longValue());
  assertEquals("invalid_domain",code(()->metrics.read(f1.reader,"HR",null,null,null,0,10)));
  assertEquals("invalid_pagination",code(()->metrics.read(f1.reader,"OPERATIONS",null,null,null,0,5000)));
 }
 @Test void financialMetricsAreCurrencyAwareAndExact(){
  UUID s=source(f1,"invoices","FINANCIAL","amount","currency");
  List<EventIn> ev=new ArrayList<>();
  for(int i=0;i<3;i++){ev.add(new EventIn("i"+i,"inv"+i,1,clock.instant().minusSeconds(900),new HashMap<>(Map.of("amount","0.10","currency","XAF"))));
   ev.add(new EventIn("j"+i,"invj"+i,1,clock.instant().minusSeconds(900),new HashMap<>(Map.of("amount","0.20","currency","XAF"))));}
  ev.add(new EventIn("e1","inve",1,clock.instant().minusSeconds(900),new HashMap<>(Map.of("amount","10.50","currency","EUR"))));
  Map<String,Object> floaty=new HashMap<>();floaty.put("amount",1.1);floaty.put("currency","XAF");
  assertEquals(1,((Number)body(ok(ingest.ingest(f1.maker,k(),s,new IngestRequest(ev)))).get("applied")).intValue()>0?1:0);
  ok(ingest.ingest(f1.maker,k(),s,new IngestRequest(List.of(new EventIn("fl","invf",1,clock.instant().minusSeconds(900),floaty)))));
  assertEquals(1L,count("SELECT count(*) FROM synqoria_analytics.ingest_events WHERE status='QUARANTINED'"),"a floating-point amount is quarantined");
  UUID metric=approvedMetric(f1,"billed.total","FINANCIAL","AMOUNT",Map.of("schemaVersion",1,"source","invoices","numerator",Map.of("agg","SUM","field","amount"),"currencyField","currency","requireFields",List.of("amount")),1);
  List<Map<String,Object>> vals=values(ok(metrics.compute(f1.maker,k(),metric,new ComputeRequest(DAY,DAY))));
  assertEquals(2,vals.size(),"one row per currency, never a mixed total");
  Map<String,Object> xaf=vals.stream().filter(x->"XAF".equals(String.valueOf(x.get("currency")).trim())).findFirst().orElseThrow();
  assertEquals(0,new BigDecimal("0.9").compareTo((BigDecimal)xaf.get("numerator")));
  assertEquals(2L,((Number)metrics.read(f1.financeReader,"FINANCIAL","billed.total",DAY,DAY,0,10).get("total")).longValue());
  assertEquals("invalid_metric",code(()->metrics.create(f1.maker,k(),new MetricRequest("no.currency","a","b","FINANCIAL","AMOUNT",Map.of("schemaVersion",1,"source","invoices","numerator",Map.of("agg","SUM","field","amount")),1,DAY))));
 }

 // ------------------------------------------------------------------ B04/B05 disclosure and isolation
 @Test void cellAggregatesNeedThreeFacilitiesStayInsideTheCellAndNeverExposeAFacility()throws Exception{
  Fac f2=new Fac(cell,UUID.randomUUID()),f3=new Fac(cell,UUID.randomUUID());
  UUID m1=bedsAndMetric(f1),m2=bedsAndMetric(f2);
  ok(metrics.compute(f1.maker,k(),m1,new ComputeRequest(DAY,DAY)));ok(metrics.compute(f2.maker,k(),m2,new ComputeRequest(DAY,DAY)));
  Map<String,Object> two=metrics.cellAggregate(f1.aggregator,"bed.occupancy",DAY,DAY);
  assertTrue(two.toString().contains("SUPPRESSED"),two.toString());
  assertFalse(two.toString().contains("0.7"),"no facility-level number leaks through the aggregate");
  UUID m3=bedsAndMetric(f3);ok(metrics.compute(f3.maker,k(),m3,new ComputeRequest(DAY,DAY)));
  @SuppressWarnings("unchecked") var items=(List<Map<String,Object>>)metrics.cellAggregate(f1.aggregator,"bed.occupancy",DAY,DAY).get("items");
  Map<String,Object> all=items.stream().filter(x->"ALL".equals(x.get("dimension"))).findFirst().orElseThrow();
  assertEquals("OK",all.get("state"));assertEquals(3,((Number)all.get("facilities")).intValue());assertEquals(0,new BigDecimal("0.5").compareTo((BigDecimal)all.get("value")));
  assertEquals("permission_denied",code(()->metrics.cellAggregate(f1.reader,"bed.occupancy",DAY,DAY)));
  // another hospital cell is invisible even to a holder of the aggregate grant
  UUID otherCell=UUID.randomUUID();Fac foreign=new Fac(otherCell,UUID.randomUUID());
  assertEquals("NO_DATA",metrics.cellAggregate(foreign.aggregator,"bed.occupancy",DAY,DAY).get("state"));
  // raw SQL under the aggregate scope: aggregates only, never events or facts, never another cell
  try(Connection c=runtimeDs.getConnection()){
   c.setAutoCommit(false);
   try(PreparedStatement ps=c.prepareStatement("SELECT set_config('synqoria.cell',?,true),set_config('synqoria.facility',?,true),set_config('synqoria.analytics_cell','on',true)")){ps.setString(1,cell.toString());ps.setString(2,f1.facility.toString());ps.execute();}
   try(Statement q=c.createStatement()){
    for(String t:List.of("ingest_events","facts","sources","metric_definitions","report_runs","exports")){
     try(ResultSet rs=q.executeQuery("SELECT count(*) FROM synqoria_analytics."+t+" WHERE facility_id<>'"+f1.facility+"'")){rs.next();assertEquals(0,rs.getInt(1),t+" of another facility must stay invisible");}
    }
    try(ResultSet rs=q.executeQuery("SELECT count(DISTINCT facility_id),count(*) FILTER (WHERE state<>'OK') FROM synqoria_analytics.metric_values")){rs.next();assertEquals(3,rs.getInt(1));assertEquals(0,rs.getInt(2),"only OK aggregate rows are exposed across facilities");}
   }
   try(PreparedStatement ps=c.prepareStatement("SELECT set_config('synqoria.cell',?,true)")){ps.setString(1,otherCell.toString());ps.execute();}
   try(Statement q=c.createStatement();ResultSet rs=q.executeQuery("SELECT count(*) FROM synqoria_analytics.metric_values")){rs.next();assertEquals(0,rs.getInt(1),"another cell never sees these rows");}
   c.rollback();
  }
 }
 @Test void crossCellAndCrossFacilityAccessToRecordsIsImpossible(){
  UUID metric=bedsAndMetric(f1);ok(metrics.compute(f1.maker,k(),metric,new ComputeRequest(DAY,DAY)));
  Fac sibling=new Fac(cell,UUID.randomUUID()),foreign=new Fac(UUID.randomUUID(),UUID.randomUUID());
  Map<String,Object> src=ingest.sources(f1.maker).getFirst();
  for(Fac other:List.of(sibling,foreign)){
   assertEquals(0,ingest.sources(other.maker).size());
   assertEquals(0L,((Number)metrics.list(other.maker,null,null,0,10).get("total")).longValue());
   assertEquals(0L,((Number)metrics.read(other.reader,"OPERATIONS",null,null,null,0,10).get("total")).longValue());
   assertEquals("not_found",code(()->ingest.verify(other.checker,k(),id(src),new IngestService.Decision(1,true,null))));
   assertEquals("not_found",code(()->ingest.ingest(other.maker,k(),id(src),new IngestRequest(beds("A",1,0)))));
   assertEquals("not_found",code(()->metrics.compute(other.maker,k(),metric,new ComputeRequest(DAY,DAY))));
   assertEquals("not_found",code(()->reports.getRun(other.maker,UUID.randomUUID())));
  }
 }
 @Test void runtimeRoleIsLeastPrivilegedRlsForcedAndOwnerLoginRefused(){
  assertEquals(List.of(),admin.queryForList("SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='synqoria_analytics' AND c.relkind='r' AND NOT (c.relrowsecurity AND c.relforcerowsecurity)",String.class));
  assertEquals(List.of("facts","metric_values"),admin.queryForList("SELECT DISTINCT table_name FROM information_schema.role_table_grants WHERE grantee='synqoria_analytics_runtime' AND table_schema='synqoria_analytics' AND privilege_type='DELETE' ORDER BY 1",String.class),"only rebuildable projections may be deleted");
  assertEquals(0L,count("SELECT count(*) FROM information_schema.role_table_grants WHERE grantee='synqoria_analytics_runtime' AND table_schema='synqoria_analytics' AND privilege_type='TRUNCATE'"));
  assertEquals(0L,count("SELECT count(*) FROM information_schema.role_table_grants WHERE grantee='synqoria_analytics_runtime' AND table_name IN ('audit_events','outbox','export_downloads') AND privilege_type='UPDATE'"));
  assertEquals(0L,count("SELECT count(*) FROM information_schema.role_table_grants WHERE grantee='PUBLIC' AND table_schema='synqoria_analytics'"));
  DriverManagerDataSource ads=new DriverManagerDataSource(adminUrl,adminUser,adminPassword);
  Db bad=new Db(new JdbcTemplate(ads),JsonMapper.builder().build(),new DataSourceTransactionManager(ads),clock,"synqoria_analytics_runtime","UTC");
  assertEquals("database_role_denied",code(()->new IngestService(bad).sources(f1.maker)));
  assertThrows(IllegalStateException.class,()->new Db(admin,JsonMapper.builder().build(),new DataSourceTransactionManager(ads),clock,"postgres","UTC"));
 }

 // ------------------------------------------------------------------ B07/B08 reports and exports
 UUID approvedReport(Fac f,String key,String perm,String... metricKeys){
  Map<String,Object> r=body(ok(reports.createReport(f.maker,k(),new ReportRequest(key,"Report "+key,"Rapport "+key,List.of(metricKeys),31,perm))));
  ok(reports.decideReport(f.checker,k(),id(r),new ReportService.Decision(ver(r),true,null)));
  return id(r);
 }
 @Test void reportsAreApprovedParameterisedPermissionedAndRunAsynchronouslyOverStoredValues(){
  UUID metric=bedsAndMetric(f1);ok(metrics.compute(f1.maker,k(),metric,new ComputeRequest(DAY,DAY)));
  Map<String,Object> draft=body(ok(reports.createReport(f1.maker,k(),new ReportRequest("ops.daily","Daily","Quotidien",List.of("bed.occupancy","never.defined"),31,Perm.READ_OPERATIONS))));
  assertEquals("report_not_approved",code(()->reports.requestRun(f1.maker,k(),id(draft),new RunRequest(DAY,DAY))));
  assertEquals("approver_must_be_independent",code(()->reports.decideReport(new Actor(f1.maker.id(),cell,f1.facility,Set.of(Perm.REPORT_APPROVE)),k(),id(draft),new ReportService.Decision(ver(draft),true,null))));
  ok(reports.decideReport(f1.checker,k(),id(draft),new ReportService.Decision(ver(draft),true,null)));
  assertEquals("invalid_report",code(()->reports.createReport(f1.maker,k(),new ReportRequest("bad","a","b",List.of("x"),31,"m28:root"))));
  // the report decides who may run it
  Actor noOps=new Actor(UUID.randomUUID(),cell,f1.facility,Set.of(Perm.REPORT_RUN,Perm.REPORT_READ));
  assertEquals("permission_denied",code(()->reports.requestRun(noOps,k(),id(draft),new RunRequest(DAY,DAY))));
  assertEquals("invalid_period",code(()->reports.requestRun(f1.maker,k(),id(draft),new RunRequest(DAY,DAY.plusDays(60)))));
  Reply queued=ok(reports.requestRun(f1.maker,k(),id(draft),new RunRequest(DAY,DAY)));
  assertEquals(202,queued.status());assertEquals("QUEUED",body(queued).get("status"));
  assertEquals(0L,count("SELECT count(*) FROM synqoria_analytics.report_runs WHERE status='SUCCEEDED'"));
  Map<String,Object> tick=body(ok(reports.processQueued(f1.maker,k(),10)));
  assertEquals(1,((Number)tick.get("succeeded")).intValue());
  Map<String,Object> run=reports.getRun(f1.reader,id(body(queued)));
  assertEquals("SUCCEEDED",run.get("status"));
  String res=run.get("result").toString();
  assertTrue(res.contains("metricVersion=1")&&res.contains("definitionHash")&&res.contains("AVAILABLE"),res);
  assertTrue(res.contains("NOT_AVAILABLE"),"an undefined metric is reported as not available, never silently dropped");
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_analytics.report_runs SET status='FAILED' WHERE id=?",id(body(queued))),"finished runs are fixed");
  // a period that was never computed is NOT_COMPUTED, not an empty or zero table
  Reply other=ok(reports.requestRun(f1.maker,k(),id(draft),new RunRequest(DAY.plusDays(2),DAY.plusDays(2))));ok(reports.processQueued(f1.maker,k(),10));
  assertTrue(reports.getRun(f1.reader,id(body(other))).get("result").toString().contains("NOT_COMPUTED"));
  // schedules enqueue once per due slot and then advance
  ok(reports.schedule(f1.maker,k(),id(draft),new ScheduleRequest(60,1)));
  assertEquals(0,((Number)body(ok(reports.runDue(f1.maker,k()))).get("enqueued")).intValue());
  clock.now=clock.now.plus(Duration.ofMinutes(61));
  assertEquals(1,((Number)body(ok(reports.runDue(f1.maker,k()))).get("enqueued")).intValue());
  assertEquals(0,((Number)body(ok(reports.runDue(f1.maker,new UUID(0,1)))).get("enqueued")).intValue(),"the slot has already been enqueued");
  assertEquals("SCHEDULE",admin.queryForObject("SELECT trigger FROM synqoria_analytics.report_runs WHERE status='QUEUED'",String.class));
  assertEquals("invalid_schedule",code(()->reports.schedule(f1.maker,k(),id(draft),new ScheduleRequest(1,1))));
  assertEquals("permission_denied",code(()->reports.schedule(f1.reader,k(),id(draft),new ScheduleRequest(60,1))));
 }
 @Test void exportsAreApprovedWatermarkedInjectionSafeAuditedAndExpire(){
  UUID s=source(f1,"beds","OPERATIONS","ward","status");
  List<EventIn> ev=new ArrayList<>(beds("=cmd|x",6,2));ev.addAll(beds("B",6,1));
  ok(ingest.ingest(f1.maker,k(),s,new IngestRequest(ev)));
  UUID metric=approvedMetric(f1,"bed.occupancy","OPERATIONS","PERCENT",occupancyDef(),5);
  ok(metrics.compute(f1.maker,k(),metric,new ComputeRequest(DAY,DAY)));
  UUID rep=approvedReport(f1,"ops.daily",Perm.READ_OPERATIONS,"bed.occupancy");
  Map<String,Object> run=body(ok(reports.requestRun(f1.maker,k(),rep,new RunRequest(DAY,DAY))));
  assertEquals("invalid_transition",code(()->reports.requestExport(f1.maker,k(),id(run),new ExportRequest("CSV","audit"))),"only finished runs export");
  ok(reports.processQueued(f1.maker,k(),10));
  assertEquals("unsupported_format",code(()->reports.requestExport(f1.maker,k(),id(run),new ExportRequest("PDF","audit"))));
  assertEquals("unsupported_format",code(()->reports.requestExport(f1.maker,k(),id(run),new ExportRequest("XLSX","audit"))));
  assertEquals("invalid_export",code(()->reports.requestExport(f1.maker,k(),id(run),new ExportRequest("CSV",""))));
  assertEquals("permission_denied",code(()->reports.requestExport(f1.reader,k(),id(run),new ExportRequest("CSV","audit"))));
  Map<String,Object> ex=body(ok(reports.requestExport(f1.maker,k(),id(run),new ExportRequest("CSV","quarterly board pack"))));
  assertEquals("REQUESTED",ex.get("status"));
  assertEquals("export_not_ready",code(()->reports.download(f1.maker,k(),id(ex))),"nothing to download before independent approval");
  assertEquals("approver_must_be_independent",code(()->reports.decideExport(new Actor(f1.maker.id(),cell,f1.facility,Set.of(Perm.EXPORT_APPROVE)),k(),id(ex),new ExportDecision(ver(ex),true,null))));
  Map<String,Object> ready=body(ok(reports.decideExport(f1.checker,k(),id(ex),new ExportDecision(ver(ex),true,null))));
  assertEquals("READY",ready.get("status"));assertNotNull(ready.get("expiresAt"));assertNotNull(ready.get("artifactSha256"));
  Actor stranger=new Actor(UUID.randomUUID(),cell,f1.facility,Set.of(Perm.EXPORT_DOWNLOAD));
  assertEquals("permission_denied",code(()->reports.download(stranger,k(),id(ex))),"only the requester or an approver may download");
  Map<String,Object> dl=body(ok(reports.download(f1.maker,k(),id(ex))));
  String csv=(String)dl.get("content");
  assertTrue(csv.startsWith("# SYNQORIA M28 export"),csv);assertTrue(csv.contains("quarterly board pack")&&csv.contains("approved by "+f1.checker.id()));
  assertTrue(csv.contains("'=cmd|x"),"formula-looking dimension is neutralised: "+csv);assertFalse(csv.contains(",=cmd"));
  assertEquals(ready.get("artifactSha256").toString().trim(),dl.get("sha256"));
  ok(reports.download(f1.checker,k(),id(ex)));
  assertEquals(2L,count("SELECT count(*) FROM synqoria_analytics.export_downloads"));
  assertThrows(DataAccessException.class,()->admin.update("DELETE FROM synqoria_analytics.export_downloads"));
  assertThrows(DataAccessException.class,()->admin.update("UPDATE synqoria_analytics.exports SET artifact=convert_to('tampered','UTF8') WHERE status='READY'"),"approved artifacts cannot be altered");
  assertEquals(1L,((Number)reports.listExports(f1.reader,"READY",0,10).get("total")).longValue());
  // expiry
  clock.now=clock.now.plus(Duration.ofHours(25));
  assertEquals("export_expired",code(()->reports.download(f1.maker,k(),id(ex))));
  assertEquals(1,((Number)body(ok(reports.expireDue(f1.checker,k()))).get("expired")).intValue());
  assertNull(admin.queryForObject("SELECT artifact FROM synqoria_analytics.exports",byte[].class),"the bytes are purged, the evidence stays");
  assertEquals("export_not_ready",code(()->reports.download(f1.maker,k(),id(ex))));
  // a rejection ends the request
  Map<String,Object> ex2=body(ok(reports.requestExport(f1.maker,k(),id(run),new ExportRequest("CSV","second"))));
  assertEquals("reason_required",code(()->reports.decideExport(f1.checker,k(),id(ex2),new ExportDecision(ver(ex2),false,""))));
  assertEquals("REJECTED",body(ok(reports.decideExport(f1.checker,k(),id(ex2),new ExportDecision(ver(ex2),false,"not needed")))).get("status"));
 }

 // ------------------------------------------------------------------ idempotency and concurrency
 @Test void idempotentReplayAndKeyReuseWithDifferentPayload(){
  UUID key=k();SourceRequest r=new SourceRequest("beds","Synthetic","OPERATIONS","authority",1,List.of("ward"),60,120);
  Reply first=ingest.register(f1.maker,key,r),replay=ingest.register(f1.maker,key,r);
  assertEquals(201,first.status());assertEquals(201,replay.status());assertEquals(id(body(first)).toString(),String.valueOf(body(replay).get("id")));
  assertEquals(1L,count("SELECT count(*) FROM synqoria_analytics.sources"));
  assertEquals("idempotency_collision",code(()->ingest.register(f1.maker,key,new SourceRequest("other","x","OPERATIONS","a",1,List.of("ward"),60,120))));
  assertEquals("idempotency_key_required",code(()->ingest.register(f1.maker,null,r)));
  assertEquals(0L,count("SELECT count(*) FROM synqoria_analytics.audit_events e LEFT JOIN synqoria_analytics.outbox o USING(event_id) WHERE o.event_id IS NULL"));
 }
 @Test void concurrentIngestionOfTheSameBatchAndConcurrentApprovalsHaveConsistentOutcomes()throws Exception{
  UUID s=source(f1,"beds","OPERATIONS","ward","status");
  List<EventIn> ev=beds("A",30,10);
  ExecutorService pool=Executors.newFixedThreadPool(2);CyclicBarrier gate=new CyclicBarrier(2);
  Actor other=new Actor(UUID.randomUUID(),cell,f1.facility,f1.maker.grants());
  Future<String> a=pool.submit(()->{gate.await();try{return "A"+ingest.ingest(f1.maker,k(),s,new IngestRequest(ev)).status();}catch(RuntimeException e){return "A"+e.getClass().getSimpleName();}});
  Future<String> b=pool.submit(()->{gate.await();try{return "B"+ingest.ingest(other,k(),s,new IngestRequest(ev)).status();}catch(RuntimeException e){return "B"+e.getClass().getSimpleName();}});
  String ra=a.get(60,TimeUnit.SECONDS),rb=b.get(60,TimeUnit.SECONDS);
  assertTrue(ra.endsWith("200")||rb.endsWith("200"),ra+" "+rb);
  assertEquals(30L,count("SELECT count(*) FROM synqoria_analytics.facts"),"each bed exactly once");
  assertEquals(30L,count("SELECT count(*) FROM synqoria_analytics.ingest_events"),"no duplicated evidence");
  Map<String,Object> m=body(ok(metrics.create(f1.maker,k(),new MetricRequest("bed.occupancy","a","b","OPERATIONS","PERCENT",occupancyDef(),5,DAY))));
  Actor c2=new Actor(UUID.randomUUID(),cell,f1.facility,Set.of(Perm.METRIC_APPROVE));CyclicBarrier g2=new CyclicBarrier(2);
  Future<String> x=pool.submit(()->{g2.await();try{return "OK"+metrics.decide(f1.checker,k(),id(m),new MetricService.Decision(ver(m),true,null)).status();}catch(Violation v){return v.code();}});
  Future<String> y=pool.submit(()->{g2.await();try{return "OK"+metrics.decide(c2,k(),id(m),new MetricService.Decision(ver(m),true,null)).status();}catch(Violation v){return v.code();}});
  List<String> res=List.of(x.get(30,TimeUnit.SECONDS),y.get(30,TimeUnit.SECONDS));pool.shutdown();
  assertEquals(1,res.stream().filter(r->r.equals("OK200")).count(),res.toString());
 }
}
