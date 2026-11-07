import {NextRequest,NextResponse} from "next/server";
import {OPS_COOKIE,opsConfig} from "../../../../../lib/operations-auth";
import {verifiedOperationsSession} from "../../../../../lib/operations-verified-session.mjs";

/**
 * Canonical P03 device-safe, server-verified session identity.
 * No JWT payload decoding, token or grants are exposed to browsers.
 * A valid session proves only the named user / cell / facility binding;
 * M38 remains the authority for operations and record access.
 */
export const runtime="nodejs";
export const dynamic="force-dynamic";

const HEADERS={
 "Cache-Control":"no-store, max-age=0",
 "Pragma":"no-cache",
 "Vary":"Cookie",
 "X-Content-Type-Options":"nosniff",
};
function failure(error:string,status:number){
 return NextResponse.json({error},{status,headers:HEADERS});
}
export async function GET(request:NextRequest){
 const token=request.cookies.get(OPS_COOKIE.token)?.value;
 if(!token)return failure("identity_denied",401);
 let config;
 try{config=opsConfig();}catch{return failure("identity_not_configured",503);}
 const audience=process.env.SYNQORIA_OPS_OIDC_AUDIENCE;
 if(!audience)return failure("identity_not_configured",503);
 try{
  const verified=await verifiedOperationsSession(token,{
   issuer:config.issuer,audience,requiredPermission:"m38:sync_read",
  });
  return NextResponse.json(verified,{headers:HEADERS});
 }catch{
  // No fallback to unverified JWT payloads, previous sessions or stale JWKS.
  return failure("identity_unavailable_or_denied",401);
 }
}
