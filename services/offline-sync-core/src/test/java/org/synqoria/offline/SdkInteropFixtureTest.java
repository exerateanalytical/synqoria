package org.synqoria.offline;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.synqoria.offline.Db.Reply;
import org.synqoria.offline.SyncService.*;
import org.synqoria.offline.domain.Domain.*;
import tools.jackson.databind.json.JsonMapper;

/**
 * Golden interoperability fixtures for the offline DEVICE SDK. Requests are scripted, answered by the REAL SyncService on REAL PostgreSQL, and the
 * serialized replies are recorded. The SDK's tests replay them, so the client is proven against what the service actually returns, not against a hand-written fake.
 * The committed fixture (packages/offline-device-sdk/tests/fixtures/m38-real-batch-replies.json) is compared STRUCTURALLY on every run (statuses, codes, field names);
 * a drift in the server's reply shape fails here. Regenerate with SDK_FIXTURE_WRITE=1. This is not a live HTTP exercise.
 */
class SdkInteropFixtureTest {
 static final Path FIXTURE = Path.of("..", "..", "packages", "offline-device-sdk", "tests", "fixtures", "m38-real-batch-replies.json");
 static class MutableClock extends Clock {
  volatile Instant now = Instant.parse("2026-11-07T06:00:00Z");
  public ZoneId getZone() { return ZoneOffset.UTC; } public Clock withZone(ZoneId z) { return this; } public Instant instant() { return now; }
 }
 JdbcTemplate admin; Db db; SyncService sync; MutableClock clock; JsonMapper json = JsonMapper.builder().build();
 final UUID cell = UUID.randomUUID(), facility = UUID.randomUUID(), owner = UUID.randomUUID();
 Actor maker, checker, nurse; long seq = 0;

 @BeforeEach void setUp() throws Exception {
  assumeTrue("1".equals(System.getenv("SYNQORIA_OFFLINE_PG_TEST")), "set SYNQORIA_OFFLINE_PG_TEST=1 with disposable PostgreSQL");
  String url = System.getenv("SYNQORIA_OFFLINE_TEST_JDBC_URL"), adminUrl = System.getenv().getOrDefault("SYNQORIA_OFFLINE_TEST_ADMIN_JDBC_URL", url);
  DriverManagerDataSource ads = new DriverManagerDataSource(adminUrl, System.getenv("SYNQORIA_OFFLINE_TEST_ADMIN_USER"), System.getenv("SYNQORIA_OFFLINE_TEST_ADMIN_PASSWORD"));
  admin = new JdbcTemplate(ads);
  admin.execute("DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='synqoria_offline_runtime') THEN CREATE ROLE synqoria_offline_runtime LOGIN NOINHERIT NOSUPERUSER NOCREATEROLE NOCREATEDB NOREPLICATION NOBYPASSRLS PASSWORD 'm38-ci-only'; END IF; END $$");
  admin.execute("DROP SCHEMA IF EXISTS synqoria_offline CASCADE");
  try (Connection c = ads.getConnection(); Statement s = c.createStatement()) { s.execute(Files.readString(Path.of("db", "migration", "V001__m38_offline_sync.sql"))); }
  DriverManagerDataSource rds = new DriverManagerDataSource(url, "synqoria_offline_runtime", "m38-ci-only");
  clock = new MutableClock();
  db = new Db(new JdbcTemplate(rds), json, new DataSourceTransactionManager(rds), clock, "synqoria_offline_runtime", "UTC");
  sync = new SyncService(db);
  maker = new Actor(UUID.randomUUID(), cell, facility, Set.of(Perm.CONTRACT_MANAGE, Perm.DEVICE_MANAGE, Perm.HEAD_PUBLISH, Perm.SYNC_READ));
  checker = new Actor(UUID.randomUUID(), cell, facility, Set.of(Perm.CONTRACT_APPROVE));
  nurse = new Actor(UUID.randomUUID(), cell, facility, Set.of(Perm.SYNC_SUBMIT, Perm.SYNC_READ));
 }
 @SuppressWarnings("unchecked") static Map<String, Object> body(Reply r) { assertTrue(r.status() < 300, r.status() + " " + r.body()); return (Map<String, Object>) r.body(); }
 static UUID id(Map<String, Object> m) { return UUID.fromString(String.valueOf(m.get("id"))); }
 void contract(String op, boolean offline, boolean gate) {
  Map<String, Object> c = body(sync.createContract(maker, UUID.randomUUID(), new ContractRequest(op, "note", gate ? "clinical-safety" : "docs", offline, gate, false, 1440)));
  body(sync.decideContract(checker, UUID.randomUUID(), id(c), new Decision(((Number) c.get("version")).longValue(), true)));
 }
 CommandIn cmd(String op, String key, long base, long epoch, Instant expires) {
  return new CommandIn(UUID.randomUUID(), ++seq, op, "note", key, base, epoch, List.of("text"), Map.of("text", "synthetic"), clock.instant().minusSeconds(60), expires);
 }
 Map<String, Object> scenario(String name, UUID device, List<CommandIn> commands) {
  Reply r = sync.submitBatch(nurse, UUID.randomUUID(), new BatchRequest(device, commands));
  Map<String, Object> req = new LinkedHashMap<>();
  req.put("deviceId", device.toString());
  req.put("commands", json.readValue(json.writeValueAsString(commands), List.class));
  Map<String, Object> s = new LinkedHashMap<>();
  s.put("name", name); s.put("request", req); s.put("reply", json.readValue(json.writeValueAsString(r.body()), Map.class));
  return s;
 }

