import test from "node:test";
import assert from "node:assert/strict";
import {verifyActiveOidcAccessToken,OidcIntrospectionError} from "../apps/web/lib/oidc-token-introspection.mjs";
const issuer="https://identity.synqoria.org/realms/hospital",now=1814980000;
const config={issuer,introspectionEndpoint:issuer+"/protocol/openid-connect/token/introspect",
 clientId:"synqoria-staff",clientSecret:"vault-only-secret-DO-NOT-COMMIT-ACTUAL-ONE-0123456789",
 apiAudience:"synqoria-clinical-core"};
const signedToken="headerpayload.somejwtclaims.signature";
const verifiedIdentity=Object.freeze({subject:"verified-staff-subject",methods:["pwd","otp"]});
const valid={active:true,sub:verifiedIdentity.subject,client_id:config.clientId,exp:now+300,
 iss:issuer,aud:config.apiAudience,token_type:"Bearer"};
const response=(body=valid,{status=200,cache="no-store",type="application/json"}={})=>
 new Response(JSON.stringify(body),{status,headers:{"Content-Type":type,"Cache-Control":cache}});
const run=(fetcher,overrides={})=>verifyActiveOidcAccessToken(signedToken,verifiedIdentity,config,{fetcher,nowSeconds:now,...overrides});
const deny=f=>assert.rejects(f,OidcIntrospectionError);

test("verified signed-token subject requires active unrevoked IdP confirmation, without granting scope",async()=>{
 const calls=[];
 const r=await run(async(url,init)=>{calls.push({url,init});return response();});
 assert.deepEqual(r,{active:true,subject:verifiedIdentity.subject,checkedAt:now,expiresAt:now+300});
 assert.equal(Object.isFrozen(r),true);
 const {url,init}=calls[0];
 assert.equal(url,config.introspectionEndpoint);
 assert.equal(init.redirect,"error");
 assert.equal(init.cache,"no-store");
 assert.equal(init.credentials,"omit");
 assert.equal(init.method,"POST");
 assert.equal(new URLSearchParams(init.body).get("token"),signedToken);
 assert.equal(new URLSearchParams(init.body).get("token_type_hint"),"access_token");
 assert.match(init.headers.Authorization,/^Basic /);
 assert.ok(!url.includes(signedToken)&&!url.includes(config.clientSecret));
 assert.ok(!JSON.stringify(r).includes(config.clientSecret));
});
test("revoked, wrong subject, wrong audience, expired, forged issuer and untrusted client all deny",async()=>{
 for(const delta of [
 {active:false},{sub:"other-user"},{client_id:"attacker-client"},
 {aud:"nonclinical-api"},{exp:now-1},{exp:now+7200},{iss:"https://attacker.example"}
 ])await deny(()=>run(async()=>response({...valid,...delta})));
});
test("untrusted configuration and malformed tokens deny before network",async()=>{
 let called=0;const fetcher=async()=>{called++;return response();};
 for(const endpoint of [
 "http://identity.synqoria.org/realms/hospital/token",
 "https://attacker.example/token",
 "https://identity.synqoria.org/other/token",
 "https://identity.synqoria.org/realms/hospital/token?redirect=https://attacker.example"
 ])await deny(()=>verifyActiveOidcAccessToken(signedToken,verifiedIdentity,
 {...config,introspectionEndpoint:endpoint},{fetcher,nowSeconds:now}));
 await deny(()=>verifyActiveOidcAccessToken("not-a-jwt",verifiedIdentity,config,{fetcher,nowSeconds:now}));
 await deny(()=>verifyActiveOidcAccessToken(signedToken,verifiedIdentity,{...config,clientSecret:"x"},{fetcher,nowSeconds:now}));
 assert.equal(called,0);
});
test("IdP unavailable, oversized response, bad cache, bad content type and spoofed DTO deny",async()=>{
 await deny(()=>run(async()=>{throw Error("IdP offline")}));
 await deny(()=>run(async()=>response(valid,{status:503})));
 await deny(()=>run(async()=>response(valid,{cache:"public,max-age=3600"})));
 await deny(()=>run(async()=>response(valid,{type:"text/html"})));
 await deny(()=>run(async()=>response({...valid,active:1})));
 await deny(()=>run(async()=>new Response("x".repeat(17000),{status:200,headers:{"Content-Type":"application/json","Cache-Control":"no-store"}})));
});
