package org.synqoria.hr;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.synqoria.hr.Db.Reply;
import org.synqoria.hr.domain.Domain.*;

/** Versioned API /api/v1/hr. Contract: contracts/openapi/synqoria-m24-hr-v1.yaml. */
@RestController
@RequestMapping("/api/v1/hr")
public class Api {
 private final Clock clock;
 private final StaffService staff;
 private final TimeService time;
 private final PayrollService payroll;
 public Api(Clock clock,StaffService staff,TimeService time,PayrollService payroll){this.clock=clock;this.staff=staff;this.time=time;this.payroll=payroll;}
 private Actor actor(Jwt j){return Platform.actorFrom(j,clock.instant());}
 private static UUID key(String h){
  if(h==null)throw new Violation("idempotency_key_required");
  try{return UUID.fromString(h);}catch(IllegalArgumentException e){throw new Violation("idempotency_key_required");}
 }
 private static ResponseEntity<Object> out(Reply r){return ResponseEntity.status(r.status()).header("Cache-Control","no-store").body(r.body());}
 private static <T> ResponseEntity<T> ok(T b){return ResponseEntity.ok().header("Cache-Control","no-store").body(b);}
 private static <T> T req(T b){if(b==null)throw new Violation("malformed_request");return b;}
 private static final String K="Idempotency-Key";

 // ---- S01 staff directory / S02 employment record
 @GetMapping("/organisation") public ResponseEntity<Map<String,Object>> organisation(@AuthenticationPrincipal Jwt j){return ok(staff.organisation(actor(j)));}
 @PostMapping("/departments") public ResponseEntity<Object> dept(@RequestHeader(value=K,required=false)String k,@RequestBody StaffService.DeptRequest b,@AuthenticationPrincipal Jwt j){return out(staff.createDepartment(actor(j),key(k),req(b)));}
 @PostMapping("/positions") public ResponseEntity<Object> position(@RequestHeader(value=K,required=false)String k,@RequestBody StaffService.PositionRequest b,@AuthenticationPrincipal Jwt j){return out(staff.createPosition(actor(j),key(k),req(b)));}
 @GetMapping("/staff") public ResponseEntity<Map<String,Object>> staffList(@RequestParam(required=false)String q,@RequestParam(required=false)UUID departmentId,@RequestParam(required=false)String status,
   @RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(staff.list(actor(j),q,departmentId,status,page,size));}
 @PostMapping("/staff") public ResponseEntity<Object> onboard(@RequestHeader(value=K,required=false)String k,@RequestBody StaffService.OnboardRequest b,@AuthenticationPrincipal Jwt j){return out(staff.onboard(actor(j),key(k),req(b)));}
 @GetMapping("/staff/{id}") public ResponseEntity<Map<String,Object>> staffGet(@PathVariable UUID id,@AuthenticationPrincipal Jwt j){return ok(staff.get(actor(j),id));}
 @PostMapping("/staff/{id}/changes") public ResponseEntity<Object> change(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody StaffService.ChangeRequest b,@AuthenticationPrincipal Jwt j){return out(staff.requestChange(actor(j),key(k),id,req(b)));}
 @GetMapping("/changes") public ResponseEntity<Map<String,Object>> changes(@RequestParam(required=false)String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(staff.listChanges(actor(j),status,page,size));}
 @PostMapping("/changes/{id}/decision") public ResponseEntity<Object> changeDecision(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody StaffService.Decision b,@AuthenticationPrincipal Jwt j){return out(staff.decideChange(actor(j),key(k),id,req(b)));}
 @PostMapping("/changes/apply-due") public ResponseEntity<Object> applyDue(@RequestHeader(value=K,required=false)String k,@AuthenticationPrincipal Jwt j){return out(staff.applyDue(actor(j),key(k)));}
 @PostMapping("/staff/{id}/contracts") public ResponseEntity<Object> contract(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody StaffService.ContractRequest b,@AuthenticationPrincipal Jwt j){return out(staff.draftContract(actor(j),key(k),id,req(b)));}
 @PostMapping("/contracts/{id}/decision") public ResponseEntity<Object> contractDecision(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody StaffService.Decision b,@AuthenticationPrincipal Jwt j){return out(staff.decideContract(actor(j),key(k),id,req(b)));}
 @PostMapping("/staff/{id}/payment-account") public ResponseEntity<Object> account(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody StaffService.AccountRequest b,@AuthenticationPrincipal Jwt j){return out(staff.setPaymentAccount(actor(j),key(k),id,req(b)));}

