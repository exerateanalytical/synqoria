/** P02-B0041. Non-identifying bilingual error summary for accessible locked intake. */
export type IntakeIssue='ACCESS'|'FACILITY'|'REVIEW'|'OFFLINE';
const copy={en:{ACCESS:'Staff access not verified',FACILITY:'Facility not selected',REVIEW:'Independent review required',OFFLINE:'Hospital connection unavailable'},
 fr:{ACCESS:'Accès du personnel non vérifié',FACILITY:'Établissement non sélectionné',REVIEW:'Vérification indépendante requise',OFFLINE:'Connexion hospitalière indisponible'}} as const;
const order:readonly IntakeIssue[]=['ACCESS','FACILITY','REVIEW','OFFLINE'];
export function intakeA11ySummary(locale:'en'|'fr',issues:readonly IntakeIssue[]){
 const distinct=new Set(issues.filter(i=>order.includes(i)));
 return {role:'alert' as const,screenId:'M01-S02' as const,items:order.filter(i=>distinct.has(i)).map(i=>({code:i,message:copy[locale][i]})),
  canSubmit:false as const,focusTarget:'intake-status' as const};
}
