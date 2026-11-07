/** Restricted M03 staff transport client. Not a replacement for server-side authorization.
 * Tokens come from a verified, volatile session provider; never localStorage or URL strings.
 * Clinical writes require live connectivity; offline queuing is deliberately prohibited.
 */
export type TriageInputKind='none'|'text'|'reference'|'observation';
export type TriageCapability='triage:intake'|'triage:queue'|'triage:manage'|'triage:assess'|'triage:sign'|
  'triage:prioritize'|'triage:handover'|'triage:receive';
export interface TriageAction {readonly action:string;readonly kind:TriageInputKind;readonly capability:TriageCapability;}
const define=(capability:TriageCapability,kind:TriageInputKind,names:string):TriageAction[]=>names.split(' ').map(action=>Object.freeze({action,kind,capability}));
export const triageActions:ReadonlyArray<TriageAction>=Object.freeze([
 ...define('triage:intake','text','ARRIVAL_REASON LANGUAGE INTERPRETER_REQUEST ACCESSIBILITY_SUPPORT IDENTITY_VERIFICATION ARRIVAL_CHANNEL REFERRAL_REFERENCE ATTENDANT_CONTACT CONSENT_EVIDENCE'),
 ...define('triage:queue','reference','QUEUE_ENQUEUE QUEUE_MOVE_STATION'),
 ...define('triage:queue','none','QUEUE_CALL QUEUE_RECALL QUEUE_RETURN QUEUE_PAUSE QUEUE_RESUME QUEUE_PATIENT_UNAVAILABLE'),
 ...define('triage:manage','none','SAFETY_HOLD SAFETY_HOLD_RELEASE'),
 ...define('triage:assess','none','ASSESSMENT_BEGIN'),
 ...define('triage:assess','observation','OBSERVATION_RECORD OBSERVATION_CORRECTION'),
 ...define('triage:assess','text','ASSESSMENT_NARRATIVE ASSESSMENT_DEVICE_SOURCE PATIENT_STATEMENT ALLERGY_REVIEW_REFERENCE MEASUREMENT_DECLINED'),
 ...define('triage:sign','none','ASSESSMENT_ATTEST'),
 ...define('triage:sign','text','ASSESSMENT_ADDENDUM'),
 ...define('triage:prioritize','text','CLINICIAN_PRIORITY_SET CLINICIAN_PRIORITY_REVISED REASSESSMENT_DOCUMENTED PRIORITY_EVIDENCE_LINK'),
 ...define('triage:prioritize','none','REASSESSMENT_REQUESTED REASSESSMENT_STARTED REASSESSMENT_COMPLETED ESCALATION_RAISED ESCALATION_ACKNOWLEDGED ESCALATION_RESOLVED'),
 ...define('triage:handover','reference','CLINICIAN_ASSIGNMENT CLINICIAN_REASSIGNMENT'),
 ...define('triage:handover','none','HANDOVER_REQUEST HANDOVER_FINALIZED TRIAGE_CLOSED'),
 ...define('triage:handover','text','HANDOVER_NOTE HANDOVER_DESTINATION'),
 ...define('triage:receive','none','HANDOVER_ACCEPT HANDOVER_REJECT RECEIVER_ACKNOWLEDGEMENT'),
]);
const catalog=new Map(triageActions.map(a=>[a.action,a]));
if(catalog.size!==triageActions.length)throw new Error('duplicate triage action catalog');
export const triageAction=(name:string):TriageAction=>{const action=catalog.get(name);if(!action)throw new Error('unsupported triage workflow');return action;};
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const checkId=(id:unknown):string=>{if(typeof id!=='string'||!uuid.test(id))throw new Error('invalid scoped identifier');return id;};
export interface TriageScope {patientId:string;caseId:string;expectedRevision:number;}
export interface TriageObservationInput {id:string;code:string;value:string;unit:string;takenAt:string;replaces?:string;}
export type TriagePayload={text?:string;reference?:string;observation?:TriageObservationInput;rationale?:string};
export interface TriageWriteResult {revision:number;eventDigest:string;replay:boolean;}
export interface TriageViewResult {cases:ReadonlyArray<{caseId:string;stage:string;revision:number;station:string|null;assignee:string|null;arrivedAt:string;paused:boolean;safetyHold:boolean;manualEscalation:boolean}>;}
export const triageViewNames=Object.freeze(['FACILITY_QUEUE','STATION_QUEUE','PENDING_HANDOVERS','MANUAL_ESCALATIONS','PENDING_REASSESSMENTS',
 'SAFETY_HOLDS','CLINICIAN_WORKLIST','ARRIVALS_SINCE'] as const);
