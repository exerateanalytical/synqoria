/**
 * SYNQORIA P03-B0041 — positive patient-by-patient disclosure authority.
 *
 * Staff authentication and facility membership are NOT authorization to read
 * an arbitrary patient record. When enabled by the server, request a fresh,
 * exact patient/resource/purpose decision from the authoritative hospital cell
 * before any patient or encounter source read. If policy is absent, malformed,
 * late, or denied, the data source MUST NOT be called.
 *
 * Back-end patient policy endpoint and clinical approval remain unimplemented
 * in the currently published P07 clinical core; fail-closed is intentional.
 */
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[89ab0-9a-f]{12}$/i;
const resources=new Set(["patient.demographics","encounter.summary","observation.read","note.read"]);
const exactKeys="actorId,decision,expiresAt,facilityId,patientId,resource,revision,tenantId";
export class PatientScopeAuthorizationError extends Error {
 constructor(){super("patient_scope_denied");this.name="PatientScopeAuthorizationError";}
}
const deny=()=>{throw new PatientScopeAuthorizationError();};
const isUuid=s=>typeof s==="string"&&UUID.test(s);
async function readBounded(response){
 if(response?.status!==200||response.redirected===true||!response.body||
   !response.headers?.get("content-type")?.toLowerCase().startsWith("application/json")||
   !response.headers?.get("cache-control")?.toLowerCase().includes("no-store"))deny();
 const header=response.headers.get("content-length");
 if(header!==null&&(!/^\d{1,5}$/.test(header)||Number(header)>4096))deny();
 const reader=response.body.getReader(),chunks=[];let size=0;
 try{
  for(;;){const n=await reader.read();if(n.done)break;size+=n.value.byteLength;
   if(size>4096){await reader.cancel();deny();}chunks.push(n.value);}
 }catch{deny();}finally{reader.releaseLock();}
 const all=new Uint8Array(size);let off=0;
 for(const chunk of chunks){all.set(chunk,off);off+=chunk.length;}
 try{const dto=JSON.parse(new TextDecoder("utf-8",{fatal:true}).decode(all));
  if(!dto||typeof dto!=="object"||Array.isArray(dto))deny();
  return dto;
 }catch{deny();}
}
export async function authorizePatientDisclosure(staff,patientId,resource,{
 origin,fetcher=fetch,nowMs=Date.now()
}={}){
 if(!staff||typeof staff!=="object"||
   ![staff.actorId,staff.tenantId,staff.facilityId,patientId].every(isUuid)||
   !Array.isArray(staff.capabilities)||!staff.capabilities.includes("patient:view")||
   typeof staff.bearer!=="string"||staff.bearer.length<16||
   !resources.has(resource)||typeof origin!=="string"||
   !/^https:\/\/[^/?#]+$/.test(origin)||typeof fetcher!=="function"||
   !Number.isSafeInteger(nowMs))deny();
 const input={actorId:staff.actorId,tenantId:staff.tenantId,facilityId:staff.facilityId,
  patientId,resource,purpose:"TREATMENT"};
 const controller=new AbortController(),timeout=setTimeout(()=>controller.abort(),7000);
 try{
  const response=await fetcher(origin+"/api/v1/security/patient-disclosure",{
   method:"POST",headers:{Accept:"application/json","Content-Type":"application/json",
    Authorization:"Bearer "+staff.bearer},
   body:JSON.stringify(input),cache:"no-store",credentials:"omit",redirect:"error",signal:controller.signal
  });
  const dto=await readBounded(response);
  if(Object.keys(dto).sort().join(",")!==exactKeys||dto.decision!=="ALLOW"||
     dto.actorId!==staff.actorId||dto.tenantId!==staff.tenantId||
     dto.facilityId!==staff.facilityId||dto.patientId!==patientId||
     dto.resource!==resource||!Number.isSafeInteger(dto.revision)||dto.revision<1||
     !Number.isSafeInteger(dto.expiresAt)||dto.expiresAt<=nowMs||
     dto.expiresAt>nowMs+60000)deny();
  return Object.freeze({patientId,resource,revision:dto.revision,expiresAt:dto.expiresAt});
 }catch{deny();}
 finally{clearTimeout(timeout);}
}
