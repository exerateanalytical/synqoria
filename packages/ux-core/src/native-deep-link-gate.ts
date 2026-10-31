/** P02-B0073. Reject noncanonical native deep links before any route selection.
 * This is presentation routing only; server authorization remains mandatory. */
export type NativeDeepLinkDecision={allowed:false;reason:'INVALID_ROUTE'|'LOCKED'}|{allowed:true;route:string};
const allowedRoots=new Set(['/work','/patients','/tasks','/more','/home','/appointments']);
export function nativeDeepLinkGate(raw:unknown,kind:'staff'|'patient',serverSessionVerified:boolean):NativeDeepLinkDecision {
 if(typeof raw!=='string'||raw.length>160||raw.includes('%')||raw.includes('?')||raw.includes('#')
   ||raw.includes('\\')||raw.includes('//')||!/^\/[a-z0-9/-]+$/.test(raw)||raw.endsWith('/')||raw.includes('/./')||raw.includes('/../'))
  return {allowed:false,reason:'INVALID_ROUTE'};
 const roots=kind==='staff'?new Set(['/work','/patients','/tasks','/more']):new Set(['/home','/appointments','/more']);
 if(!allowedRoots.has(raw)||!roots.has(raw))return {allowed:false,reason:'INVALID_ROUTE'};
 return serverSessionVerified?{allowed:true,route:raw}:{allowed:false,reason:'LOCKED'};
}
