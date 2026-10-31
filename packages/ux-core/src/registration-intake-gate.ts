import {validateRegistrationDraft,type RegistrationDraft} from './registration-draft';
/** P02-B0028. Source-only state; remote save is impossible until all safety evidence is current. */
export type IntakeAccess={verified:boolean;online:boolean;facilitySelected:boolean;shiftCurrent:boolean;canRegister:boolean;serverConnected:boolean;clinicalReviewAvailable:boolean};
export type IntakeGate='READY_FOR_AUTHORIZED_TRANSPORT'|'ACCESS_DENIED'|'OFFLINE_NO_WRITE'|'INTEGRATION_UNAVAILABLE'|'INPUT_REVIEW_REQUIRED';
export function gateRegistration(d:RegistrationDraft,access:IntakeAccess,todayISO:string):IntakeGate{
 if(!access.verified||!access.facilitySelected||!access.shiftCurrent||!access.canRegister)return 'ACCESS_DENIED';
 if(!access.online)return 'OFFLINE_NO_WRITE';
 if(!access.serverConnected||!access.clinicalReviewAvailable)return 'INTEGRATION_UNAVAILABLE';
 if(validateRegistrationDraft(d,todayISO).length)return 'INPUT_REVIEW_REQUIRED';
 return 'READY_FOR_AUTHORIZED_TRANSPORT';
}
export function safeRegistrationStatus(locale:'en'|'fr',code:IntakeGate):string {
 const texts={en:{READY_FOR_AUTHORIZED_TRANSPORT:'Authorized transport required',ACCESS_DENIED:'Verified hospital access required',OFFLINE_NO_WRITE:'Registration cannot be finalized offline',INTEGRATION_UNAVAILABLE:'Registration service unavailable',INPUT_REVIEW_REQUIRED:'Review the entered information'},fr:{READY_FOR_AUTHORIZED_TRANSPORT:'Transmission autorisée requise',ACCESS_DENIED:'Accès hospitalier vérifié requis',OFFLINE_NO_WRITE:'Inscription impossible hors ligne',INTEGRATION_UNAVAILABLE:'Service d’inscription indisponible',INPUT_REVIEW_REQUIRED:'Vérifiez les informations saisies'}};
 return texts[locale][code];
}
