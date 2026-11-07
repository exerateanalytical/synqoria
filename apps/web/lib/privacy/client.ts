export type Failure={ok:false;status:number;code:string};
export type Success<T>={ok:true;status:number;data:T};
export type Result<T>=Failure|Success<T>;
const CSRF="__Host-synq_ops_csrf";
function token(){
 if(typeof document==="undefined")return "";
 const entry=document.cookie.split("; ").find(x=>x.startsWith(CSRF+"="));
 return entry?decodeURIComponent(entry.slice(CSRF.length+1)):"";
}
export async function call<T>(method:"GET"|"POST",route:string,opts?:{
 query?:Record<string,string|number>;body?:unknown;key?:string
}):Promise<Result<T>>{
 if(typeof navigator!=="undefined"&&navigator.onLine===false)
  return {ok:false,status:0,code:"offline"};
 const query=new URLSearchParams();
 Object.entries(opts?.query||{}).forEach(([k,v])=>query.set(k,String(v)));
 const url="/api/privacy/proxy/"+route+(query.size?"?"+query.toString():"");
 const headers:Record<string,string>={"Accept":"application/json"};
 if(method==="POST"){
  headers["Content-Type"]="application/json";
  headers["Idempotency-Key"]=opts?.key||crypto.randomUUID();
  headers["x-synq-ops-csrf"]=token();
 }
 try{
  const response=await fetch(url,{method,headers,cache:"no-store",credentials:"same-origin",
   body:method==="POST"?JSON.stringify(opts?.body||{}):undefined});
  let data:unknown;
  try{data=await response.json();}catch{return {ok:false,status:503,code:"invalid_response"};}
  if(response.ok)return {ok:true,status:response.status,data:data as T};
  const e=data as {error?:unknown};
  return {ok:false,status:response.status,
   code:typeof e?.error==="string"?e.error:"privacy_unavailable"};
 }catch{return {ok:false,status:0,code:"privacy_unavailable"};}
}
export type RecordRow={
 id:string;subject_ref:string;status:string;version:number;
 purpose?:string;recipient?:string;operation?:string;effective_at?:string;expires_at?:string;
 proof_id?:string;category?:string;reason?:string;directive_id?:string;record_class?:string;
 created_at:string;
};
export const code=/^[A-Z][A-Z0-9_]{1,63}$/;
export const uuid=/^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/i;
