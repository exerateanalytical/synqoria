/**
 * SYNQORIA P03-B0054: production/clinical staging identity policy gate.
 *
 * The old sealed/demo session mode and opt-in disclosure flags cannot be
 * relied upon for clinical deployments. A production identity runtime must
 * explicitly refuse startup unless real opaque reauthorization, patient and
 * encounter policy, and denied-access pseudonymous audit are required.
 * This does not assert that the remote policy service is deployed.
 */
const REQUIRED=[
 "SYNQORIA_REQUIRE_OPAQUE_STAFF_SESSION",
 "SYNQORIA_REQUIRE_PATIENT_DISCLOSURE",
 "SYNQORIA_REQUIRE_ENCOUNTER_DISCLOSURE",
 "SYNQORIA_REQUIRE_CLINICAL_SAME_ORIGIN",
 "SYNQORIA_REQUIRE_UPSTREAM_NO_STORE",
 "SYNQORIA_AUDIT_DENIED_PATIENT_DISCLOSURE",
 "SYNQORIA_AUDIT_DENIED_ENCOUNTER_DISCLOSURE"
];
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[89ab0-9a-f]{12}$/i;
export class ClinicalIdentitySafetyError extends Error{
 constructor(){super("clinical_identity_safety_gate_denied");this.name="ClinicalIdentitySafetyError";}
}
const deny=()=>{throw new ClinicalIdentitySafetyError();};
export function requireClinicalIdentitySafety(env){
 if(!env||typeof env!=="object"||
    !["production","staging"].includes(env.SYNQORIA_RUNTIME_MODE)||
    REQUIRED.some(name=>env[name]!=="1")||
    typeof env.SYNQORIA_ROLE_POLICY_JSON!=="string"||
    env.SYNQORIA_ROLE_POLICY_JSON.length<24||env.SYNQORIA_ROLE_POLICY_JSON.length>16384||
    env.SYNQORIA_ALLOW_DEMO_SESSIONS!==undefined||
    env.SYNQORIA_ALLOW_LEGACY_STAFF_COOKIE!==undefined||
    env.SYNQORIA_ALLOW_UNVERIFIED_STAFF!==undefined)deny();
 const endpoint=env.SYNQORIA_TRUSTED_CELL_ORIGIN;
 if(typeof endpoint!=="string"||endpoint.length>260)deny();
 let url;
 try{url=new URL(endpoint);}catch{deny();}
 if(url.protocol!=="https:"||url.origin+"/"!==endpoint||
    url.username||url.password||url.port||url.search||url.hash||
    url.pathname!=="/"||!url.hostname.includes(".")||
    [".localhost",".local",".test",".invalid",".example"].some(x=>url.hostname.endsWith(x))||
    /^[0-9.]+$/.test(url.hostname)||
    !uuid.test(env.SYNQORIA_EXPECTED_TENANT_ID)||
    !uuid.test(env.SYNQORIA_EXPECTED_FACILITY_ID))deny();
 return Object.freeze({mode:env.SYNQORIA_RUNTIME_MODE,
  patientDisclosureRequired:true,encounterDisclosureRequired:true,
  opaqueStaffReauthorizationRequired:true,denialAuditingRequired:true});
}
