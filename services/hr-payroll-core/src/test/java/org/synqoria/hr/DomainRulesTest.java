package org.synqoria.hr;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.synqoria.hr.domain.Domain;
import org.synqoria.hr.domain.Domain.*;

/** Pure arithmetic, rule and admission tests. All rates and amounts are SYNTHETIC; no statutory rate is encoded anywhere. */
class DomainRulesTest {
 static Map<String,Object> comp(String code,String kind,String type,Object...kv){
  Map<String,Object> m=new LinkedHashMap<>(Map.of("code",code,"kind",kind,"type",type));
  for(int i=0;i<kv.length;i+=2)m.put((String)kv[i],kv[i+1]);return m;
 }
 static Map<String,Object> rules(String cur,String mode,int scale,String hours,Map<String,Object>...comps){
  return Map.of("schemaVersion",1,"currency",cur,"rounding",Map.of("mode",mode,"scale",scale),"monthlyHours",hours,"components",List.of(comps));
 }
 static Map<String,Object> br(String upTo,String rate){Map<String,Object> m=new HashMap<>();m.put("upTo",upTo);m.put("rate",rate);return m;}
 static PeriodInputs none(){return new PeriodInputs(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("22"),List.of());}
 static PayLine calc(Map<String,Object> r,String basis,String base,PeriodInputs in){
  assertEquals(List.of(),Domain.validateRules(r,(String)r.get("currency")));
  return Domain.calculate(Domain.spec(r),(String)r.get("currency"),basis,new BigDecimal(base),in);
 }

