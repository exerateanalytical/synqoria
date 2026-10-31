import type {ClinicalRead} from './clinical-api-client';
import type {Locale} from './locale';
/** Status MUST use visible text plus icon; never derive clinical safety from transient HTTP. */
export type ClinicalUiStatus={kind:'ready'|'locked'|'neutral'|'warning';icon:'check-circle'|'lock'|'info'|'alert-triangle';label:string};
const labels={
 en:{ok:'Verified read returned from hospital server', 'authentication-required':'Sign-in required', 'not-disclosed':'Record unavailable or access denied', unavailable:'Server outcome unknown — do not retry clinical actions blindly', 'invalid-request':'Invalid request — no data displayed'},
 fr:{ok:'Lecture confirmée par le serveur hospitalier','authentication-required':'Connexion requise','not-disclosed':'Dossier indisponible ou accès refusé',unavailable:'Résultat serveur inconnu — ne répétez pas une action clinique sans vérification','invalid-request':'Demande non valide — aucune donnée affichée'}
} as const;
export function clinicalUiStatus(result:ClinicalRead['status'],locale:Locale):ClinicalUiStatus {
 const label=labels[locale][result];
 switch(result){
  case 'ok':return {kind:'ready',icon:'check-circle',label};
  case 'authentication-required':return {kind:'locked',icon:'lock',label};
  case 'not-disclosed':return {kind:'neutral',icon:'info',label};
  case 'unavailable':case 'invalid-request':return {kind:'warning',icon:'alert-triangle',label};
 }
}
