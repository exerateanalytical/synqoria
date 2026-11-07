import {NextRequest,NextResponse} from "next/server";
import {OPS_COOKIE,opsCheckCsrf,opsCheckOrigin,opsConfig}
 from "../../../../../lib/operations-auth";
import {safeSegments,isAllowed,safeQuery} from "../../../../../lib/privacy/proxy-policy";

type Context={params:Promise<{segments:string[]}>};
function send(error:string,status:number){
 return NextResponse.json({error},{status,headers:{"Cache-Control":"no-store"}});
}
function privacyOrigin(){
 const raw=process.env.SYNQORIA_PRIVACY_API_ORIGIN;
 if(!raw)throw Error("privacy_not_configured");
 const u=new URL(raw);
 if(u.protocol!=="https:"||u.username||u.password||u.search||u.hash||u.pathname!=="/")
  throw Error("privacy_not_configured");
 return u.origin;
}
async function proxy(request:NextRequest,context:Context,method:"GET"|"POST"){
 let auth,origin;
 try{auth=opsConfig();origin=privacyOrigin();}catch{return send("privacy_not_configured",503);}
 const parts=(await context.params).segments;
 if(!safeSegments(parts))return send("invalid_path",404);
 const path=parts.join("/");
 if(!isAllowed(method,path))return send("route_denied",404);
 const token=request.cookies.get(OPS_COOKIE.token)?.value;
 if(!token||token.length<80||token.length>16000)return send("identity_denied",401);
 if(method==="POST"&&(!opsCheckOrigin(request,auth)||!opsCheckCsrf(request)))
  return send("csrf_denied",403);
 const query=safeQuery(path,request.nextUrl.searchParams);
 if(!query)return send("unsupported_filter",400);
 const endpoint=new URL("/api/v1/privacy/"+path,origin);
 query.forEach((v,k)=>endpoint.searchParams.set(k,v));
 let body:string|undefined;
 const headers:Record<string,string>={"Authorization":"Bearer "+token,"Accept":"application/json"};
 if(method==="POST"){
  if(request.headers.get("content-type")?.split(";")[0].trim()!=="application/json")
   return send("json_required",415);
  const key=request.headers.get("idempotency-key")||"";
  if(!/^[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$/.test(key))
   return send("idempotency_key_required",422);
  const len=Number(request.headers.get("content-length")||"0");
  if(!Number.isFinite(len)||len>32768)return send("payload_too_large",413);
  body=await request.text();
  if(body.length>32768)return send("payload_too_large",413);
  try{
   const data=JSON.parse(body);
   if(data===null||Array.isArray(data)||typeof data!=="object")throw Error();
  }catch{return send("invalid_json",400);}
  headers["Idempotency-Key"]=key;headers["Content-Type"]="application/json";
 }
 try{
  const response=await fetch(endpoint,{method,headers,body,redirect:"error",
   cache:"no-store",signal:AbortSignal.timeout(9000)});
  if(![200,201,400,401,403,404,409,415,422,503].includes(response.status))
   return send("privacy_upstream_unavailable",503);
  const raw=await response.text();
  if(raw.length>1048576)return send("response_too_large",503);
  let data;
  try{data=JSON.parse(raw);}catch{return send("privacy_protocol_error",503);}
  return NextResponse.json(data,{status:response.status,headers:{
   "Cache-Control":"no-store","X-Content-Type-Options":"nosniff"}});
 }catch{return send("privacy_unavailable",503);}
}
export async function GET(request:NextRequest,context:Context){return proxy(request,context,"GET");}
export async function POST(request:NextRequest,context:Context){return proxy(request,context,"POST");}
