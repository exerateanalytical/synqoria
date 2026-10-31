/** P02-B0055. Presentation-only proxy disclosure guard; backend must independently enforce. */
export type ProxyGrant={patientId:string;proxyId:string;facilityId:string;expiresAt:number;scopes:readonly string[];revoked:boolean};
const opaque=(s:unknown)=>typeof s==='string'&&/^[a-zA-Z0-9_-]{8,128}$/.test(s);
export function proxyMayShowRecord(grant:ProxyGrant|null,patientId:string,proxyId:string,facilityId:string,now:number,online:boolean):boolean{
 return grant!==null&&opaque(patientId)&&opaque(proxyId)&&opaque(facilityId)&&Number.isSafeInteger(now)&&online===true
  &&!grant.revoked&&grant.patientId===patientId&&grant.proxyId===proxyId&&grant.facilityId===facilityId
  &&Number.isSafeInteger(grant.expiresAt)&&now<grant.expiresAt&&Array.isArray(grant.scopes)&&grant.scopes.includes('patient:read:proxy');
}
