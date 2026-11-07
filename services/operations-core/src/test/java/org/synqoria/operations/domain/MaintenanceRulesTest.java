package org.synqoria.operations.domain;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Database-free rules: safe defaults, DST-safe calendar arithmetic, deterministic identity, projections, metadata. */
final class MaintenanceRulesTest {
 private static void denied(String code,org.junit.jupiter.api.function.Executable e){assertEquals(code,assertThrows(WorkflowViolation.class,e).getMessage());}

 @Test void defaultsAreExplicitAndTighterForSaferClasses(){
  var c=MaintenanceRules.defaults("CRITICAL");var l=MaintenanceRules.defaults("LOW");
  assertTrue(c.intervalDays()<l.intervalDays());assertTrue(c.calibrationIntervalDays()<l.calibrationIntervalDays());
  assertTrue(c.escalateAfterHours()<l.escalateAfterHours());
  denied("asset_safety_class_required",()->MaintenanceRules.defaults("UNKNOWN"));
  denied("asset_safety_class_required",()->MaintenanceRules.defaults(null));
 }
 @Test void dueTimeKeepsTheLocalClockAcrossDaylightSavingAndIsUtcPersisted(){
  LocalDate anchor=LocalDate.of(2026,3,20);
  Instant winter=MaintenanceRules.dueAt(anchor,LocalTime.of(9,0),"Europe/Paris","EXACT_DAY",30,0);
  Instant summer=MaintenanceRules.dueAt(anchor,LocalTime.of(9,0),"Europe/Paris","EXACT_DAY",30,1);
  assertEquals(LocalTime.of(9,0),ZonedDateTime.ofInstant(winter,ZoneId.of("Europe/Paris")).toLocalTime());
  assertEquals(LocalTime.of(9,0),ZonedDateTime.ofInstant(summer,ZoneId.of("Europe/Paris")).toLocalTime(),"09:00 local both sides of the DST change");
  assertNotEquals(winter.atZone(ZoneOffset.UTC).getHour(),summer.atZone(ZoneOffset.UTC).getHour());
  // a local time skipped by the spring-forward gap resolves deterministically
  Instant gap=MaintenanceRules.dueAt(LocalDate.of(2026,3,29),LocalTime.of(2,30),"Europe/Paris","EXACT_DAY",1,0);
  assertEquals(gap,MaintenanceRules.dueAt(LocalDate.of(2026,3,29),LocalTime.of(2,30),"Europe/Paris","EXACT_DAY",1,0));
  assertEquals(0,MaintenanceRules.dueAt(LocalDate.of(2026,7,1),LocalTime.of(12,0),"UTC","EXACT_DAY",7,0).getNano());
 }
 @Test void nextWeekdayRuleMovesWeekendsToMondayButExactDayDoesNot(){
  LocalDate saturday=LocalDate.of(2026,11,7);
  assertEquals(DayOfWeek.SATURDAY,saturday.getDayOfWeek());
  assertEquals(DayOfWeek.MONDAY,MaintenanceRules.dueAt(saturday,LocalTime.NOON,"UTC","NEXT_WEEKDAY",7,0).atZone(ZoneOffset.UTC).getDayOfWeek());
  assertEquals(DayOfWeek.SATURDAY,MaintenanceRules.dueAt(saturday,LocalTime.NOON,"UTC","EXACT_DAY",7,0).atZone(ZoneOffset.UTC).getDayOfWeek());
  denied("invalid_schedule",()->MaintenanceRules.dueAt(saturday,LocalTime.NOON,"UTC","LUNAR",7,0));
  denied("invalid_schedule",()->MaintenanceRules.dueAt(saturday,LocalTime.NOON,"UTC","EXACT_DAY",0,0));
  denied("invalid_schedule",()->MaintenanceRules.dueAt(saturday,LocalTime.NOON,"UTC","EXACT_DAY",7,-1));
  denied("invalid_timezone",()->MaintenanceRules.dueAt(saturday,LocalTime.NOON,"Mars/Olympus","EXACT_DAY",7,0));
 }
 @Test void occurrenceIdsAreStableUniqueAndNameBased(){
  UUID plan=UUID.fromString("11111111-1111-4111-8111-111111111111");
  assertEquals(MaintenanceRules.occurrenceId(plan,1,3),MaintenanceRules.occurrenceId(plan,1,3));
  Set<UUID> all=new HashSet<>();
  for(int v=1;v<=3;v++)for(int s=0;s<200;s++)all.add(MaintenanceRules.occurrenceId(plan,v,s));
  assertEquals(600,all.size());
  assertEquals(5,MaintenanceRules.occurrenceId(plan,1,3).version());
  assertNotEquals(MaintenanceRules.occurrenceId(plan,1,3),MaintenanceRules.occurrenceId(UUID.fromString("22222222-2222-4222-8222-222222222222"),1,3));
 }
 @Test void generationWindowIsBoundedAndNeverBackfillsAStaleAnchorWithoutLimit(){
  Instant now=Instant.parse("2026-11-07T12:00:00Z");
  List<Integer> seqs=MaintenanceRules.sequencesToGenerate(LocalDate.of(2020,1,1),LocalTime.of(8,0),"UTC","EXACT_DAY",30,now,60,24);
  assertFalse(seqs.isEmpty());assertTrue(seqs.size()<=4,"only the current interval and the horizon: "+seqs);
  for(int s:seqs){Instant d=MaintenanceRules.dueAt(LocalDate.of(2020,1,1),LocalTime.of(8,0),"UTC","EXACT_DAY",30,s);
   assertFalse(d.isBefore(now.minus(Duration.ofDays(30))));assertFalse(d.isAfter(now.plus(Duration.ofDays(60))));}
  assertEquals(2,MaintenanceRules.sequencesToGenerate(LocalDate.of(2026,11,1),LocalTime.NOON,"UTC","EXACT_DAY",1,now,2,2).size(),"cap applies");
  denied("invalid_horizon",()->MaintenanceRules.sequencesToGenerate(LocalDate.of(2026,1,1),LocalTime.NOON,"UTC","EXACT_DAY",30,now,121,24));
  denied("invalid_horizon",()->MaintenanceRules.sequencesToGenerate(LocalDate.of(2026,1,1),LocalTime.NOON,"UTC","EXACT_DAY",30,now,30,65));
 }
 @Test void escalationLevelsRiseWithLatenessAndStartOnlyAfterTheGracePeriod(){
  Instant due=Instant.parse("2026-11-01T00:00:00Z");
  assertEquals(0,MaintenanceRules.escalationLevel(due.plus(Duration.ofHours(24)),due,24,72));
  assertEquals(1,MaintenanceRules.escalationLevel(due.plus(Duration.ofHours(25)),due,24,72));
  assertEquals(2,MaintenanceRules.escalationLevel(due.plus(Duration.ofHours(24+72)),due,24,72));
  assertEquals(3,MaintenanceRules.escalationLevel(due.plus(Duration.ofHours(24+144)),due,24,72));
  assertEquals(0,MaintenanceRules.escalationLevel(due.minus(Duration.ofHours(5)),due,24,72));
 }
 @Test void projectionsFailClosedForUnsafeAssetsAndNeverShowBlockedAsHealthy(){
  Instant due=Instant.parse("2026-11-10T09:00:00Z");
  var before=due.minus(Duration.ofDays(1));var during=due.plus(Duration.ofHours(1));var late=due.plus(Duration.ofHours(48));
  assertEquals(MaintenanceRules.Projection.SCHEDULED,MaintenanceRules.project(before,due,24,"PLANNED","MAINTENANCE"));
  assertEquals(MaintenanceRules.Projection.DUE,MaintenanceRules.project(during,due,24,"PLANNED","MAINTENANCE"));
  assertEquals(MaintenanceRules.Projection.OVERDUE,MaintenanceRules.project(late,due,24,"PLANNED","ACTIVE"));
  assertEquals(MaintenanceRules.Projection.IN_SERVICE,MaintenanceRules.project(during,due,24,"WORK_LINKED","MAINTENANCE"));
  assertEquals(MaintenanceRules.Projection.OVERDUE,MaintenanceRules.project(late,due,24,"WORK_LINKED","MAINTENANCE"));
  for(String unsafe:new String[]{"RETIRED","REGISTERED","COMMISSIONED"})
   assertEquals(MaintenanceRules.Projection.BLOCKED,MaintenanceRules.project(during,due,24,"PLANNED",unsafe));
  assertEquals(MaintenanceRules.Projection.BLOCKED,MaintenanceRules.project(during,due,24,"PLANNED",null));
  assertEquals(MaintenanceRules.Projection.DONE,MaintenanceRules.project(late,due,24,"COMPLETED","RETIRED"));
  assertEquals(MaintenanceRules.Projection.CANCELLED,MaintenanceRules.project(late,due,24,"CANCELLED","ACTIVE"));
 }
 @Test void assetMetadataAcceptsTechnicalIdentityOnly(){
  MaintenanceRules.validateAssetMetadata(Map.of("serial","SN-2026/0042","manufacturer","Acme Medical (EU)","model","IP-200","safetyClass","HIGH"));
  MaintenanceRules.validateAssetMetadata(Map.of("title","anything"));
  denied("invalid_asset_serial",()->MaintenanceRules.validateAssetMetadata(Map.of("serial","ab")));
  denied("invalid_asset_serial",()->MaintenanceRules.validateAssetMetadata(Map.of("serial","SN 12 34")));
  denied("invalid_asset_manufacturer",()->MaintenanceRules.validateAssetMetadata(Map.of("manufacturer","<img src=x>")));
  denied("invalid_asset_model",()->MaintenanceRules.validateAssetMetadata(Map.of("model","x".repeat(81))));
  denied("invalid_asset_safety_class",()->MaintenanceRules.validateAssetMetadata(Map.of("safetyClass","high")));
  denied("invalid_reference",()->MaintenanceRules.requireRef("has space"));
  assertTrue(MaintenanceRules.needsCalibration("CALIBRATION"));assertFalse(MaintenanceRules.needsCalibration("PREVENTIVE"));
 }
}