 // ---- S03 roster
 @GetMapping("/shift-templates") public ResponseEntity<List<Map<String,Object>>> templates(@AuthenticationPrincipal Jwt j){return ok(time.templates(actor(j)));}
 @PostMapping("/shift-templates") public ResponseEntity<Object> template(@RequestHeader(value=K,required=false)String k,@RequestBody TimeService.TemplateRequest b,@AuthenticationPrincipal Jwt j){return out(time.createTemplate(actor(j),key(k),req(b)));}
 @PostMapping("/coverage") public ResponseEntity<Object> coverage(@RequestHeader(value=K,required=false)String k,@RequestBody TimeService.CoverageRequest b,@AuthenticationPrincipal Jwt j){return out(time.setCoverage(actor(j),key(k),req(b)));}
 @GetMapping("/rosters") public ResponseEntity<Map<String,Object>> rosters(@RequestParam(required=false)UUID departmentId,@RequestParam(required=false)String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(time.listRosters(actor(j),departmentId,status,page,size));}
 @PostMapping("/rosters") public ResponseEntity<Object> roster(@RequestHeader(value=K,required=false)String k,@RequestBody TimeService.RosterRequest b,@AuthenticationPrincipal Jwt j){return out(time.createRoster(actor(j),key(k),req(b)));}
 @GetMapping("/rosters/{id}") public ResponseEntity<Map<String,Object>> rosterGet(@PathVariable UUID id,@AuthenticationPrincipal Jwt j){return ok(time.getRoster(actor(j),id));}
 @GetMapping("/rosters/{id}/check") public ResponseEntity<Map<String,Object>> rosterCheck(@PathVariable UUID id,@AuthenticationPrincipal Jwt j){return ok(time.check(actor(j),id));}
 @PostMapping("/rosters/{id}/shifts") public ResponseEntity<Object> shift(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody TimeService.ShiftRequest b,@AuthenticationPrincipal Jwt j){return out(time.addShift(actor(j),key(k),id,req(b)));}
 @PostMapping("/shifts/{id}/remove") public ResponseEntity<Object> shiftRemove(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@AuthenticationPrincipal Jwt j){return out(time.removeShift(actor(j),key(k),id));}
 @PostMapping("/shifts/{id}/overtime-authorization") public ResponseEntity<Object> overtime(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@AuthenticationPrincipal Jwt j){return out(time.authorizeOvertime(actor(j),key(k),id));}
 @PostMapping("/rosters/{id}/submit") public ResponseEntity<Object> rosterSubmit(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody TimeService.Transition b,@AuthenticationPrincipal Jwt j){return out(time.submit(actor(j),key(k),id,req(b)));}
 @PostMapping("/rosters/{id}/review") public ResponseEntity<Object> rosterReview(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody TimeService.Decision b,@AuthenticationPrincipal Jwt j){return out(time.review(actor(j),key(k),id,req(b)));}
 @PostMapping("/rosters/{id}/revise") public ResponseEntity<Object> rosterRevise(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody TimeService.Transition b,@AuthenticationPrincipal Jwt j){return out(time.revise(actor(j),key(k),id,req(b)));}

