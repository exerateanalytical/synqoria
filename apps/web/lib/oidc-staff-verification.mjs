/**
 * SYNQORIA P03 / GATE-01: server-only OIDC token verification boundary.
 *
 * This DOES NOT create a staff session or grant tenant/patient access. The
 * caller must separately validate token revocation, authoritative staff/shift
 * assignment, tenant-cell binding and scoped entitlements before issuing any
 * secure cookie. Pinned HTTPS issuer metadata and trusted JWKS arrive only
 * from the server's IdP configuration; never from an HTTP request or JWT header.
 * No network access, fail-open token introspection, or demo identity fallback.
 */
import {createPublicKey, timingSafeEqual, verify as verifySignature} from "node:crypto";

const BASE64URL=/^[A-Za-z0-9_-]+$/;
const KEYID=/^[A-Za-z0-9._~-]{1,128}$/;
const JWT_ALGORITHMS=new Set(["RS256","ES256"]);
const MAX_JWT_LENGTH=16384;
const MAX_SKEW_SECONDS=60;
const MAX_AUTH_AGE_SECONDS=900;
const MAX_ACCESS_LIFETIME_SECONDS=3600;
const MAX_ID_LIFETIME_SECONDS=3600;
const allowedHeader=new Set(["alg","kid","typ"]);

export class OidcVerificationError extends Error {
 constructor(){super("oidc_verification_failed");this.name="OidcVerificationError";}
}
const denied=()=>{throw new OidcVerificationError();};
const isObject=x=>x!==null&&typeof x==="object"&&!Array.isArray(x);
const int=x=>Number.isSafeInteger(x);

function decodeB64(value,maxBytes){
 if(typeof value!=="string"||!value.length||!BASE64URL.test(value))denied();
 const buffer=Buffer.from(value,"base64url");
 if(!buffer.length||buffer.length>maxBytes||buffer.toString("base64url")!==value)denied();
 return buffer;
}
function parseJsonPart(part,maxBytes){
 try{
  const object=JSON.parse(new TextDecoder("utf-8",{fatal:true}).decode(decodeB64(part,maxBytes)));
  if(!isObject(object))denied();
  return object;
 }catch{denied();}
}
function parseSignedJwt(jwt){
 if(typeof jwt!=="string"||jwt.length>MAX_JWT_LENGTH)denied();
 const parts=jwt.split(".");
 if(parts.length!==3)denied();
 const header=parseJsonPart(parts[0],1024);
 const claims=parseJsonPart(parts[1],8192);
 if(Object.keys(header).some(k=>!allowedHeader.has(k))||
    !JWT_ALGORITHMS.has(header.alg)||!KEYID.test(header.kid)||
    (header.typ!==undefined&&!['JWT','at+jwt'].includes(header.typ)))denied();
 return {header,claims,signature:decodeB64(parts[2],1024),signedBytes:Buffer.from(parts[0]+"."+parts[1],"ascii")};
}
function verifyWithPinnedJwks(jwt,jwks){
 const parsed=parseSignedJwt(jwt);
 if(!isObject(jwks)||!Array.isArray(jwks.keys)||jwks.keys.length===0||jwks.keys.length>30)denied();
 const matches=jwks.keys.filter(k=>isObject(k)&&k.kid===parsed.header.kid);
 if(matches.length!==1)denied();
 const key=matches[0];
 if(key.use!==undefined&&key.use!=="sig")denied();
 if(key.alg!==undefined&&key.alg!==parsed.header.alg)denied();
 if(key.key_ops!==undefined&&(!Array.isArray(key.key_ops)||!key.key_ops.includes("verify")))denied();
 if(parsed.header.alg==="RS256"&&(key.kty!=="RSA"||typeof key.n!=="string"||typeof key.e!=="string"))denied();
 if(parsed.header.alg==="ES256"&&(key.kty!=="EC"||key.crv!=="P-256"||typeof key.x!=="string"||typeof key.y!=="string"))denied();
 // Import only the expected public JWK parameters; ignore any extension fields.
 const publicKey=parsed.header.alg==="RS256"
  ?{kty:"RSA",n:key.n,e:key.e}
  :{kty:"EC",crv:"P-256",x:key.x,y:key.y};
 try{
  const cryptoKey=createPublicKey({key:publicKey,format:"jwk"});
  if(parsed.header.alg==="RS256"&&cryptoKey.asymmetricKeyDetails?.modulusLength<2048)denied();
  const result=verifySignature("sha256",parsed.signedBytes,
    {key:cryptoKey,...(parsed.header.alg==="ES256"?{dsaEncoding:"ieee-p1363"}:{})},parsed.signature);
  if(!result)denied();
 }catch{denied();}
 return parsed.claims;
}
function validIssuer(issuer){
 if(typeof issuer!=="string"||issuer.length>512)return false;
 try{
  const url=new URL(issuer);
  return url.protocol==="https:"&&!url.username&&!url.password&&!url.search&&!url.hash&&
   !url.port&&url.hostname.includes(".")&&!url.hostname.endsWith(".invalid")&&
   !url.hostname.endsWith(".test")&&!url.hostname.endsWith(".localhost")&&
   url.origin+url.pathname===issuer;
 }catch{return false;}
}
function checkStandardClaims(c,issuer,audience,now,maxLife){
 if(!isObject(c)||c.iss!==issuer||typeof c.sub!=="string"||
    c.sub.length<2||c.sub.length>255||/[\u0000-\u001f\u007f]/.test(c.sub))denied();
 const aud=typeof c.aud==="string"?[c.aud]:c.aud;
 if(!Array.isArray(aud)||aud.length===0||aud.length>8||!aud.every(x=>typeof x==="string"&&x.length>0)||
   !aud.includes(audience)||new Set(aud).size!==aud.length)denied();
 if(!int(c.iat)||!int(c.exp)||!int(now)||c.iat>now+MAX_SKEW_SECONDS||
   c.iat<now-maxLife-MAX_SKEW_SECONDS||c.exp<=now||c.exp>c.iat+maxLife||
   (c.nbf!==undefined&&(!int(c.nbf)||c.nbf>now+MAX_SKEW_SECONDS)))denied();
 return aud;
}
function sameSecret(a,b){
 if(typeof a!=="string"||typeof b!=="string")return false;
 const x=Buffer.from(a,"utf8"),y=Buffer.from(b,"utf8");
 return x.length===y.length&&x.length>0&&timingSafeEqual(x,y);
}
/**
 * @param {{idToken:string, accessToken:string, jwks:{keys:object[]}, issuer:string,
 *  clientId:string, apiAudience:string, nonce:string, nowSeconds?:number,
 *  maxAuthenticationAgeSeconds?:number, acceptedMfaMethods?:string[]}} options
 * @returns {{subject:string,authenticatedAt:number,verifiedAt:number,methods:readonly string[],accessScopes:readonly string[]}}
 */
