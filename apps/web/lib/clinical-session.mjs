/**
 * SYNQORIA server-only encrypted staff-session adapter.
 * There is deliberately NO browser-facing login/session issuance endpoint.
 * A qualified OIDC authorization-code/MFA callback must validate the upstream
 * tokens, revocation, scopes, tenant and facility before calling sealStaffSession.
 * A valid cookie is not a substitute for live backend authorization on each read.
 */
import {createCipheriv,createDecipheriv,randomBytes} from "node:crypto";

export const STAFF_COOKIE="__Host-synqoria-staff";
const marker="synqoria-staff-session-v1";
const b64=x=>Buffer.from(x).toString("base64url");
const from64=(text,max)=>typeof text==="string"&&text.length>0&&text.length<=max&&/^[A-Za-z0-9_-]+$/.test(text)?Buffer.from(text,"base64url"):null;
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[89ab0-9a-f]{12}$/i;
const bearer=/^[A-Za-z0-9_.~-]{16,8192}$/;
const capability=/^[a-z][a-z0-9:._-]{2,63}$/;
function keyFromEnvironment(env=process.env){
 const raw=env.SYNQORIA_STAFF_COOKIE_KEY;
 if(typeof raw!=="string"||!/^[0-9a-f]{64}$/i.test(raw))return null;
 const key=Buffer.from(raw,"hex");return key.length===32?key:null;
}
function validPayload(x,now){
 if(!x||typeof x!=="object"||Array.isArray(x)||Object.keys(x).sort().join(",")!=="actorId,bearer,capabilities,expiresAt,facilityId,issuedAt,issuer,tenantId")return false;
 if(x.issuer!=="SYNQORIA_OIDC_VERIFIED_V1")return false;
 if(!uuid.test(x.actorId)||!uuid.test(x.tenantId)||!uuid.test(x.facilityId))return false;
 if(!bearer.test(x.bearer))return false;
 if(!Number.isSafeInteger(x.issuedAt)||!Number.isSafeInteger(x.expiresAt)||x.issuedAt>now+5000||x.issuedAt<now-3600000||x.expiresAt<=now||x.expiresAt>x.issuedAt+3600000)return false;
 if(!Array.isArray(x.capabilities)||x.capabilities.length>64||new Set(x.capabilities).size!==x.capabilities.length||!x.capabilities.every(c=>typeof c==="string"&&capability.test(c)))return false;
 return true;
}
/** Server-side OIDC callback only. Never expose a route that accepts self-asserted claims. */
export function sealStaffSession(verifiedClaims,{now=Date.now(),env=process.env}={}){
 const key=keyFromEnvironment(env);
 if(!key||!validPayload(verifiedClaims,now))throw new Error("staff_session_not_verified");
 const iv=randomBytes(12);
 const cipher=createCipheriv("aes-256-gcm",key,iv);
 cipher.setAAD(Buffer.from(marker,"utf8"));
 const text=Buffer.from(JSON.stringify(verifiedClaims),"utf8");
 const output=Buffer.concat([cipher.update(text),cipher.final()]);
 return ["v1",b64(iv),b64(output),b64(cipher.getAuthTag())].join(".");
}
/** Reject invalid, tampered, expired or unconfigured cookies without disclosing why. */
export function openStaffSession(cookie,{now=Date.now(),env=process.env}={}){
 const key=keyFromEnvironment(env);
 if(!key||typeof cookie!=="string"||cookie.length>13000)return null;
 const pieces=cookie.split(".");
 if(pieces.length!==4||pieces[0]!=="v1")return null;
 const iv=from64(pieces[1],24),ct=from64(pieces[2],12500),tag=from64(pieces[3],26);
 if(!iv||iv.length!==12||!ct||ct.length===0||ct.length>10000||!tag||tag.length!==16)return null;
 try{
  const decoder=createDecipheriv("aes-256-gcm",key,iv);
  decoder.setAAD(Buffer.from(marker,"utf8"));
  decoder.setAuthTag(tag);
  const data=Buffer.concat([decoder.update(ct),decoder.final()]);
  if(data.length>10000)return null;
  const raw=JSON.parse(data.toString("utf8"));
  if(!validPayload(raw,now))return null;
  return Object.freeze({...raw,capabilities:Object.freeze([...raw.capabilities])});
 }catch{return null;}
}
export function readSessionCookie(header,{now=Date.now(),env=process.env}={}){
 if(typeof header!=="string"||header.length>14000)return null;
 const pairs=header.split(";").map(x=>x.trim()).filter(Boolean);
 const matches=pairs.filter(x=>x.startsWith(STAFF_COOKIE+"="));
 if(matches.length!==1)return null;
 return openStaffSession(matches[0].slice(STAFF_COOKIE.length+1),{now,env});
}
export function secureCookieOptions(){
 return {httpOnly:true,secure:true,sameSite:"strict",path:"/",maxAge:3600,priority:"high"};
}
