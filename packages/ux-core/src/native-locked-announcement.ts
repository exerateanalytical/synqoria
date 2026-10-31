/** P02-B0065. Screen-reader status without patient/actor identifiers or unsafe truncation. */
export function nativeLockedAnnouncement(kind:'staff'|'patient',locale:'en'|'fr'){
 const text=locale==='fr'?(kind==='staff'?'Accès du personnel non vérifié. Les tâches cliniques restent indisponibles.':'Accès au dossier non vérifié. Aucune donnée médicale n’est affichée.')
 :(kind==='staff'?'Staff access not verified. Clinical tasks remain unavailable.':'Record access not verified. No health information is displayed.');
 return {text,accessibilityRole:'alert' as const,accessibilityLiveRegion:'polite' as const,allowFontScaling:true,maxFontSizeMultiplier:3};
}