 @Test @SuppressWarnings("unchecked") void recordsAndChecksRealServiceReplies() throws Exception {
  contract("note.add", true, false); contract("meds.give", false, true);
  for (String k : List.of("r1", "r2", "r3", "r4"))
   body(sync.registerHead(maker, UUID.randomUUID(), new HeadRequest("note", k, owner, "a".repeat(64))));
  UUID device = id(body(sync.registerDevice(maker, UUID.randomUUID(), new DeviceRequest("sdk-fixture-device", "Fixture tablet", nurse.id(), 60, 100))));
  Instant later = clock.instant().plusSeconds(86400);
  List<Map<String, Object>> out = new ArrayList<>();
  CommandIn a = cmd("note.add", "r1", 1, 1, later);
  out.add(scenario("applied", device, List.of(a)));
  out.add(scenario("duplicate", device, List.of(a)));
  Map<String, Object> head = ((List<Map<String, Object>>) sync.heads(maker, 0, 50).get("items")).stream().filter(h -> h.get("recordKey").equals("r2")).findFirst().orElseThrow();
  body(sync.publishRevision(maker, UUID.randomUUID(), id(head), new RevisionRequest(((Number) head.get("version")).longValue(), "b".repeat(64), List.of("text"))));
  out.add(scenario("conflict-stale-base", device, List.of(cmd("note.add", "r2", 1, 1, later))));
  out.add(scenario("rejected-safety-gate", device, List.of(cmd("meds.give", "r3", 1, 1, later))));
  CommandIn shortLived = cmd("note.add", "r3", 1, 1, clock.instant().plusSeconds(1));
  clock.now = clock.now.plusSeconds(5);
  out.add(scenario("expired", device, List.of(shortLived)));
  admin.update("UPDATE synqoria_offline.record_heads SET frozen=true WHERE record_key='r4'");
  out.add(scenario("retry-frozen-then-not-processed", device, List.of(cmd("note.add", "r4", 1, 1, later.plusSeconds(10)), cmd("note.add", "r1", 2, 1, later.plusSeconds(10)))));
  Map<String, Object> doc = new LinkedHashMap<>(); doc.put("source", "SyncService on PostgreSQL 18 (SdkInteropFixtureTest)"); doc.put("scenarios", out);

  List<String> shape = shapeOf(doc);
  if ("1".equals(System.getenv("SDK_FIXTURE_WRITE")) || !Files.exists(FIXTURE)) {
   Files.createDirectories(FIXTURE.getParent());
   Files.writeString(FIXTURE, json.writerWithDefaultPrettyPrinter().writeValueAsString(doc));
  } else {
   Map<String, Object> committed = json.readValue(Files.readString(FIXTURE), Map.class);
   assertEquals(shapeOf(committed), shape, "the server's reply shape drifted from the committed SDK fixture; regenerate with SDK_FIXTURE_WRITE=1 and review");
  }
  assertTrue(shape.stream().anyMatch(s -> s.startsWith("applied|APPLIED:applied:v=true:c=false")), shape.toString());
  assertTrue(shape.stream().anyMatch(s -> s.startsWith("duplicate|DUPLICATE:applied:v=true:c=false")), shape.toString());
  assertTrue(shape.stream().anyMatch(s -> s.startsWith("conflict-stale-base|CONFLICT:stale_base:v=false:c=true")), shape.toString());
  assertTrue(shape.stream().anyMatch(s -> s.contains("REJECTED:safety_gate_required")), shape.toString());
  assertTrue(shape.stream().anyMatch(s -> s.contains("EXPIRED:command_expired")), shape.toString());
  assertTrue(shape.stream().anyMatch(s -> s.contains("RETRY:record_frozen") && s.contains("NOT_PROCESSED:batch_stopped")), shape.toString());
 }
 /** Structure only: per scenario, each result's status:code plus whether a version / conflict id is present; plus the reply's field names. */
 @SuppressWarnings("unchecked")
 static List<String> shapeOf(Map<String, Object> doc) {
  List<String> out = new ArrayList<>();
  for (Map<String, Object> s : (List<Map<String, Object>>) doc.get("scenarios")) {
   Map<String, Object> reply = (Map<String, Object>) s.get("reply");
   StringBuilder b = new StringBuilder(s.get("name") + "|");
   for (Map<String, Object> r : (List<Map<String, Object>>) reply.get("results"))
    b.append(r.get("status")).append(':').append(r.get("code")).append(":v=").append(r.get("appliedVersion") != null).append(":c=").append(r.get("conflictId") != null).append(',');
   b.append("|keys=").append(new TreeSet<>(reply.keySet()));
   out.add(b.toString().replace(",|keys", "|keys"));
  }
  return out;
 }
}
