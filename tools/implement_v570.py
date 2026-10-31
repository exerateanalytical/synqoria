from pathlib import Path
p=Path(__file__).resolve().parents[1]/'packages/ux-core/src/triage-client.ts'
s=p.read_text()
s=s.replace("fetch(input:string,init:{method:'POST';headers:Record<string,string>;body:string;credentials:'omit';redirect:'error';cache:'no-store';signal?:AbortSignal})", "fetch(input:string,init:{method:'POST'|'GET';headers:Record<string,string>;body?:string;credentials:'omit';redirect:'error';cache:'no-store';signal?:AbortSignal})")
start="export interface TriageClinicalTransport {"
insert="""export interface TriageCaseSummary {
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
"""
s=s.replace(start,insert+start)
anchor=" const path=(patient:string,caseId:string)=>'/api/v1/triage/patients/'+checkId(patient)+'/cases/'+checkId(caseId);"
get=""" const get=async(path:string,signal?:AbortSignal):Promise<unknown>=>{
  if(!transport.isOnline())throw new TriageTransportError('unavailable',0);
  const token=await transport.token();if(!/^[A-Za-z0-9_.~-]{16,8192}$/.test(token))throw new TriageTransportError('reauthenticate',0);
  const r=await transport.fetch(url.origin+path,{method:'GET',headers:{Authorization:'Bearer '+token,Accept:'application/json'},credentials:'omit',redirect:'error',cache:'no-store',signal});
  if(r.status===401)throw new TriageTransportError('reauthenticate',401);
  if(r.status===403||r.status===404)throw new TriageTransportError('denied',r.status);
  if(r.status===400||r.status===422)throw new TriageTransportError('invalid',r.status);
  if(r.status!==200||r.headers.get('Cache-Control')!=='no-store, private')throw new TriageTransportError('unavailable',r.status);
  const contentType=r.headers.get('Content-Type');
  if(contentType!==null&&!/^application\\/json(?:;\\s*charset=utf-8)?$/i.test(contentType))throw new TriageTransportError('unavailable',0);
  const length=r.headers.get('Content-Length');
  if(length!==null&&(!/^\\d{1,6}$/.test(length)||Number(length)>65536))throw new TriageTransportError('unavailable',0);
  return r.json();
 };
"""
s=s.replace(anchor,get+anchor)
anchor="  worklist:async(patientId:string,view:TriageView,caseIds:readonly string[],filter?:{stationOrClinician?:string;after?:string},signal?:AbortSignal):Promise<TriageViewResult>=>{"
method="""  summary:async(patientId:string,caseId:string,signal?:AbortSignal):Promise<TriageCaseSummary>=>
   triageDecodeSummary(await get(path(patientId,caseId),signal),caseId),
  history:async(patientId:string,caseId:string,afterRevision=0,signal?:AbortSignal):Promise<TriageAuditPage>=>{
   if(!Number.isInteger(afterRevision)||afterRevision<0||afterRevision>2147483647)throw new Error('invalid audit history cursor');
   return triageDecodeAuditPage(await get(path(patientId,caseId)+'/history?afterRevision='+afterRevision,signal),afterRevision);
  },
"""
s=s.replace(anchor,method+anchor)
p.write_text(s)
