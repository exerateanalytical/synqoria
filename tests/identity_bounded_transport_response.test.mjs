import test from "node:test";
import assert from "node:assert/strict";
import {verifyBoundedIdentityResponse,IdentityBoundedResponseError}
 from "../apps/web/lib/identity-bounded-transport-response.mjs";
import {createRestrictedIdentityTransport,IdentityTransportError}
 from "../apps/web/lib/identity-restricted-transport.mjs";
const endpoint="https://id.hospital.org/realms/a/protocol/openid-connect/token";
const signal=()=>new AbortController().signal;
const opts=(maxBytes=16384)=>({expectedUrl:endpoint,maxBytes,signal:signal()});
const safe=(content="{}")=>new Response(content,{status:200,headers:{
 "Content-Type":"application/json","Cache-Control":"no-store"}});
test("B0112 reject untrusted upstream response variants, spoofed redirect or bearer cookie",async()=>{
 for(const bad of [
  {status:200,headers:{get:()=>null},body:{}},
  new Response(null,{status:204,headers:{"Content-Type":"application/json","Cache-Control":"no-store"}}),
  new Response("{}",{status:200,headers:{"Content-Type":"text/html","Cache-Control":"no-store"}}),
  new Response("{}",{status:200,headers:{"Content-Type":"application/json","Cache-Control":"max-age=3600"}}),
  new Response("{}",{status:200,headers:{"Content-Type":"application/json","Cache-Control":"no-store","Set-Cookie":"x=secret"}}),
  new Response("{}",{status:200,headers:{"Content-Type":"application/json","Cache-Control":"no-store","Location":"https://evil.org"}})
 ])await assert.rejects(()=>verifyBoundedIdentityResponse(bad,opts()),IdentityBoundedResponseError);
 const res=await verifyBoundedIdentityResponse(safe(),opts());
 assert.deepEqual(await res.json(),{});
 assert.equal(res.headers.get("cache-control"),"no-store");
 assert.equal(res.headers.get("set-cookie"),null);
});
test("B0113 exact bytes, declared content-length, compressed responses and caps enforced",async()=>{
 for(const headers of [
  {"Content-Length":"50000"},{"Content-Length":"1"},{"Content-Length":"-1"},
  {"Content-Encoding":"gzip"},{"Content-Type":"application/json; charset=latin1"}
 ]){
  const bad=new Response("{}",{status:200,headers:{
   "Content-Type":"application/json","Cache-Control":"no-store",...headers}});
  await assert.rejects(()=>verifyBoundedIdentityResponse(bad,opts()),IdentityBoundedResponseError);
 }
 const valid=new Response("{}",{headers:{"Content-Type":"application/json",
  "Cache-Control":"private, no-store","Content-Length":"2"}});
 assert.deepEqual(await (await verifyBoundedIdentityResponse(valid,opts())).json(),{});
});
test("B0114 bounded reader cancels oversized streaming responses before handing to callers",async()=>{
 let cancelled=0;
 const stream=new ReadableStream({
  start(c){c.enqueue(new Uint8Array(1200));c.enqueue(new Uint8Array(1200));},
  cancel(){cancelled++;}
 });
 const res=new Response(stream,{headers:{"Content-Type":"application/json","Cache-Control":"no-store"}});
 await assert.rejects(()=>verifyBoundedIdentityResponse(res,opts(1024)),IdentityBoundedResponseError);
 assert.equal(cancelled,1);
});
test("B0115 invalid UTF-8, primitive JSON, bad parser and ambiguous body never forwarded",async()=>{
 for(const payload of ["null","[]","true","1","{bad}"]){
  await assert.rejects(()=>verifyBoundedIdentityResponse(safe(payload),opts()),IdentityBoundedResponseError);
 }
 const invalid=new Response(new Uint8Array([0xc3,0x28]),{headers:{
  "Content-Type":"application/json","Cache-Control":"no-store"}});
 await assert.rejects(()=>verifyBoundedIdentityResponse(invalid,opts()),IdentityBoundedResponseError);
 const res=await verifyBoundedIdentityResponse(safe('{"active":true,"count":1}'),opts());
 assert.deepEqual(await res.json(),{active:true,count:1});
});
test("B0116 pre-aborted or mid-body aborted signal rejects before exposing a response",async()=>{
 const ctrl=new AbortController();ctrl.abort();
 await assert.rejects(()=>verifyBoundedIdentityResponse(safe(),{...opts(),signal:ctrl.signal}),IdentityBoundedResponseError);
 let abort;
 const ctrl2=new AbortController();
 const stream=new ReadableStream({start(c){c.enqueue(new Uint8Array([123]));abort=()=>{ctrl2.abort();c.enqueue(new Uint8Array([125]));c.close();};}});
 const reading=verifyBoundedIdentityResponse(new Response(stream,{headers:{
  "Content-Type":"application/json","Cache-Control":"no-store"
 }}),{...opts(),signal:ctrl2.signal});
 abort();
 await assert.rejects(()=>reading,IdentityBoundedResponseError);
});
