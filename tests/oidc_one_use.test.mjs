import test from "node:test";
import assert from "node:assert/strict";
import {randomBytes} from "node:crypto";
import {beginOneUseOidcAuthorization,consumeOneUseOidcAuthorization} from "../apps/web/lib/oidc-one-use.mjs";
import {OidcFlowError} from "../apps/web/lib/oidc-login-flow.mjs";

const now=Date.parse("2026-11-05T20:00:00Z");
const config={
 issuer:"https://auth.synqoria.org/realms/hospital",
 authorizationEndpoint:"https://auth.synqoria.org/realms/hospital/protocol/openid-connect/auth",
 redirectUri:"https://staff.synqoria.org/api/auth/oidc/callback",
 clientId:"synqoria-staff",
 flowKey:randomBytes(32).toString("hex")
};
const noPhiKeys=s=>!s.includes("patient")&&!s.includes("accessToken")&&!s.includes("codeVerifier");
function atomicStore(){
 const map=new Map(),callLog=[];
 return {
  callLog,
  reserve:async(key,expiresAt)=>{
   callLog.push(["reserve",key,expiresAt]);
   if(map.has(key))return false;
   map.set(key,expiresAt);
   return true;
  },
  consume:async(key,time)=>{
   callLog.push(["consume",key,time]);
   const expiresAt=map.get(key);
   if(expiresAt===undefined||time>=expiresAt)return false;
   map.delete(key);
   return true;
  }
 };
}
const callback=prepared=>{
 const u=new URL(config.redirectUri);
 u.searchParams.set("state",new URL(prepared.authorizationUrl).searchParams.get("state"));
 u.searchParams.set("code","AuthCode_Valid-0123456789");
 u.searchParams.set("iss",config.issuer);
 return u.href;
};
const denies=async fn=>await assert.rejects(fn,OidcFlowError);

test("state is reserved atomically before redirect and consumed once before token exchange",async()=>{
 const store=atomicStore();
 const prepared=await beginOneUseOidcAuthorization(config,store,{now});
 assert.ok(prepared.cookieValue.startsWith("v1."));
 assert.equal(store.callLog.length,1);
 assert.equal(store.callLog[0][0],"reserve");
 assert.ok(store.callLog[0][1].startsWith("synqoria:oidc:state:v1:"));
 assert.ok(noPhiKeys(store.callLog[0][1]));
 const proof=await consumeOneUseOidcAuthorization(callback(prepared),prepared.cookieValue,config,store,{now:now+1000});
 assert.equal(proof.clientId,config.clientId);
 assert.equal(proof.issuer,config.issuer);
 assert.equal(proof.code,"AuthCode_Valid-0123456789");
 assert.equal(store.callLog[1][0],"consume");
 assert.equal(store.callLog[0][1],store.callLog[1][1]);
 await denies(()=>consumeOneUseOidcAuthorization(callback(prepared),prepared.cookieValue,config,store,{now:now+2000}));
});
test("missing or failed durable storage never produces login redirects or code exchange",async()=>{
 await denies(()=>beginOneUseOidcAuthorization(config,null,{now}));
 await denies(()=>beginOneUseOidcAuthorization(config,{reserve:async()=>true},{now}));
 await denies(()=>beginOneUseOidcAuthorization(config,{reserve:async()=>false,consume:async()=>true},{now}));
 await denies(()=>beginOneUseOidcAuthorization(config,{reserve:async()=>{throw Error("db down")},consume:async()=>true},{now}));
 const store=atomicStore();
 const p=await beginOneUseOidcAuthorization(config,store,{now});
 await denies(()=>consumeOneUseOidcAuthorization(callback(p),p.cookieValue,config,{reserve:async()=>true,consume:async()=>{throw Error("db down")}}, {now:now+1000}));
 await denies(()=>consumeOneUseOidcAuthorization(callback(p),p.cookieValue,config,{reserve:async()=>true,consume:async()=>0}, {now:now+1000}));
});
test("no state may be consumed after TTL even if a sealed cookie is otherwise present",async()=>{
 const store=atomicStore();
 const p=await beginOneUseOidcAuthorization(config,store,{now});
 await denies(()=>consumeOneUseOidcAuthorization(callback(p),p.cookieValue,config,store,{now:now+300000}));
 assert.equal(store.callLog.some(x=>x[0]==="consume"),false); // sealed flow rejects before storage access
});
test("hostile state, duplicate state, wrong issuer and callback origin never touch replay store",async()=>{
 const store=atomicStore();
 const p=await beginOneUseOidcAuthorization(config,store,{now});
 const before=store.callLog.length;
 const hostile=callback(p).replace("staff.synqoria.org","attacker.example.org");
 await denies(()=>consumeOneUseOidcAuthorization(hostile,p.cookieValue,config,store,{now:now+1000}));
 const duplicate=callback(p)+"&state=untrusted";
 await denies(()=>consumeOneUseOidcAuthorization(duplicate,p.cookieValue,config,store,{now:now+1000}));
 const issuer=callback(p).replace(encodeURIComponent(config.issuer),encodeURIComponent("https://attacker.example.org"));
 await denies(()=>consumeOneUseOidcAuthorization(issuer,p.cookieValue,config,store,{now:now+1000}));
 assert.equal(store.callLog.length,before);
 const final=await consumeOneUseOidcAuthorization(callback(p),p.cookieValue,config,store,{now:now+1000});
 assert.equal(final.code,"AuthCode_Valid-0123456789");
});
test("concurrent callbacks cannot claim the same state twice",async()=>{
 const store=atomicStore();
 const p=await beginOneUseOidcAuthorization(config,store,{now});
 const results=await Promise.allSettled(Array.from({length:12},()=>consumeOneUseOidcAuthorization(callback(p),p.cookieValue,config,store,{now:now+1000})));
 assert.equal(results.filter(x=>x.status==="fulfilled").length,1);
 assert.equal(results.filter(x=>x.status==="rejected"&&x.reason instanceof OidcFlowError).length,11);
});
