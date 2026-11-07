/** Restricted M04–M06 staff clinical transport. No offline writes, stored bearer tokens, or client-authoritative scope. */
export interface ClinicalScope {readonly patientId:string;readonly encounterId:string;readonly revision:number;}
export interface ClinicalCommandAck {readonly revision:number;readonly auditDigest:string;readonly replay:boolean;}
export interface ClinicalCaseView {readonly encounterId:string;readonly revision:number;readonly stage:'PLANNED'|'ACTIVE'|'DISCHARGE_PLANNED'|'DISCHARGED';readonly onLeave:boolean;readonly notes:number;readonly observations:number;readonly auditHead:string;}
export interface ClinicalAuditView {readonly history:readonly {readonly revision:number;readonly operation:string;readonly at:string;readonly digest:string;}[];readonly nextRevision:number|null;}
export interface ClinicalNoteView {readonly id:string;readonly author:string;readonly title:string;readonly body:string;readonly status:string;readonly confidential:boolean;}
export interface ClinicalObservationView {readonly observations:readonly {readonly id:string;readonly code:string;readonly value:string;readonly unit:string;readonly measuredAt:string;readonly status:string;}[];}
export interface ClinicalNoteIndex {readonly notes:readonly {readonly id:string;readonly status:string;readonly title:string;readonly confidential:boolean;}[];readonly nextId:string|null;}
export interface ClinicalObservationLineage {readonly lineage:readonly {readonly id:string;readonly code:string;readonly value:string;readonly unit:string;readonly measuredAt:string;readonly status:string;readonly replaces:string|null;}[];}

