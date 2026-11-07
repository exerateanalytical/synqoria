export type Failure={ok:false;status:number;code:string};
export type Success<T>={ok:true;status:number;data:T};
export type Result<T>=Success<T>|Failure;
export const uuid=/^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/i;
export const code=/^[A-Z][A-Z0-9_]{1,63}$/;
function csrf(){
 if(typeof document==="undefined")return "";
 const cookie=document.cookie.split("; ").find(c=>c.startsWith("__Host-synq_ops_csrf="));
 return cookie?decodeURIComponent(cookie.split("=").slice(1).join("=")):"";
}
export async function call<T>(method:"GET"|"POST",path:string,opts?:{
 query?:Record<string,string|number>;body?:unknown;key?:string
}):Promise<Result<T>>{
 if(typeof navigator!=="undefined"&&navigator.onLine===false)
  return {ok:false,status:0,code:"offline"};
 const q=new URLSearchParams();
 Object.entries(opts?.query||{}).forEach(([k,v])=>q.set(k,String(v)));
 const url="/api/audit/proxy/"+path+(q.size?"?"+q.toString():"");
 const headers:Record<string,string>={"Accept":"application/json"};
 if(method==="POST"){
  headers["Content-Type"]="application/json";
  headers["Idempotency-Key"]=opts?.key||crypto.randomUUID();
  headers["x-synq-ops-csrf"]=csrf();
 }
 try{
  const res=await fetch(url,{method,headers,cache:"no-store",credentials:"same-origin",
   body:method==="POST"?JSON.stringify(opts?.body??{}):undefined});
  let body:unknown;
  try{body=await res.json();}catch{return {ok:false,status:503,code:"invalid_response"};}
  if(res.ok)return {ok:true,status:res.status,data:body as T};
  const e=body as {error?:unknown};
  return {ok:false,status:res.status,
   code:typeof e?.error==="string"?e.error:"audit_unavailable"};
 }catch{return {ok:false,status:0,code:"audit_unavailable"};}
}
export type EventRow={id:string;stream_id:string;subject_ref:string|null;action:string;
 outcome:string;sequence:number;payload_sha256:string;event_sha256:string;previous_sha256:string;
 source_actor:string;source_id:string;recorded_at:string};
export type RequestRow={id:string;stream_id:string;status:string;version:number;
 created_at:string;purpose?:string;recipient?:string;from_sequence?:number;to_sequence?:number;
 expires_at?:string;manifest_sha256?:string;policy?:string;reason?:string};
export type HoldRow={hold_id:string;stream_id:string;reason:string;issued_at:string;
 rescinded_at:string|null;authority_ref:string};
export type ChainStatus={verified:boolean;reason?:string;count:number;headHash?:string};
export type IntegrityManifest={manifestSha256:string;streamId:string;clinicalDataExported:boolean;
 signedObjectStatus:string;validUntil:string};
