/** P02-B0035: local UI conditions NEVER authorize patient submission. */
export type RegistrationRelease={authenticated:boolean;online:boolean;facilityScoped:boolean;reviewCurrent:boolean;serverReleaseVerified:boolean;clinicalSignoff:boolean};
export function registrationSubmissionLocked(state:RegistrationRelease):{locked:true;reason:'CLINICAL_RELEASE_NOT_APPROVED'|'ACCESS_NOT_VERIFIED'}{
 const verified=state.authenticated&&state.online&&state.facilityScoped&&state.reviewCurrent&&state.serverReleaseVerified;
 return {locked:true,reason:verified?'CLINICAL_RELEASE_NOT_APPROVED':'ACCESS_NOT_VERIFIED'};
}
