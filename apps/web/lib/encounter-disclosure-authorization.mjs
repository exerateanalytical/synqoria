/**
 * SYNQORIA P03-B0042 — positive encounter-scoped disclosure.
 *
 * A positive general patient record decision does not authorize every note,
 * audit or observation of that patient's encounter. Each longitudinal read
 * must have an explicit fresh decision for the exact actor, tenant, facility,
 * patient, encounter and resource type, in addition to the P07 backend gate.
 * Source endpoint may be unavailable until P07 policy code is integrated.
 */
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[89ab0-9a-f]{12}$/i;
const resources=new Map([
 ["summary","encounter.summary"],["audit","encounter.audit"],
 ["note-index","note.read"],["note","note.read"],
 ["observations","observation.read"],["lineage","observation.read"]
]);
const keys="actorId,decision,encounterId,expiresAt,facilityId,patientId,resource,revision,tenantId";
export class EncounterDisclosureError extends Error{
 constructor(){super("encounter_disclosure_denied");this.name="EncounterDisclosureError";}
}
const deny=()=>{throw new EncounterDisclosureError();};
const trustedOrigin=value=>{
 try{
  const u=new URL(value);
  return u.protocol==="https:"&&u.origin===value&&u.pathname==="/"&&
   !u.username&&!u.password&&!u.port&&!u.search&&!u.hash&&u.hostname.includes(".")&&
   ![".invalid",".test",".example",".localhost",".local"].some(s=>u.hostname.endsWith(s))&&
   !/^[0-9.]+$/.test(u.hostname);
 }catch{return false;}
};
async function payload(response){
 if(response?.status!==200||response.redirected===true||!response.body||
  !response.headers?.get("content-type")?.toLowerCase().startsWith("application/json")||
  !response.headers?.get("cache-control")?.toLowerCase().includes("no-store"))deny();
 const h=response.headers.get("content-length");
 if(h!==null&&(!/^\d{1,5}$/.test(h)||Number(h)>4096))deny();
 const reader=response.body.getReader();let chunks=[],size=0;
 try{for(;;){const n=await reader.read();if(n.done)break;size+=n.value.byteLength;
  if(size>4096){await reader.cancel();deny();}chunks.push(n.value);}}
 catch{deny();}finally{reader.releaseLock();}
 const all=new Uint8Array(size);let offset=0;
 for(const n of chunks){all.set(n,offset);offset+=n.length;}
 try{const out=JSON.parse(new TextDecoder("utf-8",{fatal:true}).decode(all));
  if(!out||typeof out!=="object"||Array.isArray(out))deny();
  return out;
 }catch{deny();}
}
export async function authorizeEncounterDisclosure(staff,patientId,encounterId,operation,{
 origin,fetcher=fetch,nowMs=Date.now()
}={}){
 if(!staff||![staff.actorId,staff.tenantId,staff.facilityId,patientId,encounterId].every(x=>
  typeof x==="string"&&UUID.test(x))||
  !staff.capabilities?.includes("patient:view")||
  typeof staff.bearer!=="string"||staff.bearer.length<16||
  !resources.has(operation)||typeof origin!=="string"||
  !trustedOrigin(origin)||typeof fetcher!=="function"||
  !Number.isSafeInteger(nowMs))deny();
 const resource=resources.get(operation);
 const requestBody=JSON.stringify({actorId:staff.actorId,tenantId:staff.tenantId,
  facilityId:staff.facilityId,patientId,encounterId,resource,purpose:"TREATMENT"});
 const controller=new AbortController(),timeout=setTimeout(()=>controller.abort(),7000);
 try{
  const response=await fetcher(origin+"/api/v1/security/encounter-disclosure",{
   method:"POST",headers:{Accept:"application/json","Content-Type":"application/json",
    Authorization:"Bearer "+staff.bearer},
   body:requestBody,cache:"no-store",credentials:"omit",redirect:"error",signal:controller.signal
  });
  const dto=await payload(response);
  if(Object.keys(dto).sort().join(",")!==keys||dto.decision!=="ALLOW"||
    dto.actorId!==staff.actorId||dto.tenantId!==staff.tenantId||
    dto.facilityId!==staff.facilityId||dto.patientId!==patientId||
    dto.encounterId!==encounterId||dto.resource!==resource||
    !Number.isSafeInteger(dto.revision)||dto.revision<1||
    !Number.isSafeInteger(dto.expiresAt)||dto.expiresAt<=nowMs||
    dto.expiresAt>nowMs+60000)deny();
  return Object.freeze({patientId,encounterId,resource,expiresAt:dto.expiresAt,
   revision:dto.revision});
 }catch{deny();}
 finally{clearTimeout(timeout);}
}
