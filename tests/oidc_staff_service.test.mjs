import test from "node:test";
import assert from "node:assert/strict";
import {randomBytes,generateKeyPairSync,sign} from "node:crypto";
import {createStaffSessionService,StaffSessionServiceError} from "../apps/web/lib/oidc-staff-service.mjs";
import {createPostgresStaffSessionStore,SESSION_INSERT_SQL,SESSION_READ_SQL,
 SESSION_REVOKE_SQL} from "../apps/web/lib/postgres-staff-session-store.mjs";
import {STAFF_TICKET_COOKIE} from "../apps/web/lib/opaque-staff-session.mjs";
import {OIDC_FLOW_COOKIE} from "../apps/web/lib/oidc-login-flow.mjs";
const nowMs=Date.parse("2026-11-05T21:50:00Z"),nowSec=Math.floor(nowMs/1000);
const issuer="https://auth.synqoria.org/realms/hospital",clientId="synqoria-staff",
 audience="synqoria-clinical-core",publicOrigin="https://staff.synqoria.org/";
const actor="11111111-1111-4111-8111-111111111111",tenant="22222222-2222-4222-8222-222222222222",
 facility="33333333-3333-4333-8333-333333333333",shift="44444444-4444-4444-8444-444444444444";
const {privateKey,publicKey}=generateKeyPairSync("rsa",{modulusLength:2048});
const config={
 oidc:{issuer,clientId,authorizationEndpoint:issuer+"/protocol/openid-connect/auth",
  redirectUri:"https://staff.synqoria.org/api/auth/oidc/callback",flowKey:randomBytes(32).toString("hex")},
 token:{issuer,clientId,tokenEndpoint:issuer+"/protocol/openid-connect/token",
  redirectUri:"https://staff.synqoria.org/api/auth/oidc/callback"},
 introspection:{issuer,clientId,clientSecret:"NotRealVaultSecret-"+"x".repeat(40),
  introspectionEndpoint:issuer+"/protocol/openid-connect/token/introspect",apiAudience:audience},
 workforce:{issuer,workforceOrigin:"https://workforce.synqoria-clinical.org/",
  expectedTenantId:tenant,expectedFacilityId:facility},
 jwks:{keys:[{...publicKey.export({format:"jwk"}),kid:"signing-key",alg:"RS256",use:"sig"}]},
 apiAudience:audience
};
const jwt=data=>{
 const b64=x=>Buffer.from(JSON.stringify(x)).toString("base64url");
 const text=b64({alg:"RS256",kid:"signing-key",typ:"JWT"})+"."+b64(data);
 return text+"."+sign("sha256",Buffer.from(text),privateKey).toString("base64url");
};
const json=x=>new Response(JSON.stringify(x),{status:200,headers:{"Content-Type":"application/json","Cache-Control":"no-store"}});
function createHarness({active=true,facilityId=facility}={}){
 const map=new Map(),replays=new Map(),calls=[],tokenKey={SYNQORIA_STAFF_TOKEN_WRAP_KEY:randomBytes(32).toString("hex")};
 let nonce="";
 const client={query:async(sql,args)=>{
  if(sql===SESSION_INSERT_SQL){map.set(args[0],{sealed_record:args[1],expires_at:new Date(args[2])});return {rowCount:1,rows:[]};}
  if(sql===SESSION_READ_SQL){const row=map.get(args[0]);return row?{rowCount:1,rows:[row]}:{rowCount:0,rows:[]};}
  if(sql===SESSION_REVOKE_SQL)return {rowCount:Number(map.delete(args[0])),rows:[]};
  throw Error("Unknown DB write");
 }};
 const sessionStore=createPostgresStaffSessionStore(client,{env:tokenKey,now:()=>nowMs});
 const replayStore={
  reserve:async(k,expiry)=>{if(replays.has(k))return false;replays.set(k,expiry);return true;},
  consume:async(k,at)=>{if(!replays.has(k)||replays.get(k)<=at)return false;replays.delete(k);return true;}
 };
 const fetcher=async(url,init)=>{
  calls.push(url);
  if(url===config.token.tokenEndpoint)return json({
   token_type:"Bearer",expires_in:300,
   id_token:jwt({iss:issuer,sub:"staff-identity-001",aud:clientId,iat:nowSec-5,exp:nowSec+300,
    auth_time:nowSec-20,nonce,amr:["pwd","otp"]}),
   access_token:jwt({iss:issuer,sub:"staff-identity-001",aud:audience,
    client_id:clientId,iat:nowSec-5,exp:nowSec+300,scope:"openid patient:view facility:view"})
  });
  if(url===config.introspection.introspectionEndpoint)return json({active,iss:issuer,
   sub:"staff-identity-001",aud:audience,client_id:clientId,exp:nowSec+300});
  if(url==="https://workforce.synqoria-clinical.org/internal/v1/staff/grants/active")return json({
   actorId:actor,tenantId:tenant,facilityId,shiftId:shift,role:"nurse",
   status:"ACTIVE",issuer,subject:"staff-identity-001",revision:17,
   shiftStartsAt:"2026-11-05T18:00:00Z",shiftEndsAt:"2026-11-06T02:00:00Z",
   capabilities:["patient:view","facility:view"]
  });
  throw Error("Unknown source");
 };
 const service=createStaffSessionService({config,publicOrigin,sessionStore,replayStore,fetcher,now:()=>nowMs});
 async function begin(){
  const start=await service.begin();
  const u=new URL(start.authorizationUrl);
  nonce=u.searchParams.get("nonce");
  const callback=config.oidc.redirectUri+"?code=OpaqueAuthCode01234567890&state="+
   encodeURIComponent(u.searchParams.get("state"))+"&iss="+encodeURIComponent(issuer);
  return {start,callback};
 }
 const callbackRequest=(data,cookie=data.start.flowCookie.value)=>
  new Request(data.callback,{headers:{Cookie:OIDC_FLOW_COOKIE+"="+cookie}});
 return {service,calls,begin,callbackRequest,map};
}
const deny=f=>assert.rejects(f,StaffSessionServiceError);
test("P03-B0039 actual signed IdP proof + roster -> durable encrypted opaque cookie -> reauthorize -> logout",async()=>{
 const h=createHarness(),data=await h.begin();
 assert.equal(data.start.flowCookie.name,OIDC_FLOW_COOKIE);
 assert.equal(data.start.flowCookie.options.httpOnly,true);
 const logged=await h.service.complete(h.callbackRequest(data));
 assert.equal(logged.sessionCookie.name,STAFF_TICKET_COOKIE);
 assert.match(logged.sessionCookie.value,/^[A-Za-z0-9_-]{43}$/);
 assert.equal(logged.sessionCookie.options.httpOnly,true);
 assert.equal(logged.sessionCookie.options.secure,true);
 assert.equal(logged.sessionCookie.options.maxAge,300);
 assert.equal(logged.clearFlowCookie.options.maxAge,0);
 assert.equal(h.map.size,1);
 const staffRequest=new Request(publicOrigin+"workforce/clinical/G-S03",{headers:{
  Cookie:STAFF_TICKET_COOKIE+"="+logged.sessionCookie.value}});
 const ctx=await h.service.authorize(staffRequest);
 assert.equal(ctx.actorId,actor);assert.equal(ctx.tenantId,tenant);
 assert.deepEqual(ctx.capabilities,["patient:view","facility:view"]);
 assert.ok(!JSON.stringify(logged).includes(ctx.bearer));
 assert.ok(!JSON.stringify(logged).includes(tenant));
 const logout=new Request(publicOrigin+"api/auth/logout",{method:"POST",headers:{
  Origin:publicOrigin.slice(0,-1),Cookie:STAFF_TICKET_COOKIE+"="+logged.sessionCookie.value}});
 const ended=await h.service.logout(logout);
 assert.equal(ended.revoked,true);
 assert.equal(ended.clearSessionCookie.options.maxAge,0);
 assert.equal(h.map.size,0);
 await deny(()=>h.service.authorize(staffRequest));
});
test("P03-B0039 denied token or wrong workforce facility never issues browser cookie",async()=>{
 for(const values of [{active:false},{facilityId:actor}]){
  const h=createHarness(values),data=await h.begin();
  await deny(()=>h.service.complete(h.callbackRequest(data)));
  assert.equal(h.map.size,0);
 }
});
test("P03-B0039 callback replay and substituted cookie fail before extra token exchange",async()=>{
 const h=createHarness(),data=await h.begin();
 await h.service.complete(h.callbackRequest(data));
 const count=h.calls.length;
 await deny(()=>h.service.complete(h.callbackRequest(data)));
 assert.equal(h.calls.length,count);
 await deny(()=>h.service.complete(h.callbackRequest(data,"invalid")));
});
test("P03-B0039 CSRF-protected logout never uses browser Host spoofing",async()=>{
 const h=createHarness(),data=await h.begin(),logged=await h.service.complete(h.callbackRequest(data));
 const headers={Cookie:STAFF_TICKET_COOKIE+"="+logged.sessionCookie.value};
 await deny(()=>h.service.logout(new Request(publicOrigin+"api/auth/logout",{method:"GET",headers})));
 await deny(()=>h.service.logout(new Request(publicOrigin+"api/auth/logout",{method:"POST",headers:{
  ...headers,Origin:"https://attacker.example"}})));
 assert.equal(h.map.size,1);
});
