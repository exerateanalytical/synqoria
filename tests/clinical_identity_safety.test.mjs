import test from "node:test";
import assert from "node:assert/strict";
import {requireClinicalIdentitySafety,ClinicalIdentitySafetyError} from "../apps/web/lib/clinical-identity-safety.mjs";
const env={
 SYNQORIA_RUNTIME_MODE:"production",
 SYNQORIA_TRUSTED_CELL_ORIGIN:"https://clinical.synqoria.org/",
 SYNQORIA_EXPECTED_TENANT_ID:"11111111-1111-4111-8111-111111111111",
 SYNQORIA_EXPECTED_FACILITY_ID:"22222222-2222-4222-8222-222222222222",
 SYNQORIA_REQUIRE_OPAQUE_STAFF_SESSION:"1",
 SYNQORIA_REQUIRE_PATIENT_DISCLOSURE:"1",
 SYNQORIA_REQUIRE_ENCOUNTER_DISCLOSURE:"1",
 SYNQORIA_AUDIT_DENIED_PATIENT_DISCLOSURE:"1",
 SYNQORIA_AUDIT_DENIED_ENCOUNTER_DISCLOSURE:"1"
};
const deny=obj=>assert.throws(()=>requireClinicalIdentitySafety(obj),ClinicalIdentitySafetyError);
test("B0054 explicitly enabled clinical identity security gates are immutable",()=>{
 const r=requireClinicalIdentitySafety(env);
 assert.deepEqual(r,{mode:"production",patientDisclosureRequired:true,
  encounterDisclosureRequired:true,opaqueStaffReauthorizationRequired:true,denialAuditingRequired:true});
 assert.equal(Object.isFrozen(r),true);
 assert.equal(requireClinicalIdentitySafety({...env,SYNQORIA_RUNTIME_MODE:"staging"}).mode,"staging");
});
test("B0054 any missing/disabled patient, encounter, audit or opaque session guard prevents production startup",()=>{
 for(const name of [
 "SYNQORIA_REQUIRE_OPAQUE_STAFF_SESSION","SYNQORIA_REQUIRE_PATIENT_DISCLOSURE",
 "SYNQORIA_REQUIRE_ENCOUNTER_DISCLOSURE","SYNQORIA_AUDIT_DENIED_PATIENT_DISCLOSURE",
 "SYNQORIA_AUDIT_DENIED_ENCOUNTER_DISCLOSURE"
 ])for(const state of ["0",undefined,"true","false"])deny({...env,[name]:state});
});
test("B0054 demo and legacy cookie bypass flags are rejected even if set to false",()=>{
 for(const name of ["SYNQORIA_ALLOW_DEMO_SESSIONS","SYNQORIA_ALLOW_LEGACY_STAFF_COOKIE",
  "SYNQORIA_ALLOW_UNVERIFIED_STAFF"])
  for(const value of ["0","1","false"])deny({...env,[name]:value});
});
test("B0054 no guessed mode, local HTTP cell or swapped tenant/facility can start",()=>{
 for(const val of ["dev","test",undefined,"demo"])deny({...env,SYNQORIA_RUNTIME_MODE:val});
 for(const url of ["http://clinical.synqoria.org/","https://localhost/",
   "https://clinical.synqoria.org:8443/","https://clinical.synqoria.org/internal",
   "https://127.0.0.1/","https://evil.example/"])deny({...env,SYNQORIA_TRUSTED_CELL_ORIGIN:url});
 deny({...env,SYNQORIA_EXPECTED_TENANT_ID:"fake"});
 deny({...env,SYNQORIA_EXPECTED_FACILITY_ID:"fake"});
});
