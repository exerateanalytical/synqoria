import {NextRequest,NextResponse} from "next/server";
import {OPS_COOKIE,opsCheckCsrf,opsCheckOrigin,opsConfig}
 from "../../../../../lib/operations-auth";
import {validSegments,allowed,scopedQuery} from "../../../../../lib/scheduling/proxy-policy";
type Context={params:Promise<{segments:string[]}>};
function fail(error:string,status:number){
 return NextResponse.json({error},{status,headers:{"Cache-Control":"no-store"}});
}
function upstream(){
 const value=process.env.SYNQORIA_SCHEDULING_API_ORIGIN;
 if(!value)throw Error("not_configured");
 const u=new URL(value);
 if(u.protocol!=="https:"||u.username||u.password||u.search||u.hash||u.pathname!=="/")
  throw Error("not_configured");
 return u.origin;
}
async function proxy(request:NextRequest,context:Context,method:"GET"|"POST"){
 let config,origin;
 try{config=opsConfig();origin=upstream();}catch{return fail("scheduling_not_configured",503);}
 const parts=(await context.params).segments;
 if(!validSegments(parts))return fail("invalid_path",404);
 const route=parts.join("/");
 if(!allowed(method,route))return fail("route_denied",404);
 const bearer=request.cookies.get(OPS_COOKIE.token)?.value;
 if(!bearer||bearer.length<80||bearer.length>16000)return fail("identity_denied",401);
 if(method==="POST"&&(!opsCheckOrigin(request,config)||!opsCheckCsrf(request)))
  return fail("csrf_denied",403);
 const query=method==="GET"?scopedQuery(route,request.nextUrl.searchParams):
  [...request.nextUrl.searchParams.keys()].length===0?new URLSearchParams():null;
 if(!query)return fail("unsupported_filter",400);
 const endpoint=new URL("/api/v1/scheduling/"+route,origin);
 query.forEach((v,k)=>endpoint.searchParams.set(k,v));
 const headers:Record<string,string>={"Accept":"application/json","Authorization":"Bearer "+bearer};
 let body:string|undefined;
 if(method==="POST"){
  if(request.headers.get("content-type")?.split(";")[0].trim()!=="application/json")
   return fail("json_required",415);
  const key=request.headers.get("idempotency-key")||"";
  if(!/^[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$/.test(key))
   return fail("idempotency_key_required",422);
  const hint=Number(request.headers.get("content-length")||"0");
  if(!Number.isFinite(hint)||hint>32768)return fail("payload_too_large",413);
  body=await request.text();
  if(body.length>32768)return fail("payload_too_large",413);
  try{const parsed=JSON.parse(body);if(!parsed||typeof parsed!=="object"||Array.isArray(parsed))throw Error();}
  catch{return fail("malformed_request",400);}
  headers["Content-Type"]="application/json";headers["Idempotency-Key"]=key;
 }
 try{
  const res=await fetch(endpoint,{method,headers,body,cache:"no-store",
   redirect:"error",signal:AbortSignal.timeout(9000)});
  if(![200,201,400,401,403,404,409,415,422,503].includes(res.status))
   return fail("scheduling_upstream_unavailable",503);
  const raw=await res.text();
  if(raw.length>1048576)return fail("response_too_large",503);
  let json;
  try{json=JSON.parse(raw);}catch{return fail("scheduling_protocol_error",503);}
  return NextResponse.json(json,{status:res.status,headers:{
   "Cache-Control":"no-store","X-Content-Type-Options":"nosniff"}});
 }catch{return fail("scheduling_unavailable",503);}
}
export async function GET(r:NextRequest,c:Context){return proxy(r,c,"GET");}
export async function POST(r:NextRequest,c:Context){return proxy(r,c,"POST");}
