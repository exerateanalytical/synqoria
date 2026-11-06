import {createHash, randomBytes, timingSafeEqual} from "node:crypto";
import type {NextRequest} from "next/server";

export const OPS_COOKIE={
  token:"__Host-synq_ops_access",
  state:"__Host-synq_ops_oauth_state",
  verifier:"__Host-synq_ops_oauth_verifier",
  csrf:"__Host-synq_ops_csrf"
} as const;
export type OpsConfig={issuer:string;clientId:string;webOrigin:string;apiOrigin:string;clientSecret?:string};
function secureOrigin(value:string):string{
 const url=new URL(value);
 if(url.protocol!=="https:"||url.username||url.password||url.search||url.hash||url.pathname!=="/")
  throw new Error("operations_secure_origin_required");
 return url.origin;
}
export function opsConfig():OpsConfig{
 const issuer=process.env.SYNQORIA_OPS_OIDC_ISSUER?.replace(/\/$/,"");
 const clientId=process.env.SYNQORIA_OPS_OIDC_CLIENT_ID;
 const web=process.env.SYNQORIA_OPS_WEB_ORIGIN;
 const api=process.env.SYNQORIA_OPS_API_ORIGIN;
 if(!issuer||!clientId||!web||!api||!/^[A-Za-z0-9_.:-]{3,120}$/.test(clientId))
  throw new Error("operations_identity_not_configured");
 const parsed=new URL(issuer);
 if(parsed.protocol!=="https:"||parsed.username||parsed.password||parsed.search||parsed.hash)
  throw new Error("operations_oidc_issuer_invalid");
 return {issuer,clientId,webOrigin:secureOrigin(web),apiOrigin:secureOrigin(api),
  clientSecret:process.env.SYNQORIA_OPS_OIDC_CLIENT_SECRET};
}
export function opsEqual(a:string,b:string):boolean{
 const one=Buffer.from(a),two=Buffer.from(b);
 return one.length===two.length && timingSafeEqual(one,two);
}
export function opsRandom(){return randomBytes(32).toString("base64url");}
export function opsChallenge(verifier:string){
 return createHash("sha256").update(verifier,"ascii").digest("base64url");
}
export function opsCallback(config:OpsConfig){
 return config.webOrigin+"/api/operations/auth/callback";
}
export type OidcDiscovery={issuer:string;authorization_endpoint:string;token_endpoint:string};
export async function opsDiscovery(config:OpsConfig):Promise<OidcDiscovery>{
 const endpoint=config.issuer+"/.well-known/openid-configuration";
 const response=await fetch(endpoint,{cache:"no-store",signal:AbortSignal.timeout(6000)});
 if(!response.ok)throw new Error("operations_oidc_unavailable");
 const json=await response.json() as Partial<OidcDiscovery>;
 const issuerOrigin=new URL(config.issuer).origin;
 for(const value of [json.authorization_endpoint,json.token_endpoint]){
  if(!value||new URL(value).protocol!=="https:"||new URL(value).origin!==issuerOrigin)
   throw new Error("operations_oidc_untrusted_endpoint");
 }
 if(json.issuer!==config.issuer)throw new Error("operations_oidc_issuer_mismatch");
 return json as OidcDiscovery;
}
export function opsCheckOrigin(request:NextRequest,config:OpsConfig){
 const origin=request.headers.get("origin");
 return origin===config.webOrigin;
}
export function opsCheckCsrf(request:NextRequest){
 const cookie=request.cookies.get(OPS_COOKIE.csrf)?.value;
 const header=request.headers.get("x-synq-ops-csrf");
 return !!cookie&&!!header&&opsEqual(cookie,header)&&cookie.length>=32;
}
export const opsCookieDefaults={secure:true,httpOnly:true,path:"/",sameSite:"lax" as const};
