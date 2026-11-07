import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const read=x=>readFileSync(x,"utf8");
const contract=read("contracts/openapi/synqoria-m02-scheduling-v1.yaml");
const java=read("services/scheduling-core/src/main/java/org/synqoria/scheduling/SchedulingApi.java");
const ids=["listScopedScheduling","registerSchedulingResource","publishAvailableSlot","requestAppointment","transitionAppointment","rescheduleAppointment","joinResourceWaitlist","offerWaitlistSlot","acceptWaitlistSlot"];
test("M02 versioned API-first contract and operation uniqueness",()=>{
 assert.match(contract,/openapi: 3\.1\.0/);
 assert.match(contract,/securitySchemes:/);
 assert.match(contract,/Idempotency-Key/);
 assert.match(contract,/responses:/);
 assert.equal(new Set(ids).size,ids.length);
 for(const operation of ids)assert.ok(contract.includes("operationId: "+operation));
 assert.ok(java.includes("@RequestMapping("));
});
test("M02 status transitions describe HTTP200 rather than HTTP201",()=>{
 for(const op of ["transitionAppointment","rescheduleAppointment","offerWaitlistSlot","acceptWaitlistSlot"]){
  const position=contract.indexOf("operationId: "+op);
  const after=contract.substring(position,position+2000);
  const response=after.indexOf("      responses:");
  assert.ok(response>=0,op);
  assert.ok(after.substring(response).startsWith("      responses:\n        '200':"),op);
 }
});
