export const uuid=/^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/i;
export const code=/^[A-Z][A-Z0-9_]{1,63}$/;
export const hex=/^[0-9a-f]{64}$/;
export type Result<T>={ok:true;status:number;data:T}|{ok:false;status:number;error:string};
export type Row={id:string;status:string;version:number;created_at:string;
 partner_code?:string;partner_type?:string;api_code?:string;api_version?:string;
 contract_sha256?:string;partner_id?:string;api_product_id?:string;app_code?:string;
 credential_status?:string;map_code?:string;map_version?:string;
 source_system?:string;target_system?:string;event_type?:string;payload_sha256?:string;
 reason_code?:string;message_id?:string;external_event_id?:string;
 proof_id?:string;received_by?:string;reviewed_by?:string};
function csrf(){
 if(typeof document==="undefined")return "";
 const c=document.cookie.split("; ").find(x=>x.startsWith("__Host-synq_ops_csrf="));
 return c?decodeURIComponent(c.slice("__Host-synq_ops_csrf=".length)):"";
}
export async function api<T>(method:"GET"|"POST",route:string,opts?:{
 query?:Record<string,string|number>;body?:object
}):Promise<Result<T>>{
 if(typeof navigator!=="undefined"&&navigator.onLine===false)
  return {ok:false,status:0,error:"offline"};
 const query=new URLSearchParams();
 Object.entries(opts?.query??{}).forEach(([k,v])=>query.set(k,String(v)));
 const url="/api/integrations/proxy/"+route+(query.size?"?"+query.toString():"");
 const headers:Record<string,string>={"Accept":"application/json"};
 if(method==="POST"){
  headers["Content-Type"]="application/json";
  headers["Idempotency-Key"]=crypto.randomUUID();
  headers["x-synq-ops-csrf"]=csrf();
 }
 try{
  const r=await fetch(url,{method,headers,cache:"no-store",credentials:"same-origin",
   body:method==="POST"?JSON.stringify(opts?.body??{}):undefined});
  let data:unknown;
  try{data=await r.json();}catch{return {ok:false,status:503,error:"invalid_response"};}
  if(r.ok)return {ok:true,status:r.status,data:data as T};
  const e=data as {error?:unknown};
  return {ok:false,status:r.status,error:typeof e?.error==="string"?e.error:"request_failed"};
 }catch{return {ok:false,status:0,error:"integrations_unavailable"};}
}
