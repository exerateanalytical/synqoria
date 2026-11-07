import {NextRequest,NextResponse} from "next/server";
import {OPS_COOKIE,opsCheckCsrf,opsCheckOrigin,opsConfig}
 from "../../../../../lib/operations-auth";
import {allowed,safeSegments,query} from "../../../../../lib/integrations/proxy-policy";
type Context={params:Promise<{segments:string[]}>};
function error(code:string,status:number){
 return NextResponse.json({error:code},{status,headers:{"Cache-Control":"no-store"}});
}
function targetOrigin(){
 const raw=process.env.SYNQORIA_INTEGRATION_MANAGEMENT_API_ORIGIN;
 if(!raw)throw Error("missing_origin");
 const u=new URL(raw);
 if(u.protocol!=="https:"||u.username||u.password||u.pathname!=="/"||u.search||u.hash)
  throw Error("invalid_origin");
 return u.origin;
}
async function proxy(request:NextRequest,context:Context,method:"GET"|"POST"){
 let config,origin;
 try{config=opsConfig();origin=targetOrigin();}catch{return error("integrations_not_configured",503);}
 const parts=(await context.params).segments;
 if(!safeSegments(parts))return error("invalid_path",404);
 const path=parts.join("/");
 if(!allowed(method,path))return error("route_denied",404);
 const token=request.cookies.get(OPS_COOKIE.token)?.value;
 if(!token||token.length<80||token.length>16000)return error("identity_denied",401);
 if(method==="POST"&&(!opsCheckOrigin(request,config)||!opsCheckCsrf(request)))
  return error("csrf_denied",403);
 const qs=query(method,path,request.nextUrl.searchParams);
 if(!qs)return error("unsupported_filter",400);
 const target=new URL("/api/v1/integrations/"+path,origin);
 qs.forEach((v,k)=>target.searchParams.set(k,v));
 const headers:Record<string,string>={"Accept":"application/json","Authorization":"Bearer "+token};
 let body:string|undefined;
 if(method==="POST"){
  if(request.headers.get("content-type")?.split(";")[0].trim()!=="application/json")
   return error("json_required",415);
  const key=request.headers.get("idempotency-key")||"";
  if(!/^[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$/.test(key))
   return error("idempotency_key_required",422);
  const len=Number(request.headers.get("content-length")||"0");
  if(!Number.isFinite(len)||len>32768)return error("payload_too_large",413);
  body=await request.text();
  if(body.length>32768)return error("payload_too_large",413);
  try{const p=JSON.parse(body);if(!p||Array.isArray(p)||typeof p!=="object")throw Error();}
  catch{return error("invalid_json",400);}
  headers["Content-Type"]="application/json";headers["Idempotency-Key"]=key;
 }
 try{
  const res=await fetch(target,{method,headers,body,cache:"no-store",
   redirect:"error",signal:AbortSignal.timeout(9000)});
  if(![200,201,400,401,403,404,409,415,422,503].includes(res.status))
   return error("integrations_upstream_unavailable",503);
  const raw=await res.text();
  if(raw.length>1048576)return error("response_too_large",503);
  let payload;
  try{payload=JSON.parse(raw);}catch{return error("integrations_protocol_error",503);}
  return NextResponse.json(payload,{status:res.status,headers:{
   "Cache-Control":"no-store","X-Content-Type-Options":"nosniff"}});
 }catch{return error("integrations_unavailable",503);}
}
export async function GET(r:NextRequest,c:Context){return proxy(r,c,"GET");}
export async function POST(r:NextRequest,c:Context){return proxy(r,c,"POST");}
