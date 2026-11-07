import test from "node:test";
import assert from "node:assert/strict";
import {randomBytes,generateKeyPairSync,sign} from "node:crypto";
import {beginOneUseOidcAuthorization} from "../apps/web/lib/oidc-one-use.mjs";
import {finishStaffOidcLogin,StaffLoginPipelineError} from "../apps/web/lib/oidc-session-orchestrator.mjs";

const nowMs=Date.parse("2026-11-05T21:50:00Z"),nowSec=Math.floor(nowMs/1000);
const issuer="https://auth.synqoria.org/realms/hospital",clientId="synqoria-staff",
 audience="synqoria-clinical-core",subject="staff-identity-001";
const actor="11111111-1111-4111-8111-111111111111",tenant="22222222-2222-4222-8222-222222222222",
 facility="33333333-3333-4333-8333-333333333333",shift="44444444-4444-4444-8444-444444444444";
const {privateKey,publicKey}=generateKeyPairSync("rsa",{modulusLength:2048});
const jwks={keys:[{...publicKey.export({format:"jwk"}),kid:"synqoria-oidc-signing",alg:"RS256",use:"sig"}]};
const config={
 oidc:{issuer,authorizationEndpoint:issuer+"/protocol/openid-connect/auth",
  clientId,redirectUri:"https://staff.synqoria.org/api/auth/oidc/callback",
  flowKey:randomBytes(32).toString("hex")},
 token:{issuer,tokenEndpoint:issuer+"/protocol/openid-connect/token",clientId,
  redirectUri:"https://staff.synqoria.org/api/auth/oidc/callback"},
 introspection:{issuer,clientId,clientSecret:"Mock-Vault-Secret-"+ "x".repeat(40),
  apiAudience:audience,introspectionEndpoint:issuer+"/protocol/openid-connect/token/introspect"},
 workforce:{issuer,workforceOrigin:"https://workforce.synqoria-clinical.org/",
  expectedTenantId:tenant,expectedFacilityId:facility},jwks,apiAudience:audience
};
const baseGrant={actorId:actor,tenantId:tenant,facilityId:facility,shiftId:shift,
 role:"nurse",status:"ACTIVE",issuer,subject,revision:17,
 shiftStartsAt:"2026-11-05T18:00:00Z",shiftEndsAt:"2026-11-06T02:00:00Z",
 capabilities:["facility:view","patient:view"]};
const json=(x)=>new Response(JSON.stringify(x),{status:200,headers:{"Content-Type":"application/json","Cache-Control":"no-store"}});
const jwt=claims=>{
 const b64=x=>Buffer.from(JSON.stringify(x)).toString("base64url");
 const data=b64({alg:"RS256",kid:"synqoria-oidc-signing",typ:"JWT"})+"."+b64(claims);
 return data+"."+sign("sha256",Buffer.from(data),privateKey).toString("base64url");
};
function newStore(){
 const rows=new Map(),events=[];
 return {events,
  reserve:async(k,exp)=>{events.push("reserve");if(rows.has(k))return false;rows.set(k,exp);return true;},
  consume:async(k,at)=>{events.push("consume");const exp=rows.get(k);if(exp===undefined||at>=exp)return false;rows.delete(k);return true;}
 };
}
async function setup(){
 const store=newStore(),begun=await beginOneUseOidcAuthorization(config.oidc,store,{now:nowMs});
 const state=new URL(begun.authorizationUrl).searchParams.get("state");
 const nonce=new URL(begun.authorizationUrl).searchParams.get("nonce");
 const callback=config.oidc.redirectUri+"?code=OpaqueAuthCode123456789012345&state="+encodeURIComponent(state)+"&iss="+encodeURIComponent(issuer);
 return {store,begun,callback,nonce};
}
function simulation(nonce,{introspection={},grant={},idClaims={},accessClaims={},onFetch=()=>{}}={}){
 const signedId=jwt({iss:issuer,sub:subject,aud:clientId,iat:nowSec-10,exp:nowSec+300,
  auth_time:nowSec-20,amr:["pwd","otp"],nonce,...idClaims});
 const signedAccess=jwt({iss:issuer,sub:subject,aud:audience,client_id:clientId,iat:nowSec-10,
  exp:nowSec+300,scope:"openid facility:view patient:view",...accessClaims});
 const endpoints=[];
 const fetcher=async(url,init)=>{
  endpoints.push(url);onFetch(url,init);
  if(url===config.token.tokenEndpoint)return json({token_type:"Bearer",id_token:signedId,
   access_token:signedAccess,expires_in:300});
  if(url===config.introspection.introspectionEndpoint)return json({
   active:true,sub:subject,client_id:clientId,exp:nowSec+300,iss:issuer,aud:audience,
   token_type:"Bearer",...introspection
  });
  if(url==="https://workforce.synqoria-clinical.org/internal/v1/staff/grants/active")
   return json({...baseGrant,...grant});
  throw Error("unexpected upstream URL");
 };
 return {fetcher,endpoints,signedAccess};
}
const reject=async f=>assert.rejects(f,StaffLoginPipelineError);

