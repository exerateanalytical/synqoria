/** P02-B0047. No clinical/patient data in screen-reader status; EN/FR semantic status pairs. */
export type LockedReason='AUTH_REQUIRED'|'FACILITY_REQUIRED'|'OFFLINE'|'REVIEW_REQUIRED'|'CLINICAL_RELEASE_LOCKED';
const copy:Readonly<Record<'en'|'fr',Record<LockedReason,string>>>={
 en:{AUTH_REQUIRED:'Verified sign-in required',FACILITY_REQUIRED:'Verified hospital and shift required',OFFLINE:'Hospital connection unavailable',REVIEW_REQUIRED:'Independent identity review required',CLINICAL_RELEASE_LOCKED:'Patient registration is not authorized for clinical use'},
 fr:{AUTH_REQUIRED:'Connexion vérifiée requise',FACILITY_REQUIRED:'Établissement et service vérifiés requis',OFFLINE:'Connexion à l’hôpital indisponible',REVIEW_REQUIRED:'Vérification indépendante de l’identité requise',CLINICAL_RELEASE_LOCKED:'Inscription du patient non autorisée pour un usage clinique'}
};
export function accessibleLockedStatus(locale:'en'|'fr',reason:LockedReason){
 return Object.freeze({id:'synqoria-locked-status',role:'alert' as const,live:'assertive' as const,
  message:copy[locale][reason],canSubmit:false as const,minimumTouchTarget:48,containsPatientData:false as const});
}
