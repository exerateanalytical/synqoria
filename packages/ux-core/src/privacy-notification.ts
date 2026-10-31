/** P02-B0050: public device notifications contain no patient, proxy, diagnosis or appointment details. */
export type SafeNoticeKind='new-task'|'appointment-update'|'access-expired'|'sync-review';
const copy:Readonly<Record<SafeNoticeKind,{en:string;fr:string}>>={
 'new-task':{en:'A work item requires secure sign-in.',fr:'Une tâche nécessite une connexion sécurisée.'},
 'appointment-update':{en:'An update is available in the secure app.',fr:'Une mise à jour est disponible dans l’application sécurisée.'},
 'access-expired':{en:'Your secure session has expired.',fr:'Votre session sécurisée a expiré.'},
 'sync-review':{en:'Open the app to review synchronization status.',fr:'Ouvrez l’application pour vérifier la synchronisation.'}
};
export function privacyNotification(kind:unknown,locale:'en'|'fr'):{title:string;body:string;data:Record<string,never>;containsClinicalData:false}|null{
 if(typeof kind!=='string'||!Object.prototype.hasOwnProperty.call(copy,kind)||!['en','fr'].includes(locale))return null;
 return {title:'SYNQORIA',body:copy[kind as SafeNoticeKind][locale],data:{},containsClinicalData:false};
}
