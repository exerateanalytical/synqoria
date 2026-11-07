// Browser wiring of the M38 device SDK to the existing same-origin proxy and the existing operations sign-in (P03/OIDC).
// No tokens are handled here: the HttpOnly cookie travels with same-origin requests; the SDK only sees fetch + the CSRF value.
import {ContractCache,DurableQueue,Diagnostics,EncryptedStore,IdbKeyVault,IdbStore,KeyManager,LeaderElector,SyncEngine,Transport,redactedExport,applyLockPolicy,DEFAULT_DEVICE_POLICY,SyncScheduler,DEFAULT_SCHEDULER} from "../../../../packages/offline-device-sdk/src/index.ts";
import type {DeviceBinding,Session} from "../../../../packages/offline-device-sdk/src/index.ts";

const CSRF_COOKIE="__Host-synq_ops_csrf";
const BINDING_KEY="synq_device_binding";
export type Device={engine:SyncEngine;scheduler:SyncScheduler;queue:DurableQueue;km:KeyManager;binding:DeviceBinding;session:()=>Session|null;diag:Diagnostics;exportMetadata:()=>Promise<ReturnType<typeof redactedExport>>;lock:()=>Promise<void>;wipe:()=>Promise<{lost:number}>};

function csrf():string{
 const pair=document.cookie.split("; ").find(x=>x.startsWith(CSRF_COOKIE+"="));
 return pair?decodeURIComponent(pair.slice(CSRF_COOKIE.length+1)):"";
}
export async function readSession():Promise<Session|null>{
 try{
  const r=await fetch("/api/offline/session",{credentials:"same-origin",cache:"no-store"});
  if(!r.ok)return null;
  const j=await r.json() as Session;
  return typeof j.userId==="string"&&typeof j.scope==="string"&&typeof j.expiresAt==="number"?j:null;
 }catch{return null;}
}
export function savedBinding():DeviceBinding|null{
 try{const raw=window.localStorage.getItem(BINDING_KEY);return raw?JSON.parse(raw) as DeviceBinding:null;}catch{return null;}
}
/**
 * Binding is a SERVER fact. This only records it locally after the server confirmed that the device exists in this hospital scope and is ACTIVE
 * (GET devices/{id}/cursor is scoped by the verified token). A device id the server does not know, or that is revoked, is never stored.
 */
export async function bindDevice(deviceId:string,session:Session):Promise<{ok:true;binding:DeviceBinding}|{ok:false;code:string}>{
 const t=new Transport({fetch:(u,i)=>fetch(u,i),csrf});
 const r=await t.cursor(deviceId);
 if(!r.ok)return {ok:false,code:r.kind==="notfound"?"device_not_found":r.code};
 if(r.data.status!=="ACTIVE")return {ok:false,code:"device_not_active"};
 const binding:DeviceBinding={deviceId,userId:session.userId,scope:session.scope};
 try{window.localStorage.setItem(BINDING_KEY,JSON.stringify(binding));}catch{return {ok:false,code:"storage_unavailable"};}
 return {ok:true,binding};
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
