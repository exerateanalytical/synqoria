import {NextRequest,NextResponse} from "next/server";
import {OPS_COOKIE,opsCheckCsrf,opsCheckOrigin,opsConfig}
 from "../../../../../lib/operations-auth";
import {validSegments,policy,upstreamRoute,scopedQuery} from "../../../../../lib/referrals/proxy-policy";
type Context={params:Promise<{segments:string[]}>};
function fail(error:string,status:number){
 return NextResponse.json({error},{status,headers:{"Cache-Control":"no-store"}});
}
function origin(){
 const value=process.env.SYNQORIA_REFERRAL_API_ORIGIN;
 if(!value)throw Error("missing_origin");
 const parsed=new URL(value);
 if(parsed.protocol!=="https:"||parsed.username||parsed.password||parsed.search||
   parsed.hash||parsed.pathname!=="/")throw Error("invalid_origin");
 return parsed.origin;
}
async function proxy(request:NextRequest,context:Context,method:"GET"|"POST"){
 let config,source;
 try{config=opsConfig();source=origin();}catch{return fail("referrals_not_configured",503);}
 const segments=(await context.params).segments;
 if(!validSegments(segments))return fail("invalid_path",404);
 const route=segments.join("/");
 if(!policy(method,route))return fail("route_denied",404);
 const path=upstreamRoute(route);
 if(!path)return fail("route_denied",404);
 const access=request.cookies.get(OPS_COOKIE.token)?.value;
 if(!access||access.length<80||access.length>16000)return fail("identity_denied",401);
 if(method==="POST"&&(!opsCheckOrigin(request,config)||!opsCheckCsrf(request)))
  return fail("csrf_denied",403);
 const search=scopedQuery(method,request.nextUrl.searchParams);
 if(!search)return fail("unsupported_filter",400);
 const endpoint=new URL(path,source);
 search.forEach((value,key)=>endpoint.searchParams.set(key,value));
 let body:string|undefined;
 const headers:Record<string,string>={"Accept":"application/json","Authorization":"Bearer "+access};
 if(method==="POST"){
  if(request.headers.get("content-type")?.split(";")[0].trim()!=="application/json")
   return fail("json_required",415);
  const key=request.headers.get("idempotency-key")||"";
  if(!/^[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$/.test(key))
   return fail("idempotency_key_required",422);
  const length=Number(request.headers.get("content-length")||"0");
  if(!Number.isFinite(length)||length>32768)return fail("payload_too_large",413);
  body=await request.text();
  if(body.length>32768)return fail("payload_too_large",413);
  try{const x=JSON.parse(body);if(x===null||typeof x!=="object"||Array.isArray(x))throw Error();}
  catch{return fail("invalid_json",400);}
  headers["Idempotency-Key"]=key;headers["Content-Type"]="application/json";
 }
 try{
  const res=await fetch(endpoint,{method,headers,body,cache:"no-store",
   redirect:"error",signal:AbortSignal.timeout(9000)});
  if(![200,201,400,401,403,404,409,415,422,503].includes(res.status))
   return fail("referrals_upstream_unavailable",503);
  const raw=await res.text();
  if(raw.length>1048576)return fail("response_too_large",503);
  let value;
  try{value=JSON.parse(raw);}catch{return fail("referrals_protocol_error",503);}
  return NextResponse.json(value,{status:res.status,headers:{
   "Cache-Control":"no-store","X-Content-Type-Options":"nosniff"}});
 }catch{return fail("referrals_unavailable",503);}
}
export async function GET(r:NextRequest,c:Context){return proxy(r,c,"GET");}
export async function POST(r:NextRequest,c:Context){return proxy(r,c,"POST");}
