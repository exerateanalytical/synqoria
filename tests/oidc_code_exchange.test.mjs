import test from "node:test";
import assert from "node:assert/strict";
import {generateKeyPairSync,sign} from "node:crypto";
import {exchangeOneUseOidcCode,OidcTokenExchangeError} from "../apps/web/lib/oidc-code-exchange.mjs";

const {privateKey,publicKey}=generateKeyPairSync("rsa",{modulusLength:2048});
const now=1814980000,issuer="https://identity.synqoria.org/realms/hospital";
const config={issuer,clientId:"synqoria-staff",redirectUri:"https://staff.synqoria.org/api/auth/oidc/callback",
 tokenEndpoint:issuer+"/protocol/openid-connect/token"};
const proof={code:"OpaqueAuthorizationCode0123456789",codeVerifier:"v".repeat(64),
 clientId:config.clientId,redirectUri:config.redirectUri,issuer,nonce:"n".repeat(43)};
const apiAudience="synqoria-clinical-core",jwks={keys:[{...publicKey.export({format:"jwk"}),kid:"signing-one",alg:"RS256",use:"sig"}]};
const idClaims={iss:issuer,sub:"authorized-staff-directory-id",aud:config.clientId,
 iat:now-10,exp:now+300,auth_time:now-30,amr:["pwd","otp"],nonce:proof.nonce};
const accessClaims={iss:issuer,sub:idClaims.sub,aud:apiAudience,client_id:config.clientId,
 iat:now-10,exp:now+300,scope:"openid patient:view"};
function token(payload){
 const b64=x=>Buffer.from(JSON.stringify(x)).toString("base64url");
 const input=b64({alg:"RS256",kid:"signing-one",typ:"JWT"})+"."+b64(payload);
 return input+"."+sign("sha256",Buffer.from(input),privateKey).toString("base64url");
}
function data(id=idClaims,access=accessClaims){
 return {token_type:"Bearer",id_token:token(id),access_token:token(access),expires_in:300};
}
function response(dto=data(),{status=200,cache="no-store",type="application/json"}={}){
 return new Response(JSON.stringify(dto),{status,headers:{"Content-Type":type,"Cache-Control":cache}});
}
function run(fetcher,opts={}){return exchangeOneUseOidcCode(proof,config,{fetcher,jwks,apiAudience,nowSeconds:now,...opts});}
const deny=fn=>assert.rejects(fn,OidcTokenExchangeError);

test("real signed ID/access tokens accepted only after exact PKCE form exchange",async()=>{
 const calls=[];
 const result=await run(async(url,opts)=>{calls.push([url,opts]);return response();});
 assert.equal(result.identity.subject,idClaims.sub);
 assert.equal(result.identity.methods.includes("otp"),true);
 assert.equal(result.upstreamAccessToken,data().access_token,false);
 assert.equal("tenantId" in result,false);
 assert.equal("staffCookie" in result,false);
 assert.equal(calls.length,1);
 const [url,opts]=calls[0],params=new URLSearchParams(opts.body);
 assert.equal(url,config.tokenEndpoint);
 assert.equal(opts.method,"POST");
 assert.equal(opts.redirect,"error");
 assert.equal(opts.cache,"no-store");
 assert.equal(opts.credentials,"omit");
 assert.deepEqual([...params.keys()].sort(),["client_id","code","code_verifier","grant_type","redirect_uri"]);
 assert.equal(params.get("grant_type"),"authorization_code");
 assert.equal(params.get("code_verifier"),proof.codeVerifier);
 assert.equal(params.get("redirect_uri"),config.redirectUri);
 assert.equal("refresh_token" in result,false);
});
test("all untrusted IdP endpoints and injected proof values denied before outbound fetch",async()=>{
 let calls=0;const fetcher=async()=>{calls++;return response();};
 for(const tokenEndpoint of [
 "http://identity.synqoria.org/realms/hospital/protocol/openid-connect/token",
 "https://attacker.example.org/realms/hospital/protocol/openid-connect/token",
 "https://identity.synqoria.org/other/token",
 "https://identity.synqoria.org/realms/hospital/protocol/openid-connect/token?redirect=https://evil.example",
 "https://identity.synqoria.org:8443/realms/hospital/protocol/openid-connect/token"
 ])await deny(()=>exchangeOneUseOidcCode(proof,{...config,tokenEndpoint},{fetcher,jwks,apiAudience,nowSeconds:now}));
 for(const invalid of [
 {...proof,redirectUri:"https://attacker.example/callback"}, {...proof,nonce:"x"},
 {...proof,code:"bad code<script>"}, {...proof,authorization:"Bearer stolen"},
 {...proof,codeVerifier:"short"}
 ])await deny(()=>exchangeOneUseOidcCode(invalid,config,{fetcher,jwks,apiAudience,nowSeconds:now}));
 assert.equal(calls,0);
});
test("IdP HTTP failures, non-JSON, missing no-store, oversize, redirects and refresh grants denied",async()=>{
 await deny(()=>run(async()=>response(data(),{status:401})));
 await deny(()=>run(async()=>response(data(),{type:"text/html"})));
 await deny(()=>run(async()=>response(data(),{cache:"public,max-age=60"})));
 await deny(()=>run(async()=>response({...data(),refresh_token:"private-refresh-token"})));
 await deny(()=>run(async()=>response({...data(),expires_in:7200})));
 await deny(()=>run(async()=>new Response(JSON.stringify(data()),{status:200,headers:{"Content-Type":"application/json","Cache-Control":"no-store","Content-Length":"99999"}})));
 await deny(()=>run(async()=>{throw Error("IdP unreachable");}));
 await deny(()=>run(async()=>new Response("x".repeat(40000),{status:200,headers:{"Content-Type":"application/json","Cache-Control":"no-store"}})));
});
test("cryptographic errors, missing MFA, wrong nonce, wrong subject and scope fail closed",async()=>{
 await deny(()=>run(async()=>response(data({...idClaims,nonce:"x".repeat(43)}))));
 await deny(()=>run(async()=>response(data({...idClaims,amr:["pwd"]}))));
 await deny(()=>run(async()=>response(data(idClaims,{...accessClaims,sub:"other-subject"}))));
 await deny(()=>run(async()=>response(data({...idClaims,exp:now-1}))));
 await deny(()=>run(async()=>response(data()),{jwks:{keys:[]}}));
 await deny(()=>run(async()=>response(data(idClaims,{...accessClaims,scope:"patient:view patient:view"}))));
});