 // ---- S04 attendance and leave
 @GetMapping("/attendance") public ResponseEntity<Map<String,Object>> attendance(@RequestParam(required=false)UUID staffId,@RequestParam(required=false)String status,
   @RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate from,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate to,
   @RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(time.listAttendance(actor(j),staffId,status,from,to,page,size));}
 @PostMapping("/attendance") public ResponseEntity<Object> capture(@RequestHeader(value=K,required=false)String k,@RequestBody TimeService.AttendanceRequest b,@AuthenticationPrincipal Jwt j){return out(time.capture(actor(j),key(k),req(b)));}
 @PostMapping("/attendance/import") public ResponseEntity<Object> attImport(@RequestHeader(value=K,required=false)String k,@RequestBody TimeService.ImportRequest b,@AuthenticationPrincipal Jwt j){return out(time.importBatch(actor(j),key(k),req(b)));}
 @PostMapping("/attendance/{id}/dispute") public ResponseEntity<Object> dispute(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody TimeService.DisputeRequest b,@AuthenticationPrincipal Jwt j){return out(time.dispute(actor(j),key(k),id,req(b)));}
 @PostMapping("/attendance/{id}/correction") public ResponseEntity<Object> correct(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody TimeService.CorrectionRequest b,@AuthenticationPrincipal Jwt j){return out(time.correct(actor(j),key(k),id,req(b)));}
 @PostMapping("/attendance/{id}/decision") public ResponseEntity<Object> attDecision(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody TimeService.Decision b,@AuthenticationPrincipal Jwt j){return out(time.decideAttendance(actor(j),key(k),id,req(b)));}
 @GetMapping("/leave-requests") public ResponseEntity<Map<String,Object>> leaves(@RequestParam(required=false)UUID staffId,@RequestParam(required=false)String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(time.listLeave(actor(j),staffId,status,page,size));}
 @PostMapping("/leave-requests") public ResponseEntity<Object> leave(@RequestHeader(value=K,required=false)String k,@RequestBody TimeService.LeaveRequestIn b,@AuthenticationPrincipal Jwt j){return out(time.requestLeave(actor(j),key(k),req(b)));}
 @PostMapping("/leave-requests/{id}/decision") public ResponseEntity<Object> leaveDecision(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody TimeService.Decision b,@AuthenticationPrincipal Jwt j){return out(time.decideLeave(actor(j),key(k),id,req(b)));}
 @GetMapping("/leave-balances/{staffId}") public ResponseEntity<List<Map<String,Object>>> balances(@PathVariable UUID staffId,@AuthenticationPrincipal Jwt j){return ok(time.balances(actor(j),staffId));}
 @GetMapping("/leave-policy") public ResponseEntity<Map<String,Object>> policy(@AuthenticationPrincipal Jwt j){return ok(time.leaveTypesAndHolidays(actor(j)));}
 @PostMapping("/leave-types") public ResponseEntity<Object> leaveType(@RequestHeader(value=K,required=false)String k,@RequestBody TimeService.LeaveTypeRequest b,@AuthenticationPrincipal Jwt j){return out(time.createLeaveType(actor(j),key(k),req(b)));}
 @PostMapping("/holidays") public ResponseEntity<Object> holiday(@RequestHeader(value=K,required=false)String k,@RequestBody TimeService.HolidayRequest b,@AuthenticationPrincipal Jwt j){return out(time.addHoliday(actor(j),key(k),req(b)));}

