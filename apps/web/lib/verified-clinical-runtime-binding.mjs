/**
 * SYNQORIA P03-B0063: verified staff identity -> clinical read composition.
 *
 * Only an instance issued by createVerifiedStaffIdentityRuntime() may supply
 * hospital read authentication. The binding snapshots server-owned hospital
 * context and exposes no token/secret/environment configuration to browsers.
 * A missing live SQL/IdP/workforce/clinical-policy source denies reads.
 *
 * This is the deployable composition contract, not a claim that the Next
 * routes, patient policy services or hospital infrastructure are live.
 */
import {assertVerifiedStaffIdentityRuntime} from "./staff-identity-runtime.mjs";
import {requireClinicalIdentitySafety} from "./clinical-identity-safety.mjs";
import {readFacility,readPatient} from "./clinical-cell.mjs";
import {readLongitudinal} from "./longitudinal-cell.mjs";

export class ClinicalRuntimeBindingError extends Error{
 constructor(){super("clinical_runtime_binding_denied");this.name="ClinicalRuntimeBindingError";}
}
const deny=()=>{throw new ClinicalRuntimeBindingError();};
const names=[
 "SYNQORIA_RUNTIME_MODE","SYNQORIA_PUBLIC_ORIGIN",
 "SYNQORIA_TRUSTED_CELL_ORIGIN","SYNQORIA_EXPECTED_TENANT_ID",
 "SYNQORIA_EXPECTED_FACILITY_ID","SYNQORIA_REQUIRE_OPAQUE_STAFF_SESSION",
 "SYNQORIA_REQUIRE_CLINICAL_SAME_ORIGIN","SYNQORIA_REQUIRE_UPSTREAM_NO_STORE",
 "SYNQORIA_REQUIRE_PATIENT_DISCLOSURE","SYNQORIA_AUDIT_DENIED_PATIENT_DISCLOSURE",
 "SYNQORIA_REQUIRE_ENCOUNTER_DISCLOSURE","SYNQORIA_AUDIT_DENIED_ENCOUNTER_DISCLOSURE"
];
export function createVerifiedClinicalRuntimeBinding(runtime,env,{
 fetcher=fetch,now=Date.now
}={}){
 try{
  assertVerifiedStaffIdentityRuntime(runtime);
  requireClinicalIdentitySafety(env);
 }catch{deny();}
 if(typeof fetcher!=="function"||typeof now!=="function"||
    !runtime?.configuration||runtime.configuration.publicOrigin!==env.SYNQORIA_PUBLIC_ORIGIN||
    runtime.configuration.expectedTenantId!==env.SYNQORIA_EXPECTED_TENANT_ID||
    runtime.configuration.expectedFacilityId!==env.SYNQORIA_EXPECTED_FACILITY_ID||
    !runtime.auditWriter||typeof runtime.auditWriter.append!=="function"||
    typeof runtime.authorize!=="function")deny();
 const snapshot=Object.freeze(Object.fromEntries(names.map(name=>[name,env[name]])));
 const options=()=>{
  const nowMs=now();
  if(!Number.isSafeInteger(nowMs))deny();
  return {env:snapshot,fetcher,nowMs,
   sessionResolver:request=>runtime.authorize(request),
   auditWriter:runtime.auditWriter};
 };
 return Object.freeze({
  readFacility:request=>readFacility(request,options()),
  readPatient:(request,patientId)=>readPatient(request,patientId,options()),
  readLongitudinal:(request,patientId,encounterId,resource,memberId=null)=>
    readLongitudinal(request,patientId,encounterId,resource,memberId,options())
 });
}
