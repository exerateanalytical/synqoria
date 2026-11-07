export const uuid=/^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/i;
export const code=/^[A-Z][A-Z0-9_]{1,63}$/;
export const hex=/^[0-9a-f]{64}$/;
export type Result<T>={ok:true;data:T;status:number}|{ok:false;error:string;status:number};
export type Row={id:string;patient_ref:string;recipient_facility:string;reason_code:string;
 summary_sha256:string;status:string;version:number;created_at:string;updated_at:string};
export type History={from_status:string;to_status:string;actor_id:string;version:number;occurred_at:string};
function csrf(){
 if(typeof document==="undefined")return "";
 const v=document.cookie.split("; ").find(x=>x.startsWith("__Host-synq_ops_csrf="));
 return v?decodeURIComponent(v.slice("__Host-synq_ops_csrf=".length)):"";
}
export async function api<T>(method:"GET"|"POST",route:string,opts?:{
 body?:unknown;query?:Record<string,number|string>
}):Promise<Result<T>>{
 if(typeof navigator!=="undefined"&&!navigator.onLine)
  return {ok:false,error:"offline",status:0};
 const qs=new URLSearchParams();
 Object.entries(opts?.query??{}).forEach(([k,v])=>qs.set(k,String(v)));
 const endpoint="/api/referrals/proxy/"+route+(qs.size?"?"+qs.toString():"");
 const headers:Record<string,string>={"Accept":"application/json"};
 if(method==="POST"){
  headers["Content-Type"]="application/json";
  headers["Idempotency-Key"]=crypto.randomUUID();
  headers["x-synq-ops-csrf"]=csrf();
 }
 try{
  const res=await fetch(endpoint,{method,headers,cache:"no-store",credentials:"same-origin",
   body:method==="POST"?JSON.stringify(opts?.body??{}):undefined});
  let value:unknown;
  try{value=await res.json();}catch{return {ok:false,status:503,error:"invalid_response"};}
  if(res.ok)return {ok:true,data:value as T,status:res.status};
  const err=value as {error?:unknown};
  return {ok:false,error:typeof err?.error==="string"?err.error:"request_failed",status:res.status};
 }catch{return {ok:false,error:"referrals_unavailable",status:0};}
}