 // ---- S05 payroll preparation / S06 approval and payment reconciliation
 @GetMapping("/rule-sets") public ResponseEntity<List<Map<String,Object>>> ruleSets(@AuthenticationPrincipal Jwt j){return ok(payroll.listRuleSets(actor(j)));}
 @PostMapping("/rule-sets") public ResponseEntity<Object> ruleSet(@RequestHeader(value=K,required=false)String k,@RequestBody PayrollService.RuleSetRequest b,@AuthenticationPrincipal Jwt j){return out(payroll.createRuleSet(actor(j),key(k),req(b)));}
 @PostMapping("/rule-sets/{id}/decision") public ResponseEntity<Object> ruleDecision(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody PayrollService.Decision b,@AuthenticationPrincipal Jwt j){return out(payroll.decideRuleSet(actor(j),key(k),id,req(b)));}
 @GetMapping("/periods") public ResponseEntity<Map<String,Object>> periods(@AuthenticationPrincipal Jwt j){return ok(payroll.listPeriods(actor(j)));}
 @PostMapping("/periods") public ResponseEntity<Object> period(@RequestHeader(value=K,required=false)String k,@RequestBody PayrollService.PeriodRequest b,@AuthenticationPrincipal Jwt j){return out(payroll.createPeriod(actor(j),key(k),req(b)));}
 @PostMapping("/periods/{id}/runs") public ResponseEntity<Object> calculate(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@AuthenticationPrincipal Jwt j){return out(payroll.calculate(actor(j),key(k),id));}
 @PostMapping("/adjustments") public ResponseEntity<Object> adjustment(@RequestHeader(value=K,required=false)String k,@RequestBody PayrollService.AdjustmentRequest b,@AuthenticationPrincipal Jwt j){return out(payroll.requestAdjustment(actor(j),key(k),req(b)));}
 @PostMapping("/adjustments/{id}/decision") public ResponseEntity<Object> adjDecision(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody PayrollService.Decision b,@AuthenticationPrincipal Jwt j){return out(payroll.decideAdjustment(actor(j),key(k),id,req(b)));}
 @GetMapping("/runs") public ResponseEntity<Map<String,Object>> runs(@RequestParam(required=false)String status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size,@AuthenticationPrincipal Jwt j){return ok(payroll.listRuns(actor(j),status,page,size));}
 @GetMapping("/runs/{id}") public ResponseEntity<Map<String,Object>> run(@PathVariable UUID id,@AuthenticationPrincipal Jwt j){return ok(payroll.getRun(actor(j),id));}
 @PostMapping("/runs/{id}/submit") public ResponseEntity<Object> runSubmit(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody PayrollService.Transition b,@AuthenticationPrincipal Jwt j){return out(payroll.submit(actor(j),key(k),id,req(b)));}
 @PostMapping("/runs/{id}/decision") public ResponseEntity<Object> runDecision(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody PayrollService.Decision b,@AuthenticationPrincipal Jwt j){return out(payroll.decide(actor(j),key(k),id,req(b)));}
 @PostMapping("/runs/{id}/reversal") public ResponseEntity<Object> reversal(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody PayrollService.ReverseRequest b,@AuthenticationPrincipal Jwt j){return out(payroll.reverse(actor(j),key(k),id,req(b)));}
 @PostMapping("/runs/{id}/export") public ResponseEntity<Object> export(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@AuthenticationPrincipal Jwt j){return out(payroll.exportRun(actor(j),key(k),id));}
 @PostMapping("/runs/{id}/payments") public ResponseEntity<Object> instruct(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody PayrollService.Transition b,@AuthenticationPrincipal Jwt j){return out(payroll.instruct(actor(j),key(k),id,req(b)));}
 @GetMapping("/runs/{id}/payments") public ResponseEntity<Map<String,Object>> payments(@PathVariable UUID id,@AuthenticationPrincipal Jwt j){return ok(payroll.listInstructions(actor(j),id));}
 @PostMapping("/payments/{id}/confirmation") public ResponseEntity<Object> confirm(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody PayrollService.Confirmation b,@AuthenticationPrincipal Jwt j){return out(payroll.confirm(actor(j),key(k),id,req(b)));}
 @GetMapping("/runs/{id}/reconciliation") public ResponseEntity<Map<String,Object>> reconciliation(@PathVariable UUID id,@AuthenticationPrincipal Jwt j){return ok(payroll.getReconciliation(actor(j),id));}
 @PostMapping("/runs/{id}/reconcile") public ResponseEntity<Object> reconcile(@PathVariable UUID id,@RequestHeader(value=K,required=false)String k,@RequestBody PayrollService.Transition b,@AuthenticationPrincipal Jwt j){return out(payroll.reconcile(actor(j),key(k),id,req(b)));}
}