 @SuppressWarnings("unchecked")
 @Test void exactDecimalArithmeticWithoutBinaryFloatingPointError(){
  var r=rules("XXX","HALF_UP",2,"160",comp("A","EARNING","FIXED","amount","0.10"),comp("B","EARNING","FIXED","amount","0.20"),comp("T","DEDUCTION","PERCENT_OF_GROSS","rate","0.10"));
  PayLine l=calc(r,"MONTHLY","1000.10",none());
  assertEquals(new BigDecimal("1000.40"),l.gross(),"0.1 + 0.2 must be exactly 0.30");
  assertEquals(new BigDecimal("100.04"),l.totalDeductions());
  assertEquals(l.gross().subtract(l.totalDeductions()),l.net());
  assertEquals(new BigDecimal("900.36"),l.net());
  assertEquals(l.net(),l.items().stream().map(i->i.kind().equals("EARNING")?i.amount():i.amount().negate()).reduce(BigDecimal.ZERO,BigDecimal::add),"payslip lines add up exactly");
 }
 @Test void configuredRoundingModeAndScaleAreApplied(){
  var half=rules("XAF","HALF_UP",0,"160",comp("T","DEDUCTION","PERCENT_OF_GROSS","rate","0.025"));
  var even=rules("XAF","HALF_EVEN",0,"160",comp("T","DEDUCTION","PERCENT_OF_GROSS","rate","0.025"));
  assertEquals(new BigDecimal("3"),calc(half,"MONTHLY","100",none()).totalDeductions(),"2.5 rounds half-up to 3");
  assertEquals(new BigDecimal("2"),calc(even,"MONTHLY","100",none()).totalDeductions(),"2.5 rounds half-even to 2");
  var eur=rules("EUR","HALF_UP",2,"160",comp("T","DEDUCTION","PERCENT_OF_GROSS","rate","0.0333"));
  assertEquals(new BigDecimal("33.30"),calc(eur,"MONTHLY","1000",none()).totalDeductions());
 }
 @Test void bracketsAreProgressiveAndCapsApply(){
  var r=rules("XAF","HALF_UP",0,"160",comp("BR","DEDUCTION","BRACKETS","brackets",List.of(br("100000","0"),br("300000","0.10"),br(null,"0.20"))));
  // 100000 @0 + 200000 @10% + 100000 @20% = 40000
  assertEquals(new BigDecimal("40000"),calc(r,"MONTHLY","400000",none()).totalDeductions());
  assertEquals(new BigDecimal("0"),calc(r,"MONTHLY","90000",none()).totalDeductions());
  assertEquals(new BigDecimal("10000"),calc(r,"MONTHLY","200000",none()).totalDeductions());
  var cap=rules("XAF","HALF_UP",0,"160",comp("S","DEDUCTION","PERCENT_OF_BASIC","rate","0.04","cap","20000"));
  assertEquals(new BigDecimal("20000"),calc(cap,"MONTHLY","900000",none()).totalDeductions());
  assertEquals(new BigDecimal("16000"),calc(cap,"MONTHLY","400000",none()).totalDeductions());
 }
 @Test void overtimeUnpaidLeaveHourlyAndAdjustmentsAreExact(){
  var r=rules("XAF","HALF_UP",0,"160",comp("OT","EARNING","OVERTIME","multiplier","1.5"));
  // hourly = 160000/160 = 1000; 10h x 1.5 = 15000
  PayLine ot=calc(r,"MONTHLY","160000",new PeriodInputs(BigDecimal.ZERO,new BigDecimal("10"),BigDecimal.ZERO,new BigDecimal("20"),List.of()));
  assertEquals(new BigDecimal("175000"),ot.gross());
  // 2 unpaid days of 20 => 10% of base
  PayLine unpaid=calc(r,"MONTHLY","160000",new PeriodInputs(BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("2"),new BigDecimal("20"),List.of()));
  assertEquals(new BigDecimal("16000"),unpaid.totalDeductions());assertEquals(new BigDecimal("144000"),unpaid.net());
  // hourly workers are paid for approved hours only
  PayLine hr=calc(r,"HOURLY","1500",new PeriodInputs(new BigDecimal("37.5"),BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("20"),List.of()));
  assertEquals(new BigDecimal("56250"),hr.gross());
  PayLine adj=calc(r,"MONTHLY","100000",new PeriodInputs(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("20"),
   List.of(new Adjustment("ARREARS",new BigDecimal("5000")),new Adjustment("DEDUCTION",new BigDecimal("1200")))));
  assertEquals(new BigDecimal("105000"),adj.gross());assertEquals(new BigDecimal("103800"),adj.net());
 }
 @Test void negativeNetAndCurrencyMismatchAreRefused(){
  var r=rules("XAF","HALF_UP",0,"160",comp("F","DEDUCTION","FIXED","amount","500000"));
  assertEquals("negative_net",assertThrows(Violation.class,()->calc(r,"MONTHLY","400000",none())).code());
  assertEquals("currency_mismatch",assertThrows(Violation.class,()->Domain.calculate(Domain.spec(rules("XAF","HALF_UP",0,"160",comp("F","DEDUCTION","FIXED","amount","1"))),"EUR","MONTHLY",BigDecimal.TEN,none())).code());
 }
 @Test void ruleSetsAreDataAndInvalidOnesAreRejectedWithEveryProblem(){
  assertEquals(List.of(),Domain.validateRules(rules("XAF","HALF_UP",0,"160",comp("A","EARNING","FIXED","amount","1")),"XAF"));
  // JSON numbers for money are refused outright: they could carry binary floating-point error
  var numeric=Map.<String,Object>of("schemaVersion",1,"currency","XAF","rounding",Map.of("mode","HALF_UP","scale",0),"monthlyHours","160","components",List.of(comp("A","EARNING","FIXED","amount",1.1)));
  assertTrue(Domain.validateRules(numeric,"XAF").contains("INVALID_DECIMAL:A"));
  var bad=rules("XAF","SIDEWAYS",9,"-1",comp("A","EARNING","FIXED","amount","1"),comp("A","EARNING","FIXED","amount","1"),
   comp("R","DEDUCTION","PERCENT_OF_GROSS","rate","1.5"),comp("B","DEDUCTION","BRACKETS","brackets",List.of(br("10","0.1"),br("5","0.1"))),comp("Z","BONUS","FIXED"));
  var errs=Domain.validateRules(bad,"EUR");
  for(String e:List.of("CURRENCY_MISMATCH","INVALID_ROUNDING","INVALID_MONTHLY_HOURS","DUPLICATE_COMPONENT:A","RATE_OUT_OF_RANGE:R","BRACKETS_NOT_ASCENDING:B","INVALID_KIND:Z"))assertTrue(errs.contains(e),e+" in "+errs);
  assertTrue(Domain.validateRules(rules("XAF","HALF_UP",0,"160",comp("B","DEDUCTION","BRACKETS","brackets",List.of(br(null,"0.1"),br("5","0.1")))),"XAF").contains("OPEN_BRACKET_NOT_LAST:B"));
 }
 @Test void sameEngineServesDifferentCountriesCurrenciesAndEmployers(){
  var cm=rules("XAF","HALF_UP",0,"173.33",comp("H","EARNING","FIXED","amount","10000"),comp("T","DEDUCTION","PERCENT_OF_GROSS","rate","0.05"));
  var ke=rules("KES","HALF_EVEN",2,"173.33",comp("T","DEDUCTION","BRACKETS","brackets",List.of(br("24000","0.1"),br(null,"0.25"))));
  assertEquals(new BigDecimal("98000"),calc(cm,"MONTHLY","100000",none()).gross().subtract(new BigDecimal("12000")));
  assertEquals(new BigDecimal("2400.00"),calc(ke,"MONTHLY","24000.00",none()).totalDeductions());
  assertEquals(new BigDecimal("8400.00"),calc(ke,"MONTHLY","48000.00",none()).totalDeductions(),"24000 at 10% + 24000 at 25%");
 }
 @Test void batchHashDetectsAnyChange(){
  UUID p=UUID.randomUUID();List<String[]> rows=List.of(new String[]{"s1","c1","100","90"},new String[]{"s2","c2","200","180"});
  String h=Domain.batchHash(p,"rh",rows);
  assertEquals(h,Domain.batchHash(p,"rh",List.of(rows.get(1),rows.get(0))),"order independent");
  assertNotEquals(h,Domain.batchHash(p,"rh",List.of(rows.get(0),new String[]{"s2","c2","200","181"})));
  assertNotEquals(h,Domain.batchHash(p,"rh2",rows));assertNotEquals(h,Domain.batchHash(UUID.randomUUID(),"rh",rows));
  assertTrue(h.matches("[0-9a-f]{64}"));
 }
 @Test void rosterOverlapRestAndCoverageRules(){
  UUID s=UUID.randomUUID();Instant t0=Instant.parse("2026-10-05T07:00:00Z");
  Shift a=new Shift(s,t0,t0.plus(Duration.ofHours(8)));
  assertEquals(List.of(),Domain.rosterProblems(List.of(a,new Shift(s,t0.plus(Duration.ofHours(19)),t0.plus(Duration.ofHours(27)))),Duration.ofHours(11)));
  assertEquals(List.of("REST:"+s),Domain.rosterProblems(List.of(a,new Shift(s,t0.plus(Duration.ofHours(16)),t0.plus(Duration.ofHours(24)))),Duration.ofHours(11)),"8h gap < 11h rest");
  assertEquals(List.of("OVERLAP:"+s),Domain.rosterProblems(List.of(a,new Shift(s,t0.plus(Duration.ofHours(4)),t0.plus(Duration.ofHours(12)))),Duration.ofHours(11)));
  assertEquals(List.of(),Domain.rosterProblems(List.of(new Shift(null,t0,t0.plusSeconds(60)),new Shift(null,t0,t0.plusSeconds(60))),Duration.ofHours(11)),"vacancies never conflict");
  UUID tpl=UUID.randomUUID();LocalDate d=LocalDate.parse("2026-10-05");
  assertEquals(List.of("GAP:"+tpl+":"+d.plusDays(1)),Domain.coverageGaps(Map.of(tpl,2),List.of(new Object[]{tpl,d},new Object[]{tpl,d},new Object[]{tpl,d.plusDays(1)}),d,d.plusDays(1)));
 }
 @Test void leaveAccrualAndWorkingDays(){
  LocalDate hire=LocalDate.parse("2026-01-15");
  assertEquals(new BigDecimal("12.5000"),Domain.accrued(hire,LocalDate.parse("2026-11-15"),new BigDecimal("1.25"),new BigDecimal("30")).setScale(4));
  assertEquals(new BigDecimal("30"),Domain.accrued(hire,LocalDate.parse("2030-01-01"),new BigDecimal("1.25"),new BigDecimal("30")),"capped");
  assertEquals(BigDecimal.ZERO,Domain.accrued(hire,hire.minusDays(1),BigDecimal.ONE,BigDecimal.TEN));
  assertEquals(4,Domain.workingDays(LocalDate.parse("2026-10-05"),LocalDate.parse("2026-10-11"),Set.of(LocalDate.parse("2026-10-07"))),"Mon-Sun minus a Wednesday holiday");
 }
 @Test void lifecycleStateMachineAndEffectiveDates(){
  assertTrue(StaffStatus.ONBOARDING.canApply(ChangeKind.ACTIVATE));assertFalse(StaffStatus.ACTIVE.canApply(ChangeKind.ACTIVATE));
  assertFalse(StaffStatus.TERMINATED.canApply(ChangeKind.SUSPEND));assertTrue(StaffStatus.TERMINATED.canApply(ChangeKind.REHIRE));
  assertEquals(StaffStatus.ACTIVE,StaffStatus.TERMINATED.after(ChangeKind.REHIRE));
  LocalDate hire=LocalDate.parse("2026-01-01"),today=LocalDate.parse("2026-11-07");
  assertDoesNotThrow(()->Domain.checkEffectiveDate(ChangeKind.SUSPEND,today,hire,hire,null,today));
  for(var bad:List.<Runnable>of(()->Domain.checkEffectiveDate(ChangeKind.SUSPEND,hire.minusDays(1),hire,hire.minusDays(10),null,today),
    ()->Domain.checkEffectiveDate(ChangeKind.SUSPEND,today.minusDays(5),hire,today,null,today),
    ()->Domain.checkEffectiveDate(ChangeKind.REHIRE,today,hire,hire,today,today),
    ()->Domain.checkEffectiveDate(ChangeKind.TERMINATE,today.plusDays(400),hire,hire,null,today)))
   assertEquals("effective_date_conflict",assertThrows(Violation.class,()->bad.run()).code());
 }
 @Test void csvExportNeutralisesFormulaInjection(){
  assertEquals("\"'=HYPERLINK(\"\"x\"\")\"",PayrollService.csvCell("=HYPERLINK(\"x\")"));
  assertEquals("'+1",PayrollService.csvCell("+1"));assertEquals("'-1",PayrollService.csvCell("-1"));assertEquals("'@SUM",PayrollService.csvCell("@SUM"));
  assertEquals("'\tx",PayrollService.csvCell("\tx"));assertEquals("\"a,b\"",PayrollService.csvCell("a,b"));assertEquals("plain",PayrollService.csvCell("plain"));
 }
 @Test void checkerPermissionsAreDistinctFromMakerPermissions(){
  assertTrue(Perm.CHECKER.stream().noneMatch(p->List.of(Perm.STAFF_MANAGE,Perm.CONTRACT_MANAGE,Perm.ROSTER_MANAGE,Perm.RULES_MANAGE,Perm.PAYROLL_PREPARE,Perm.PAYMENT_INSTRUCT).contains(p)));
  assertEquals("permission_denied",assertThrows(Violation.class,()->new Actor(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),Set.of(Perm.STAFF_READ)).require(Perm.COMP_READ)).code());
 }

 // ---- admission
 static final Instant NOW=Instant.parse("2026-11-07T06:00:00Z");
 static Jwt jwt(Map<String,Object> o){
  Map<String,Object> c=new HashMap<>(Map.of("sub",UUID.randomUUID().toString(),"synqoria_cell",UUID.randomUUID().toString(),"synqoria_facility",UUID.randomUUID().toString(),
   "synqoria_permissions",List.of("m24:staff_read"),"synqoria_grant_expires",NOW.plusSeconds(600).getEpochSecond()));
  c.putAll(o);return new Jwt("t",NOW.minusSeconds(5),NOW.plusSeconds(600),Map.of("alg","RS256"),c);
 }
 static String code(Jwt j){try{Platform.actorFrom(j,NOW);return "OK";}catch(Violation v){return v.code();}}
 @Test void admissionRejectsForgedOrStaleGrants(){
  assertEquals("OK",code(jwt(Map.of())));assertEquals("identity_denied",code(null));
  assertEquals("identity_denied",code(jwt(Map.of("synqoria_cell","x"))));
  assertEquals("grant_denied",code(jwt(Map.of("synqoria_permissions",List.of("m24:root")))));
  assertEquals("grant_denied",code(jwt(Map.of("synqoria_permissions",List.of("m30:rollback")))));
  assertEquals("grant_denied",code(jwt(Map.of("synqoria_permissions",List.of("m24:comp_read","m24:comp_read")))));
  assertEquals("grant_expired",code(jwt(Map.of("synqoria_grant_expires",NOW.minusSeconds(1).getEpochSecond()))));
  assertEquals("grant_expired",code(jwt(Map.of("synqoria_grant_expires",NOW.plusSeconds(3600).getEpochSecond()))));
  assertEquals(403,Platform.Errors.status("confirmer_must_be_independent").value());
  assertEquals(409,Platform.Errors.status("period_locked").value());assertEquals(404,Platform.Errors.status("not_found").value());
 }
}
