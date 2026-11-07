/**
 * SYNQORIA P03-B0070 and B0082–B0085: exact confidential IdP transport.
 * Do not forward client-supplied HTTP methods, credentials, tokens or URLs.
 * Token-exchange, introspection and workforce credentials are not interchangeable.
 */
import {verifyBoundedIdentityResponse} from "./identity-bounded-transport-response.mjs";
export class IdentityTransportError extends Error{
 constructor(){super("identity_transport_denied");this.name="IdentityTransportError";}
}
const deny=()=>{throw new IdentityTransportError();};
const JWT=/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/;
const CODE=/^[A-Za-z0-9._~-]{8,2048}$/;
const VERIFIER=/^[A-Za-z0-9_-]{32,128}$/;
const SUB=/^[A-Za-z0-9@._:|~-]{2,255}$/;
function absolute(value){
 if(typeof value!=="string"||value.length<15||value.length>700)deny();
 let parsed;
 try{parsed=new URL(value);}catch{deny();}
 if(parsed.protocol!=="https:"||parsed.username||parsed.password||parsed.port||
    parsed.hash||parsed.search||!parsed.hostname.includes(".")||
    [".invalid",".test",".local",".localhost",".example"].some(x=>parsed.hostname.endsWith(x))||
    parsed.href!==value)deny();
 return value;
}
function exactForm(body,names){
 let values;
 try{values=new URLSearchParams(body);}catch{deny();}
 if([...values.keys()].sort().join(",")!==[...names].sort().join(","))deny();
 return values;
}
function exactHeaders(options){
 let headers;
 try{headers=new Headers(options.headers);}catch{deny();}
 if([...headers.keys()].some(k=>!["accept","content-type","authorization"].includes(k))||
    headers.get("accept")!=="application/json")deny();
 return headers;
}
export function createRestrictedIdentityTransport(config,{fetcher=fetch}={}){
 if(!config?.oidc||!config?.token||!config?.introspection||
    !config?.workforce||typeof fetcher!=="function")deny();
 const base=absolute(config.workforce.workforceOrigin);
 if(!base.endsWith("/")||config.workforce.issuer!==config.oidc.issuer)deny();
 const urls=Object.freeze({
  exchange:absolute(config.token.tokenEndpoint),
  introspect:absolute(config.introspection.introspectionEndpoint),
  workforce:absolute(base+"internal/v1/staff/grants/active")
 });
 if(new Set(Object.values(urls)).size!==3)deny();
 if(typeof config.token.clientId!=="string"||typeof config.token.redirectUri!=="string"||
    typeof config.introspection.clientId!=="string"||
    typeof config.introspection.clientSecret!=="string"||
    config.token.clientId!==config.introspection.clientId||
    config.token.clientId!==config.oidc.clientId)deny();
 const basic="Basic "+Buffer.from(
  config.introspection.clientId+":"+config.introspection.clientSecret,"ascii"
 ).toString("base64");
 return async function restrictedIdentityFetch(url,options){
  if(!Object.values(urls).includes(url)||!options||typeof options!=="object"||
     options.method!=="POST"||options.redirect!=="error"||
     options.credentials!=="omit"||options.cache!=="no-store"||
     !options.signal||typeof options.signal.aborted!=="boolean"||options.signal.aborted||
     typeof options.body!=="string"||options.body.length<1||
     options.body.length>32768)deny();
  const headers=exactHeaders(options);
  if(url===urls.exchange){
   if(headers.get("content-type")!=="application/x-www-form-urlencoded"||
      headers.has("authorization"))deny();
   const p=exactForm(options.body,["grant_type","client_id","code","redirect_uri","code_verifier"]);
   if(p.get("grant_type")!=="authorization_code"||
      p.get("client_id")!==config.token.clientId||
      p.get("redirect_uri")!==config.token.redirectUri||
      !CODE.test(p.get("code"))||!VERIFIER.test(p.get("code_verifier")))deny();
  }else if(url===urls.introspect){
   if(headers.get("content-type")!=="application/x-www-form-urlencoded"||
      headers.get("authorization")!==basic)deny();
   const p=exactForm(options.body,["token","token_type_hint"]);
   if(p.get("token_type_hint")!=="access_token"||
      typeof p.get("token")!=="string"||p.get("token").length>16384||
      !JWT.test(p.get("token")))deny();
  }else if(url===urls.workforce){
   if(headers.get("content-type")!=="application/json"||
      !headers.get("authorization")?.startsWith("Bearer ")||
      !JWT.test(headers.get("authorization").slice(7))||
      headers.get("authorization").length>16391)deny();
   let body;
   try{body=JSON.parse(options.body);}catch{deny();}
   if(!body||typeof body!=="object"||Array.isArray(body)||
      Object.keys(body).sort().join(",")!=="issuer,subject"||
      body.issuer!==config.workforce.issuer||!SUB.test(body.subject))deny();
  }else deny();
  try{
   const response=await fetcher(url,{...options,redirect:"error",credentials:"omit",cache:"no-store"});
   if(response?.redirected===true||response?.status!==200||
      (typeof response.url==="string"&&response.url!==""&&response.url!==url)||
      !response?.headers?.get("content-type")?.toLowerCase().startsWith("application/json")||
      !response?.headers?.get("cache-control")?.toLowerCase().includes("no-store"))deny();
   return await verifyBoundedIdentityResponse(response,{expectedUrl:url,
    maxBytes:url===urls.exchange?32768:16384,signal:options.signal});
  }catch{deny();}
 };
}
