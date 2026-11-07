/**
 * SYNQORIA P03 — authenticated OAuth2 access-token introspection.
 *
 * A successfully signature-verified JWT is not proof that a token has not
 * been revoked. Call AFTER verifyStaffOidcTokens / code exchange and BEFORE
 * evaluating any workforce or patient/cell authorization.
 *
 * The confidential IdP client secret must come from a trusted external vault.
 * It is never accepted from a browser, logged, cached, or returned.
 */
export class OidcIntrospectionError extends Error{
 constructor(){super("oidc_introspection_denied");this.name="OidcIntrospectionError";}
}
const deny=()=>{throw new OidcIntrospectionError();};
const simple=/^[A-Za-z0-9._~-]{3,128}$/;
const secret=/^[A-Za-z0-9._~-]{32,512}$/;
const token=/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/;
const MAX_BODY=16384;
function trustedEndpoint(config){
 if(!config||typeof config!=="object"||!simple.test(config.clientId)||
    !secret.test(config.clientSecret)||typeof config.issuer!=="string"||
    typeof config.introspectionEndpoint!=="string")deny();
 let a,b;
 try{a=new URL(config.issuer);b=new URL(config.introspectionEndpoint);}catch{deny();}
 if(a.protocol!=="https:"||b.protocol!=="https:"||a.origin!==b.origin||
    a.username||a.password||a.port||a.search||a.hash||b.username||b.password||
    b.port||b.search||b.hash||b.href!==config.introspectionEndpoint||
    a.href!==config.issuer||!b.hostname.includes(".")||
    !b.pathname.startsWith(a.pathname.replace(/\/$/,"")+"/")||
    [".test",".invalid",".local",".example",".localhost"].some(x=>b.hostname.endsWith(x)))deny();
 return b.href;
}
async function boundedJson(response){
 if(response?.status!==200||response.redirected===true||!response.body||
    !response.headers?.get("content-type")?.toLowerCase().startsWith("application/json")||
    !response.headers?.get("cache-control")?.toLowerCase().includes("no-store"))deny();
 const length=response.headers.get("content-length");
 if(length!==null&&(!/^\d{1,5}$/.test(length)||Number(length)>MAX_BODY))deny();
 const reader=response.body.getReader(),parts=[];let size=0;
 try{
  for(;;){
   const entry=await reader.read();
   if(entry.done)break;
   size+=entry.value.byteLength;
   if(size>MAX_BODY){await reader.cancel();deny();}
   parts.push(entry.value);
  }
 }catch{deny();}finally{reader.releaseLock();}
 const buffer=new Uint8Array(size);let offset=0;
 for(const part of parts){buffer.set(part,offset);offset+=part.byteLength;}
 try{
  const data=JSON.parse(new TextDecoder("utf-8",{fatal:true}).decode(buffer));
  if(!data||typeof data!=="object"||Array.isArray(data))deny();
  return data;
 }catch{deny();}
}
export async function verifyActiveOidcAccessToken(accessToken,verifiedIdentity,config,{
 fetcher=fetch,nowSeconds=Math.floor(Date.now()/1000)
}={}){
 if(!token.test(accessToken)||accessToken.length>16384||!verifiedIdentity||
    typeof verifiedIdentity!=="object"||typeof verifiedIdentity.subject!=="string"||
    verifiedIdentity.subject.length<2||typeof fetcher!=="function"||
    !Number.isSafeInteger(nowSeconds))deny();
 const endpoint=trustedEndpoint(config);
 const headers={
  Accept:"application/json",
  "Content-Type":"application/x-www-form-urlencoded",
  Authorization:"Basic "+Buffer.from(config.clientId+":"+config.clientSecret,"ascii").toString("base64")
 };
 const body=new URLSearchParams({token:accessToken,token_type_hint:"access_token"});
 const controller=new AbortController(),timeout=setTimeout(()=>controller.abort(),7000);
 try{
  const response=await fetcher(endpoint,{method:"POST",headers,body:body.toString(),
   redirect:"error",cache:"no-store",credentials:"omit",signal:controller.signal});
  const dto=await boundedJson(response);
  if(dto.active!==true||dto.sub!==verifiedIdentity.subject||
     dto.client_id!==config.clientId||!Number.isSafeInteger(dto.exp)||
     dto.exp<=nowSeconds||dto.exp>nowSeconds+3600||
     (dto.iss!==undefined&&dto.iss!==config.issuer)||
     (dto.token_type!==undefined&&dto.token_type!=="Bearer"))deny();
  const aud=typeof dto.aud==="string"?[dto.aud]:dto.aud;
  if(!Array.isArray(aud)||!aud.includes(config.apiAudience)||
     !aud.every(x=>typeof x==="string"&&x.length>0))deny();
  // Only a minimal authorization-independent confirmation is returned.
  // No access, refresh token, client secret, patient or tenant grant returned.
  return Object.freeze({active:true,subject:dto.sub,checkedAt:nowSeconds,expiresAt:dto.exp});
 }catch{deny();}
 finally{clearTimeout(timeout);}
}
