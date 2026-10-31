/** Semantic, wrap-safe field descriptors; no patient values or logs. P02-B0033. */
export type RegistrationLocale='en'|'fr';
export type RegistrationFieldKey='legalName'|'birthInformation'|'dateOfBirth'|'estimatedAgeYears'|'language';
export type AccessibleField={key:RegistrationFieldKey;id:string;label:string;hint:string;required:boolean;type:'text'|'select'|'date'|'number';};
export function registrationFieldStructure(locale:RegistrationLocale):readonly AccessibleField[]{
 const fr=locale==='fr';
 return [
 {key:'legalName',id:'reg-legal-name',label:fr?'Nom déclaré (facultatif)':'Reported name (optional)',hint:fr?'Ne pas inventer de nom.':'Do not invent a name.',required:false,type:'text'},
 {key:'birthInformation',id:'reg-birth-state',label:fr?'Information sur la naissance':'Birth information',hint:fr?'Choisir connue, âge estimé ou inconnue.':'Choose known, estimated age or unknown.',required:true,type:'select'},
 {key:'dateOfBirth',id:'reg-birth-date',label:fr?'Date de naissance (si connue)':'Date of birth (if known)',hint:fr?'Ne pas deviner une date.':'Do not guess a date.',required:false,type:'date'},
 {key:'estimatedAgeYears',id:'reg-est-age',label:fr?'Âge estimé en années':'Estimated age in years',hint:fr?'Seulement si l’âge est estimé.':'Only for an estimated age.',required:false,type:'number'},
 {key:'language',id:'reg-language',label:fr?'Langue préférée':'Preferred language',hint:fr?'Anglais ou français.':'English or French.',required:true,type:'select'}
 ];
}
