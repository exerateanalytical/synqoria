import {NextRequest,NextResponse} from "next/server";
import {OPS_COOKIE,opsConfig} from "../../../../lib/operations-auth";
import {makeJwksLoader,verifierMode,verifyAccessToken} from "../../../../lib/offline/identity";

/**
 * Tells the installed device WHO the signed-in user is and for which hospital scope, without ever exposing the token.
 * FAIL CLOSED: unless a cryptographic verifier is explicitly configured (SYNQORIA_OFFLINE_SESSION_VERIFIER=jwks plus an audience), this route answers 503 and
 * the device refuses to enroll, unlock or synchronize. The claims are used for local admission only; the M38 service verifies the token again on every call.
 * Pending: replacement by the shared P03 verifier (integration lead).
 */
function send(error:string,status:number){return NextResponse.json({error},{status,headers:{"Cache-Control":"no-store","X-Content-Type-Options":"nosniff"}});}
let loader:ReturnType<typeof makeJwksLoader>|null=null;
export async function GET(request:NextRequest){
 if(verifierMode(process.env)!=="jwks")return send("identity_verification_not_integrated",503);
 const token=request.cookies.get(OPS_COOKIE.token)?.value;
 if(!token)return send("identity_denied",401);
 let issuer:string;
 try{issuer=opsConfig().issuer;}catch{return send("identity_verification_not_integrated",503);}
 loader??=makeJwksLoader(issuer);
 const r=await verifyAccessToken(token,{issuer,audience:process.env.SYNQORIA_OFFLINE_OIDC_AUDIENCE!,jwks:loader,nowMs:()=>Date.now()});
 if(!r.ok)return send(r.code==="jwks_unavailable"?"identity_verification_unavailable":"identity_denied",r.code==="jwks_unavailable"?503:401);
 return NextResponse.json(r.session,{headers:{"Cache-Control":"no-store","X-Content-Type-Options":"nosniff"}});
}
