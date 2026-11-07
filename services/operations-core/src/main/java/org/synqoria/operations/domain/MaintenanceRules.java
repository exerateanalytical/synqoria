package org.synqoria.operations.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Pure, database-free M23 preventive-maintenance rules: explicit safe defaults, calendar arithmetic, deterministic occurrence
 * identity, projections and asset metadata validation. A schedule computed here is an internal hospital plan, never a
 * manufacturer certification, and nothing here changes asset state.
 */
public final class MaintenanceRules {
 private MaintenanceRules(){}
 public static final Set<String> SAFETY_CLASSES=Set.of("LOW","MEDIUM","HIGH","CRITICAL");
 public static final Set<String> SERVICE_TYPES=Set.of("PREVENTIVE","CALIBRATION","PREVENTIVE_AND_CALIBRATION");
 public static final Set<String> CALENDAR_RULES=Set.of("EXACT_DAY","NEXT_WEEKDAY");
 private static final Pattern SERIAL=Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]{2,63}");
 private static final Pattern MAKER=Pattern.compile("[A-Za-z0-9][A-Za-z0-9 ._/&()+-]{0,79}");
 private static final Pattern REF=Pattern.compile("[A-Za-z0-9_.:/-]{1,160}");
 private static final Set<String> ZONES=ZoneId.getAvailableZoneIds();

 /** Explicit conservative defaults used only when a facility has no APPROVED policy for the class. Shorter interval = safer. */
 public record Policy(String safetyClass,int intervalDays,int calibrationIntervalDays,int graceHours,int escalateAfterHours){}
 public static Policy defaults(String safetyClass){
  return switch(requireClass(safetyClass)){
   case "CRITICAL" -> new Policy("CRITICAL",30,90,12,24);
   case "HIGH" -> new Policy("HIGH",90,180,24,72);
   case "MEDIUM" -> new Policy("MEDIUM",180,365,48,168);
   default -> new Policy("LOW",365,730,72,336);
  };
 }
 public static String requireClass(String value){
  if(value==null||!SAFETY_CLASSES.contains(value))throw new WorkflowViolation("asset_safety_class_required");
  return value;
 }
 public static boolean needsCalibration(String serviceType){return !"PREVENTIVE".equals(serviceType);}
 public static String requireRef(String value){
  if(value==null||!REF.matcher(value).matches())throw new WorkflowViolation("invalid_reference");
  return value;
 }
 /** Technical identity only: no patient or staff data may be stored in asset metadata. */
 public static void validateAssetMetadata(Map<String,Object> fields){
  Object serial=fields.get("serial");
  if(serial!=null&&!(serial instanceof String s&&SERIAL.matcher(s).matches()))throw new WorkflowViolation("invalid_asset_serial");
  for(String key:List.of("manufacturer","model")){
   Object v=fields.get(key);
   if(v!=null&&!(v instanceof String s&&MAKER.matcher(s).matches()))throw new WorkflowViolation("invalid_asset_"+key);
  }
  Object cls=fields.get("safetyClass");
  if(cls!=null&&!(cls instanceof String s&&SAFETY_CLASSES.contains(s)))throw new WorkflowViolation("invalid_asset_safety_class");
 }
 public static ZoneId zone(String id){
  if(id==null||!ZONES.contains(id))throw new WorkflowViolation("invalid_timezone");
  return ZoneId.of(id);
 }
 /** Due instant of occurrence n: local calendar-day arithmetic in the plan time zone (DST-safe), optional weekday shift, UTC result. */
 public static Instant dueAt(LocalDate anchor,LocalTime localTime,String timezone,String calendarRule,int intervalDays,int seq){
  if(seq<0||intervalDays<1||!CALENDAR_RULES.contains(calendarRule))throw new WorkflowViolation("invalid_schedule");
  LocalDate day=anchor.plusDays((long)intervalDays*seq);
  if("NEXT_WEEKDAY".equals(calendarRule)){
   while(day.getDayOfWeek()==DayOfWeek.SATURDAY||day.getDayOfWeek()==DayOfWeek.SUNDAY)day=day.plusDays(1);
  }
  // ZonedDateTime resolves a local time skipped by a DST gap to the next valid instant, deterministically.
  return ZonedDateTime.of(day,localTime,zone(timezone)).toInstant();
 }
 /** Name-based UUID (v5-style) of plan/version/seq: the same schedule always yields the same ids, so regeneration cannot double-schedule. */
 public static UUID occurrenceId(UUID planId,int version,int seq){
  try{
   MessageDigest sha=MessageDigest.getInstance("SHA-256");
   sha.update("synqoria-m23-occurrence|".getBytes(StandardCharsets.UTF_8));
   sha.update(planId.toString().getBytes(StandardCharsets.UTF_8));
   sha.update(("|"+version+"|"+seq).getBytes(StandardCharsets.UTF_8));
   byte[] d=sha.digest();
   d[6]=(byte)((d[6]&0x0f)|0x50);d[8]=(byte)((d[8]&0x3f)|0x80);
   ByteBuffer b=ByteBuffer.wrap(d,0,16);
   return new UUID(b.getLong(),b.getLong());
  }catch(NoSuchAlgorithmException e){throw new IllegalStateException("sha256_unavailable",e);}
 }
 /** Sequence numbers to materialise for [now-interval, now+horizon]; bounded so a stale anchor never creates an unbounded backlog. */
 public static List<Integer> sequencesToGenerate(LocalDate anchor,LocalTime t,String tz,String rule,int intervalDays,Instant now,int horizonDays,int cap){
  if(horizonDays<1||horizonDays>120||cap<1||cap>64)throw new WorkflowViolation("invalid_horizon");
  Instant from=now.minus(Duration.ofDays(intervalDays)),to=now.plus(Duration.ofDays(horizonDays));
  List<Integer> out=new ArrayList<>();
  long guess=Math.max(0,Duration.between(anchor.atStartOfDay(ZoneOffset.UTC).toInstant(),from).toDays()/intervalDays-1);
  for(int seq=(int)Math.min(guess,Integer.MAX_VALUE-1);out.size()<cap;seq++){
   Instant due=dueAt(anchor,t,tz,rule,intervalDays,seq);
   if(due.isAfter(to))break;
   if(!due.isBefore(from))out.add(seq);
   if(seq>1_000_000)throw new WorkflowViolation("invalid_schedule");
  }
  return out;
 }
 public enum Projection{SCHEDULED,DUE,OVERDUE,BLOCKED,IN_SERVICE,DONE,CANCELLED}
 /** Escalation level 0..3 from how far past (due + grace) the occurrence is. */
 public static int escalationLevel(Instant now,Instant due,int graceHours,int escalateAfterHours){
  Instant overdueFrom=due.plus(Duration.ofHours(graceHours));
  if(!now.isAfter(overdueFrom))return 0;
  long hours=Duration.between(overdueFrom,now).toHours();
  if(hours>=2L*escalateAfterHours)return 3;
  if(hours>=escalateAfterHours)return 2;
  return 1;
 }
 /** BLOCKED (fail closed) wins over time-based states: a retired or uncommissioned asset is never shown as healthy (calibration currency is reported separately and blocks return to service). */
 public static Projection project(Instant now,Instant due,int graceHours,String occurrenceState,String assetStatus){
  if("COMPLETED".equals(occurrenceState))return Projection.DONE;
  if("CANCELLED".equals(occurrenceState))return Projection.CANCELLED;
  if(assetStatus==null||"RETIRED".equals(assetStatus)||"REGISTERED".equals(assetStatus)||"COMMISSIONED".equals(assetStatus))
   return Projection.BLOCKED;
  boolean late=now.isAfter(due.plus(Duration.ofHours(graceHours)));
  if("WORK_LINKED".equals(occurrenceState))return late?Projection.OVERDUE:Projection.IN_SERVICE;
  if(late)return Projection.OVERDUE;
  return now.isBefore(due)?Projection.SCHEDULED:Projection.DUE;
 }
}
