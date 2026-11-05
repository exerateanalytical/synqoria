/**
 * SYNQORIA M04–M06 restricted longitudinal READ adapter.
 * Contract source: P07's contracts/openapi/synqoria-m040506-restricted-v1.json
 * and LongitudinalHttpHandler.java.  No clinical commands or demo PHI.
 * The P07 backend remains unmerged/unqualified: an absent clinical cell fails closed.
 */
import {admission,safeFetch,verifyPatient,validUuid,reject,ok} from "./clinical-cell.mjs";
import {authorizeEncounterDisclosure} from "./encounter-disclosure-authorization.mjs";
const numeric=/^(?:0|[1-9][0-9]{0,9})$/;
const codePattern=/^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$/;
const utc=/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?Z$/;
const sha256=/^[0-9a-f]{64}$/;
const clinicalOperation=/^[a-z][a-z0-9._-]{1,79}$/;
const noteStatuses=new Set(["DRAFT","REVIEW","READY","SIGNED","AMENDED","ENTERED_IN_ERROR"]);
const measurementStatuses=new Set(["CAPTURED","PENDING","VERIFIED","CORRECTED","INVALIDATED","REJECTED"]);
const fixedResources=new Map([
 ["summary","encounter.read"],["audit","encounter.audit.read"],["note-index","note.read"],
 ["observations","observation.read"],["note","note.read"],["lineage","observation.read"]
]);
const maxRead=32768;
const keys=(v,expected)=>v&&typeof v==="object"&&!Array.isArray(v)&&
 Object.keys(v).sort().join(",")===expected.slice().sort().join(",");
const validText=(s,max,mayBeEmpty=true)=>typeof s==="string"&&s.length<=max&&(mayBeEmpty||s.trim().length>0)&&!/\u0000/.test(s);
const validTime=s=>typeof s==="string"&&utc.test(s)&&Number.isFinite(Date.parse(s));
const validNumber=n=>Number.isSafeInteger(n)&&n>=0&&n<=2147483647;
const validNote=n=>keys(n,["id","status","author","title","body","confidential"])&&
 validUuid(n.id)&&validUuid(n.author)&&noteStatuses.has(n.status)&&
 validText(n.title,160)&&validText(n.body,8192)&&typeof n.confidential==="boolean";
const validSummary=(b,encounter)=>keys(b,["encounterId","revision","stage","onLeave","notes","observations","auditHead"])&&
 b.encounterId===encounter&&validNumber(b.revision)&&b.revision>0&&
 typeof b.stage==="string"&&/^[A-Z_]{2,40}$/.test(b.stage)&&typeof b.onLeave==="boolean"&&
 validNumber(b.notes)&&validNumber(b.observations)&&sha256.test(b.auditHead);
const validAudit=(b,after,limit)=> {
 if(!keys(b,["history","nextRevision"])||!Array.isArray(b.history)||b.history.length>limit)return false;
 let last=after;
 for(const row of b.history){
  if(!keys(row,["revision","operation","at","digest"])||!validNumber(row.revision)||row.revision<=last||
   !clinicalOperation.test(row.operation)||!validTime(row.at)||!sha256.test(row.digest))return false;
  last=row.revision;
 }
 return b.nextRevision===null||(validNumber(b.nextRevision)&&b.nextRevision===last&&b.history.length===limit);
};
const validNoteIndex=(b,limit)=>keys(b,["notes","nextId"])&&Array.isArray(b.notes)&&b.notes.length<=limit&&
 b.notes.every(n=>keys(n,["id","status","title","confidential"])&&validUuid(n.id)&&
 noteStatuses.has(n.status)&&validText(n.title,160)&&typeof n.confidential==="boolean")&&
 new Set(b.notes.map(n=>n.id)).size===b.notes.length&&
 (b.nextId===null||validUuid(b.nextId)&&b.notes.length===limit&&b.notes.at(-1)?.id===b.nextId);
const validMeasurement=(x,withLineage)=>keys(x,withLineage?
 ["id","code","value","unit","measuredAt","status","replaces"]:
 ["id","code","value","unit","measuredAt","status"])&&
 validUuid(x.id)&&validText(x.code,128,false)&&validText(x.value,256,false)&&
 validText(x.unit,64,false)&&validTime(x.measuredAt)&&measurementStatuses.has(x.status)&&
 (!withLineage||(x.replaces===null||validUuid(x.replaces)));
const validObservations=(b,limit,code)=>keys(b,["observations"])&&Array.isArray(b.observations)&&b.observations.length<=limit&&
 b.observations.every(x=>validMeasurement(x,false)&&x.code===code)&&
 new Set(b.observations.map(x=>x.id)).size===b.observations.length;
const validLineage=(b,requested)=>keys(b,["lineage"])&&Array.isArray(b.lineage)&&b.lineage.length>0&&b.lineage.length<=100&&
 b.lineage.every(x=>validMeasurement(x,true))&&
 new Set(b.lineage.map(x=>x.id)).size===b.lineage.length&&b.lineage.some(x=>x.id===requested)&&
 b.lineage.every(x=>x.replaces===null||b.lineage.some(p=>p.id===x.replaces))&&
 b.lineage.every(x=>!x.replaces||x.replaces!==x.id);

