/** P02-B0039. Never hide a locked action through color alone; use labelled status and full-height hit targets. */
export function registrationActionPresentation(locale:'en'|'fr',online:boolean,staffVerified:boolean){
 const fr=locale==='fr';
 const reason=!staffVerified?(fr?'Vérifiez l’identité du personnel':'Verify staff identity'):
 !online?(fr?'Connexion hospitalière indisponible':'Hospital connection unavailable'):
 (fr?'Validation clinique non autorisée':'Clinical release not authorized');
 return {screenId:'M01-S02' as const,buttonLabel:fr?'Enregistrement indisponible':'Registration unavailable',
  reason,disabled:true as const,minTouchTargetPx:48,role:'status' as const,
  displayStatusIcon:'lock' as const,visibleText:true as const};
}
