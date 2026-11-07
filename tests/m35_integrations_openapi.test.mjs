import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const read=x=>readFileSync(x,"utf8");
const contract=read("contracts/openapi/synqoria-m35-integrations-v1.yaml");
const java=read("services/integration-management-core/src/main/java/org/synqoria/integrationmanagement/IntegrationApi.java");
const ids=["listIntegrationGovernance","createPartner","transitionPartner","createApiProduct","publishApiProduct","requestDeveloperApplication","reviewDeveloperApplication","proposeMapping","reviewMapping","ingestTrustedSourceMetadata","reviewQuarantinedMessage","requestReplayReview","reviewReplayWithoutDispatch"];
test("M35 versioned API-first contract and operation uniqueness",()=>{
 assert.match(contract,/openapi: 3\.1\.0/);
 assert.match(contract,/securitySchemes:/);
 assert.match(contract,/Idempotency-Key/);
 assert.match(contract,/responses:/);
 assert.equal(new Set(ids).size,ids.length);
 for(const operation of ids)assert.ok(contract.includes("operationId: "+operation));
 assert.ok(java.includes("@RequestMapping("));
});
test("M35 status transitions describe HTTP200 rather than HTTP201",()=>{
 for(const op of ["transitionPartner","publishApiProduct","reviewDeveloperApplication","reviewMapping","reviewQuarantinedMessage","reviewReplayWithoutDispatch"]){
  const position=contract.indexOf("operationId: "+op);
  const after=contract.substring(position,position+2000);
  const response=after.indexOf("      responses:");
  assert.ok(response>=0,op);
  assert.ok(after.substring(response).startsWith("      responses:\n        '200':"),op);
 }
});
