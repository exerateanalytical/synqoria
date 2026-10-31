/** P02-B0069. Bilingual, non-disclosing status for both mobile applications. */
export type NativeAccessStatus='NO_SESSION'|'SESSION_EXPIRED'|'FACILITY_REQUIRED'|'PROXY_REVOKED'|'OFFLINE';
const text={
 en:{NO_SESSION:'Sign in with a verified account to continue.',SESSION_EXPIRED:'Your session ended. Sign in again.',FACILITY_REQUIRED:'A verified facility and active shift are required.',PROXY_REVOKED:'Access to this represented record is unavailable.',OFFLINE:'Connect to verify access. Clinical actions are unavailable offline.'},
 fr:{NO_SESSION:'Connectez-vous avec un compte vérifié pour continuer.',SESSION_EXPIRED:'Votre session a expiré. Reconnectez-vous.',FACILITY_REQUIRED:'Un établissement vérifié et un service actif sont requis.',PROXY_REVOKED:'L’accès à ce dossier représenté est indisponible.',OFFLINE:'Connectez-vous pour vérifier l’accès. Les actes cliniques sont indisponibles hors ligne.'}
} as const;
export function nativeAccessStatus(locale:'en'|'fr',code:NativeAccessStatus){
 if(!(code in text.en))throw new Error('Invalid access status');
 return {code,text:text[locale][code],accessibilityRole:'alert' as const,accessibilityLiveRegion:'polite' as const,allowFontScaling:true};
}
