// Browser wiring of the M38 device SDK to the existing same-origin proxy and the existing operations sign-in (P03/OIDC).
// No tokens are handled here: the HttpOnly cookie travels with same-origin requests; the SDK only sees fetch + the CSRF value.
import {ContractCache,DurableQueue,Diagnostics,EncryptedStore,IdbKeyVault,IdbStore,KeyManager,LeaderElector,SyncEngine,Transport,redactedExport,applyLockPolicy,DEFAULT_DEVICE_POLICY,SyncScheduler,DEFAULT_SCHEDULER,verifyBinding} from "../../../../packages/offline-device-sdk/src/index.ts";
import type {DeviceBinding,Session} from "../../../../packages/offline-device-sdk/src/index.ts";

const CSRF_COOKIE="__Host-synq_ops_csrf";
const BINDING_KEY="synq_device_binding";
export type Device={engine:SyncEngine;scheduler:SyncScheduler;queue:DurableQueue;km:KeyManager;binding:DeviceBinding;session:()=>Session|null;diag:Diagnostics;exportMetadata:()=>Promise<ReturnType<typeof redactedExport>>;lock:()=>Promise<void>;wipe:()=>Promise<{lost:number}>};

function csrf():string{
 const pair=document.cookie.split("; ").find(x=>x.startsWith(CSRF_COOKIE+"="));
 return pair?decodeURIComponent(pair.slice(CSRF_COOKIE.length+1)):"";
}
export type SessionState={session:Session|null;blocked:boolean};
/** blocked = the server refuses to vouch for an identity (verifier not integrated / unavailable): enrollment, unlock and sync stay disabled. */
export async function readSession():Promise<SessionState>{
 try{
  const r=await fetch("/api/operations/auth/verified-session",{credentials:"same-origin",cache:"no-store"});
  if(r.status===503)return {session:null,blocked:true};
  if(!r.ok)return {session:null,blocked:false};
  const j=await r.json() as Session;
  return typeof j.userId==="string"&&typeof j.scope==="string"&&typeof j.expiresAt==="number"?{session:j,blocked:false}:{session:null,blocked:false};
 }catch{return {session:null,blocked:true};}
}
export function savedBinding():DeviceBinding|null{
 try{const raw=window.localStorage.getItem(BINDING_KEY);return raw?JSON.parse(raw) as DeviceBinding:null;}catch{return null;}
}
/** Binding is a SERVER fact: verified against the server's device list (existence, ACTIVE, bound to exactly this user, live window) before it is stored locally. */
export async function bindDevice(deviceId:string,session:Session):Promise<{ok:true;binding:DeviceBinding}|{ok:false;code:string}>{
 const r=await verifyBinding(new Transport({fetch:(u,i)=>fetch(u,i),csrf}),deviceId,session,Date.now());
 if(!r.ok)return r;
 try{window.localStorage.setItem(BINDING_KEY,JSON.stringify(r.binding));}catch{return {ok:false,code:"storage_unavailable"};}
 return r;
}
export function forgetBinding(){try{window.localStorage.removeItem(BINDING_KEY);}catch{/* ignore */}}

export async function openDevice(binding:DeviceBinding,session:()=>Session|null,onLog?:(l:string)=>void):Promise<Device>{
 const name="synqoria-device-"+binding.deviceId;
 const plain=new IdbStore(name);
 const km=new KeyManager(new IdbKeyVault(name));
 const enc=new EncryptedStore(plain,km);
 const queue=new DurableQueue(enc,()=>Date.now());
 const diag=new Diagnostics(onLog);
 const tabId=globalThis.crypto.randomUUID();
 const engine=new SyncEngine({store:enc,plain,km,queue,transport:new Transport({fetch:(u,i)=>fetch(u,i),csrf}),cache:new ContractCache(),binding,session,clock:()=>Date.now(),
  rng:Math.random,diag,leader:new LeaderElector(plain,()=>Date.now(),tabId),online:()=>navigator.onLine});
 const locks=(navigator as Navigator&{locks?:{request:unknown}}).locks;
 const scheduler=new SyncScheduler(engine,{set:(f,m)=>window.setTimeout(f,m),clear:id=>window.clearTimeout(id as number)},DEFAULT_SCHEDULER,locks?(locks as unknown as ConstructorParameters<typeof SyncScheduler>[3]):null);
 return {engine,scheduler,queue,km,binding,session,diag,
  exportMetadata:async()=>redactedExport(await queue.list(),new Date().toISOString()),
  lock:async()=>{await applyLockPolicy(km,plain,DEFAULT_DEVICE_POLICY.onLogout);},
  wipe:()=>applyLockPolicy(km,plain,"WIPE")};
}
