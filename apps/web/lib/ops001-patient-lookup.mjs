/**
 * SYNQ-OPS-001 / G-S05: exact-identifier, authorized patient lookup.
 * No query string, local cache or speculative demographic search. A patient
 * record is fetched ONLY by the verified clinical staff runtime and the
 * existing patient-specific disclosure guard, never a browser-supplied token.
 */
import {createVerifiedClinicalRuntimeBinding} from "./verified-clinical-runtime-binding.mjs";
import {requireClinicalRequestProvenance} from "./clinical-request-provenance.mjs";

const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[89ab0-9a-f]{12}$/i;
const PRIVACY={"Cache-Control":"no-store, private","Pragma":"no-cache",
 "Referrer-Policy":"no-referrer","X-Content-Type-Options":"nosniff",
 "Content-Security-Policy":"default-src 'none'; frame-ancestors 'none'"};
export class Ops001LookupError extends Error{
 constructor(){super("patient_lookup_invalid");this.name="Ops001LookupError";}
}
const deny=()=>{throw new Ops001LookupError();};
const response=(status,code)=>new Response(JSON.stringify({status,code}),{
 status,headers:{...PRIVACY,"Content-Type":"application/json; charset=utf-8"}
});
/** Accept only a bounded, exact schema. No names/phone searches without reviewed server policy. */
export async function readCanonicalPatientLookup(request,publicOrigin){
 if(request?.method!=="POST"||
    request?.headers?.get?.("content-type")!=="application/json")deny();
 try{requireClinicalRequestProvenance(request,publicOrigin);}catch{deny();}
 const u=new URL(request.url);
 if(u.pathname!=="/api/clinical/patient-lookup"||u.search||u.hash)deny();
 const length=request.headers.get("content-length");
 if(length!==null&&(!/^\d{1,3}$/.test(length)||Number(length)>256))deny();
 if(!request.body)deny();
 const reader=request.body.getReader();let size=0;const chunks=[];
 try{
  for(;;){
   const item=await reader.read();
   if(item.done)break;
   size+=item.value.byteLength;
   if(size>256){await reader.cancel();deny();}
   chunks.push(item.value);
  }
 }catch{deny();}finally{reader.releaseLock();}
 if(size<30)deny();
 const bytes=new Uint8Array(size);let offset=0;
 for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.byteLength;}
 let parsed;
 try{parsed=JSON.parse(new TextDecoder("utf-8",{fatal:true}).decode(bytes));}
 catch{deny();}
 if(!parsed||typeof parsed!=="object"||Array.isArray(parsed)||
    Object.keys(parsed).length!==1||Object.keys(parsed)[0]!=="patientId"||
    typeof parsed.patientId!=="string"||!UUID.test(parsed.patientId))deny();
 return Object.freeze({patientId:parsed.patientId});
}
export function createVerifiedOps001Lookup(runtime,env,{fetcher=fetch,now=Date.now}={}){
 const clinical=createVerifiedClinicalRuntimeBinding(runtime,env,{fetcher,now});
 return Object.freeze({
  async lookup(request){
   let parsed;
   try{parsed=await readCanonicalPatientLookup(request,env.SYNQORIA_PUBLIC_ORIGIN);}
   catch{return response(400,"invalid_request");}
   try{
    const result=await clinical.readPatient(request,parsed.patientId);
    // Opaque disclosure, consent and patient existence remain backend-controlled.
    if(!(result instanceof Response)||!result.headers.get("cache-control")?.includes("no-store"))
     return response(503,"source_unavailable");
    return result;
   }catch{return response(503,"source_unavailable");}
  },
  readFacility:request=>clinical.readFacility(request)
 });
}
