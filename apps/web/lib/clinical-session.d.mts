export const STAFF_COOKIE:string;
export interface VerifiedStaffClaims{
 issuer:"SYNQORIA_OIDC_VERIFIED_V1";
 actorId:string;tenantId:string;facilityId:string;
 bearer:string;issuedAt:number;expiresAt:number;capabilities:string[];
}
export interface OpenStaffClaims extends Omit<VerifiedStaffClaims,"capabilities">{readonly capabilities:readonly string[]}
export type ClinicalSessionEnvironment=Record<string,string|undefined>;
export function sealStaffSession(claims:VerifiedStaffClaims,opts?:{now?:number;env?:ClinicalSessionEnvironment}):string;
export function openStaffSession(cookie:unknown,opts?:{now?:number;env?:ClinicalSessionEnvironment}):Readonly<OpenStaffClaims>|null;
export function readSessionCookie(header:unknown,opts?:{now?:number;env?:ClinicalSessionEnvironment}):Readonly<OpenStaffClaims>|null;
export function secureCookieOptions():{httpOnly:true;secure:true;sameSite:"strict";path:"/";maxAge:number;priority:"high"};
