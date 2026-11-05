import {requireCanonicalStaffAuthRequest} from "./staff-auth-request-origin.mjs";
import {OIDC_FLOW_COOKIE,clearOidcFlowCookieOptions} from "./oidc-login-flow.mjs";
/**
 * SYNQORIA P03-B0049: server-only Next/Fetch-compatible login HTTP boundary.
 *
 * IdP start, callback, and logout responses cannot reflect caller-controlled
 * redirect URLs or tokens. Cookies are host-only Secure HttpOnly SameSite,
 * never bearer or clinical claims. Caller injects the already qualified
 * server-owned staff service; unconfigured deployment must answer 503.
 */
const PRIVACY={
 "Cache-Control":"no-store, private","Pragma":"no-cache",
 "Referrer-Policy":"no-referrer","X-Content-Type-Options":"nosniff",
 "Content-Security-Policy":"default-src 'none'; frame-ancestors 'none'",
 "X-Robots-Tag":"noindex, nofollow"
};
const COOKIE=/^__Host-[a-z0-9-]{1,72}$/;
const VALUE=/^[A-Za-z0-9._-]{1,3000}$/;
const PATH=/^\/[a-z0-9/_-]*$/;
export class StaffAuthHttpError extends Error{
 constructor(){super("staff_auth_unavailable");this.name="StaffAuthHttpError";}
}
const deny=()=>{throw new StaffAuthHttpError();};
function cookie(name,value,opts){
 if(!COOKIE.test(name)||!VALUE.test(value)||!opts||opts.path!=="/"||
    opts.httpOnly!==true||opts.secure!==true||
    !["strict","lax"].includes(opts.sameSite)||
    !Number.isSafeInteger(opts.maxAge)||opts.maxAge<0||opts.maxAge>600)deny();
 return name+"="+value+"; Path=/; Max-Age="+opts.maxAge+
  "; Secure; HttpOnly; SameSite="+(opts.sameSite==="strict"?"Strict":"Lax");
}
function clearCookie(name,opts){
 if(!COOKIE.test(name)||!opts||opts.path!=="/"||opts.maxAge!==0||
    opts.httpOnly!==true||opts.secure!==true)deny();
 return name+"=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=Strict";
}
const headers=()=>new Headers(PRIVACY);
function response(status,extra={}){return new Response(null,{status,headers:{...PRIVACY,...extra}});}
function trustedIssuer(issuer,authorization){
 let base,endpoint;
 try{base=new URL(issuer);endpoint=new URL(authorization);}catch{deny();}
 if(base.protocol!=="https:"||endpoint.protocol!=="https:"||
   base.origin!==endpoint.origin||!base.hostname.includes(".")||
   endpoint.username||endpoint.password||endpoint.port||endpoint.hash||
   !endpoint.pathname.startsWith(base.pathname.replace(/\/$/,"")+"/")||
   endpoint.searchParams.get("response_type")!=="code"||
   endpoint.searchParams.get("code_challenge_method")!=="S256"||
   !endpoint.searchParams.get("state"))deny();
 return endpoint.href;
}
export function createStaffAuthHttpHandlers(service,{
 issuer,redirectPath="/workforce",publicOrigin=null
}={}){
 if(!service||typeof service.begin!=="function"||
    typeof service.complete!=="function"||typeof service.logout!=="function"||
    typeof issuer!=="string"||!PATH.test(redirectPath)||redirectPath.includes("//"))deny();
 return Object.freeze({
  async start(request){
   if(request?.method!=="GET")return response(405,{Allow:"GET"});
   if(publicOrigin!==null){
    try{requireCanonicalStaffAuthRequest(request,{publicOrigin,operation:"start"});}
    catch{return response(403);}
   }
   try{
    const start=await service.begin();
    const location=trustedIssuer(issuer,start?.authorizationUrl);
    const h=headers();h.set("Location",location);
    h.append("Set-Cookie",cookie(start.flowCookie.name,start.flowCookie.value,start.flowCookie.options));
    return new Response(null,{status:303,headers:h});
   }catch{return response(503);}
  },
  async callback(request){
   if(request?.method!=="GET")return response(405,{Allow:"GET"});
   if(publicOrigin!==null){
    try{requireCanonicalStaffAuthRequest(request,{publicOrigin,operation:"callback"});}
    catch{return response(403);}
   }
   try{
    const done=await service.complete(request);
    const h=headers();h.set("Location",redirectPath);
    h.append("Set-Cookie",cookie(done.sessionCookie.name,done.sessionCookie.value,done.sessionCookie.options));
    h.append("Set-Cookie",clearCookie(done.clearFlowCookie.name,done.clearFlowCookie.options));
    return new Response(null,{status:303,headers:h});
   }catch{
    // A denied one-use callback must also expire the old PKCE flow cookie.
    // Never leak the code, state, failure reason or access token in response.
    return response(401,{"Set-Cookie":
     clearCookie(OIDC_FLOW_COOKIE,clearOidcFlowCookieOptions())});
   }
  },
  async logout(request){
   if(request?.method!=="POST")return response(405,{Allow:"POST"});
   if(publicOrigin!==null){
    try{requireCanonicalStaffAuthRequest(request,{publicOrigin,operation:"logout"});}
    catch{return response(403);}
   }
   try{
    const done=await service.logout(request);
    const h=headers();
    h.append("Set-Cookie",clearCookie(done.clearSessionCookie.name,done.clearSessionCookie.options));
    return new Response(null,{status:204,headers:h});
   }catch{
    // Even a DB outage should remove the browser cookie. Never falsely
    // report server-side revocation success; return 503 to caller.
    const h=headers();h.append("Set-Cookie",
     "__Host-synqoria-session=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=Strict");
    return new Response(null,{status:503,headers:h});
   }
  }
 });
}
