/**
 * SYNQORIA P03-B0078–B0081: live IdP preflight before any route binding.
 * Production OIDC discovery must match the deployment's exact issuer,
 * PKCE/S256, server-owned endpoints and pinned JWK public key material.
 * This does not authorize staff or substitute for token introspection.
 */
export class IdentityProviderPreflightError extends Error{
 constructor(){super("identity_provider_preflight_denied");this.name="IdentityProviderPreflightError";}
}
const deny=()=>{throw new IdentityProviderPreflightError();};
const MAX=32768;
const KID=/^[A-Za-z0-9._~-]{1,128}$/;
function endpoint(value){
 if(typeof value!=="string"||value.length>700)deny();
 let u;
 try{u=new URL(value);}catch{deny();}
 if(u.protocol!=="https:"||!u.hostname.includes(".")||u.port||
    u.username||u.password||u.hash||u.search||u.href!==value)deny();
 return u;
}
async function json(response){
 if(response?.status!==200||response.redirected===true||!response.body||
    !response.headers?.get("content-type")?.toLowerCase().startsWith("application/json"))deny();
 const length=response.headers.get("content-length");
 if(length!==null&&(!/^\d{1,5}$/.test(length)||Number(length)>MAX))deny();
 let reader,size=0,fragments=[];
 try{
  reader=response.body.getReader();
  for(;;){
   const chunk=await reader.read();
   if(chunk.done)break;
   if(!(chunk.value instanceof Uint8Array))deny();
   size+=chunk.value.byteLength;
   if(size>MAX){await reader.cancel();deny();}
   fragments.push(chunk.value);
  }
 }catch{deny();}
 finally{try{reader?.releaseLock();}catch{/* denied below */}}
 const data=new Uint8Array(size);let at=0;
 for(const fragment of fragments){data.set(fragment,at);at+=fragment.length;}
 try{
  const value=JSON.parse(new TextDecoder("utf-8",{fatal:true}).decode(data));
  if(!value||typeof value!=="object"||Array.isArray(value))deny();
  return value;
 }catch{deny();}
}
function exactPublicKey(remote,pinned){
 if(!remote||typeof remote!=="object"||Array.isArray(remote)||remote.kid!==pinned.kid||
    remote.kty!==pinned.kty||
    (remote.alg!==undefined&&remote.alg!==pinned.alg)||
    (remote.use!==undefined&&remote.use!=="sig"))deny();
 if(pinned.kty==="RSA"){
  if(remote.n!==pinned.n||remote.e!==pinned.e)deny();
 }else if(pinned.kty==="EC"){
  if(remote.crv!==pinned.crv||remote.x!==pinned.x||remote.y!==pinned.y)deny();
 }else deny();
}
export async function verifyTrustedIdentityProvider(config,{fetcher=fetch}={}){
 if(!config?.oidc||!config?.token||!config?.introspection||
    !Array.isArray(config?.jwks?.keys)||config.jwks.keys.length<1||
    typeof fetcher!=="function")deny();
 const issuer=endpoint(config.oidc.issuer);
 if(!/^\/realms\/[a-zA-Z0-9._~-]+\/?$/.test(issuer.pathname))deny();
 const realm=issuer.href.replace(/\/$/,"");
 const discovery=realm+"/.well-known/openid-configuration";
 const jwksUrl=realm+"/protocol/openid-connect/certs";
 const expectedAuth=endpoint(config.oidc.authorizationEndpoint).href,
  expectedToken=endpoint(config.token.tokenEndpoint).href,
  expectedIntrospect=endpoint(config.introspection.introspectionEndpoint).href;
 if([expectedAuth,expectedToken,expectedIntrospect].some(x=>new URL(x).origin!==issuer.origin))deny();
 const controller=new AbortController(),timeout=setTimeout(()=>controller.abort(),5000);
 async function get(url){
  const answer=await fetcher(url,{method:"GET",
   headers:{Accept:"application/json"},redirect:"error",cache:"no-store",
   credentials:"omit",signal:controller.signal});
  return json(answer);
 }
 try{
  const metadata=await get(discovery);
  if(metadata.issuer!==config.oidc.issuer||
     metadata.authorization_endpoint!==expectedAuth||
     metadata.token_endpoint!==expectedToken||
     metadata.introspection_endpoint!==expectedIntrospect||
     metadata.jwks_uri!==jwksUrl||
     !Array.isArray(metadata.response_types_supported)||
     !metadata.response_types_supported.includes("code")||
     !Array.isArray(metadata.code_challenge_methods_supported)||
     !metadata.code_challenge_methods_supported.includes("S256")||
     (metadata.grant_types_supported!==undefined&&
       (!Array.isArray(metadata.grant_types_supported)||
        !metadata.grant_types_supported.includes("authorization_code"))))deny();
  const keys=(await get(jwksUrl)).keys;
  if(!Array.isArray(keys)||keys.length<1||keys.length>30||
     keys.some(k=>!k||typeof k!=="object"||!KID.test(k.kid))||
     new Set(keys.map(k=>k.kid)).size!==keys.length)deny();
  for(const pinned of config.jwks.keys){
   const matched=keys.filter(k=>k.kid===pinned.kid);
   if(matched.length!==1)deny();
   exactPublicKey(matched[0],pinned);
  }
  return Object.freeze({verified:true,issuer:config.oidc.issuer,
   signingKeysVerified:config.jwks.keys.length,
   codeFlowVerified:true,pkceS256Verified:true});
 }catch{deny();}
 finally{clearTimeout(timeout);}
}
