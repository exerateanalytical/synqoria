/** P02-B0057. Locked presentation is not a clinical release decision. */
export type RegistrationReleasePresentation={status:'blocked';canSubmit:false;canSave:false;canApprove:false;title:string;reason:string;ariaLive:'polite'};
export function registrationReleasePresentation(locale:'en'|'fr',gate:'identity'|'database'|'clinical'|'offline'):RegistrationReleasePresentation{
 const messages={
  en:{identity:'Verified reviewer identity is unavailable.',database:'Database authorization is not verified.',clinical:'Clinical approval has not been recorded.',offline:'A verified online connection is required.'},
  fr:{identity:'L’identité du réviseur n’est pas vérifiée.',database:'L’autorisation de la base de données n’est pas vérifiée.',clinical:'L’approbation clinique n’a pas été enregistrée.',offline:'Une connexion vérifiée est nécessaire.'}
 };
 return {status:'blocked',canSubmit:false,canSave:false,canApprove:false,title:locale==='fr'?'Enregistrement indisponible':'Registration unavailable',reason:messages[locale][gate],ariaLive:'polite'};
}
