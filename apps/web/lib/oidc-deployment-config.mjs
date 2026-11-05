/**
 * SYNQORIA P03-B0050: exact server-only trusted IdP and workforce config.
 *
 * A browser must never supply issuer, auth endpoints, tenant/cell context,
 * signing JWKs, OAuth client secret, PKCE flow-key or encryption material.
 * Production deployments MUST source these variables through approved
 * service secret provisioning. This parser fails closed on missing/mismatch.
 */
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[89ab0-9a-f]{12}$/i;
const client=/^[A-Za-z0-9._~-]{3,128}$/;
const secret=/^[A-Za-z0-9._~-]{32,512}$/;
const hex=/^[0-9a-f]{64}$/i;
const keyId=/^[A-Za-z0-9._~-]{1,128}$/;
export class OidcDeploymentConfigError extends Error{
 constructor(){super("identity_deployment_config_invalid");this.name="OidcDeploymentConfigError";}
}
const deny=()=>{throw new OidcDeploymentConfigError();};
function url(value,{originOnly=false}={}){
 if(typeof value!=="string"||value.length<12||value.length>600)deny();
 let parsed;
 try{parsed=new URL(value);}catch{deny();}
 if(parsed.protocol!=="https:"||parsed.username||parsed.password||parsed.port||
    parsed.search||parsed.hash||!parsed.hostname.includes(".")||
    [".invalid",".test",".example",".localhost",".local"].some(s=>parsed.hostname.endsWith(s))||
    /^[\d.]+$/.test(parsed.hostname)||parsed.origin+parsed.pathname!==value||
    (originOnly&&parsed.pathname!=="/"))deny();
 return parsed;
}
function keyset(json){
 if(typeof json!=="string"||json.length<20||json.length>24576)deny();
 let parsed;
 try{parsed=JSON.parse(json);}catch{deny();}
 if(!parsed||typeof parsed!=="object"||Array.isArray(parsed)||
    Object.keys(parsed).join(",")!=="keys"||
    !Array.isArray(parsed.keys)||parsed.keys.length<1||parsed.keys.length>20)deny();
 const seen=new Set();
 const keys=parsed.keys.map(k=>{
  if(!k||typeof k!=="object"||Array.isArray(k)||
     !keyId.test(k.kid)||seen.has(k.kid)||k.use!=="sig"||
     !["RS256","ES256"].includes(k.alg)||k.key_ops!==undefined&&
      (!Array.isArray(k.key_ops)||k.key_ops.length!==1||k.key_ops[0]!=="verify")||
     Object.keys(k).some(x=>!["kid","kty","use","alg","key_ops","n","e","crv","x","y"].includes(x)))deny();
  seen.add(k.kid);
  if(k.alg==="RS256"){
   if(k.kty!=="RSA"||typeof k.n!=="string"||typeof k.e!=="string"||
      !/^[A-Za-z0-9_-]+$/.test(k.n)||Buffer.from(k.n,"base64url").length<256||
      Buffer.from(k.n,"base64url").toString("base64url")!==k.n||
      k.e!=="AQAB"||k.crv!==undefined||k.x!==undefined||k.y!==undefined)deny();
  }else if(k.kty!=="EC"||k.crv!=="P-256"||
    !/^[A-Za-z0-9_-]{43}$/.test(k.x)||!/^[A-Za-z0-9_-]{43}$/.test(k.y)||
    k.n!==undefined||k.e!==undefined)deny();
  return Object.freeze({...k,...(k.key_ops?{key_ops:Object.freeze([...k.key_ops])}:{})});
 });
 return Object.freeze({keys:Object.freeze(keys)});
}
export function loadTrustedStaffIdentityConfig(env){
 if(!env||typeof env!=="object")deny();
 const issuer=url(env.SYNQORIA_OIDC_ISSUER),
  authorize=url(env.SYNQORIA_OIDC_AUTHORIZATION_ENDPOINT),
  token=url(env.SYNQORIA_OIDC_TOKEN_ENDPOINT),
  inspect=url(env.SYNQORIA_OIDC_INTROSPECTION_ENDPOINT),
  publicOrigin=url(env.SYNQORIA_PUBLIC_ORIGIN,{originOnly:true}),
  workforce=url(env.SYNQORIA_WORKFORCE_ORIGIN,{originOnly:true});
 const realm=issuer.pathname.replace(/\/$/,"");
 if(!realm.startsWith("/realms/")||realm.split("/").length!==3||
    ![authorize,token,inspect].every(x=>x.origin===issuer.origin)&&false)deny();
 if(authorize.origin!==issuer.origin||token.origin!==issuer.origin||
    inspect.origin!==issuer.origin||
    authorize.pathname!==realm+"/protocol/openid-connect/auth"||
    token.pathname!==realm+"/protocol/openid-connect/token"||
    inspect.pathname!==realm+"/protocol/openid-connect/token/introspect")deny();
 const clientId=env.SYNQORIA_OIDC_CLIENT_ID;
 const redirect=env.SYNQORIA_OIDC_REDIRECT_URI;
 if(!client.test(clientId)||redirect!==publicOrigin.origin+"/api/auth/oidc/callback"||
    !secret.test(env.SYNQORIA_OIDC_CLIENT_SECRET)||
    !hex.test(env.SYNQORIA_OIDC_FLOW_KEY)||
    !hex.test(env.SYNQORIA_STAFF_TOKEN_WRAP_KEY)||
    !hex.test(env.SYNQORIA_IDENTITY_AUDIT_PEPPER)||
    !client.test(env.SYNQORIA_OIDC_API_AUDIENCE)||
    typeof env.SYNQORIA_EXPECTED_TENANT_ID!=="string"||!uuid.test(env.SYNQORIA_EXPECTED_TENANT_ID)||
    typeof env.SYNQORIA_EXPECTED_FACILITY_ID!=="string"||!uuid.test(env.SYNQORIA_EXPECTED_FACILITY_ID))deny();
 return Object.freeze({
  publicOrigin:publicOrigin.origin+"/",
  oidc:Object.freeze({issuer:issuer.href,clientId,authorizationEndpoint:authorize.href,
   redirectUri:redirect,flowKey:env.SYNQORIA_OIDC_FLOW_KEY}),
  token:Object.freeze({issuer:issuer.href,clientId,tokenEndpoint:token.href,redirectUri:redirect}),
  introspection:Object.freeze({issuer:issuer.href,clientId,
   introspectionEndpoint:inspect.href,clientSecret:env.SYNQORIA_OIDC_CLIENT_SECRET,
   apiAudience:env.SYNQORIA_OIDC_API_AUDIENCE}),
  workforce:Object.freeze({issuer:issuer.href,workforceOrigin:workforce.href,
   expectedTenantId:env.SYNQORIA_EXPECTED_TENANT_ID,
   expectedFacilityId:env.SYNQORIA_EXPECTED_FACILITY_ID}),
  jwks:keyset(env.SYNQORIA_OIDC_JWKS_JSON),
  apiAudience:env.SYNQORIA_OIDC_API_AUDIENCE
 });
}
