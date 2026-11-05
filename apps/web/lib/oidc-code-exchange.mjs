/**
 * SYNQORIA P03 — server-only OIDC authorization-code token exchange.
 *
 * Accepts only the ephemeral validated, ONE-USE proof from oidc-one-use.
 * Uses a configuration-owned HTTPS IdP token endpoint and PKCE S256.
 * Does NOT issue a staff cookie or convert IdP authentication into facility/
 * patient/clinical authorization. Revocation, roster, tenant-cell privileges,
 * active shift and consent require separate authoritative checks.
 */
import {verifyStaffOidcTokens} from "./oidc-staff-verification.mjs";

export class OidcTokenExchangeError extends Error{
 constructor(){super("oidc_token_exchange_denied");this.name="OidcTokenExchangeError";}
}
const deny=()=>{throw new OidcTokenExchangeError();};
const CODE=/^[A-Za-z0-9._~-]{8,2048}$/;
const VERIFIER=/^[A-Za-z0-9_-]{32,128}$/;
const JWT=/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/;
const MAX_RESPONSE=32768;
const exactProofKeys="clientId,code,codeVerifier,issuer,nonce,redirectUri";
function pinnedEndpoint(issuer,tokenEndpoint){
 if(typeof issuer!=="string"||typeof tokenEndpoint!=="string"||issuer.length>512||tokenEndpoint.length>700)deny();
 let url,base;
 try{url=new URL(tokenEndpoint);base=new URL(issuer);}catch{deny();}
 if(url.protocol!=="https:"||base.protocol!=="https:"||
    url.username||url.password||url.port||url.search||url.hash||
    base.username||base.password||base.port||base.search||base.hash||
    !url.hostname.includes(".")||url.origin!==base.origin||
    !url.pathname.startsWith(base.pathname.replace(/\/$/,"")+"/")||
    url.pathname===base.pathname||url.href!==tokenEndpoint||base.href!==issuer||
    ["localhost","127.0.0.1"].includes(url.hostname)||
    [".local",".invalid",".example",".test"].some(s=>url.hostname.endsWith(s)))deny();
 return url.toString();
}
function validateProof(proof,config){
 if(!proof||typeof proof!=="object"||Array.isArray(proof)||
    Object.keys(proof).sort().join(",")!==exactProofKeys||
    proof.issuer!==config.issuer||proof.clientId!==config.clientId||
    proof.redirectUri!==config.redirectUri||
    !CODE.test(proof.code)||!VERIFIER.test(proof.codeVerifier)||
    !VERIFIER.test(proof.nonce))deny();
}
async function parseBoundedResponse(response){
 if(response?.status!==200||response.redirected===true||!response.body||
    !response.headers?.get("content-type")?.toLowerCase().startsWith("application/json")||
    !response.headers?.get("cache-control")?.toLowerCase().includes("no-store"))deny();
 const contentLength=response.headers.get("content-length");
 if(contentLength!==null&&(!/^\d{1,5}$/.test(contentLength)||Number(contentLength)>MAX_RESPONSE))deny();
 const reader=response.body.getReader();
 const buffers=[];let total=0;
 try{
  for(;;){
   const part=await reader.read();
   if(part.done)break;
   total+=part.value.byteLength;
   if(total>MAX_RESPONSE){await reader.cancel();deny();}
   buffers.push(part.value);
  }
 }catch{deny();}finally{reader.releaseLock();}
 const out=new Uint8Array(total);let offset=0;
 for(const buf of buffers){out.set(buf,offset);offset+=buf.byteLength;}
 let dto;
 try{dto=JSON.parse(new TextDecoder("utf-8",{fatal:true}).decode(out));}catch{deny();}
 if(!dto||typeof dto!=="object"||Array.isArray(dto))deny();
 const allowed=new Set(["id_token","access_token","token_type","expires_in","scope","session_state"]);
 if(Object.keys(dto).some(k=>!allowed.has(k))||dto.token_type!=="Bearer"||
    typeof dto.id_token!=="string"||dto.id_token.length>16384||!JWT.test(dto.id_token)||
    typeof dto.access_token!=="string"||dto.access_token.length>16384||!JWT.test(dto.access_token)||
    !Number.isSafeInteger(dto.expires_in)||dto.expires_in<1||dto.expires_in>3600||
    (dto.scope!==undefined&&(typeof dto.scope!=="string"||dto.scope.length>2048))||
    (dto.session_state!==undefined&&(typeof dto.session_state!=="string"||dto.session_state.length>256)))deny();
 return dto;
}
export async function exchangeOneUseOidcCode(proof,config,{
 fetcher=fetch,jwks,apiAudience,nowSeconds=Math.floor(Date.now()/1000)
}={}){
 if(!config||typeof config!=="object"||typeof fetcher!=="function"||
    typeof apiAudience!=="string"||!apiAudience||
    !Number.isSafeInteger(nowSeconds))deny();
 const endpoint=pinnedEndpoint(config.issuer,config.tokenEndpoint);
 validateProof(proof,config);
 const body=new URLSearchParams({
  grant_type:"authorization_code",client_id:config.clientId,
  code:proof.code,redirect_uri:proof.redirectUri,code_verifier:proof.codeVerifier
 });
 const controller=new AbortController();
 const timeout=setTimeout(()=>controller.abort(),7000);
 let response;
 try{
  response=await fetcher(endpoint,{method:"POST",
   headers:{"Accept":"application/json","Content-Type":"application/x-www-form-urlencoded"},
   body:body.toString(),redirect:"error",cache:"no-store",credentials:"omit",signal:controller.signal});
  const dto=await parseBoundedResponse(response);
  const verified=verifyStaffOidcTokens({idToken:dto.id_token,accessToken:dto.access_token,
   jwks,issuer:config.issuer,clientId:config.clientId,apiAudience,nonce:proof.nonce,nowSeconds});
  // SERVER ONLY. Subsequent IdP revocation/introspection and workforce permissions
  // must succeed before any staff cookie can be sealed. Caller must not serialize.
  return Object.freeze({identity:verified,upstreamAccessToken:dto.access_token});
 }catch{deny();}
 finally{clearTimeout(timeout);}
}