export function verifyStaffOidcTokens({idToken,accessToken,jwks,issuer,clientId,apiAudience,nonce,
 nowSeconds=Math.floor(Date.now()/1000),maxAuthenticationAgeSeconds=MAX_AUTH_AGE_SECONDS,
 acceptedMfaMethods=["mfa","otp","webauthn","hwk"]}={}){
 if(!validIssuer(issuer)||typeof clientId!=="string"||!clientId||clientId.length>255||
    typeof apiAudience!=="string"||!apiAudience||apiAudience.length>255||
    !int(nowSeconds)||!int(maxAuthenticationAgeSeconds)||maxAuthenticationAgeSeconds<1||
    maxAuthenticationAgeSeconds>MAX_AUTH_AGE_SECONDS||
    typeof nonce!=="string"||nonce.length<16||nonce.length>256||
    !Array.isArray(acceptedMfaMethods)||acceptedMfaMethods.length<1||acceptedMfaMethods.length>8||
    !acceptedMfaMethods.every(x=>typeof x==="string"&&/^[a-z0-9_-]{2,32}$/.test(x)))denied();
 const identity=verifyWithPinnedJwks(idToken,jwks);
 const access=verifyWithPinnedJwks(accessToken,jwks);
 const idAud=checkStandardClaims(identity,issuer,clientId,nowSeconds,MAX_ID_LIFETIME_SECONDS);
 checkStandardClaims(access,issuer,apiAudience,nowSeconds,MAX_ACCESS_LIFETIME_SECONDS);
 if(idAud.length>1&&identity.azp!==clientId)denied();
 if(identity.azp!==undefined&&identity.azp!==clientId)denied();
 if(!sameSecret(identity.nonce,nonce)||identity.sub!==access.sub||
    !int(identity.auth_time)||identity.auth_time>nowSeconds||
    identity.auth_time<nowSeconds-maxAuthenticationAgeSeconds||
    !Array.isArray(identity.amr)||identity.amr.length<2||identity.amr.length>12||
    new Set(identity.amr).size!==identity.amr.length||
    !identity.amr.every(x=>typeof x==="string"&&/^[a-z0-9_-]{2,32}$/.test(x))||
    !identity.amr.some(x=>acceptedMfaMethods.includes(x)))denied();
 if(access.azp!==undefined&&access.azp!==clientId)denied();
 if(access.client_id!==undefined&&access.client_id!==clientId)denied();
 const scopes=access.scope===undefined?[]:typeof access.scope==="string"&&access.scope.length<4096?
  access.scope.split(" ").filter(Boolean):null;
 if(!scopes||scopes.length>64||new Set(scopes).size!==scopes.length||
    !scopes.every(x=>/^[A-Za-z0-9:._-]{2,80}$/.test(x)))denied();
 // These are verified authentication facts, NOT authorization/cell grants.
 return Object.freeze({subject:identity.sub,authenticatedAt:identity.auth_time,
  verifiedAt:nowSeconds,methods:Object.freeze([...identity.amr]),accessScopes:Object.freeze(scopes)});
}
