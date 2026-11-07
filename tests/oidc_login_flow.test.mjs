import test from "node:test";
import assert from "node:assert/strict";
import {createHash,randomBytes} from "node:crypto";
import {OidcFlowError,OIDC_FLOW_COOKIE,beginOidcAuthorization,inspectOidcAuthorizationCallback,
 oidcFlowCookieOptions,clearOidcFlowCookieOptions} from "../apps/web/lib/oidc-login-flow.mjs";

const key=randomBytes(32).toString("hex"),now=1814980000000;
const config={
 issuer:"https://identity.synqoria.org/realms/hospital",
 authorizationEndpoint:"https://identity.synqoria.org/realms/hospital/protocol/openid-connect/auth",
 clientId:"synqoria-staff",redirectUri:"https://app.synqoria.org/api/auth/oidc/callback",
 flowKey:key
};
const begin=()=>beginOidcAuthorization(config,{now});
const callback=(start,state=null,extra="")=>{
 const a=new URL(start.authorizationUrl);
 return config.redirectUri+"?code=OpaqueAuthorizationCode0123456789&state="+encodeURIComponent(state??a.searchParams.get("state"))+extra;
};
const inspect=(start,url=callback(start),cfg=config,time=now+2000)=>
 inspectOidcAuthorizationCallback(url,start.cookieValue,cfg,{now:time});
const rejects=fn=>assert.throws(fn,OidcFlowError);

test("fixed HTTPS IdP redirect, cryptographic S256 challenge, encrypted scoped cookie",()=>{
 const start=begin(),url=new URL(start.authorizationUrl);
 assert.equal(url.origin,"https://identity.synqoria.org");
 assert.equal(url.searchParams.get("response_type"),"code");
 assert.equal(url.searchParams.get("client_id"),config.clientId);
 assert.equal(url.searchParams.get("redirect_uri"),config.redirectUri);
 assert.equal(url.searchParams.get("code_challenge_method"),"S256");
 assert.equal(url.searchParams.get("scope"),"openid");
 assert.match(url.searchParams.get("state"),/^[A-Za-z0-9_-]{32,128}$/);
 assert.match(url.searchParams.get("nonce"),/^[A-Za-z0-9_-]{32,128}$/);
 assert.ok(!url.toString().includes(key));
 assert.ok(!start.cookieValue.includes(url.searchParams.get("state")));
 assert.equal(OIDC_FLOW_COOKIE,"__Host-synqoria-oidc-flow");
 assert.deepEqual(oidcFlowCookieOptions(),{httpOnly:true,secure:true,sameSite:"lax",path:"/",maxAge:300,priority:"high"});
 assert.equal(clearOidcFlowCookieOptions().maxAge,0);
 assert.notEqual(begin().cookieValue,start.cookieValue);
});
test("callback returns only ephemeral proof and matches PKCE challenge",()=>{
 const start=begin(),url=new URL(start.authorizationUrl),proof=inspect(start);
 assert.equal(proof.code,"OpaqueAuthorizationCode0123456789");
 assert.equal(proof.nonce,url.searchParams.get("nonce"));
 assert.equal(proof.clientId,config.clientId);
 assert.equal(proof.issuer,config.issuer);
 assert.equal(Object.isFrozen(proof),true);
 assert.equal(createHash("sha256").update(proof.codeVerifier,"ascii").digest("base64url"),url.searchParams.get("code_challenge"));
 assert.equal("staffSession" in proof,false);
 assert.equal("tenantId" in proof,false);
 assert.equal("accessToken" in proof,false);
});
test("callback expiry and tampering fail closed, without session issuance",()=>{
 const start=begin();
 rejects(()=>inspect(start,callback(start),config,now+300000));
 rejects(()=>inspect(start,callback(start),config,now-1));
 const modified=start.cookieValue.slice(0,-1)+(start.cookieValue.endsWith("a")?"b":"a");
 rejects(()=>inspectOidcAuthorizationCallback(callback(start),modified,config,{now:now+1000}));
 rejects(()=>inspect(start,callback(start),{...config,flowKey:randomBytes(32).toString("hex")}));
 rejects(()=>inspectOidcAuthorizationCallback(callback(start),"",config,{now:now+1000}));
});
test("state binding denies concurrent session substitution and duplicate parameters",()=>{
 const a=begin(),b=begin(),stateOfB=new URL(b.authorizationUrl).searchParams.get("state");
 rejects(()=>inspect(a,callback(a,stateOfB)));
 rejects(()=>inspect(a,callback(a)+"&state=another"));
 rejects(()=>inspect(a,callback(a)+"&code=another"));
 rejects(()=>inspect(a,callback(a)+"&token=malicious"));
 rejects(()=>inspect(a,callback(a)+"#hash"));
 rejects(()=>inspect(a,callback(a).replace("code=OpaqueAuthorizationCode0123456789","error=access_denied")));
});
test("issuer hint, callback origin, fragment and open-redirect fields are strictly fenced",()=>{
 const a=begin();
 assert.equal(inspect(a,callback(a,null,"&iss="+encodeURIComponent(config.issuer))).code,"OpaqueAuthorizationCode0123456789");
 rejects(()=>inspect(a,callback(a,null,"&iss=https%3A%2F%2Fevil.example.org")));
 rejects(()=>inspect(a,callback(a,null,"&iss="+encodeURIComponent(config.issuer)+"&iss="+encodeURIComponent(config.issuer))));
 rejects(()=>inspect(a,callback(a).replace("https://app.synqoria.org","https://other.synqoria.org")));
 rejects(()=>inspect(a,callback(a).replace("/api/auth/oidc/callback","/admin/callback")));
 rejects(()=>inspect(a,callback(a).replace("code=OpaqueAuthorizationCode0123456789","code=%3Cscript%3E")));
});
test("invalid backend configuration and unsafe endpoints never create a redirect",()=>{
 for(const delta of [
  {issuer:"http://identity.synqoria.org/realms/hospital"},
  {issuer:"https://localhost/realms/hospital"},
  {authorizationEndpoint:"https://evil.synqoria.org/realms/hospital/protocol/openid-connect/auth"},
  {authorizationEndpoint:"https://identity.synqoria.org/realms/other/protocol/openid-connect/auth"},
  {redirectUri:"https://evil.synqoria.org/redirect"},
  {redirectUri:"http://app.synqoria.org/api/auth/oidc/callback"},
  {redirectUri:"https://app.synqoria.org:8443/api/auth/oidc/callback"},
  {clientId:""},
  {flowKey:"short"}
 ])rejects(()=>beginOidcAuthorization({...config,...delta},{now}));
});
test("nonstandard clocks and caller-supplied query options fail closed",()=>{
 rejects(()=>beginOidcAuthorization(config,{now:NaN}));
 rejects(()=>beginOidcAuthorization(config,{now:-1}));
 const a=begin();
 rejects(()=>inspect(a,callback(a),config,NaN));
 rejects(()=>inspect(a,callback(a)+"&redirect_uri=https://attacker.example"));
});