test("P03-B0035 login pipeline consumes state then checks crypto, revocation, roster and issues opaque server reference",async()=>{
 const p=await setup(),s=simulation(p.nonce),issued=[],ref="B".repeat(48);
 const result=await finishStaffOidcLogin(p.callback,p.begun.cookieValue,config,{
  replayStore:p.store,fetcher:s.fetcher,nowMs,
  issueServerSession:async record=>{issued.push(record);return ref;}
 });
 assert.deepEqual(result,{sessionReference:ref,expiresAt:(nowSec+300)*1000});
 assert.deepEqual(p.store.events,["reserve","consume"]);
 assert.deepEqual(s.endpoints,[config.token.tokenEndpoint,config.introspection.introspectionEndpoint,
  "https://workforce.synqoria-clinical.org/internal/v1/staff/grants/active"]);
 assert.equal(issued.length,1);
 assert.equal(issued[0].bearer,s.signedAccess);
 assert.equal(issued[0].tenantId,tenant);assert.equal(issued[0].facilityId,facility);
 assert.deepEqual(issued[0].capabilities,["facility:view","patient:view"]);
 assert.equal(issued[0].issuedAt,nowMs);
 assert.ok(!JSON.stringify(result).includes(s.signedAccess));
 assert.equal("patientId" in result,false);
 await reject(()=>finishStaffOidcLogin(p.callback,p.begun.cookieValue,config,{
  replayStore:p.store,fetcher:s.fetcher,nowMs,issueServerSession:async()=>ref
 }));
 assert.equal(s.endpoints.length,3,"replay must not reach IdP");
});
test("P03-B0035 revoked access token denies before workforce lookup or writer",async()=>{
 const p=await setup(),s=simulation(p.nonce,{introspection:{active:false}});
 let wrote=0;
 await reject(()=>finishStaffOidcLogin(p.callback,p.begun.cookieValue,config,{
  replayStore:p.store,fetcher:s.fetcher,nowMs,issueServerSession:async()=>{wrote++;return "B".repeat(48);}
 }));
 assert.equal(wrote,0);assert.equal(s.endpoints.length,2);
});
test("P03-B0035 role escalation, wrong facility and expired shift deny before writer",async()=>{
 for(const grant of [{capabilities:["admin:all"]},{facilityId:actor},
   {shiftEndsAt:"2026-11-05T21:40:00Z"},{status:"SUSPENDED"}]){
  const p=await setup(),s=simulation(p.nonce,{grant});let writes=0;
  await reject(()=>finishStaffOidcLogin(p.callback,p.begun.cookieValue,config,{
   replayStore:p.store,fetcher:s.fetcher,nowMs,issueServerSession:async()=>{writes++;return "B".repeat(48);}
  }));
  assert.equal(writes,0);
 }
});
test("P03-B0035 invalid MFA or forged signed nonce denies before revocation and grants",async()=>{
 for(const idClaims of [{amr:["pwd"]},{nonce:"tampered-nonce0123456789"}]){
  const p=await setup(),s=simulation(p.nonce,{idClaims}),seen=[];
  await reject(()=>finishStaffOidcLogin(p.callback,p.begun.cookieValue,config,{
   replayStore:p.store,fetcher:async(url,init)=>{seen.push(url);return s.fetcher(url,init);},
   nowMs,issueServerSession:async()=>{throw Error("should never reach writer");}
  }));
  assert.deepEqual(seen,[config.token.tokenEndpoint]);
 }
});
test("P03-B0035 invalid callback or inconsistent configuration denied before token exchange",async()=>{
 const p=await setup(),s=simulation(p.nonce);
 await reject(()=>finishStaffOidcLogin(p.callback.replace("code=","forged="),
  p.begun.cookieValue,config,{replayStore:p.store,fetcher:s.fetcher,nowMs,
   issueServerSession:async()=>{throw Error("not called");}}));
 assert.equal(s.endpoints.length,0);
 const q=await setup();
 await reject(()=>finishStaffOidcLogin(q.callback,q.begun.cookieValue,
  {...config,introspection:{...config.introspection,issuer:"https://attacker.example"}},{
   replayStore:q.store,fetcher:s.fetcher,nowMs,issueServerSession:async()=>{throw Error("not called");}
  }));
 assert.equal(q.store.events.length,1,"bad server configuration must not consume state");
});
test("P03-B0035 invalid session writer, failed persistence or token disclosure deny login",async()=>{
 for(const issue of [async()=>{throw Error("persistence down");},
   async()=>"invalid",async record=>record.bearer]){
  const p=await setup(),s=simulation(p.nonce);
  await reject(()=>finishStaffOidcLogin(p.callback,p.begun.cookieValue,config,{
   replayStore:p.store,fetcher:s.fetcher,nowMs,issueServerSession:issue
  }));
  assert.equal(p.store.events.at(-1),"consume");
 }
});
