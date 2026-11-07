/**
 * SYNQORIA P03 — server-only authorization-code + PKCE handshake boundary.
 *
 * Source-only until integrated with a TRUSTED OIDC token endpoint, one-time
 * replay store, IdP revocation and scoped workforce/tenant-cell authorization.
 * A validated callback is NOT a staff login and never seals a staff session.
 * No browser localStorage, patient identifiers, app-supplied redirect or JWK URL.
 */
import {createCipheriv, createDecipheriv, createHash, randomBytes, timingSafeEqual} from "node:crypto";

export const OIDC_FLOW_COOKIE="__Host-synqoria-oidc-flow";
const AAD=Buffer.from("synqoria-oidc-pkce-handshake-v1","utf8");
const FLOW_MS=300000;
const b64=b=>Buffer.from(b).toString("base64url");
const tokenPattern=/^[A-Za-z0-9_-]{32,128}$/;
const authCodePattern=/^[A-Za-z0-9._~-]{8,2048}$/;
const webClientPattern=/^[A-Za-z0-9._~-]{3,128}$/;
const exactPayloadKeys="clientId,expiresAt,issuedAt,issuer,nonce,redirectUri,state,verifier";
export class OidcFlowError extends Error {
 constructor(){super("oidc_flow_denied");this.name="OidcFlowError";}
}
const deny=()=>{throw new OidcFlowError();};

function pinnedUrl(value){
 if(typeof value!=="string"||value.length>700||value.trim()!==value)deny();
 try{
  const url=new URL(value);
  const hostname=url.hostname.toLowerCase();
  if(url.protocol!=="https:"||url.username||url.password||url.port||url.search||url.hash||
     !hostname.includes(".")||hostname==="localhost"||hostname.endsWith(".localhost")||
     hostname.endsWith(".test")||hostname.endsWith(".invalid")||hostname.endsWith(".example")||
     /^[0-9.]+$/.test(hostname)||hostname.includes(".."))deny();
  return url;
 }catch{deny();}
}
function validConfig(config){
 if(!config||typeof config!=="object"||Array.isArray(config))deny();
 const {issuer,authorizationEndpoint,clientId,redirectUri,flowKey}=config;
 if(typeof clientId!=="string"||!webClientPattern.test(clientId)||
    typeof flowKey!=="string"||!/^[0-9a-f]{64}$/i.test(flowKey))deny();
 const i=pinnedUrl(issuer),a=pinnedUrl(authorizationEndpoint),r=pinnedUrl(redirectUri);
 if(i.origin!==a.origin||a.pathname===i.pathname||
    !a.pathname.startsWith(i.pathname.replace(/\/$/,"")+"/")||
    r.pathname!=="/api/auth/oidc/callback"||r.origin===i.origin||
    i.href!==issuer||a.href!==authorizationEndpoint||r.href!==redirectUri)deny();
 return {issuer,authorizationEndpoint,clientId,redirectUri,key:Buffer.from(flowKey,"hex")};
}
function decode(value,maxBytes){
 if(typeof value!=="string"||!/^[A-Za-z0-9_-]+$/.test(value))deny();
 const bytes=Buffer.from(value,"base64url");
 if(bytes.length===0||bytes.length>maxBytes||bytes.toString("base64url")!==value)deny();
 return bytes;
}
function encodeFlow(payload,key){
 const iv=randomBytes(12),cipher=createCipheriv("aes-256-gcm",key,iv);
 cipher.setAAD(AAD);
 const encrypted=Buffer.concat([cipher.update(Buffer.from(JSON.stringify(payload),"utf8")),cipher.final()]);
 return ["v1",b64(iv),b64(encrypted),b64(cipher.getAuthTag())].join(".");
}
function decodeFlow(cookie,key){
 if(typeof cookie!=="string"||cookie.length>3000)deny();
 const parts=cookie.split(".");
 if(parts.length!==4||parts[0]!=="v1")deny();
 const iv=decode(parts[1],12),encrypted=decode(parts[2],2000),tag=decode(parts[3],16);
 if(iv.length!==12||tag.length!==16)deny();
 try{
  const cipher=createDecipheriv("aes-256-gcm",key,iv);
  cipher.setAAD(AAD);cipher.setAuthTag(tag);
  const bytes=Buffer.concat([cipher.update(encrypted),cipher.final()]);
  const payload=JSON.parse(new TextDecoder("utf-8",{fatal:true}).decode(bytes));
  if(!payload||typeof payload!=="object"||Array.isArray(payload)||
     Object.keys(payload).sort().join(",")!==exactPayloadKeys)deny();
  return payload;
 }catch{deny();}
}
function constantEqual(a,b){
 if(typeof a!=="string"||typeof b!=="string")return false;
 const x=Buffer.from(a),y=Buffer.from(b);
 return x.length===y.length&&x.length>0&&timingSafeEqual(x,y);
}
export const oidcFlowCookieOptions=()=>Object.freeze({
 httpOnly:true,secure:true,sameSite:"lax",path:"/",maxAge:300,priority:"high"
});
export const clearOidcFlowCookieOptions=()=>Object.freeze({...oidcFlowCookieOptions(),maxAge:0});

