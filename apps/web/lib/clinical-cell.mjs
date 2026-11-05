/** Verified server-side boundary for actual clinical reads. No fallback to demo data.
 * All requests are sent to a server-provisioned HTTPS cell (not caller-provided).
 * Browser requests never carry or receive upstream bearer tokens.
 */
import {readSessionCookie} from "./clinical-session.mjs";
import {authorizePatientDisclosure} from "./patient-disclosure-authorization.mjs";
import {authorizeAuditedPatientDisclosure} from "./audited-patient-disclosure.mjs";
import {requireClinicalRequestProvenance} from "./clinical-request-provenance.mjs";
import {requireClinicalCellAffinity} from "./clinical-cell-affinity.mjs";

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
export async function safeFetch(origin,path,bearer,fetcher,{method="GET",body=null,maxBytes=8192,requireNoStore=process.env.SYNQORIA_REQUIRE_UPSTREAM_NO_STORE==="1"}={}){
 const controller=new AbortController();
 const timeout=setTimeout(()=>controller.abort(),7000);
 try{
  const headers={Accept:"application/json"};
  if(bearer)headers.Authorization="Bearer "+bearer;
  if(method==="POST")headers["Content-Type"]="application/json";
  const r=await fetcher(origin+path,{method,headers,...(body===null?{}:{body:JSON.stringify(body)}),cache:"no-store",credentials:"omit",redirect:"error",signal:controller.signal});
  if(r.redirected===true||(r.url&&new URL(r.url).origin!==origin))return {status:503};
  if(requireNoStore&&!r.headers.get("cache-control")?.toLowerCase().includes("no-store"))return {status:503};
  if(r.status===401)return {status:401};
  if(r.status===403||r.status===404)return {status:404};
  if(r.status===503)return {status:503};
  if(r.status!==200||!r.headers.get("content-type")?.toLowerCase().startsWith("application/json"))return {status:503};
  const length=r.headers.get("content-length");
  if(length!==null&&(!/^\d{1,5}$/.test(length)||Number(length)>maxBytes))return {status:503};
  if(!r.body)return {status:503};
  const reader=r.body.getReader();
  const chunks=[];let size=0;
  try{for(;;){const n=await reader.read();if(n.done)break;size+=n.value.byteLength;if(size>maxBytes){await reader.cancel();return {status:503};}chunks.push(n.value);}}
  finally{reader.releaseLock();}
  const bytes=new Uint8Array(size);let off=0;for(const chunk of chunks){bytes.set(chunk,off);off+=chunk.length;}
  const parsedBody=JSON.parse(new TextDecoder("utf-8",{fatal:true}).decode(bytes));
  if(!parsedBody||typeof parsedBody!=="object"||Array.isArray(parsedBody))return {status:503};
  return {status:200,body:parsedBody};
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
export async function admission(request,{env=process.env,fetcher=fetch,capability="patient:view",additionalCapabilities=[],sessionResolver=null}={}){
 if(env?.SYNQORIA_REQUIRE_CLINICAL_SAME_ORIGIN==="1"){
  try{requireClinicalRequestProvenance(request,env.SYNQORIA_PUBLIC_ORIGIN);}
  catch{return {error:reject(403,"request_provenance_denied")};}
 }
 const opaqueRequired=env?.SYNQORIA_REQUIRE_OPAQUE_STAFF_SESSION==="1";
 if(opaqueRequired&&typeof sessionResolver!=="function")
  return {error:reject(503,"opaque_session_not_integrated")};
 let staff;
 if(sessionResolver!==null){
  if(typeof sessionResolver!=="function")
   return {error:reject(503,"session_resolver_invalid")};
  try{staff=await sessionResolver(request);}catch{return {error:reject(401,"authentication_required")};}
  const validSession=staff&&validUuid(staff.actorId)&&validUuid(staff.tenantId)&&
   validUuid(staff.facilityId)&&Number.isSafeInteger(staff.sessionExpiresAt)&&
   staff.sessionExpiresAt>Date.now()&&typeof staff.bearer==="string"&&
   /^[A-Za-z0-9_.~-]{16,8192}$/.test(staff.bearer)&&
   Array.isArray(staff.capabilities)&&staff.capabilities.length>0&&
   staff.capabilities.length<=64&&new Set(staff.capabilities).size===staff.capabilities.length&&
   staff.capabilities.every(x=>typeof x==="string"&&/^[a-z][a-z0-9:._-]{2,63}$/.test(x));
  if(!validSession)return {error:reject(401,"authentication_required")};
 }else staff=readSessionCookie(request.headers.get("cookie"),{env});
 if(!staff)return {error:reject(401,"authentication_required")};
 if(["production","staging"].includes(env?.SYNQORIA_RUNTIME_MODE)){
  try{requireClinicalCellAffinity(staff,env);}
  catch{return {error:reject(404,"not_found")};}
 }

 if(!staff.capabilities.includes(capability)||!additionalCapabilities.every(c=>staff.capabilities.includes(c)))return {error:reject(404,"not_found")};
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
 const disclosureEnv=options.env??process.env;
 if(disclosureEnv.SYNQORIA_AUDIT_DENIED_PATIENT_DISCLOSURE==="1"&&
    disclosureEnv.SYNQORIA_REQUIRE_PATIENT_DISCLOSURE!=="1")
  return reject(503,"patient_policy_not_integrated");
 if(disclosureEnv.SYNQORIA_REQUIRE_PATIENT_DISCLOSURE==="1"){
  try{
   const args={origin:scope.origin,fetcher:scope.fetcher,
    nowMs:options.nowMs??Date.now()};
   if(disclosureEnv.SYNQORIA_AUDIT_DENIED_PATIENT_DISCLOSURE==="1")
    await authorizeAuditedPatientDisclosure(scope.staff,patientId,"patient.demographics",{
     ...args,auditWriter:options.auditWriter
    });
   else await authorizePatientDisclosure(scope.staff,patientId,"patient.demographics",args);
  }catch{return reject(404,"not_found");}
 }
 const result=await safeFetch(scope.origin,"/api/v1/patients/"+encodeURIComponent(patientId),scope.staff.bearer,scope.fetcher);
 if(result.status!==200)return reject(result.status===401?401:result.status===404?404:503,result.status===404?"not_found":"patient_unavailable");
 if(!verifyPatient(result.body,patientId))return reject(503,"invalid_source_record");
 return ok({patientId,identityStatus:result.body.identityStatus,revision:result.body.revision,facilityId:scope.staff.facilityId});
}
export async function readTriageCase(request,patientId,caseId,options={}){
 if(!validUuid(patientId)||!validUuid(caseId))return reject(400,"invalid_request");
 const scope=await admission(request,{...options,capability:"triage:assess",additionalCapabilities:["patient:view"]});
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

const worklistViewNames=new Set(["FACILITY_QUEUE","STATION_QUEUE","PENDING_HANDOVERS","MANUAL_ESCALATIONS","PENDING_REASSESSMENTS","SAFETY_HOLDS","CLINICIAN_WORKLIST","ARRIVALS_SINCE"]);
export function validateTriageViewRequest(value){
 if(!value||typeof value!=="object"||Array.isArray(value))return null;
 const allowed=new Set(["view","caseIds","stationOrClinician","after"]);
 if(Object.keys(value).some(x=>!allowed.has(x)))return null;
 if(!worklistViewNames.has(value.view)||!Array.isArray(value.caseIds)||value.caseIds.length<1||value.caseIds.length>50||!value.caseIds.every(validUuid)||new Set(value.caseIds).size!==value.caseIds.length)return null;
 const station=["STATION_QUEUE","CLINICIAN_WORKLIST"].includes(value.view),time=value.view==="ARRIVALS_SINCE";
 if(station!==("stationOrClinician" in value)||time!==("after" in value))return null;
 if(station&&!validUuid(value.stationOrClinician))return null;
 if(time&&(typeof value.after!=="string"||!/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?Z$/.test(value.after)||!Number.isFinite(Date.parse(value.after))))return null;
 return Object.freeze({view:value.view,caseIds:Object.freeze([...value.caseIds]),...(station?{stationOrClinician:value.stationOrClinician}:{}),...(time?{after:value.after}:{})});
}
async function readBoundedJsonRequest(request,maxBytes=4096){
 if(!request.body)return null;
 const length=request.headers.get("content-length");
 if(length!==null&&(!/^\d{1,5}$/.test(length)||Number(length)>maxBytes))return null;
 const reader=request.body.getReader();const chunks=[];let size=0;
 try{
  for(;;){const entry=await reader.read();if(entry.done)break;size+=entry.value.byteLength;if(size>maxBytes){await reader.cancel();return null;}chunks.push(entry.value);}
 }catch{return null;}finally{reader.releaseLock();}
 try{
  const all=new Uint8Array(size);let offset=0;for(const x of chunks){all.set(x,offset);offset+=x.length;}
  return JSON.parse(new TextDecoder("utf-8",{fatal:true}).decode(all));
 }catch{return null;}
}
export async function readTriageWorklist(request,patientId,options={}){
 if(!validUuid(patientId))return reject(400,"invalid_request");
 const originHeader=request.headers.get("origin");
 // Behind reverse proxies Next's internal request URL may use a different host
 // from the browser-visible origin. Compare against the deployment-owned URL,
 // never the caller-controlled Host/X-Forwarded-Host headers.
 const expectedOrigin=options.env?.SYNQORIA_WEB_PUBLIC_ORIGIN??process.env.SYNQORIA_WEB_PUBLIC_ORIGIN??new URL(request.url).origin;
 let configuredWebOrigin;
 try {
  const u=new URL(expectedOrigin);
  if(u.username||u.password||u.search||u.hash||u.pathname!=="/"||
     (u.protocol!=="https:"&&!(u.protocol==="http:"&&["localhost","127.0.0.1"].includes(u.hostname))))
    return reject(503,"web_origin_not_configured");
  configuredWebOrigin=u.origin;
 }catch{return reject(503,"web_origin_not_configured");}
 if(!originHeader||originHeader!==configuredWebOrigin)return reject(403,"origin_denied");
 if(!/^application\/json(?:;\s*charset=utf-8)?$/i.test(request.headers.get("content-type")||""))return reject(415,"unsupported_media_type");
 const input=validateTriageViewRequest(await readBoundedJsonRequest(request));
 if(!input)return reject(400,"invalid_request");
 const scope=await admission(request,{...options,capability:"triage:queue",additionalCapabilities:["patient:view"]});
 if(scope.error)return scope.error;
 if(!scope.staff.capabilities.includes("patient:view"))return reject(404,"not_found");
 const patientRecord=await safeFetch(scope.origin,"/api/v1/patients/"+patientId,scope.staff.bearer,scope.fetcher);
 if(patientRecord.status!==200)return reject(patientRecord.status===401?401:patientRecord.status===404?404:503,"patient_unavailable");
 if(!verifyPatient(patientRecord.body,patientId))return reject(503,"invalid_source_record");
 const result=await safeFetch(scope.origin,"/api/v1/triage/patients/"+patientId+"/views/"+input.view,scope.staff.bearer,scope.fetcher,{method:"POST",body:Object.fromEntries(Object.entries(input).filter(([k])=>k!=="view")),maxBytes:32768});
 if(result.status!==200)return reject(result.status===401?401:result.status===404?404:503,"triage_unavailable");
 const entries=result.body.cases;
 if(Object.keys(result.body).length!==1||!Array.isArray(entries)||entries.length>input.caseIds.length||entries.length>50)return reject(503,"invalid_source_record");
 const seen=new Set();
 for(const row of entries){
  if(!row||typeof row.caseId!=="string"||!input.caseIds.includes(row.caseId)||seen.has(row.caseId)||!verifyTriageCase({case:row},row.caseId))return reject(503,"invalid_source_record");
  seen.add(row.caseId);
 }
 return ok({cases:entries,facilityId:scope.staff.facilityId,view:input.view});
}


/**
 * M03-S04: bounded patient-scoped reassessment/audit read. Never infers clinical
 * reassessment from an event name, and never permits an assessment write.
 * Facility, patient and case are verified before the audit source is queried.
 */
const auditOperation=/^[A-Z_]{2,80}$/;
const auditDigest=/^[0-9a-f]{64}$/;
const auditInstant=/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?Z$/;
export function validateTriageAuditPage(raw,afterRevision){
 if(!Number.isSafeInteger(afterRevision)||afterRevision<0||afterRevision>2147483647)return null;
 if(!raw||typeof raw!=="object"||Array.isArray(raw)||
   Object.keys(raw).sort().join(",")!=="history,nextAfterRevision"||
   !Array.isArray(raw.history)||raw.history.length>100)return null;
 let prior=afterRevision;
 const result=[];
 for(const item of raw.history){
  if(!item||typeof item!=="object"||Array.isArray(item)||
    Object.keys(item).sort().join(",")!=="digest,occurredAt,operation,revision"||
    !Number.isSafeInteger(item.revision)||item.revision<=prior||item.revision>2147483647||
    typeof item.operation!=="string"||!auditOperation.test(item.operation)||
    typeof item.occurredAt!=="string"||!auditInstant.test(item.occurredAt)||
    !Number.isFinite(Date.parse(item.occurredAt))||
    typeof item.digest!=="string"||!auditDigest.test(item.digest))return null;
  prior=item.revision;
  result.push({revision:item.revision,operation:item.operation,occurredAt:item.occurredAt,digest:item.digest});
 }
 const next=raw.nextAfterRevision;
 if(next!==null&&(!Number.isSafeInteger(next)||next!==prior||result.length!==100||prior===afterRevision))return null;
 return {history:result,nextAfterRevision:next};
}
export async function readTriageAudit(request,patientId,caseId,options={}){
 if(!validUuid(patientId)||!validUuid(caseId))return reject(400,"invalid_request");
 let afterRevision;
 try{
  const url=new URL(request.url);
  if([...url.searchParams.keys()].some(x=>x!=="afterRevision")||
   url.searchParams.getAll("afterRevision").length>1)return reject(400,"invalid_cursor");
  const raw=url.searchParams.get("afterRevision")??"0";
  if(!/^(?:0|[1-9]\d{0,9})$/.test(raw))return reject(400,"invalid_cursor");
  afterRevision=Number(raw);
  if(afterRevision>2147483647)return reject(400,"invalid_cursor");
 }catch{return reject(400,"invalid_cursor");}
 const scope=await admission(request,{...options,capability:"triage:assess",additionalCapabilities:["patient:view"]});
 if(scope.error)return scope.error;
 if(!scope.staff.capabilities.includes("patient:view"))return reject(404,"not_found");
 const patientSource=await safeFetch(scope.origin,"/api/v1/patients/"+patientId,scope.staff.bearer,scope.fetcher);
 if(patientSource.status!==200)return reject(patientSource.status===401?401:patientSource.status===404?404:503,"patient_unavailable");
 if(!verifyPatient(patientSource.body,patientId))return reject(503,"invalid_source_record");
 const stem="/api/v1/triage/patients/"+patientId+"/cases/"+caseId;
 const caseSource=await safeFetch(scope.origin,stem,scope.staff.bearer,scope.fetcher);
 if(caseSource.status!==200)return reject(caseSource.status===401?401:caseSource.status===404?404:503,"case_unavailable");
 if(!verifyTriageCase(caseSource.body,caseId))return reject(503,"invalid_source_record");
 const history=await safeFetch(scope.origin,stem+"/history?afterRevision="+afterRevision,scope.staff.bearer,scope.fetcher,{maxBytes:32768});
 if(history.status!==200)return reject(history.status===401?401:history.status===404?404:503,"audit_unavailable");
 const verified=validateTriageAuditPage(history.body,afterRevision);
 if(!verified||verified.history.some(event=>event.revision>caseSource.body.case.revision))return reject(503,"invalid_source_record");
 // Only source-verified non-PHI audit metadata is projected to the browser.
 return ok({
  patientId,caseId,facilityId:scope.staff.facilityId,
  currentCaseRevision:caseSource.body.case.revision,
  history:verified.history,nextAfterRevision:verified.nextAfterRevision
 });
}
