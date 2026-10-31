/** P02-B0037. Disclose only the existence of a review requirement, never candidate identities or counts. */
export type DuplicateReviewState='NOT_CHECKED'|'REVIEW_REQUIRED'|'REVIEW_PENDING'|'RESOLVED_SERVER_SIDE'|'UNKNOWN';
export function duplicateStatus(state:DuplicateReviewState,locale:'en'|'fr'):{status:string;instruction:string;action:'WAIT'|'REQUEST_REVIEW';canSubmit:false}{
 const en:Record<DuplicateReviewState,[string,string,'WAIT'|'REQUEST_REVIEW']>={
  NOT_CHECKED:['Check not completed','Ask an authorized reviewer to check the record','REQUEST_REVIEW'],
  REVIEW_REQUIRED:['Review required','An independent identity review is required','REQUEST_REVIEW'],
  REVIEW_PENDING:['Review pending','Await the authorized reviewer decision','WAIT'],
  RESOLVED_SERVER_SIDE:['Review recorded','Hospital-side checks are still required','WAIT'],
  UNKNOWN:['Review outcome unknown','Do not retry a patient registration with a new command','WAIT']};
 const fr:Record<DuplicateReviewState,[string,string,'WAIT'|'REQUEST_REVIEW']>={
  NOT_CHECKED:['Vérification non effectuée','Demandez un contrôle à une personne habilitée','REQUEST_REVIEW'],
  REVIEW_REQUIRED:['Révision requise','Une vérification indépendante est obligatoire','REQUEST_REVIEW'],
  REVIEW_PENDING:['Révision en attente','Attendez la décision de la personne habilitée','WAIT'],
  RESOLVED_SERVER_SIDE:['Révision enregistrée','Les contrôles de l’hôpital restent obligatoires','WAIT'],
  UNKNOWN:['Résultat inconnu','Ne relancez pas une inscription avec une nouvelle commande','WAIT']};
 const [status,instruction,action]=(locale==='fr'?fr:en)[state];return {status,instruction,action,canSubmit:false};
}