/** Prepare a fixed-origin IdP redirect; return a sealed HttpOnly PKCE cookie. */
export function beginOidcAuthorization(config,{now=Date.now()}={}){
 const c=validConfig(config);
 if(!Number.isSafeInteger(now)||now<1||now>8640000000000000-FLOW_MS)deny();
 const state=b64(randomBytes(32)),nonce=b64(randomBytes(32)),verifier=b64(randomBytes(48));
 const challenge=b64(createHash("sha256").update(verifier,"ascii").digest());
 const payload={state,nonce,verifier,issuedAt:now,expiresAt:now+FLOW_MS,issuer:c.issuer,
  clientId:c.clientId,redirectUri:c.redirectUri};
 const url=new URL(c.authorizationEndpoint);
 url.searchParams.set("response_type","code");
 url.searchParams.set("client_id",c.clientId);
 url.searchParams.set("redirect_uri",c.redirectUri);
 url.searchParams.set("scope","openid");
 url.searchParams.set("state",state);
 url.searchParams.set("nonce",nonce);
 url.searchParams.set("code_challenge",challenge);
 url.searchParams.set("code_challenge_method","S256");
 return Object.freeze({authorizationUrl:url.toString(),cookieValue:encodeFlow(payload,c.key),
  cookieOptions:oidcFlowCookieOptions()});
}
/**
 * Validate callback proof; returns ephemeral code/PKCE values server-side ONLY.
 * Caller MUST consume an atomic one-use server-side state entry before exchange;
 * then verify both tokens, revocation and authoritative workforce grants. Never
 * treat the result as a login, return token material to the browser, or reuse it.
 */
export function inspectOidcAuthorizationCallback(callbackUrl,cookie,config,{now=Date.now()}={}){
 const c=validConfig(config);
 if(!Number.isSafeInteger(now)||now<1)deny();
 const flow=decodeFlow(cookie,c.key);
 if(flow.issuer!==c.issuer||flow.clientId!==c.clientId||flow.redirectUri!==c.redirectUri||
    !Number.isSafeInteger(flow.issuedAt)||!Number.isSafeInteger(flow.expiresAt)||
    flow.expiresAt!==flow.issuedAt+FLOW_MS||now<flow.issuedAt||now>=flow.expiresAt||
    !tokenPattern.test(flow.state)||!tokenPattern.test(flow.nonce)||
    !tokenPattern.test(flow.verifier))deny();
 let url;
 try{url=new URL(callbackUrl);}catch{deny();}
 if(url.origin+url.pathname!==c.redirectUri||url.hash)deny();
 for(const k of url.searchParams.keys())if(!["code","state","iss"].includes(k))deny();
 const state=url.searchParams.getAll("state"),code=url.searchParams.getAll("code"),issuer=url.searchParams.getAll("iss");
 if(state.length!==1||code.length!==1||issuer.length>1||!authCodePattern.test(code[0])||
    !constantEqual(state[0],flow.state)||(issuer.length&&issuer[0]!==c.issuer))deny();
 return Object.freeze({code:code[0],codeVerifier:flow.verifier,nonce:flow.nonce,
  issuer:flow.issuer,clientId:flow.clientId,redirectUri:flow.redirectUri});
}
