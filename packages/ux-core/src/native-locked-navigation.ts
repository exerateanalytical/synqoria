/** P02-B0063. No navigation to clinical workspaces from unverified native shell. */
export type NativeRouteIntent={shell:'staff'|'patient';root:string;detail?:string};
const staffRoots=new Set(['work','patients','tasks','more']);
const patientRoots=new Set(['home','appointments','records','more']);
export function lockedNativeRoute(intent:NativeRouteIntent|null):{allowed:false;reason:'UNVERIFIED_SESSION'|'INVALID_ROUTE'}{
 if(!intent||!(/^[a-z]+$/.test(intent.root))||intent.detail||!(intent.shell==='staff'?staffRoots:patientRoots).has(intent.root))return {allowed:false,reason:'INVALID_ROUTE'};
 return {allowed:false,reason:'UNVERIFIED_SESSION'};
}
