import {NextRequest,NextResponse} from "next/server";
import {OPS_COOKIE,opsCheckCsrf,opsCheckOrigin,opsConfig}
 from "../../../../../lib/operations-auth";

type RouteContext={params:Promise<{segments:string[]}>};
const moduleIds="M(?:21|22|23|25|29|31|37)";
const uuid="[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[1-8][a-fA-F0-9]{3}-[89aAbB][a-fA-F0-9]{3}-[a-fA-F0-9]{12}";
const listing=new RegExp("^"+moduleIds+"/items$");
const record=new RegExp("^"+moduleIds+"/items/"+uuid+"$");
const stockBalances=new RegExp("^M22/items/"+uuid+"/balances$");
const domainDetails=new RegExp("^"+moduleIds+"/items/"+uuid+"/details$");
const transitions=new RegExp("^"+moduleIds+"/items/"+uuid+"/transitions$");
const stockMovement=new RegExp("^M22/items/"+uuid+"/movements$");
// M23 preventive maintenance: READ-ONLY views for the existing M23 screens. Planning, linking, calibration and approvals are not proxied.
const m23Reads=/^M23\/maintenance\/(?:policies|plans|calendar|planner\/health|inspections\/pending)$/;
const m23Limited=/^M23\/maintenance\/(?:plans|calendar|inspections\/pending)$/;
const isoInstant=/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/;
const mortuaryList=/^M27\/cases$/;
const mortuaryCase=new RegExp("^M27/cases/"+uuid+"$");
const mortuaryActions=new RegExp("^M27/cases/"+uuid+"/actions$");
const mortuaryCustody=new RegExp("^M27/cases/"+uuid+"/custody$");
const mortuaryApprovals=new RegExp("^M27/cases/"+uuid+"/approvals$");
const mortuaryHolds=new RegExp("^M27/cases/"+uuid+"/holds$");
const mortuaryAudit=new RegExp("^M27/cases/"+uuid+"/audit$");
function allow(path:string,method:"GET"|"POST"){
 if(method==="GET")return path==="catalog"||listing.test(path)||record.test(path)||stockBalances.test(path)||domainDetails.test(path)||mortuaryList.test(path)||mortuaryCase.test(path)||mortuaryCustody.test(path)||mortuaryApprovals.test(path)||mortuaryHolds.test(path)||mortuaryAudit.test(path)||m23Reads.test(path);
 return listing.test(path)||transitions.test(path)||stockMovement.test(path)||mortuaryList.test(path)||mortuaryActions.test(path);
}
function send(error:string,status:number){return NextResponse.json({error},{status,headers:{"Cache-Control":"no-store"}});}
async function proxy(request:NextRequest,context:RouteContext,method:"GET"|"POST"){
 let config;
 try{config=opsConfig();}catch{return send("operations_not_configured",503);}
 const parts=(await context.params).segments;
 if(!Array.isArray(parts)||parts.length>5||parts.some(p=>!p||p.length>80||p.includes("%")))
  return send("invalid_path",404);
 const path=parts.join("/");
 if(!allow(path,method))return send("route_denied",404);
 const token=request.cookies.get(OPS_COOKIE.token)?.value;
 if(!token||token.length>16000||token.length<80)return send("sign_in_required",401);
 if(method==="POST"&&(!opsCheckOrigin(request,config)||!opsCheckCsrf(request)))
  return send("csrf_denied",403);
 const url=new URL("/api/v1/operations/"+path,config.apiOrigin);
 if(method==="GET"){
  const limit=request.nextUrl.searchParams.get("limit");
  if(limit!==null){
   if((!listing.test(path)&&!mortuaryList.test(path)&&!m23Limited.test(path))||!/^\d{1,3}$/.test(limit)||Number(limit)>100||Number(limit)<1)
    return send("invalid_limit",400);
   url.searchParams.set("limit",limit);
  }
  const extra=path==="M23/maintenance/calendar"?["from","to"]:path==="M23/maintenance/planner/health"?["staleAfterMinutes"]:[];
  if([...request.nextUrl.searchParams.keys()].some(k=>k!=="limit"&&!extra.includes(k)))
   return send("unsupported_filter",400);
  if(path==="M23/maintenance/calendar"){
   const from=request.nextUrl.searchParams.get("from"),to=request.nextUrl.searchParams.get("to");
   if(!from||!to||!isoInstant.test(from)||!isoInstant.test(to))return send("invalid_range",400);
   url.searchParams.set("from",from);url.searchParams.set("to",to);
  }
  const stale=request.nextUrl.searchParams.get("staleAfterMinutes");
  if(stale!==null){
   if(!/^\d{1,4}$/.test(stale)||Number(stale)<1||Number(stale)>1440)return send("invalid_threshold",400);
   url.searchParams.set("staleAfterMinutes",stale);
  }
 }
 let body:string|undefined;
 if(method==="POST"){
  if(request.headers.get("content-type")?.split(";")[0].trim()!=="application/json")
   return send("json_required",415);
  const hinted=Number(request.headers.get("content-length")||"0");
  if(hinted>32768)return send("payload_too_large",413);
  body=await request.text();
  if(body.length>32768)return send("payload_too_large",413);
  try{const parsed=JSON.parse(body);if(!parsed||Array.isArray(parsed)||typeof parsed!=="object")throw Error();}
  catch{return send("invalid_json",400);}
 }
 try{
  const upstream=await fetch(url,{
   method,
   headers:{"Authorization":"Bearer "+token,"Accept":"application/json",
    ...(method==="POST"?{"Content-Type":"application/json"}:{})},
   ...(method==="POST"?{body}:{}),
   cache:"no-store",redirect:"error",signal:AbortSignal.timeout(9000)
  });
  if(![200,201,400,401,403,404,409,412,413,422,503].includes(upstream.status))
   return send("operations_upstream_unavailable",503);
  const raw=await upstream.text();
  if(raw.length>1048576)return send("response_too_large",503);
  let data;
  try{data=JSON.parse(raw);}catch{return send("operations_protocol_error",503);}
  return NextResponse.json(data,{status:upstream.status,headers:{
   "Cache-Control":"no-store","X-Content-Type-Options":"nosniff"
  }});
 }catch{return send("operations_unavailable",503);}
}
export async function GET(request:NextRequest,context:RouteContext){
 return proxy(request,context,"GET");
}
export async function POST(request:NextRequest,context:RouteContext){
 return proxy(request,context,"POST");
}
