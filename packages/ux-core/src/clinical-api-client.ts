/** Typed clinical read adapter. HTTPS and a validated backend are mandatory; no local admission claims. */
export type ClinicalRead =
 | {status:'ok';patientId:string;identityStatus:'verified'|'provisional'|'merged';revision:number}
 | {status:'authentication-required'|'not-disclosed'|'unavailable'|'invalid-request'};
export type HttpFetch = typeof fetch;
const canonicalUuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
export class ClinicalApiClient {
 private readonly endpoint:string;
 constructor(base:string,private readonly transport:HttpFetch=fetch){
  const url=new URL(base);
  if(url.protocol!=='https:'||url.username||url.password||url.search||url.hash||url.pathname!=='/'||!url.hostname)throw new Error('HTTPS cell origin required');
  this.endpoint=url.origin;
 }
 async readPatient(id:string,token:string,signal?:AbortSignal):Promise<ClinicalRead>{
  if(!canonicalUuid.test(id)||typeof token!=='string'||!/^[-A-Za-z0-9_.~]{16,8192}$/.test(token))return {status:'invalid-request'};
  try {
   const response=await this.transport(this.endpoint+'/api/v1/patients/'+id,{
    method:'GET',headers:{'Authorization':'Bearer '+token,'Accept':'application/json'},cache:'no-store',credentials:'omit',redirect:'error',signal
   });
   if(response.status===401)return {status:'authentication-required'};
   if(response.status===404)return {status:'not-disclosed'};
   if(response.status!==200||!response.headers.get('content-type')?.toLowerCase().startsWith('application/json'))return {status:'unavailable'};
   const length=response.headers.get('content-length');
   if(length!==null&&(!/^\d{1,7}$/.test(length)||Number(length)>4096))return {status:'unavailable'};
   // P01-B0019: bound actual bytes, even when Content-Length is omitted/forged.
   if(!response.body)return {status:'unavailable'};
   const reader=response.body.getReader();let size=0;const chunks:Uint8Array[]=[];
   try {
    while(true){
     const part=await reader.read();if(part.done)break;
     size+=part.value.byteLength;
     if(size>4096){await reader.cancel();return {status:'unavailable'};}
     chunks.push(part.value);
    }
   }finally{reader.releaseLock();}
   const all=new Uint8Array(size);let offset=0;
   for(const chunk of chunks){all.set(chunk,offset);offset+=chunk.byteLength;}
   const raw:unknown=JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(all));
   if(!raw||typeof raw!=='object'||Array.isArray(raw))return {status:'unavailable'};
   const data=raw as Record<string,unknown>;
   if(data.patientId!==id||!['verified','provisional','merged'].includes(String(data.identityStatus))
       ||!Number.isSafeInteger(data.revision)||Number(data.revision)<1)return {status:'unavailable'};
   return {status:'ok',patientId:id,identityStatus:data.identityStatus as 'verified'|'provisional'|'merged',revision:data.revision as number};
  }catch{return {status:'unavailable'};}
 }
}
