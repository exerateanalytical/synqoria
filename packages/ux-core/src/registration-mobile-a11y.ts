/** P02-B0036: native screen-reader announcements; no clinical data or simulated save. */
export function mobileRegistrationAccessibility(locale:'en'|'fr'){
 return {title:locale==='fr'?'Enregistrement du patient':'Patient registration',
 alert:locale==='fr'?'Enregistrement bloqué. Vérification du personnel et de l’établissement requise.':'Registration locked. Verified staff and facility access required.',
 actionLabel:locale==='fr'?'Enregistrer — indisponible':'Save — unavailable',
 actionDisabled:true,minimumTarget:48,screen:'M01-S02' as const};
}
