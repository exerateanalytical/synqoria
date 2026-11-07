import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {generateKeyPairSync,sign} from "node:crypto";
import {verifiedOperationsSession} from "../apps/web/lib/operations-verified-session.mjs";

const {publicKey,privateKey}=generateKeyPairSync("rsa",{modulusLength:2048});
const jwk=publicKey.export({format:"jwk"});
const jwks={keys:[{...jwk,kid:"production-key",use:"sig",alg:"RS256"}]};
const issuer="https://id.example.org/realms/synqoria";
const audience="synqoria-offline-api";
const now=Date.parse("2026-11-07T12:00:00Z");
const epoch=Math.floor(now/1000);
const user="00000000-0000-4000-8000-000000000001",cell="00000000-0000-4000-8000-000000000002";
const facility="00000000-0000-4000-8000-000000000003";
const claims={
 iss:issuer,aud:audience,sub:user,
 synqoria_cell:cell,synqoria_facility:facility,
 synqoria_permissions:["m38:sync_read","m38:sync_submit"],
 synqoria_grant_expires:epoch+250,
 iat:epoch-30,nbf:epoch-30,exp:epoch+300,
};
function token(payload=claims){
 const part=x=>Buffer.from(JSON.stringify(x)).toString("base64url");
 const head=part({alg:"RS256",kid:"production-key",typ:"JWT"});
 const unsigned=head+"."+part(payload);
 return unsigned+"."+sign("RSA-SHA256",Buffer.from(unsigned),privateKey).toString("base64url");
}
function provider({badIssuer=false,badKey=false}={}){
 const calls=[];
 const fetchImpl=async(url,options)=>{
  calls.push({url,options});
  const value=url.endsWith("openid-configuration")?
   {issuer:badIssuer?"https://fake.example":issuer,jwks_uri:"https://id.example.org/keys"}:
   badKey?{keys:[]}:jwks;
  return {ok:true,headers:{get:k=>k==="content-type"?"application/json":null},
   text:async()=>JSON.stringify(value)};
 };
 return {fetchImpl,calls};
}
function opts(extra={}){
 const p=provider();
 return {issuer,audience,now,requiredPermission:"m38:sync_read",fetchImpl:p.fetchImpl,...extra};
}
test("signed token returns exact minimal verified binding",async()=>{
 const session=await verifiedOperationsSession(token(),opts());
 assert.deepEqual(session,{userId:user,scope:cell+"/"+facility,expiresAt:(epoch+250)*1000});
 assert.equal("token" in session,false);
 assert.equal("grants" in session,false);
});
test("missing invalid and malformed token rejects before network access",async()=>{
 const p=provider();
 for(const input of [null,undefined,"", "not.a.jwt","a".repeat(17000)]){
  await assert.rejects(verifiedOperationsSession(input,{issuer,audience,fetchImpl:p.fetchImpl}),
   {name:"IdentityDenied"});
 }
 assert.equal(p.calls.length,0);
});
test("tampered identity or hospital claims reject despite valid original signature",async()=>{
 const signed=token().split(".");
 const part=JSON.parse(Buffer.from(signed[1],"base64url").toString());
 signed[1]=Buffer.from(JSON.stringify({...part,synqoria_cell:user})).toString("base64url");
 await assert.rejects(verifiedOperationsSession(signed.join("."),opts()),{name:"IdentityDenied"});
});
test("invalid issuer, audience and permission reject",async()=>{
 await assert.rejects(verifiedOperationsSession(token({...claims,aud:"another-service"}),opts()),
  {name:"IdentityDenied"});
 await assert.rejects(verifiedOperationsSession(token({...claims,synqoria_permissions:["m38:sync_submit"]}),opts()),
  {message:"permission_denied"});
 await assert.rejects(verifiedOperationsSession(token(),opts({audience:""})),{name:"IdentityDenied"});
});
test("expired grant, revoked signing key and IdP discovery drift fail closed",async()=>{
 await assert.rejects(verifiedOperationsSession(token({...claims,synqoria_grant_expires:epoch-1}),opts()),
  {name:"IdentityDenied"});
 await assert.rejects(verifiedOperationsSession(token(),opts({fetchImpl:provider({badKey:true}).fetchImpl})),
  {name:"IdentityDenied"});
 await assert.rejects(verifiedOperationsSession(token(),opts({fetchImpl:provider({badIssuer:true}).fetchImpl})),
  {name:"IdentityDenied"});
});
test("network issuer outage denies and never downgrades to unsigned claims",async()=>{
 await assert.rejects(verifiedOperationsSession(token(),opts({fetchImpl:async()=>{throw Error("offline");}})),
  {name:"IdentityDenied"});
});
test("Next route binds to HttpOnly operations cookie without returning claims from unsigned payload",()=>{
 const source=readFileSync(new URL("../apps/web/app/api/operations/auth/verified-session/route.ts",import.meta.url),"utf8");
 assert.match(source,/OPS_COOKIE\.token/);
 assert.match(source,/verifiedOperationsSession\(/);
 assert.match(source,/SYNQORIA_OPS_OIDC_AUDIENCE/);
 assert.match(source,/requiredPermission:"m38:sync_read"/);
 assert.match(source,/no-store/);
 assert.doesNotMatch(source,/Buffer\.from\(|\.split\("\."\)|localStorage|sessionStorage/);
});