export interface ClinicalTransport {
  token():Promise<string>;
  online():boolean;
  fetch(url:string,init:{method:'GET'|'POST';headers:Record<string,string>;body?:string;credentials:'omit';redirect:'error';cache:'no-store';signal?:AbortSignal}):Promise<{
    readonly status:number;readonly headers:{get(key:string):string|null};json():Promise<unknown>;
  }>;
}
export class ClinicalClientError extends Error {constructor(readonly kind:'reauthenticate'|'denied'|'conflict'|'invalid'|'unavailable',readonly status:number){super(kind);}}
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const SHA=/^[0-9a-f]{64}$/;
const safeId=(s:unknown):string=>{if(typeof s!=='string'||!UUID.test(s))throw new ClinicalClientError('invalid',0);return s;};
const fail=():never=>{throw new ClinicalClientError('unavailable',0);};
const obj=(v:unknown):Record<string,unknown>=>v&&typeof v==='object'&&!Array.isArray(v)?v as Record<string,unknown>:fail();
const keys=(raw:unknown,expected:readonly string[]):Record<string,unknown>=>{const x=obj(raw);if(Object.keys(x).length!==expected.length||expected.some(k=>!Object.hasOwn(x,k)))return fail();return x;};
const nonnegative=(v:unknown):v is number=>typeof v==='number'&&Number.isSafeInteger(v)&&v>=0;
const digest=(v:unknown):v is string=>typeof v==='string'&&SHA.test(v);
const iso=(v:unknown):v is string=>typeof v==='string'&&/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?Z$/.test(v)&&Number.isFinite(Date.parse(v));
export function clinicalCommandAck(raw:unknown):ClinicalCommandAck {
 const x=keys(raw,['revision','auditDigest','replay']);
 if(!nonnegative(x.revision)||!digest(x.auditDigest)||typeof x.replay!=='boolean')return fail();
 return {revision:x.revision,auditDigest:x.auditDigest,replay:x.replay};
}
export function clinicalCaseView(raw:unknown,encounterId:string):ClinicalCaseView {
 const x=keys(raw,['encounterId','revision','stage','onLeave','notes','observations','auditHead']);
 if(x.encounterId!==safeId(encounterId)||!nonnegative(x.revision)||!['PLANNED','ACTIVE','DISCHARGE_PLANNED','DISCHARGED'].includes(String(x.stage))||
    typeof x.onLeave!=='boolean'||!nonnegative(x.notes)||!nonnegative(x.observations)||!digest(x.auditHead))return fail();
 return x as unknown as ClinicalCaseView;
}
export function clinicalAuditPage(raw:unknown,afterRevision:number):ClinicalAuditView {
 if(!nonnegative(afterRevision))return fail();const x=keys(raw,['history','nextRevision']);
 if(!Array.isArray(x.history)||x.history.length>100)return fail();
 let prev=afterRevision;
 for(const row of x.history){const a=keys(row,['revision','operation','at','digest']);
  if(!nonnegative(a.revision)||a.revision<=prev||typeof a.operation!=='string'||!/^\w+\.[a-z_]{2,60}$/.test(a.operation)||
    !iso(a.at)||!digest(a.digest))return fail();prev=a.revision;
 }
 if(x.nextRevision!==null&&(!nonnegative(x.nextRevision)||x.nextRevision!==prev||x.history.length===0))return fail();
 return x as unknown as ClinicalAuditView;
}
export function clinicalNoteView(raw:unknown,id:string):ClinicalNoteView {
 const x=keys(raw,['id','status','author','title','body','confidential']);
 if(x.id!==safeId(id)||!UUID.test(String(x.author))||typeof x.status!=='string'||
    !['DRAFT','REVIEW','READY','SIGNED','AMENDED','ENTERED_IN_ERROR'].includes(x.status)||
    typeof x.title!=='string'||x.title.length>160||typeof x.body!=='string'||x.body.length>8192||typeof x.confidential!=='boolean')return fail();
 return x as unknown as ClinicalNoteView;
}
export function clinicalObservationView(raw:unknown):ClinicalObservationView {
 const x=keys(raw,['observations']);if(!Array.isArray(x.observations)||x.observations.length>100)return fail();
 for(const row of x.observations){const o=keys(row,['id','code','value','unit','measuredAt','status']);
  if(typeof o.id!=='string'||!UUID.test(o.id)||typeof o.code!=='string'||o.code.length>128||
     typeof o.value!=='string'||o.value.length>256||typeof o.unit!=='string'||o.unit.length>64||!iso(o.measuredAt)||
     !['CAPTURED','PENDING','VERIFIED','CORRECTED','INVALIDATED','REJECTED'].includes(String(o.status)))return fail();
 }
 return x as unknown as ClinicalObservationView;
}
export function clinicalNoteIndex(raw:unknown,afterId?:string):ClinicalNoteIndex {
 if(afterId!==undefined)safeId(afterId);
 const x=keys(raw,['notes','nextId']);if(!Array.isArray(x.notes)||x.notes.length>100)return fail();
 let previous=afterId??'';
 for(const row of x.notes){const n=keys(row,['id','status','title','confidential']);
  if(typeof n.id!=='string'||!UUID.test(n.id)||n.id<=previous||
     !['DRAFT','REVIEW','READY','SIGNED','AMENDED','ENTERED_IN_ERROR'].includes(String(n.status))||
     typeof n.title!=='string'||n.title.length>160||typeof n.confidential!=='boolean')return fail();
  previous=n.id;
 }
 if(x.nextId!==null&&(typeof x.nextId!=='string'||x.nextId!==previous||x.notes.length===0))return fail();
 return x as unknown as ClinicalNoteIndex;
}
export function clinicalObservationLineage(raw:unknown,selected:string):ClinicalObservationLineage {
 safeId(selected);const x=keys(raw,['lineage']);if(!Array.isArray(x.lineage)||x.lineage.length<1||x.lineage.length>100)return fail();
 const seen=new Set<string>();let contains=false;let priorTime=-Infinity;
 for(const row of x.lineage){const o=keys(row,['id','code','value','unit','measuredAt','status','replaces']);
  if(typeof o.id!=='string'||!UUID.test(o.id)||seen.has(o.id)||
      typeof o.code!=='string'||o.code.length<1||o.code.length>128||typeof o.value!=='string'||o.value.length>256||
      typeof o.unit!=='string'||o.unit.length>64||!iso(o.measuredAt)||
      !['CAPTURED','PENDING','VERIFIED','CORRECTED','INVALIDATED','REJECTED'].includes(String(o.status))||
      o.replaces!==null&&(typeof o.replaces!=='string'||!UUID.test(o.replaces)))return fail();
  let timestamp=Date.parse(o.measuredAt);if(timestamp<priorTime)return fail();priorTime=timestamp;
  seen.add(o.id);if(o.id===selected)contains=true;
 }
 if(!contains||x.lineage.some((o:unknown)=>{const row=obj(o);return row.replaces!==null&&!seen.has(String(row.replaces));}))return fail();
 return x as unknown as ClinicalObservationLineage;
}
export function longitudinalClient(origin:string,transport:ClinicalTransport){
 const u=new URL(origin);
 if((u.protocol!=='https:'&&!(u.protocol==='http:'&&u.hostname==='127.0.0.1'))||u.username||u.password||
    u.pathname!=='/'||u.search||u.hash)throw new ClinicalClientError('invalid',0);
 const checkCurrent=(signal?:AbortSignal,isCurrent?:()=>boolean)=>{if(signal?.aborted||isCurrent?.()===false)throw new ClinicalClientError('unavailable',0);};
 const base=(scope:ClinicalScope)=>{
   safeId(scope.patientId);safeId(scope.encounterId);
   if(!nonnegative(scope.revision))throw new ClinicalClientError('invalid',0);
   return `${u.origin}/api/v1/clinical/patients/${scope.patientId}/encounters/${scope.encounterId}`;
 };
 async function send(path:string,method:'POST'|'GET',signal?:AbortSignal,isCurrent?:()=>boolean,body?:object):Promise<unknown>{
   checkCurrent(signal,isCurrent);if(!transport.online())throw new ClinicalClientError('unavailable',0);
   const token=await transport.token();checkCurrent(signal,isCurrent);
   if(!/^[A-Za-z0-9_.~-]{16,8192}$/.test(token))throw new ClinicalClientError('reauthenticate',0);
   const r=await transport.fetch(path,{method,headers:{Authorization:'Bearer '+token,Accept:'application/json',
     ...(body?{'Content-Type':'application/json'}:{})},...(body?{body:JSON.stringify(body)}:{}),credentials:'omit',redirect:'error',cache:'no-store',signal});
   checkCurrent(signal,isCurrent);
   if(r.status===401)throw new ClinicalClientError('reauthenticate',r.status);
   if(r.status===404||r.status===403)throw new ClinicalClientError('denied',r.status);
   if(r.status===409||r.status===412)throw new ClinicalClientError('conflict',r.status);
   if(r.status===400||r.status===422)throw new ClinicalClientError('invalid',r.status);
   if(r.status!==200)throw new ClinicalClientError('unavailable',r.status);
   if(r.headers.get('Cache-Control')!=='no-store, private'||
      !/^application\/json(?:;\s*charset=utf-8)?$/i.test(r.headers.get('Content-Type')??''))return fail();
   const length=r.headers.get('Content-Length');
   if(length!==null&&(!/^\d{1,6}$/.test(length)||Number(length)>65536))return fail();
   let data:unknown;try{data=await r.json();}catch{return fail();}checkCurrent(signal,isCurrent);
   try{const bytes=new TextEncoder().encode(JSON.stringify(data)).byteLength;if(bytes<1||bytes>65536)return fail();}catch{return fail();}
   return data;
 }
 return {
  command:async(scope:ClinicalScope,commandId:string,operation:string,inputs:Readonly<Record<string,string>>,
     signal?:AbortSignal,isCurrent?:()=>boolean):Promise<ClinicalCommandAck>=>{
    safeId(commandId);if(!/^(encounter|note|observation)\.[a-z_]{2,60}$/.test(operation))throw new ClinicalClientError('invalid',0);
    if(Object.keys(inputs).length>32||Object.keys(inputs).some(k=>!/^[a-zA-Z][a-zA-Z0-9]{0,63}$/.test(k)||
      typeof inputs[k]!=='string'||inputs[k]!.length>8192))throw new ClinicalClientError('invalid',0);
    const data=await send(base(scope)+'/commands','POST',signal,isCurrent,{commandId,expectedRevision:scope.revision,operation,inputs});
    const ack=clinicalCommandAck(data);if(!ack.replay&&ack.revision!==scope.revision+1)return fail();return ack;
  },
  summary:async(scope:ClinicalScope,signal?:AbortSignal,isCurrent?:()=>boolean)=>
    clinicalCaseView(await send(base(scope)+'/summary','GET',signal,isCurrent),scope.encounterId),
  history:async(scope:ClinicalScope,afterRevision:number,limit=50,signal?:AbortSignal,isCurrent?:()=>boolean)=>{
    if(!nonnegative(afterRevision)||!Number.isSafeInteger(limit)||limit<1||limit>100)throw new ClinicalClientError('invalid',0);
    return clinicalAuditPage(await send(base(scope)+`/audit?afterRevision=${afterRevision}&limit=${limit}`,'GET',signal,isCurrent),afterRevision);
  },
  note:async(scope:ClinicalScope,noteId:string,signal?:AbortSignal,isCurrent?:()=>boolean)=>
    clinicalNoteView(await send(base(scope)+'/notes/'+safeId(noteId),'GET',signal,isCurrent),noteId),
  noteIndex:async(scope:ClinicalScope,afterId?:string,limit=50,signal?:AbortSignal,isCurrent?:()=>boolean)=>{
    if(afterId!==undefined)safeId(afterId);
    if(!Number.isSafeInteger(limit)||limit<1||limit>100)throw new ClinicalClientError('invalid',0);
    const query=new URLSearchParams({limit:String(limit),...(afterId?{afterId}:{})});
    return clinicalNoteIndex(await send(base(scope)+`/note-index?${query}`,'GET',signal,isCurrent),afterId);
  },
  observationLineage:async(scope:ClinicalScope,id:string,signal?:AbortSignal,isCurrent?:()=>boolean)=>
    clinicalObservationLineage(await send(base(scope)+`/observations/${safeId(id)}/lineage`,'GET',signal,isCurrent),id),
  observations:async(scope:ClinicalScope,code:string,limit=50,signal?:AbortSignal,isCurrent?:()=>boolean)=>{
    if(typeof code!=='string'||!code.trim()||code.length>128||/[\x00-\x1f]/.test(code)||
       !Number.isSafeInteger(limit)||limit<1||limit>100)throw new ClinicalClientError('invalid',0);
    return clinicalObservationView(await send(base(scope)+`/observations?code=${encodeURIComponent(code)}&limit=${limit}`,'GET',signal,isCurrent));
  }
 };
}
