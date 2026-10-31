/** P02-B0051: readonly step model, never permits clinical registration or editable patient data. */
export type IntakeStep='access'|'facility'|'duplicate-review'|'registration';
const steps:IntakeStep[]=['access','facility','duplicate-review','registration'];
const names={en:['Verify staff access','Confirm facility and shift','Review possible duplicates','Register patient'],fr:['Vérifier l’accès du personnel','Confirmer l’établissement et le service','Examiner les doublons possibles','Enregistrer le patient']};
export function lockedRegistrationJourney(locale:'en'|'fr'){
 return steps.map((id,i)=>({id,label:names[locale][i]!,status:'blocked' as const,canOpen:false,canSubmit:false,containsPatientData:false}));
}
