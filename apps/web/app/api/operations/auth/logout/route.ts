import {NextRequest,NextResponse} from "next/server";
import {OPS_COOKIE,opsCheckCsrf,opsCheckOrigin,opsConfig,opsCookieDefaults}
 from "../../../../../lib/operations-auth";

export async function POST(request:NextRequest){
 try{
  const config=opsConfig();
  if(!opsCheckOrigin(request,config)||!opsCheckCsrf(request))
   return NextResponse.json({error:"csrf_denied"},{status:403});
  const response=NextResponse.json({signedOut:true},{status:200});
  for(const name of [OPS_COOKIE.token,OPS_COOKIE.csrf,OPS_COOKIE.state,OPS_COOKIE.verifier])
   response.cookies.set(name,"",{...opsCookieDefaults,maxAge:0});
  return response;
 }catch{return NextResponse.json({error:"operations_identity_unavailable"},{status:503});}
}
