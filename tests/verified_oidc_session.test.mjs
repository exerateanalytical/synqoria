import test from "node:test";
import assert from "node:assert/strict";
import {generateKeyPairSync,sign as rsaSign} from "node:crypto";
import {IdentityDenied,verifyOperationsJwt,fetchIssuerJwks} from "../apps/web/lib/verified-oidc-session.mjs";

const {privateKey,publicKey}=generateKeyPairSync("rsa",{modulusLength:2048});
const jwk=publicKey.export({format:"jwk"});
const jwks={keys:[{...jwk,kid:"current",use:"sig",alg:"RS256"}]};
const TIME=Date.parse("2026-11-07T12:00:00Z"), T=Math.floor(TIME/1000);
const CELL="00000000-0000-4000-8000-000000000011";
const FAC="00000000-0000-4000-8000-000000000022";
const USER="00000000-0000-4000-8000-000000000033";
const issuer="https://id.example.org/realms/synqoria",audience="synqoria-operations";
const options={issuer,audience,jwks,now:TIME,expectedCell:CELL,expectedFacility:FAC};
const claims={iss:issuer,aud:audience,sub:USER,synqoria_cell:CELL,synqoria_facility:FAC,exp:T+300,iat:T-30,nbf:T-30,synqoria_grant_expires:T+200,synqoria_permissions:["m38:sync_submit","m38:sync_read"]};
function jwt(payload=claims,header={alg:"RS256",typ:"JWT",kid:"current"}){
 const part=s=>Buffer.from(JSON.stringify(s)).toString("base64url");
 const input=part(header)+"."+part(payload);
 return input+"."+rsaSign("RSA-SHA256",Buffer.from(input),privateKey).toString("base64url");
}
function rejected(code,fn){assert.throws(fn,e=>e instanceof IdentityDenied&&(!code||e.message===code));}
test("valid signed scoped OIDC JWT returns bounded session only",()=>{
 const session=verifyOperationsJwt(jwt(),options);
 assert.equal(session.userId,USER);
 assert.equal(session.scope,CELL+"/"+FAC);
 assert.equal(session.expiresAt,(T+200)*1000);
 assert.deepEqual([...session.grants],["m38:sync_submit","m38:sync_read"]);
 assert.equal("token" in session,false);
});
test("tampered subject cannot be accepted",()=>{
 const token=jwt();const parts=token.split(".");
 parts[1]=Buffer.from(JSON.stringify({...claims,sub:"00000000-0000-4000-8000-000000000099"})).toString("base64url");
 rejected("signature_denied",()=>verifyOperationsJwt(parts.join("."),options));
});
test("wrong issuer, audience, hospital, facility or user denied",()=>{
 for(const bad of [{iss:"https://evil.example"},{aud:"elsewhere"},{synqoria_cell:USER},{synqoria_facility:USER},{sub:"not-a-uuid"}]){
  rejected(null,()=>verifyOperationsJwt(jwt({...claims,...bad}),options));
 }
 rejected(null,()=>verifyOperationsJwt(jwt(),{...options,expectedCell:USER}));
 rejected(null,()=>verifyOperationsJwt(jwt(),{...options,expectedFacility:USER}));
});
test("exp, iat, nbf and grant windows reject invalid tokens",()=>{
 for(const bad of [{exp:T-1},{iat:T+40},{nbf:T+40},{synqoria_grant_expires:T-1},{synqoria_grant_expires:T+901},{exp:T+1000}]){
  rejected(null,()=>verifyOperationsJwt(jwt({...claims,...bad}),options));
 }
});
test("invalid permissions and forged header metadata denied",()=>{
 for(const bad of [{synqoria_permissions:[]},{synqoria_permissions:["m38:sync_read","m38:sync_read"]},
  {synqoria_permissions:["m38:sync_read","foo"]}]){
  rejected(null,()=>verifyOperationsJwt(jwt({...claims,...bad}),options));
 }
 for(const header of [{alg:"none",kid:"current"},{alg:"HS256",kid:"current"},{alg:"RS256",kid:"current",jku:"https://evil.example"}]){
  rejected(null,()=>verifyOperationsJwt(jwt(claims,header),options));
 }
});
test("key mismatch, unknown kid and invalid JWKS fail closed",()=>{
 rejected("signing_key_unavailable",()=>verifyOperationsJwt(jwt(),{...options,jwks:{keys:[]}}));
 rejected("signing_key_unavailable",()=>verifyOperationsJwt(jwt(),{...options,jwks:{keys:[{...jwk,kid:"else"}]}}));
 rejected("jwks_unavailable",()=>verifyOperationsJwt(jwt(),{...options,jwks:{}}));
});
test("discovery fetch accepts same-origin JSON JWKS and never follows redirects",async()=>{
 const called=[];
 const fetchImpl=async(url,request)=>{
  called.push({url,request});
  const data=url.endsWith("openid-configuration")?{issuer,jwks_uri:"https://id.example.org/keys"}:jwks;
  return {ok:true,headers:{get:(k)=>k==="content-type"?"application/json":null},text:async()=>JSON.stringify(data)};
 };
 const value=await fetchIssuerJwks(issuer,{fetchImpl});
 assert.equal(value.keys[0].kid,"current");
 assert.equal(called.length,2);
 assert(called.every(x=>x.request.redirect==="error"));
});
test("cross-origin jwks, HTTP and mismatched discovery issuer refused",async()=>{
 for(const discovery of [{issuer,jwks_uri:"https://evil.example/keys"},
                            {issuer:"https://other.example",jwks_uri:"https://id.example.org/keys"},
                            {issuer,jwks_uri:"http://id.example.org/keys"}]){
  await assert.rejects(fetchIssuerJwks(issuer,{fetchImpl:async()=>({
   ok:true,headers:{get:()=> "application/json"},text:async()=>JSON.stringify(discovery)
  })}),IdentityDenied);
 }
});