export type TriageView=typeof triageViewNames[number];
const hex=/^[0-9a-f]{64}$/;
const triageStages=new Set(['ARRIVED','WAITING','CALLED','ASSESSING','ASSESSED','PRIORITIZED','ASSIGNED','HANDED_OVER','CLOSED']);
const worklistFields=['caseId','stage','revision','station','assignee','arrivedAt','paused','safetyHold','manualEscalation'];
/** Strict, privacy-minimal projection decoder. Never forward unexpected clinical fields. */
export function triageDecodeWorklist(raw:unknown,requested:readonly string[]):TriageViewResult{
 const fail=()=>{throw new TriageTransportError('unavailable',0);};
 if(!raw||typeof raw!=='object'||Array.isArray(raw)||Object.keys(raw).length!==1||!Object.prototype.hasOwnProperty.call(raw,'cases'))return fail();
 const rows=(raw as Record<string,unknown>).cases;
 if(!Array.isArray(rows)||rows.length>requested.length||rows.length>50)return fail();
 const seen=new Set<string>();
 for(const row of rows){
  if(!row||typeof row!=='object'||Array.isArray(row))return fail();
  const x=row as Record<string,unknown>;
  if(Object.keys(x).length!==worklistFields.length||worklistFields.some(k=>!Object.prototype.hasOwnProperty.call(x,k)))return fail();
  if(typeof x.caseId!=='string'||!requested.includes(x.caseId)||seen.has(x.caseId))return fail();
  seen.add(x.caseId);
  if(typeof x.stage!=='string'||!triageStages.has(x.stage))return fail();
  if(typeof x.revision!=='number'||!Number.isInteger(x.revision)||x.revision<1||x.revision>2147483647)return fail();
  if(x.station!==null&&(typeof x.station!=='string'||!uuid.test(x.station)))return fail();
  if(x.assignee!==null&&(typeof x.assignee!=='string'||!uuid.test(x.assignee)))return fail();
  try{instant(x.arrivedAt);}catch{return fail();}
  if(typeof x.paused!=='boolean'||typeof x.safetyHold!=='boolean'||typeof x.manualEscalation!=='boolean')return fail();
 }
 return {cases:rows as TriageViewResult['cases']};
}
const instant=(s:unknown):string=>{if(typeof s!=='string'||!/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?Z$/.test(s)||!Number.isFinite(Date.parse(s)))throw new Error('invalid UTC timestamp');return s;};
const safeText=(v:unknown,max:number):string=>{if(typeof v!=='string'||!v.trim()||v.length>max||/[\x00-\x08\x0b\x0c\x0e-\x1f]/.test(v))throw new Error('invalid clinical text');return v;};
export function triageCommandBody(actionName:string,scope:TriageScope,commandId:string,payload:TriagePayload={}){
 const action=triageAction(actionName);
 checkId(commandId);checkId(scope.caseId);checkId(scope.patientId);
 if(!Number.isInteger(scope.expectedRevision)||scope.expectedRevision<1||scope.expectedRevision>2147483647)throw new Error('invalid current revision');
 const fields=Object.keys(payload);if(fields.some(x=>!['text','reference','observation','rationale'].includes(x)))throw new Error('unexpected command field');
 if(payload.text!==undefined && action.kind!=='text')throw new Error('text forbidden for this workflow');
 if(payload.reference!==undefined && action.kind!=='reference')throw new Error('reference forbidden for this workflow');
 if(payload.observation!==undefined && action.kind!=='observation')throw new Error('observation forbidden for this workflow');
 if(action.kind==='text')safeText(payload.text,4000);
 if(action.kind==='reference')checkId(payload.reference);
 let observation: TriageObservationInput|undefined;
 if(action.kind==='observation'){
  const o=payload.observation;if(!o||Object.keys(o).some(k=>!['id','code','value','unit','takenAt','replaces'].includes(k)))throw new Error('observation evidence required');
  observation={id:checkId(o.id),code:safeText(o.code,64),value:safeText(o.value,256),unit:safeText(o.unit,48),takenAt:instant(o.takenAt)};
  if(o.replaces!==undefined)observation.replaces=checkId(o.replaces);
  if(actionName==='OBSERVATION_RECORD'&&observation.replaces!==undefined)throw new Error('first observation may not replace another');
  if(actionName==='OBSERVATION_CORRECTION'&&observation.replaces===undefined)throw new Error('correction must identify original');
  if(observation.replaces===observation.id)throw new Error('self correction forbidden');
 }
 const rationale=payload.rationale===undefined?undefined:safeText(payload.rationale,500);
 return Object.freeze({commandId,expectedRevision:scope.expectedRevision,action:action.action,
  ...(action.kind==='text'?{text:payload.text}:{}),...(action.kind==='reference'?{reference:payload.reference}:{}),
  ...(observation?{observation}:{}),...(rationale?{rationale}:{})});
}
export function triageReadBody(view:TriageView,caseIds:readonly string[],filter?:{stationOrClinician?:string;after?:string}){
 if(!triageViewNames.includes(view)||!caseIds.length||caseIds.length>50)throw new Error('invalid bounded worklist');
 const ids=caseIds.map(checkId);if(new Set(ids).size!==ids.length)throw new Error('duplicate case identifiers');
 const stationRequired=view==='STATION_QUEUE'||view==='CLINICIAN_WORKLIST',timeRequired=view==='ARRIVALS_SINCE';
 if(stationRequired!==Boolean(filter?.stationOrClinician)||timeRequired!==Boolean(filter?.after))throw new Error('inconsistent worklist filter');
 return Object.freeze({caseIds:ids,...(stationRequired?{stationOrClinician:checkId(filter?.stationOrClinician)}:{}),
  ...(timeRequired?{after:instant(filter?.after)}:{})});
}
export interface TriageCaseSummary {
 readonly caseId:string;readonly stage:string;readonly revision:number;readonly station:string|null;
 readonly assignee:string|null;readonly arrivedAt:string;readonly paused:boolean;readonly safetyHold:boolean;readonly manualEscalation:boolean;
}
export interface TriageAuditRecord {readonly revision:number;readonly operation:string;readonly occurredAt:string;readonly digest:string;}
export interface TriageAuditPage {readonly history:readonly TriageAuditRecord[];readonly nextAfterRevision:number|null;}
/** Decoder intentionally strips no unknown fields: an unexpected field fails closed before reaching UI. */
export function triageDecodeSummary(raw:unknown,requestedCaseId:string):TriageCaseSummary {
 checkId(requestedCaseId);
 if(!raw||typeof raw!=='object'||Array.isArray(raw)||Object.keys(raw).join()!=='case')throw new TriageTransportError('unavailable',0);
 return triageDecodeWorklist({cases:[(raw as {case:unknown}).case]},[requestedCaseId]).cases[0]!;
}
export function triageDecodeAuditPage(raw:unknown,afterRevision:number):TriageAuditPage {
 const fail=():never=>{throw new TriageTransportError('unavailable',0);};
 if(!Number.isInteger(afterRevision)||afterRevision<0||afterRevision>2147483647)return fail();
 if(!raw||typeof raw!=='object'||Array.isArray(raw))return fail();
 const x=raw as Record<string,unknown>;
 if(Object.keys(x).length!==2||!Object.prototype.hasOwnProperty.call(x,'history')||!Object.prototype.hasOwnProperty.call(x,'nextAfterRevision'))return fail();
 if(!Array.isArray(x.history)||x.history.length>100)return fail();
 let last=afterRevision;
 for(const entry of x.history){
  if(!entry||typeof entry!=='object'||Array.isArray(entry))return fail();
  const e=entry as Record<string,unknown>;
  if(Object.keys(e).length!==4||['revision','operation','occurredAt','digest'].some(k=>!Object.prototype.hasOwnProperty.call(e,k)))return fail();
  if(typeof e.revision!=='number'||!Number.isInteger(e.revision)||e.revision<=last||e.revision>2147483647)return fail();
  if(typeof e.operation!=='string'||!/^[A-Z_]{2,80}$/.test(e.operation))return fail();
  try{instant(e.occurredAt);}catch{return fail();}
  if(typeof e.digest!=='string'||!hex.test(e.digest))return fail();
  last=e.revision;
 }
 if(x.nextAfterRevision!==null && (typeof x.nextAfterRevision!=='number'||!Number.isInteger(x.nextAfterRevision)||x.nextAfterRevision!==last||x.history.length!==100))return fail();
 if(x.nextAfterRevision!==null && last===afterRevision)return fail();
 return {history:x.history as TriageAuditRecord[],nextAfterRevision:x.nextAfterRevision as number|null};
}
export interface TriageClinicalTransport {
 /** Implementor must supply an in-memory verified token without persistence. */
 token():Promise<string>;
 fetch(input:string,init:{method:'POST'|'GET';headers:Record<string,string>;body?:string;credentials:'omit';redirect:'error';cache:'no-store';signal?:AbortSignal}):Promise<{status:number;headers:{get(name:string):string|null};json():Promise<unknown>}>;
 isOnline():boolean;
}
export class TriageTransportError extends Error{
 constructor(readonly category:'reauthenticate'|'denied'|'conflict'|'invalid'|'unavailable',readonly status:number){super(category);}
}
export function triageClient(origin:string,transport:TriageClinicalTransport){
 const url=new URL(origin);
 if((url.protocol!=='https:' && !(url.protocol==='http:'&&url.hostname==='127.0.0.1'))||url.username||url.password||url.search||url.hash||url.pathname!=='/')throw new Error('trusted clinical API origin required');
 const notAborted=(signal?:AbortSignal)=>{if(signal?.aborted)throw new TriageTransportError('unavailable',0);};
 // Limits apply to BOTH mutation acknowledgements and scoped reads. No cache, redirects, or oversized PHI.
 const clinicalResponse=async(r:Awaited<ReturnType<TriageClinicalTransport['fetch']>>,signal?:AbortSignal):Promise<unknown>=>{
  notAborted(signal);
  if(r.headers.get('Cache-Control')!=='no-store, private')throw new TriageTransportError('unavailable',0);
  const type=r.headers.get('Content-Type');
  if(type!==null&&!/^application\/json(?:;\s*charset=utf-8)?$/i.test(type))throw new TriageTransportError('unavailable',0);
  const length=r.headers.get('Content-Length');
  if(length!==null&&(!/^\d{1,6}$/.test(length)||Number(length)>65536))throw new TriageTransportError('unavailable',0);
  let body:unknown;
  try{body=await r.json();}catch{throw new TriageTransportError('unavailable',0);}
  notAborted(signal);
  let size=0;try{size=new TextEncoder().encode(JSON.stringify(body)).byteLength;}catch{throw new TriageTransportError('unavailable',0);}
  if(size>65536||size===0)throw new TriageTransportError('unavailable',0);
  return body;
 };
 const post=async(path:string,body:object,signal?:AbortSignal,accepted:readonly number[]=[200]):Promise<unknown>=>{
  notAborted(signal);
  if(!transport.isOnline())throw new TriageTransportError('unavailable',0);
  const token=await transport.token();notAborted(signal);
  if(!/^[A-Za-z0-9_.~-]{16,8192}$/.test(token))throw new TriageTransportError('reauthenticate',0);
  const r=await transport.fetch(url.origin+path,{method:'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json','Accept':'application/json'},
   body:JSON.stringify(body),credentials:'omit',redirect:'error',cache:'no-store',signal});notAborted(signal);
  if(r.status===401)throw new TriageTransportError('reauthenticate',401);
  if(r.status===404||r.status===403)throw new TriageTransportError('denied',r.status);
  if(r.status===409||r.status===412)throw new TriageTransportError('conflict',r.status);
  if(r.status===422||r.status===400)throw new TriageTransportError('invalid',r.status);
  if(r.status>=500||!accepted.includes(r.status))throw new TriageTransportError('unavailable',r.status);
  return clinicalResponse(r,signal);
 };
 const get=async(path:string,signal?:AbortSignal):Promise<unknown>=>{
  notAborted(signal);
  if(!transport.isOnline())throw new TriageTransportError('unavailable',0);
  const token=await transport.token();notAborted(signal);
  if(!/^[A-Za-z0-9_.~-]{16,8192}$/.test(token))throw new TriageTransportError('reauthenticate',0);
  const r=await transport.fetch(url.origin+path,{method:'GET',headers:{Authorization:'Bearer '+token,Accept:'application/json'},credentials:'omit',redirect:'error',cache:'no-store',signal});notAborted(signal);
  if(r.status===401)throw new TriageTransportError('reauthenticate',401);
  if(r.status===403||r.status===404)throw new TriageTransportError('denied',r.status);
  if(r.status===400||r.status===422)throw new TriageTransportError('invalid',r.status);
  if(r.status!==200)throw new TriageTransportError('unavailable',r.status);
  return clinicalResponse(r,signal);
 };
 const path=(patient:string,caseId:string)=>'/api/v1/triage/patients/'+checkId(patient)+'/cases/'+checkId(caseId);
 const result=async(pending:Promise<unknown>):Promise<TriageWriteResult>=>{
  const raw=await pending;
  if(!raw||typeof raw!=='object')throw new TriageTransportError('unavailable',0);
  const x=raw as Record<string,unknown>;
  if(!Number.isInteger(x.revision)||!(typeof x.revision==='number'&&x.revision>=1)||typeof x.eventDigest!=='string'||!hex.test(x.eventDigest)||typeof x.replay!=='boolean')throw new TriageTransportError('unavailable',0);
  return {revision:x.revision as number,eventDigest:x.eventDigest,replay:x.replay};
 };
 return Object.freeze({
  command:(scope:TriageScope,commandId:string,action:string,payload:TriagePayload={},signal?:AbortSignal)=>
   result(post(path(scope.patientId,scope.caseId)+'/commands',triageCommandBody(action,scope,commandId,payload),signal)),
  intake:(patientId:string,caseId:string,commandId:string,appointment?:string,signal?:AbortSignal)=>
   result(post(path(patientId,caseId)+'/intake',{commandId:checkId(commandId),expectedRevision:0,...(appointment?{appointment:checkId(appointment)}:{})},signal,[200,201])),
  summary:async(patientId:string,caseId:string,signal?:AbortSignal):Promise<TriageCaseSummary>=>
   triageDecodeSummary(await get(path(patientId,caseId),signal),caseId),
  history:async(patientId:string,caseId:string,afterRevision=0,signal?:AbortSignal):Promise<TriageAuditPage>=>{
   if(!Number.isInteger(afterRevision)||afterRevision<0||afterRevision>2147483647)throw new Error('invalid audit history cursor');
   return triageDecodeAuditPage(await get(path(patientId,caseId)+'/history?afterRevision='+afterRevision,signal),afterRevision);
  },
  worklist:async(patientId:string,view:TriageView,caseIds:readonly string[],filter?:{stationOrClinician?:string;after?:string},signal?:AbortSignal):Promise<TriageViewResult>=>{
   const data=await post('/api/v1/triage/patients/'+checkId(patientId)+'/views/'+view,triageReadBody(view,caseIds,filter),signal);
   return triageDecodeWorklist(data,caseIds);
  }
 });
}
