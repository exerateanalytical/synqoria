/** P02-B0070. Staff scope summary never trusts raw user-supplied facility labels. */
export type NativeStaffScope=Readonly<{facilityName:string;roleName:string;shiftName:string;verifiedAt:number;expiresAt:number}>;
const allowed=/^[\p{L}\p{N} .,'’()/-]{2,70}$/u;
export function nativeStaffScope(locale:'en'|'fr',scope:NativeStaffScope|null,now:number,serverVerified:boolean){
 const denied=locale==='fr'?'Établissement et service non vérifiés':'Facility and shift not verified';
 if(!serverVerified||!scope||!Number.isFinite(now)||!Number.isFinite(scope.verifiedAt)||!Number.isFinite(scope.expiresAt)
  ||now<scope.verifiedAt||now>=scope.expiresAt||scope.expiresAt-scope.verifiedAt>12*60*60*1000
  ||![scope.facilityName,scope.roleName,scope.shiftName].every(v=>allowed.test(v)))return {verified:false,label:denied};
 return {verified:true,label:`${scope.facilityName} · ${scope.roleName} · ${scope.shiftName}`};
}
