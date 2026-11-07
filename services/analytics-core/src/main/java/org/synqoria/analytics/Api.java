package org.synqoria.analytics;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.synqoria.analytics.Db.Reply;
import org.synqoria.analytics.domain.Domain.*;

/** Versioned API /api/v1/analytics. Contract: contracts/openapi/synqoria-m28-analytics-v1.yaml (generated from this class). */
@RestController
@RequestMapping("/api/v1/analytics")
public class Api {
 private final Clock clock;
 private final IngestService ingest;
 private final MetricService metrics;
 private final ReportService reports;
 public Api(Clock clock,IngestService ingest,MetricService metrics,ReportService reports){this.clock=clock;this.ingest=ingest;this.metrics=metrics;this.reports=reports;}
 private Actor actor(Jwt j){return Platform.actorFrom(j,clock.instant());}
 private static UUID key(String h){
  if(h==null)throw new Violation("idempotency_key_required");
  try{return UUID.fromString(h);}catch(IllegalArgumentException e){throw new Violation("idempotency_key_required");}
 }
 private static ResponseEntity<Object> out(Reply r){return ResponseEntity.status(r.status()).header("Cache-Control","no-store").body(r.body());}
 private static <T> ResponseEntity<T> ok(T b){return ResponseEntity.ok().header("Cache-Control","no-store").body(b);}
 private static <T> T req(T b){if(b==null)throw new Violation("malformed_request");return b;}
 private static final String K="Idempotency-Key";

 // ---- sources, freshness, ingestion
 @GetMapping("/sources") public ResponseEntity<List<Map<String,Object>>> sources(@AuthenticationPrincipal Jwt j){return ok(ingest.sources(actor(j)));}
 @PostMapping("/sources") public ResponseEntity<Object> registerSource(@RequestHeader(value=K,required=false)String k,@RequestBody IngestService.SourceRequest b,@AuthenticationPrincipal Jwt j){return out(ingest.register(actor(j),key(k),req(b)));}
 @PostMapping("/sources/{id}/verification") public ResponseEntity<Object> verifySource(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody IngestService.Decision b,@AuthenticationPrincipal Jwt j){return out(ingest.verify(actor(j),key(k),id,req(b)));}
 @PostMapping("/sources/{id}/suspension") public ResponseEntity<Object> suspendSource(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody IngestService.Decision b,@AuthenticationPrincipal Jwt j){return out(ingest.suspend(actor(j),key(k),id,req(b)));}
 @GetMapping("/freshness") public ResponseEntity<List<Map<String,Object>>> freshness(@AuthenticationPrincipal Jwt j){return ok(ingest.freshness(actor(j)));}
 @PostMapping("/sources/{id}/events") public ResponseEntity<Object> ingestEvents(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody IngestService.IngestRequest b,@AuthenticationPrincipal Jwt j){return out(ingest.ingest(actor(j),key(k),id,req(b)));}
 @GetMapping("/quarantine") public ResponseEntity<Map<String,Object>> quarantine(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(ingest.quarantine(actor(j),page,size));}
 @PostMapping("/quarantine/{id}/retry") public ResponseEntity<Object> retry(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@AuthenticationPrincipal Jwt j){return out(ingest.retry(actor(j),key(k),id));}
 @PostMapping("/quarantine/{id}/discard") public ResponseEntity<Object> discard(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody IngestService.DiscardRequest b,@AuthenticationPrincipal Jwt j){return out(ingest.discard(actor(j),key(k),id,req(b)));}
 @PostMapping("/sources/{id}/rebuild") public ResponseEntity<Object> rebuild(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody IngestService.Transition b,@AuthenticationPrincipal Jwt j){return out(ingest.rebuild(actor(j),key(k),id,req(b)));}

