/**
 * SYNQORIA P03-B0070: prevent identity OIDC/roster request SSRF and redirects.
 * This adapter only forwards exact, server-approved identity POST endpoints.
 * Callers still must verify upstream body, signature, status and authorization.
 */
export class IdentityTransportError extends Error{
 constructor(){super("identity_transport_denied");this.name="IdentityTransportError";}
}
const deny=()=>{throw new IdentityTransportError();};
function absolute(url){
 if(typeof url!=="string"||url.length<15||url.length>600)deny();
 let parsed;
 try{parsed=new URL(url);}catch{deny();}
 if(parsed.protocol!=="https:"||parsed.username||parsed.password||parsed.port||
    parsed.hash||parsed.search||!parsed.hostname.includes(".")||
    [".invalid",".test",".local",".localhost",".example"].some(x=>parsed.hostname.endsWith(x))||
    parsed.href!==url)deny();
 return url;
}
export function createRestrictedIdentityTransport(config,{fetcher=fetch}={}){
 if(!config?.token||!config?.introspection||!config?.workforce||
    typeof fetcher!=="function")deny();
 const base=absolute(config.workforce.workforceOrigin);
 if(!base.endsWith("/"))deny();
 const allowed=new Set([
  absolute(config.token.tokenEndpoint),
  absolute(config.introspection.introspectionEndpoint),
  absolute(base+"internal/v1/staff/grants/active")
 ]);
 if(allowed.size!==3)deny();
 return async function restrictedIdentityFetch(url,options){
  if(!allowed.has(url)||!options||typeof options!=="object"||
     options.method!=="POST"||options.redirect!=="error"||
     options.credentials!=="omit"||options.cache!=="no-store"||
     !options.signal||typeof options.signal.aborted!=="boolean")deny();
  let headers;
  try{headers=new Headers(options.headers);}catch{deny();}
  if(headers.has("cookie")||headers.has("proxy-authorization")||
     headers.has("x-forwarded-host")||headers.has("x-forwarded-for")||
     headers.has("x-original-url")||headers.has("x-http-method-override"))deny();
  if(!["application/json","application/x-www-form-urlencoded"].some(
       x=>headers.get("content-type")?.toLowerCase().startsWith(x)))deny();
  if(typeof options.body!=="string"||options.body.length<1||options.body.length>32768)deny();
  try{
   // Do not follow cross-origin redirects or forward ambient browser cookies.
   return await fetcher(url,{...options,redirect:"error",credentials:"omit",cache:"no-store"});
  }catch{deny();}
 };
}
