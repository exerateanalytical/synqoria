/** Locked native More is a local, nonclinical accessibility/help surface only. */
export type NativeMoreRole='staff'|'patient';
export type NativeMoreLocale='en'|'fr';
export type NativeMoreAction='access_status'|'privacy_help'|'language';
export interface NativeMoreItem {id:NativeMoreAction;label:string;hint:string;requiresSession:false}
const copy={
 staff:{en:[['access_status','Access status','No verified facility or shift'],['privacy_help','Privacy','No patient data is available'],['language','Language','Change display language']],fr:[['access_status','État de l’accès','Aucun établissement ni service vérifié'],['privacy_help','Confidentialité','Aucune donnée patient disponible'],['language','Langue','Changer la langue d’affichage']]},
 patient:{en:[['access_status','Access status','No verified patient or proxy session'],['privacy_help','Privacy','No health record is available'],['language','Language','Change display language']],fr:[['access_status','État de l’accès','Aucune session patient ou mandataire vérifiée'],['privacy_help','Confidentialité','Aucun dossier médical disponible'],['language','Langue','Changer la langue d’affichage']]}
} as const;
export function nativeMoreItems(role:NativeMoreRole,locale:NativeMoreLocale):ReadonlyArray<NativeMoreItem>{
 if(!['staff','patient'].includes(role)||!['en','fr'].includes(locale))return [];
 return copy[role][locale].map(([id,label,hint])=>({id,label,hint,requiresSession:false as const}));
}
export function nativeMoreCanOpen(tab:string,sessionVerified:boolean):boolean{
 return tab==='more'; // nonclinical local content only; other tabs still require server grants
}
export function nativeMoreCanExecute(action:string):action is NativeMoreAction{
 return action==='access_status'||action==='privacy_help'||action==='language';
}
