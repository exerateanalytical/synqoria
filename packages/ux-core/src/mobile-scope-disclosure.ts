/** P02-B0062. Render safe scope status; NEVER infer verified staff or proxy access from local selection. */
export type MobileScope='staff'|'patient';
export type VerifiedScope=Readonly<{actorId:string;facilityId:string;role:string;shiftId?:string;proxyPatientId?:string;expiresAt:number;serverVerified:boolean}>;
export function mobileScopeDisclosure(kind:MobileScope,locale:'en'|'fr',grant:VerifiedScope|null,now:number,online:boolean){
 const valid=!!grant&&grant.serverVerified===true&&online===true&&Number.isFinite(now)&&Number.isFinite(grant.expiresAt)
  &&now<grant.expiresAt&&grant.actorId.length>=8&&grant.facilityId.length>=8&&grant.role.length>=3
  &&(kind==='patient'||!!grant.shiftId&&grant.shiftId.length>=8)
  &&(kind==='staff'||!grant.proxyPatientId||grant.proxyPatientId.length>=8);
 if(!valid)return {verified:false as const,label:locale==='fr'?(kind==='staff'?'Personnel · Site et service non vérifiés':'Patient · Identité ou mandat non vérifié'):(kind==='staff'?'Staff · Facility and shift unverified':'Patient · Identity or proxy grant unverified')};
 // Never display opaque identifiers or PHI in a banner, even with a valid grant.
 return {verified:true as const,label:locale==='fr'?'Contexte vérifié côté serveur':'Server-verified context'};
}