export function parseLongitudinalParams(resource,query){
 if(!fixedResources.has(resource)||!query||typeof query!=="object"||Array.isArray(query))return null;
 const allowed={
  summary:[],audit:["afterRevision","limit"],"note-index":["afterId","limit"],
  observations:["code","limit"],note:[],lineage:[]
 }[resource];
 if(Object.keys(query).some(k=>!allowed.includes(k))||
  Object.entries(query).some(([,value])=>typeof value!=="string"))return null;
 const result={};
 if(resource==="observations"){
  if(!codePattern.test(query.code||""))return null;
  result.code=query.code;
 }
 if(resource==="audit"){
  if(query.afterRevision!==undefined&&(!numeric.test(query.afterRevision)||Number(query.afterRevision)>2147483647))return null;
  result.afterRevision=query.afterRevision===undefined?0:Number(query.afterRevision);
 }
 if(resource==="note-index"&&query.afterId!==undefined){
  if(!validUuid(query.afterId))return null;
  result.afterId=query.afterId;
 }
 if(["audit","note-index","observations"].includes(resource)){
  if(query.limit!==undefined&&(!/^[1-9][0-9]{0,2}$/.test(query.limit)||Number(query.limit)>100))return null;
  result.limit=query.limit===undefined?50:Number(query.limit);
 }
 return result;
}
export function validateLongitudinalSource(resource,body,params,identifiers){
 if(resource==="summary")return validSummary(body,identifiers.encounterId);
 if(resource==="audit")return validAudit(body,params.afterRevision,params.limit);
 if(resource==="note-index")return validNoteIndex(body,params.limit);
 if(resource==="observations")return validObservations(body,params.limit,params.code);
 if(resource==="note")return validNote(body)&&body.id===identifiers.memberId;
 if(resource==="lineage")return validLineage(body,identifiers.memberId);
 return false;
}
/** Requires both a verified patient identity and per-resource longitudinal capability. */
export async function readLongitudinal(request,patientId,encounterId,resource,memberId=null,options={}){
 if(!validUuid(patientId)||!validUuid(encounterId)||memberId!==null&&!validUuid(memberId))return reject(400,"invalid_request");
 if(!fixedResources.has(resource)||(["note","lineage"].includes(resource)!==(memberId!==null)))return reject(404,"not_found");
 let query;
 try{
  const u=new URL(request.url),entries=[...u.searchParams.entries()];
  if(new Set(entries.map(([k])=>k)).size!==entries.length)return reject(400,"invalid_request");
  query=parseLongitudinalParams(resource,Object.fromEntries(entries));
 }catch{return reject(400,"invalid_request");}
 if(!query)return reject(400,"invalid_request");
 const capability=fixedResources.get(resource);
 const scope=await admission(request,{...options,capability,additionalCapabilities:["patient:view"]});
 if(scope.error)return scope.error;
 if((options.env??process.env).SYNQORIA_REQUIRE_ENCOUNTER_DISCLOSURE==="1"){
  try{
   await authorizeEncounterDisclosure(scope.staff,patientId,encounterId,resource,{
    origin:scope.origin,fetcher:scope.fetcher,nowMs:options.nowMs??Date.now()
   });
  }catch{return reject(404,"not_found");}
 }
 const patientRecord=await safeFetch(scope.origin,"/api/v1/patients/"+patientId,scope.staff.bearer,scope.fetcher);
 if(patientRecord.status!==200)return reject(patientRecord.status===401?401:patientRecord.status===404?404:503,"patient_unavailable");
 if(!verifyPatient(patientRecord.body,patientId))return reject(503,"invalid_source_record");
 const base="/api/v1/clinical/patients/"+patientId+"/encounters/"+encounterId;
 const suffix=resource==="note"?"/notes/"+memberId:resource==="lineage"?"/observations/"+memberId+"/lineage":"/"+resource;
 const q=resource==="audit"?"?afterRevision="+query.afterRevision+"&limit="+query.limit:
  resource==="note-index"?"?limit="+query.limit+(query.afterId?"&afterId="+query.afterId:""):
  resource==="observations"?"?code="+encodeURIComponent(query.code)+"&limit="+query.limit:"";
 const source=await safeFetch(scope.origin,base+suffix+q,scope.staff.bearer,scope.fetcher,{maxBytes:maxRead});
 if(source.status!==200)return reject(source.status===401?401:source.status===404?404:503,"clinical_resource_unavailable");
 if(!validateLongitudinalSource(resource,source.body,query,{patientId,encounterId,memberId}))
  return reject(503,"invalid_source_record");
 // Facility ID is derived from verified staff session, never from client path, query or body.
 return ok({resource,patientId,encounterId,facilityId:scope.staff.facilityId,data:source.body});
}
