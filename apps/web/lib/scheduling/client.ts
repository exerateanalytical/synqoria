export const uuid=/^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/i;
export const code=/^[A-Z][A-Z0-9_]{1,63}$/;
export type Result<T>={ok:true;status:number;data:T}|{ok:false;status:number;error:string};
export type Row={id:string;status:string;version:number;created_at:string;starts_at?:string;ends_at?:string;
 patient_ref?:string;resource_id?:string;resource_code?:string;resource_type?:string;slot_id?:string;
 proof_id?:string;offered_slot_id?:string;appointment_id?:string};
function csrf(){
 if(typeof document==="undefined")return "";
 const part=document.cookie.split("; ").find(x=>x.startsWith("__Host-synq_ops_csrf="));
 return part?decodeURIComponent(part.slice("__Host-synq_ops_csrf=".length)):"";
}
export async function api<T>(method:"GET"|"POST",route:string,
   options?:{body?:object;query?:Record<string,string|number>}):Promise<Result<T>>{
 if(typeof navigator!=="undefined"&&navigator.onLine===false)
  return {ok:false,status:0,error:"offline"};
 const qs=new URLSearchParams();
 Object.entries(options?.query||{}).forEach(([k,v])=>qs.set(k,String(v)));
 const url="/api/scheduling/proxy/"+route+(qs.size?"?"+qs.toString():"");
 const headers:Record<string,string>={"Accept":"application/json"};
 if(method==="POST"){
  headers["Content-Type"]="application/json";
  headers["Idempotency-Key"]=crypto.randomUUID();
  headers["x-synq-ops-csrf"]=csrf();
 }
 try{
  const r=await fetch(url,{method,headers,credentials:"same-origin",cache:"no-store",
   body:method==="POST"?JSON.stringify(options?.body??{}):undefined});
  let data:unknown;
  try{data=await r.json();}catch{return {ok:false,status:503,error:"invalid_response"};}
  if(r.ok)return {ok:true,status:r.status,data:data as T};
  const e=data as {error?:unknown};
  return {ok:false,status:r.status,error:typeof e?.error==="string"?e.error:"request_failed"};
 }catch{return {ok:false,status:0,error:"scheduling_unavailable"};}
}
