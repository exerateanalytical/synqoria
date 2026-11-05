/**
 * SYNQORIA P03-B0039 — trusted server-only OIDC staff session service.
 *
 * Unifies begin, callback, every-request reauthorization, and logout with
 * one durable replay store and one encrypted opaque-session store. It does NOT
 * accept user-authored grant claims or offer a browser login API by itself.
 * Next.js route wiring and deployed IdP/roster/vault configuration are gated.
 */
import {beginOneUseOidcAuthorization} from "./oidc-one-use.mjs";
import {finishStaffOidcLogin} from "./oidc-session-orchestrator.mjs";
import {reauthorizeClinicalStaffRequest,revokeStaffRequestSession,
 clearStaffTicketCookieOptions} from "./oidc-session-reauthorization.mjs";
import {STAFF_TICKET_COOKIE,staffTicketCookieOptions} from "./opaque-staff-session.mjs";
import {OIDC_FLOW_COOKIE,clearOidcFlowCookieOptions} from "./oidc-login-flow.mjs";

export class StaffSessionServiceError extends Error {
 constructor(){super("staff_session_service_denied");this.name="StaffSessionServiceError";}
}
const deny=()=>{throw new StaffSessionServiceError();};
const ticketFlow=/^v1\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{30,}\.[A-Za-z0-9_-]{20,}$/;
function flowCookie(request){
 const raw=request?.headers?.get?.("cookie");
 if(typeof raw!=="string"||raw.length>12000)deny();
 const parts=raw.split(";").map(x=>x.trim()).filter(x=>x.startsWith(OIDC_FLOW_COOKIE+"="));
 if(parts.length!==1)deny();
 const value=parts[0].slice(OIDC_FLOW_COOKIE.length+1);
 if(value.length>3000||!ticketFlow.test(value))deny();
 return value;
}
function trustedPublicOrigin(value){
 if(typeof value!=="string"||value.length>260)deny();
 let url;
 try{url=new URL(value);}catch{deny();}
 if(url.protocol!=="https:"||url.username||url.password||url.port||url.search||
    url.hash||url.pathname!=="/"||url.origin+"/"!==value||
    !url.hostname.includes(".")||url.hostname.endsWith(".invalid")||
    url.hostname.endsWith(".test")||url.hostname.endsWith(".localhost"))deny();
 return url.origin;
}
export function createStaffSessionService({
 config,sessionStore,replayStore,fetcher=fetch,now=Date.now,publicOrigin
}={}){
 const origin=trustedPublicOrigin(publicOrigin);
 if(!config||!config.oidc||!config.workforce||!config.introspection||
    !config.token||!config.jwks||!sessionStore||
    !["issue","read","revoke"].every(k=>typeof sessionStore[k]==="function")||
    !replayStore||!["reserve","consume"].every(k=>typeof replayStore[k]==="function")||
    typeof fetcher!=="function"||typeof now!=="function")deny();
 const clock=()=>{const at=now();if(!Number.isSafeInteger(at))deny();return at;};
 return Object.freeze({
  async begin(){
   try{
    const started=await beginOneUseOidcAuthorization(config.oidc,replayStore,{now:clock()});
    return Object.freeze({
     authorizationUrl:started.authorizationUrl,
     flowCookie:Object.freeze({name:OIDC_FLOW_COOKIE,value:started.cookieValue,options:started.cookieOptions})
    });
   }catch{deny();}
  },
  async complete(request){
   const startedAt=clock(),cookie=flowCookie(request);
   try{
    const done=await finishStaffOidcLogin(request.url,cookie,config,{
     replayStore,fetcher,issueServerSession:record=>sessionStore.issue(record),nowMs:startedAt
    });
    const maxAge=Math.floor((done.expiresAt-startedAt)/1000);
    if(!Number.isSafeInteger(maxAge)||maxAge<1||maxAge>600)deny();
    return Object.freeze({
     sessionCookie:Object.freeze({name:STAFF_TICKET_COOKIE,value:done.sessionReference,
      options:Object.freeze({...staffTicketCookieOptions(),maxAge})}),
     clearFlowCookie:Object.freeze({name:OIDC_FLOW_COOKIE,value:"",options:clearOidcFlowCookieOptions()})
    });
   }catch{deny();}
  },
  async authorize(request){
   try{return await reauthorizeClinicalStaffRequest(request,{
    sessionStore,fetcher,nowMs:clock(),
    workforceConfig:config.workforce,introspectionConfig:config.introspection
   });}catch{deny();}
  },
  async logout(request){
   if(request?.method!=="POST"||request.headers?.get("origin")!==origin)deny();
   try{
    const result=await revokeStaffRequestSession(request,{sessionStore});
    return Object.freeze({revoked:result.revoked,clearSessionCookie:Object.freeze({
     name:STAFF_TICKET_COOKIE,value:"",options:clearStaffTicketCookieOptions()
    })});
   }catch{deny();}
  }
 });
}
