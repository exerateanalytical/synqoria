/**
 * P03 reusable, verified OIDC JWT session boundary (Node server only).
 * Not browser code: consumers must never return the bearer token or the JWKS.
 * Uses built-in crypto and pinned issuer/audience. RS256 only by design.
 */
import {createPublicKey, verify as verifySignature} from "node:crypto";

const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const PERM=/^[a-z0-9]+:[a-z][a-z0-9_]{1,79}$/;
const MAX_TOKEN=16000, MAX_JWKS=65536;
export class IdentityDenied extends Error {
 constructor(code="identity_denied"){super(code);this.name="IdentityDenied";}
}
function deny(code){throw new IdentityDenied(code);}
function exactOrigin(url){
 let u;try{u=new URL(url);}catch{deny("identity_config_invalid");}
 if(u.protocol!=="https:"||u.username||u.password||u.hash||u.search)deny("identity_config_invalid");
 return u;
}
function decode(segment){
 if(typeof segment!=="string"||!segment||segment.length>11000||!/^[A-Za-z0-9_-]+$/.test(segment))deny();
 try{
  const raw=Buffer.from(segment,"base64url");
  // Reject alternate, noncanonical encodings that might bypass logging/auditing.
  if(raw.toString("base64url")!==segment)deny();
  const parsed=JSON.parse(raw.toString("utf8"));
  if(!parsed||Array.isArray(parsed)||typeof parsed!=="object")deny();
  return parsed;
 }catch{deny();}
}
function integerSeconds(x){return typeof x==="number"&&Number.isSafeInteger(x)&&x>0;}
function validUuid(s){return typeof s==="string"&&UUID.test(s);}
function jwtAudience(a,needed){
 return typeof a==="string"?a===needed:Array.isArray(a)&&a.length>0&&a.length<=8&&a.every(x=>typeof x==="string")&&a.includes(needed);
}
function objectClaim(o,k){return Object.prototype.hasOwnProperty.call(o,k)?o[k]:undefined;}

/**
 * Verify against *trusted, independently fetched* issuer JWKS.
 * Never select a JWK supplied by the token or a request.
 */
export function verifyOperationsJwt(token,{issuer,audience,jwks,now=Date.now(),expectedCell,expectedFacility}={}){
 if(typeof token!=="string"||token.length<80||token.length>MAX_TOKEN)deny();
 const parts=token.split(".");
 if(parts.length!==3)deny();
 const header=decode(parts[0]),payload=decode(parts[1]);
 if(header.alg!=="RS256"||header.typ && !["JWT","at+jwt"].includes(header.typ)||
    typeof header.kid!=="string"||!header.kid||header.kid.length>128||
    Object.prototype.hasOwnProperty.call(header,"jku")||
    Object.prototype.hasOwnProperty.call(header,"jwk")||
    Object.prototype.hasOwnProperty.call(header,"x5u")||
    Object.prototype.hasOwnProperty.call(header,"crit"))deny();
 if(typeof issuer!=="string"||typeof audience!=="string"||!audience)deny("identity_config_invalid");
 exactOrigin(issuer);
 if(typeof now!=="number"||!Number.isFinite(now))deny("identity_config_invalid");
 const epoch=Math.floor(now/1000);
 if(payload.iss!==issuer||!jwtAudience(payload.aud,audience)||
    !integerSeconds(payload.exp)||payload.exp<=epoch||
    !integerSeconds(payload.iat)||payload.iat>epoch+30||
    payload.exp-payload.iat>900||
    (payload.nbf!==undefined&&(!integerSeconds(payload.nbf)||payload.nbf>epoch+30))||
    !validUuid(payload.sub)||!validUuid(payload.synqoria_cell)||!validUuid(payload.synqoria_facility)||
    (expectedCell!==undefined&&payload.synqoria_cell!==expectedCell)||
    (expectedFacility!==undefined&&payload.synqoria_facility!==expectedFacility))deny();
 if(!integerSeconds(payload.synqoria_grant_expires)||
    payload.synqoria_grant_expires<=epoch||
    payload.synqoria_grant_expires>epoch+900||
    payload.synqoria_grant_expires>payload.exp)deny("grant_expired");
 const grants=payload.synqoria_permissions;
 if(!Array.isArray(grants)||!grants.length||grants.length>64||
    !grants.every(p=>typeof p==="string"&&PERM.test(p))||
    new Set(grants).size!==grants.length)deny("grant_denied");
 if(!jwks||!Array.isArray(jwks.keys)||jwks.keys.length>50)deny("jwks_unavailable");
 const keys=jwks.keys.filter(k=>k&&k.kid===header.kid&&k.kty==="RSA"&&
  k.use==="sig"&&(k.alg===undefined||k.alg==="RS256")&&typeof k.n==="string"&&typeof k.e==="string");
 if(keys.length!==1)deny("signing_key_unavailable");
 let key;
 try{key=createPublicKey({key:{kty:"RSA",n:keys[0].n,e:keys[0].e},format:"jwk"});}
 catch{deny("signing_key_unavailable");}
 const sig=Buffer.from(parts[2],"base64url");
 if(!parts[2]||sig.toString("base64url")!==parts[2]||sig.length!==key.asymmetricKeyDetails?.modulusLength/8)deny();
 if(!verifySignature("RSA-SHA256",Buffer.from(parts[0]+"."+parts[1],"ascii"),key,sig))deny("signature_denied");
 return Object.freeze({
  userId:payload.sub,
  scope:payload.synqoria_cell+"/"+payload.synqoria_facility,
  cell:payload.synqoria_cell,
  facility:payload.synqoria_facility,
  expiresAt:Math.min(payload.exp,payload.synqoria_grant_expires)*1000,
  grants:Object.freeze([...grants]),
 });
}

/** Load OIDC discovery and issuer JWKS server-side; no redirects, cross-origin or HTTP. */
export async function fetchIssuerJwks(issuer,{fetchImpl=fetch}={}){
 const base=exactOrigin(issuer);
 if(base.pathname!=="/"&&base.pathname.endsWith("/"))deny("identity_config_invalid");
 const discoveryUrl=issuer.replace(/\/$/,"")+"/.well-known/openid-configuration";
 async function boundedJson(url){
  let result;
  try{result=await fetchImpl(url,{method:"GET",redirect:"error",cache:"no-store",signal:AbortSignal.timeout(5000),
   headers:{Accept:"application/json"}});}
  catch{deny("jwks_unavailable");}
  if(!result.ok||!result.headers.get("content-type")?.toLowerCase().includes("application/json"))deny("jwks_unavailable");
  const length=result.headers.get("content-length");
  if(length&&(!/^\d+$/.test(length)||Number(length)>MAX_JWKS))deny("jwks_unavailable");
  let body;try{body=await result.text();}catch{deny("jwks_unavailable");}
  if(body.length>MAX_JWKS)deny("jwks_unavailable");
  try{return JSON.parse(body);}catch{deny("jwks_unavailable");}
 }
 const discovery=await boundedJson(discoveryUrl);
 if(discovery.issuer!==issuer||typeof discovery.jwks_uri!=="string")deny("jwks_unavailable");
 const jwksUri=exactOrigin(discovery.jwks_uri);
 if(jwksUri.origin!==base.origin)deny("jwks_unavailable");
 const keys=await boundedJson(jwksUri.href);
 if(!keys||!Array.isArray(keys.keys)||keys.keys.length>50)deny("jwks_unavailable");
 return keys;
}
