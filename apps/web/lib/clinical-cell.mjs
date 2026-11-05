/** Verified server-side boundary for actual clinical reads. No fallback to demo data.
 * All requests are sent to a server-provisioned HTTPS cell (not caller-provided).
 * Browser requests never carry or receive upstream bearer tokens.
 */
import {readSessionCookie} from "./clinical-session.mjs";

const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[89ab0-9a-f]{12}$/i;
const triageStages=new Set(["ARRIVED","WAITING","CALLED","ASSESSING","ASSESSED","PRIORITIZED","ASSIGNED","HANDED_OVER","CLOSED"]);
const triageCaseKeys=["caseId","stage","revision","station","assignee","arrivedAt","paused","safetyHold","manualEscalation"];
const privacyHeaders={"Cache-Control":"no-store, private","Pragma":"no-cache","X-Content-Type-Options":"nosniff","Referrer-Policy":"no-referrer","Content-Security-Policy":"default-src 'none'; frame-ancestors 'none'","X-Robots-Tag":"noindex, nofollow"};
export const validUuid=id=>typeof id==="string"&&uuid.test(id);
export const reject=(status,code)=>new Response(JSON.stringify({status,code}),{status,headers:{...privacyHeaders,"Content-Type":"application/json; charset=utf-8"}});
export const ok=(value)=>new Response(JSON.stringify(value),{status:200,headers:{...privacyHeaders,"Content-Type":"application/json; charset=utf-8"}});
export function configuredCellOrigin(env=process.env){
 const value=env.SYNQORIA_TRUSTED_CELL_ORIGIN;
 if(typeof value!=="string"||value.length>260)return null;
 try{
  const u=new URL(value);
  const hostname=u.hostname.toLowerCase();
  if(u.protocol!=="https:"||u.username||u.password||u.port||u.pathname!=="/"||u.search||u.hash||!hostname.includes(".")||hostname==="localhost"||hostname.endsWith(".localhost")||hostname.endsWith(".local")||hostname.endsWith(".invalid")||hostname.endsWith(".example")||hostname.endsWith(".test")||/^[0-9.]+$/.test(hostname)||hostname.includes(".."))return null;
  return u.origin;
 }catch{return null;}
}
async function safeFetch(origin,path,bearer,fetcher){
 const controller=new AbortController();
 const timeout=setTimeout(()=>controller.abort(),7000);
 try{
  const headers={Accept:"application/json"};
  if(bearer)headers.Authorization="Bearer "+bearer;
  const r=await fetcher(origin+path,{method:"GET",headers,cache:"no-store",credentials:"omit",redirect:"error",signal:controller.signal});
  if(r.status===401)return {status:401};
  if(r.status===403||r.status===404)return {status:404};
  if(r.status===503)return {status:503};
  if(r.status!==200||!r.headers.get("content-type")?.toLowerCase().startsWith("application/json"))return {status:503};
  const length=r.headers.get("content-length");
  if(length!==null&&(!/^\d{1,5}$/.test(length)||Number(length)>8192))return {status:503};
  if(!r.body)return {status:503};
  const reader=r.body.getReader();
  const chunks=[];let size=0;
  try{for(;;){const n=await reader.read();if(n.done)break;size+=n.value.byteLength;if(size>8192){await reader.cancel();return {status:503};}chunks.push(n.value);}}
  finally{reader.releaseLock();}
  const bytes=new Uint8Array(size);let off=0;for(const chunk of chunks){bytes.set(chunk,off);off+=chunk.length;}
  const body=JSON.parse(new TextDecoder("utf-8",{fatal:true}).decode(bytes));
  if(!body||typeof body!=="object"||Array.isArray(body))return {status:503};
  return {status:200,body};
 }catch{return {status:503};}
 finally{clearTimeout(timeout);}
}
function verifyFacility(dto,session){
 return dto&&Object.keys(dto).sort().join(",")==="facilityId,revision"&&dto.facilityId===session.facilityId&&Number.isSafeInteger(dto.revision)&&dto.revision>0;
}
export function verifyPatient(dto,id){
 return dto&&Object.keys(dto).sort().join(",")==="identityStatus,patientId,revision"&&dto.patientId===id&&["verified","provisional","merged"].includes(dto.identityStatus)&&Number.isSafeInteger(dto.revision)&&dto.revision>0;
}
export function verifyTriageCase(dto,caseId){
 if(!dto||Object.keys(dto).join(",")!=="case")return false;
 const x=dto.case;
 if(!x||typeof x!=="object"||Array.isArray(x)||Object.keys(x).sort().join(",")!==[...triageCaseKeys].sort().join(","))return false;
 return x.caseId===caseId&&triageStages.has(x.stage)&&Number.isSafeInteger(x.revision)&&x.revision>0&&x.revision<=2147483647&&
  (x.station===null||validUuid(x.station))&&(x.assignee===null||validUuid(x.assignee))&&
  typeof x.arrivedAt==="string"&&/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?Z$/.test(x.arrivedAt)&&Number.isFinite(Date.parse(x.arrivedAt))&&
  ["paused","safetyHold","manualEscalation"].every(k=>typeof x[k]==="boolean");
}
async function admission(request,{env=process.env,fetcher=fetch,capability="patient:view"}={}){
 const staff=readSessionCookie(request.headers.get("cookie"),{env});
 if(!staff)return {error:reject(401,"authentication_required")};
 if(!staff.capabilities.includes(capability))return {error:reject(404,"not_found")};
 const origin=configuredCellOrigin(env);
 if(!origin)return {error:reject(503,"cell_not_integrated")};
 const facility=await safeFetch(origin,"/api/v1/facility/context",staff.bearer,fetcher);
 if(facility.status!==200)return {error:reject(facility.status===401?401:facility.status===404?404:503,"facility_unavailable")};
 if(!verifyFacility(facility.body,staff))return {error:reject(404,"not_found")};
 return {staff,origin,fetcher,facility:facility.body};
}
export async function readFacility(request,options={}){
 const scope=await admission(request,{...options,capability:"facility:view"});
 if(scope.error)return scope.error;
 return ok({facilityId:scope.facility.facilityId,revision:scope.facility.revision});
}
export async function readPatient(request,patientId,options={}){
 if(!validUuid(patientId))return reject(400,"invalid_request");
 const scope=await admission(request,{...options,capability:"patient:view"});
 if(scope.error)return scope.error;
 const result=await safeFetch(scope.origin,"/api/v1/patients/"+encodeURIComponent(patientId),scope.staff.bearer,scope.fetcher);
 if(result.status!==200)return reject(result.status===401?401:result.status===404?404:503,result.status===404?"not_found":"patient_unavailable");
 if(!verifyPatient(result.body,patientId))return reject(503,"invalid_source_record");
 return ok({patientId,identityStatus:result.body.identityStatus,revision:result.body.revision,facilityId:scope.staff.facilityId});
}
export async function readTriageCase(request,patientId,caseId,options={}){
 if(!validUuid(patientId)||!validUuid(caseId))return reject(400,"invalid_request");
 const scope=await admission(request,{...options,capability:"triage:assess"});
 if(scope.error)return scope.error;
 if(!scope.staff.capabilities.includes("patient:view"))return reject(404,"not_found");
 const record=await safeFetch(scope.origin,"/api/v1/patients/"+encodeURIComponent(patientId),scope.staff.bearer,scope.fetcher);
 if(record.status!==200)return reject(record.status===401?401:record.status===404?404:503,"patient_unavailable");
 if(!verifyPatient(record.body,patientId))return reject(503,"invalid_source_record");
 const path="/api/v1/triage/patients/"+encodeURIComponent(patientId)+"/cases/"+encodeURIComponent(caseId);
 const result=await safeFetch(scope.origin,path,scope.staff.bearer,scope.fetcher);
 if(result.status!==200)return reject(result.status===401?401:result.status===404?404:503,"triage_unavailable");
 if(!verifyTriageCase(result.body,caseId))return reject(503,"invalid_source_record");
 return ok({case:result.body.case,facilityId:scope.staff.facilityId});
}
export async function readCellHealth(request,options={}){
 const env=options.env??process.env,fetcher=options.fetcher??fetch;
 const origin=configuredCellOrigin(env);
 if(!origin)return reject(503,"cell_not_integrated");
 const [live,ready]=await Promise.all([
  safeFetch(origin,"/health/live",null,fetcher),
  safeFetch(origin,"/health/ready",null,fetcher)
 ]);
 if(live.status!==200||live.body?.process!=="alive")return reject(503,"clinical_service_unavailable");
 if(ready.status!==200||ready.body?.status!=="ready")return reject(503,"clinical_integrations_not_ready");
 return ok({process:"alive",readiness:"ready",kind:"service_health_only",clinicalCertification:false});
}
