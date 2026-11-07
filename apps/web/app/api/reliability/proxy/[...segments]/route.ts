import {NextRequest,NextResponse} from "next/server";
import {OPS_COOKIE,opsCheckCsrf,opsCheckOrigin,opsConfig} from "../../../../../lib/operations-auth";
import {isAllowed,isSafeSegments,safeQuery} from "../../../../../lib/reliability/proxy-policy";

/**
 * Same-origin proxy for the M40 browser screens. It adds the bearer token from the HttpOnly session
 * cookie, enforces CSRF + Origin on writes, allow-lists every route and query key, and never exposes the
 * integration-only feed endpoints. Cell/facility scope comes from the verified token on the service.
 * Reuses the operations hospital sign-in session; no IAM change is made here.
 */
type RouteContext={params:Promise<{segments:string[]}>};
function send(error:string,status:number){return NextResponse.json({error,details:[]},{status,headers:{"Cache-Control":"no-store"}});}
function apiOrigin():string{
 const raw=process.env.SYNQORIA_RELIABILITY_API_ORIGIN;
 if(!raw)throw new Error("reliability_not_configured");
 const url=new URL(raw);
 if(url.protocol!=="https:"||url.username||url.password||url.search||url.hash||url.pathname!=="/")throw new Error("reliability_not_configured");
 return url.origin;
}
async function proxy(request:NextRequest,context:RouteContext,method:"GET"|"POST"){
 let config,origin;
 try{config=opsConfig();origin=apiOrigin();}catch{return send("reliability_not_configured",503);}
 const parts=(await context.params).segments;
 if(!isSafeSegments(parts))return send("invalid_path",404);
 const path=parts.join("/");
 if(!isAllowed(method,path))return send("route_denied",404);
 const token=request.cookies.get(OPS_COOKIE.token)?.value;
 if(!token||token.length>16000||token.length<80)return send("identity_denied",401);
 if(method==="POST"&&(!opsCheckOrigin(request,config)||!opsCheckCsrf(request)))return send("csrf_denied",403);
 const query=safeQuery(request.nextUrl.searchParams);
 if(!query)return send("unsupported_filter",400);
 const url=new URL("/api/v1/reliability/"+path,origin);
 query.forEach((v,k)=>url.searchParams.append(k,v));
 const headers:Record<string,string>={"Authorization":"Bearer "+token,"Accept":"application/json"};
 let body:string|undefined;
 if(method==="POST"){
  if(request.headers.get("content-type")?.split(";")[0].trim()!=="application/json")return send("json_required",415);
  const key=request.headers.get("idempotency-key")||"";
  if(!/^[0-9a-fA-F-]{36}$/.test(key))return send("idempotency_key_required",422);
  headers["Idempotency-Key"]=key;headers["Content-Type"]="application/json";
  body=await request.text();
  if(body.length>32768)return send("payload_too_large",413);
  try{const parsed=JSON.parse(body);if(!parsed||Array.isArray(parsed)||typeof parsed!=="object")throw Error();}
  catch{return send("malformed_request",400);}
 }
 try{
  const upstream=await fetch(url,{method,headers,body,cache:"no-store",redirect:"error",signal:AbortSignal.timeout(9000)});
  if(![200,201,400,401,403,404,409,422,503].includes(upstream.status))return send("reliability_unavailable",503);
  const raw=await upstream.text();
  if(raw.length>2097152)return send("reliability_unavailable",503);
  let data;
  try{data=JSON.parse(raw);}catch{return send("reliability_unavailable",503);}
  return NextResponse.json(data,{status:upstream.status,headers:{"Cache-Control":"no-store","X-Content-Type-Options":"nosniff"}});
 }catch{return send("reliability_unavailable",503);}
}
export async function GET(request:NextRequest,context:RouteContext){return proxy(request,context,"GET");}
export async function POST(request:NextRequest,context:RouteContext){return proxy(request,context,"POST");}
