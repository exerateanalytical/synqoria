package org.synqoria.reliability.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/** Pure M40 domain: permissions, health derivation, redaction, WAL continuity and the recovery line. No I/O, no clock. */
public final class Domain {
 private Domain(){}

 public static class Violation extends RuntimeException {
  private final List<String> details;
  public Violation(String code){this(code,List.of());}
  public Violation(String code,List<String> details){super(code,null,false,false);this.details=List.copyOf(details);}
  public String code(){return getMessage();}
  public List<String> details(){return details;}
 }

 public static final class Perm {
  private Perm(){}
  public static final String SERVICE_MANAGE="m40:service_manage", SIGNAL_REPORT="m40:signal_report", HEALTH_READ="m40:health_read", TELEMETRY_INGEST="m40:telemetry_ingest", TELEMETRY_READ="m40:telemetry_read",
   INCIDENT_MANAGE="m40:incident_manage", INCIDENT_VERIFY="m40:incident_verify", BACKUP_RECORD="m40:backup_record", BACKUP_VERIFY="m40:backup_verify", BACKUP_READ="m40:backup_read",
   RECOVERY_PLAN="m40:recovery_plan", RECOVERY_AUTHORIZE="m40:recovery_authorize", RECOVERY_OPERATE="m40:recovery_operate", RECOVERY_VALIDATE="m40:recovery_validate",
   REOPEN_APPROVE="m40:reopen_approve", RECOVERY_READ="m40:recovery_read";
  public static final Set<String> ALL=Set.of(SERVICE_MANAGE,SIGNAL_REPORT,HEALTH_READ,TELEMETRY_INGEST,TELEMETRY_READ,INCIDENT_MANAGE,INCIDENT_VERIFY,BACKUP_RECORD,BACKUP_VERIFY,BACKUP_READ,
   RECOVERY_PLAN,RECOVERY_AUTHORIZE,RECOVERY_OPERATE,RECOVERY_VALIDATE,REOPEN_APPROVE,RECOVERY_READ);
 }
 public record Actor(UUID id,UUID cell,UUID facility,Set<String> grants){
  public Actor{grants=Set.copyOf(grants);}
  public void require(String p){if(!grants.contains(p))throw new Violation("permission_denied");}
  public boolean has(String p){return grants.contains(p);}
 }
 public static String sha256(String s){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}
  catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }

 // ================================================================== B01 health derivation (unknown is never healthy)
 public static final Duration SIGNAL_FRESHNESS=Duration.ofMinutes(5);
 public static final Set<String> SIGNAL_KINDS=Set.of("READINESS","LATENCY_P95_MS","ERROR_RATE_PCT","SATURATION_PCT","BACKLOG");
 /** OK / WARN / CRIT for one observation. READINESS is 1 (ready) or 0 (not ready). */
 public static String signalState(String kind,double v){
  return switch(kind){
   case "READINESS"->v>=1?"OK":"CRIT";
   case "LATENCY_P95_MS"->v>2000?"CRIT":v>800?"WARN":"OK";
   case "ERROR_RATE_PCT"->v>5?"CRIT":v>1?"WARN":"OK";
   case "SATURATION_PCT"->v>95?"CRIT":v>80?"WARN":"OK";
   case "BACKLOG"->v>10000?"CRIT":v>1000?"WARN":"OK";
   default->throw new Violation("invalid_signal");
  };
 }
 public record Signal(String kind,String state,Instant observedAt){}
 /**
  * HEALTHY / DEGRADED / DOWN / UNKNOWN. UNKNOWN when there is no fresh READINESS signal. A HARD dependency that is DOWN or UNKNOWN
  * caps the service at DEGRADED (reported as dependency-degraded by the caller); SOFT dependencies never change the state.
  */
 public static String serviceHealth(Collection<Signal> latestPerKind,Collection<String> hardDependencyStates,Instant now){
  List<Signal> fresh=latestPerKind.stream().filter(s->!s.observedAt().plus(SIGNAL_FRESHNESS).isBefore(now)).toList();
  Optional<Signal> ready=fresh.stream().filter(s->s.kind().equals("READINESS")).findFirst();
  if(ready.isEmpty())return "UNKNOWN";
  if(ready.get().state().equals("CRIT"))return "DOWN";
  String own=fresh.stream().anyMatch(s->s.state().equals("CRIT"))?"DEGRADED":fresh.stream().anyMatch(s->s.state().equals("WARN"))?"DEGRADED":"HEALTHY";
  if(own.equals("HEALTHY")&&hardDependencyStates.stream().anyMatch(d->d.equals("DOWN")||d.equals("UNKNOWN")))return "DEGRADED";
  return own;
 }

 // ================================================================== B02 redaction
 private static final Set<String> SENSITIVE_KEYS=Set.of("patient","name","firstname","lastname","fullname","email","phone","mobile","address","dob","birth","ssn","nationalid","passport","mrn","nhi","token","password",
  "secret","authorization","cookie","apikey","credential","diagnosis","medication","allergy","note");
 private static final Pattern EMAIL=Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
 private static final Pattern LONG_DIGITS=Pattern.compile("\\b\\d{7,}\\b");
 private static final Pattern BEARER=Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/=-]{8,}");
 public static final String REDACTED="[REDACTED]";
 public record Redacted<T>(T value,int count){}
 public static Redacted<String> redactText(String s){
  if(s==null)return new Redacted<>("",0);
  int[] n={0};
  String out=replace(replace(replace(s,EMAIL,n),BEARER,n),LONG_DIGITS,n);
  return new Redacted<>(out,n[0]);
 }
 private static String replace(String s,Pattern p,int[] n){
  var m=p.matcher(s);StringBuilder b=new StringBuilder();
  while(m.find()){n[0]++;m.appendReplacement(b,java.util.regex.Matcher.quoteReplacement(REDACTED));}
  m.appendTail(b);return b.toString();
 }
 public static boolean sensitiveKey(String k){
  String n=k.toLowerCase().replaceAll("[^a-z0-9]","");
  return SENSITIVE_KEYS.stream().anyMatch(n::contains);
 }
 @SuppressWarnings("unchecked")
 public static Redacted<Map<String,Object>> redactAttributes(Map<String,Object> in){
  Map<String,Object> out=new TreeMap<>();int count=0;
  for(Map.Entry<String,Object> e:new TreeMap<>(in).entrySet()){
   if(sensitiveKey(e.getKey())){out.put(e.getKey(),REDACTED);count++;continue;}
   Object v=e.getValue();
   if(v instanceof String s){Redacted<String> r=redactText(s);out.put(e.getKey(),r.value());count+=r.count();}
   else if(v instanceof Map<?,?> m){Redacted<Map<String,Object>> r=redactAttributes((Map<String,Object>)m);out.put(e.getKey(),r.value());count+=r.count();}
   else if(v==null||v instanceof Number||v instanceof Boolean)out.put(e.getKey(),v);
   else {out.put(e.getKey(),REDACTED);count++;}                           // arrays and unknown shapes are not logged
  }
  return new Redacted<>(out,count);
 }

 // ================================================================== B03 incident SLA
 public static int ackSlaMinutes(String severity){
  return switch(severity){case "SEV1"->5;case "SEV2"->15;case "SEV3"->60;case "SEV4"->240;default->throw new Violation("invalid_severity");};
 }
 public static boolean ackOverdue(String severity,String status,Instant declaredAt,Instant now){
  return status.equals("OPEN")&&declaredAt.plus(Duration.ofMinutes(ackSlaMinutes(severity))).isBefore(now);
 }

 // ================================================================== B05 WAL continuity
 public record WalRange(long start,long end){}
 public record Continuity(boolean continuous,long coveredUpTo,Long gapStart,Long gapEnd){}
 /**
  * Starting at the base backup's end position, every archived WAL range must begin exactly where the previous one ended (overlap is fine,
  * a gap is not) until the target position is covered. Returns the first gap if any.
  */
 public static Continuity walContinuity(long baseEnd,long target,Collection<WalRange> ranges){
  if(target<baseEnd)return new Continuity(true,baseEnd,null,null);          // the base backup alone already reaches back that far
  long covered=baseEnd;
  List<WalRange> sorted=new ArrayList<>(ranges);sorted.sort(Comparator.comparingLong(WalRange::start));
  for(WalRange r:sorted){
   if(r.end()<=covered)continue;
   if(r.start()>covered)return new Continuity(false,covered,covered,r.start());
   covered=r.end();
   if(covered>=target)return new Continuity(true,covered,null,null);
  }
  return covered>=target?new Continuity(true,covered,null,null):new Continuity(false,covered,covered,target);
 }

 // ================================================================== B06 recovery line
 public static final List<String> STEPS=List.of("FENCE","RESTORE_DB","RECONCILE_OBJECTS","RECOVER_AUDIT","RECONCILE_EFFECTS","BEGIN_VALIDATION");
 private static final Map<String,String[]> STEP=Map.of(
  "FENCE",new String[]{"AUTHORIZED","FENCED"},"RESTORE_DB",new String[]{"FENCED","DB_RESTORED"},"RECONCILE_OBJECTS",new String[]{"DB_RESTORED","OBJECTS_RECONCILED"},
  "RECOVER_AUDIT",new String[]{"OBJECTS_RECONCILED","AUDIT_RECOVERED"},"RECONCILE_EFFECTS",new String[]{"AUDIT_RECOVERED","EFFECTS_RECONCILED"},"BEGIN_VALIDATION",new String[]{"EFFECTS_RECONCILED","VALIDATING"});
 public static String stepFrom(String step){String[] s=STEP.get(step);if(s==null)throw new Violation("invalid_step");return s[0];}
 public static String stepTo(String step){String[] s=STEP.get(step);if(s==null)throw new Violation("invalid_step");return s[1];}
 public static final List<String> VALIDATION_ITEMS=List.of("SMOKE_TESTS","IDENTITY_CONTINUITY","CLINICAL_RECONCILIATION","FINANCIAL_RECONCILIATION","AUDIT_CONTINUITY","EGRESS_STATE");
 public static final Set<String> SCENARIOS=Set.of("DATABASE_FAILURE","APPLICATION_FAILURE","INFRASTRUCTURE_LOSS","CONNECTIVITY_INTERRUPTION","ISOLATED_CELL_FAILURE");
 public static final List<String> BACKUP_CHECKS=List.of("INVENTORY","INTEGRITY","ENCRYPTION_KEY","COMPLETENESS","RESTORE_READINESS");
}
