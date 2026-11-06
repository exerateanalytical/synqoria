/**
 * SYNQORIA P03-B0086–B0089: independent last-mile auth response gate.
 * Even a verified server runtime cannot return a token-bearing response,
 * unpinned redirect, unscoped cookie, cacheable sensitive result or CORS grant.
 * Staff login outputs are redirects and Secure HttpOnly host-only cookies only.
 */
import {OIDC_FLOW_COOKIE} from "./oidc-login-flow.mjs";
import {STAFF_TICKET_COOKIE} from "./opaque-staff-session.mjs";
export class IdentityHttpResponseError extends Error{
 constructor(){super("identity_http_response_denied");this.name="IdentityHttpResponseError";}
}
const deny=()=>{throw new IdentityHttpResponseError();};
function parseCookies(response){
 const header=response.headers;
 const list=header.getSetCookie?.();
 if(!Array.isArray(list)||list.length>2||(!list.length&&header.has("set-cookie")))deny();
 return list;
}
function cookie(line,name,{clear=false,lax=false}={}){
 if(typeof line!=="string"||line.length>4200)deny();
 const parts=line.split(";").map(x=>x.trim());
 if(parts.length!==6||!parts[0].startsWith(name+"="))deny();
 const value=parts[0].slice(name.length+1);
 if(clear?value!=="":!/^[A-Za-z0-9._-]{10,3000}$/.test(value))deny();
 const attrs=parts.slice(1).map(x=>x.toLowerCase());
 if(!attrs.includes("path=/")||!attrs.includes("secure")||
    !attrs.includes("httponly")||
    !attrs.includes(lax?"samesite=lax":"samesite=strict"))deny();
 const max=attrs.find(x=>x.startsWith("max-age="));
 if(!max||!/^max-age=\d{1,3}$/.test(max)||new Set(attrs).size!==5)deny();
 const seconds=Number(max.slice(8));
 if(clear?seconds!==0:seconds<1||seconds>600)deny();
 if(attrs.some(x=>x.startsWith("domain=")||x.startsWith("expires=")))deny();
}
function issuerRedirect(value,issuer){
 if(typeof value!=="string"||typeof issuer!=="string")deny();
 let to,base;
 try{to=new URL(value);base=new URL(issuer);}catch{deny();}
 const realm=base.pathname.replace(/\/$/,"");
 if(base.protocol!=="https:"||to.protocol!=="https:"||to.origin!==base.origin||
    to.pathname!==realm+"/protocol/openid-connect/auth"||
    to.username||to.password||to.hash||
    to.searchParams.get("response_type")!=="code"||
    to.searchParams.get("code_challenge_method")!=="S256"||
    !to.searchParams.get("state")||!to.searchParams.get("nonce"))deny();
}
const PRIVACY=[
 ["cache-control",value=>typeof value==="string"&&value.includes("no-store")],
 ["pragma",value=>value==="no-cache"],
 ["referrer-policy",value=>value==="no-referrer"],
 ["x-content-type-options",value=>value==="nosniff"],
 ["content-security-policy",value=>value?.includes("default-src 'none'")]
];
export function assertSafeStaffAuthResponse(response,{operation,issuer}={}){
 if(!(response instanceof Response)||!["start","callback","logout"].includes(operation)||
    response.body!==null||response.headers.has("access-control-allow-origin")||
    response.headers.has("access-control-allow-credentials")||
    response.headers.has("access-control-expose-headers")||
    response.headers.has("content-type"))deny();
 for(const [header,check] of PRIVACY){
  if(!check(response.headers.get(header)))deny();
 }
 const status=response.status,cookies=parseCookies(response);
 const location=response.headers.get("location");
 if(status===303){
  if(operation==="start"){
   issuerRedirect(location,issuer);
   if(cookies.length!==1)deny();
   cookie(cookies[0],OIDC_FLOW_COOKIE,{lax:true});
  }else if(operation==="callback"){
   if(location!=="/workforce"||cookies.length!==2)deny();
   if(!cookies.some(c=>c.startsWith(STAFF_TICKET_COOKIE+"="))||
      !cookies.some(c=>c.startsWith(OIDC_FLOW_COOKIE+"=")))deny();
   cookie(cookies.find(c=>c.startsWith(STAFF_TICKET_COOKIE+"=")),STAFF_TICKET_COOKIE);
   cookie(cookies.find(c=>c.startsWith(OIDC_FLOW_COOKIE+"=")),OIDC_FLOW_COOKIE,{clear:true});
  }else deny();
 }else{
  if(location!==null)deny();
  if(status===204&&operation==="logout"){
   if(cookies.length!==1)deny();
   cookie(cookies[0],STAFF_TICKET_COOKIE,{clear:true});
  }else if(status===401&&operation==="callback"){
   if(cookies.length!==1)deny();
   cookie(cookies[0],OIDC_FLOW_COOKIE,{clear:true});
  }else if(status===503&&operation==="logout"){
   if(cookies.length!==1)deny();
   cookie(cookies[0],STAFF_TICKET_COOKIE,{clear:true});
  }else if([403,405,503].includes(status)){
   if(cookies.length!==0)deny();
  }else deny();
 }
 return true;
}