 // ---- metrics, dashboard, analytics
 @GetMapping("/metrics") public ResponseEntity<Map<String,Object>> metricList(@RequestParam(required=false)String domain,@RequestParam(required=false)String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(metrics.list(actor(j),domain,status,page,size));}
 @PostMapping("/metrics") public ResponseEntity<Object> metricCreate(@RequestHeader(value=K,required=false)String k,@RequestBody MetricService.MetricRequest b,@AuthenticationPrincipal Jwt j){return out(metrics.create(actor(j),key(k),req(b)));}
 @PostMapping("/metrics/{id}/decision") public ResponseEntity<Object> metricDecision(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody MetricService.Decision b,@AuthenticationPrincipal Jwt j){return out(metrics.decide(actor(j),key(k),id,req(b)));}
 @PostMapping("/metrics/{id}/values") public ResponseEntity<Object> metricCompute(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody MetricService.ComputeRequest b,@AuthenticationPrincipal Jwt j){return out(metrics.compute(actor(j),key(k),id,req(b)));}
 @GetMapping("/metric-values") public ResponseEntity<Map<String,Object>> metricValues(@RequestParam String domain,@RequestParam(required=false)String metricKey,
   @RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate from,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate to,
   @RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size,@AuthenticationPrincipal Jwt j){return ok(metrics.read(actor(j),domain,metricKey,from,to,page,size));}
 @GetMapping("/dashboard") public ResponseEntity<Map<String,Object>> dashboard(@AuthenticationPrincipal Jwt j){return ok(metrics.dashboard(actor(j),ingest));}
 @GetMapping("/cell-aggregates") public ResponseEntity<Map<String,Object>> cellAggregates(@RequestParam String metricKey,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate from,
   @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate to,@AuthenticationPrincipal Jwt j){return ok(metrics.cellAggregate(actor(j),metricKey,from,to));}

 // ---- reports, runs, schedules
 @GetMapping("/reports") public ResponseEntity<Map<String,Object>> reportList(@RequestParam(required=false)String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(reports.listReports(actor(j),status,page,size));}
 @PostMapping("/reports") public ResponseEntity<Object> reportCreate(@RequestHeader(value=K,required=false)String k,@RequestBody ReportService.ReportRequest b,@AuthenticationPrincipal Jwt j){return out(reports.createReport(actor(j),key(k),req(b)));}
 @PostMapping("/reports/{id}/decision") public ResponseEntity<Object> reportDecision(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody ReportService.Decision b,@AuthenticationPrincipal Jwt j){return out(reports.decideReport(actor(j),key(k),id,req(b)));}
 @PostMapping("/reports/{id}/runs") public ResponseEntity<Object> requestRun(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody ReportService.RunRequest b,@AuthenticationPrincipal Jwt j){return out(reports.requestRun(actor(j),key(k),id,req(b)));}
 @PostMapping("/reports/{id}/schedule") public ResponseEntity<Object> schedule(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody ReportService.ScheduleRequest b,@AuthenticationPrincipal Jwt j){return out(reports.schedule(actor(j),key(k),id,req(b)));}
 @PostMapping("/schedules/run-due") public ResponseEntity<Object> runDue(@RequestHeader(value=K,required=false)String k,@AuthenticationPrincipal Jwt j){return out(reports.runDue(actor(j),key(k)));}
 @PostMapping("/runs/process") public ResponseEntity<Object> process(@RequestHeader(value=K,required=false)String k,@RequestParam(defaultValue="10")int limit,@AuthenticationPrincipal Jwt j){return out(reports.processQueued(actor(j),key(k),limit));}
 @GetMapping("/runs") public ResponseEntity<Map<String,Object>> runs(@RequestParam(required=false)String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(reports.listRuns(actor(j),status,page,size));}
 @GetMapping("/runs/{id}") public ResponseEntity<Map<String,Object>> run(@PathVariable UUID id,@AuthenticationPrincipal Jwt j){return ok(reports.getRun(actor(j),id));}

 // ---- exports
 @PostMapping("/runs/{id}/exports") public ResponseEntity<Object> exportRequest(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody ReportService.ExportRequest b,@AuthenticationPrincipal Jwt j){return out(reports.requestExport(actor(j),key(k),id,req(b)));}
 @GetMapping("/exports") public ResponseEntity<Map<String,Object>> exportList(@RequestParam(required=false)String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(reports.listExports(actor(j),status,page,size));}
 @PostMapping("/exports/{id}/decision") public ResponseEntity<Object> exportDecision(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody ReportService.ExportDecision b,@AuthenticationPrincipal Jwt j){return out(reports.decideExport(actor(j),key(k),id,req(b)));}
 @PostMapping("/exports/{id}/download") public ResponseEntity<Object> exportDownload(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@AuthenticationPrincipal Jwt j){return out(reports.download(actor(j),key(k),id));}
 @PostMapping("/exports/expire-due") public ResponseEntity<Object> exportExpire(@RequestHeader(value=K,required=false)String k,@AuthenticationPrincipal Jwt j){return out(reports.expireDue(actor(j),key(k)));}
}
