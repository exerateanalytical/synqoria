/** P02-B0071. Never reveal patient or proxy identities through a stale client grant. */
export type NativeProxyGrant=Readonly<{subjectId:string;proxyId:string;verifiedAt:number;expiresAt:number;revoked:boolean;epoch:number}>;
export function nativeProxyGrantStatus(grant:NativeProxyGrant|null,subjectId:string,proxyId:string,epoch:number,now:number,serverVerified:boolean){
 const denied={allowed:false,reason:'ACCESS_UNAVAILABLE' as const};
 if(!serverVerified||!grant||!Number.isSafeInteger(epoch)||epoch<1||!Number.isFinite(now)||!Number.isFinite(grant.verifiedAt)||!Number.isFinite(grant.expiresAt)
  ||!grant.subjectId||!grant.proxyId||grant.subjectId===grant.proxyId||grant.subjectId!==subjectId||grant.proxyId!==proxyId
  ||grant.epoch!==epoch||grant.revoked||now<grant.verifiedAt||now>=grant.expiresAt||grant.expiresAt-grant.verifiedAt>5*60*1000)return denied;
 return {allowed:true,reason:'VERIFIED_GRANT' as const};
}
