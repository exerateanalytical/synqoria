/** P02-B0022: no offline authorization for clinical effects. Local draft is distinct from server save. */
export type ActionResult='local-draft-only'|'online-verification-required'|'blocked';
export function allowedOfflineAction(action:'draft-note'|'sign-note'|'prescribe'|'administer-medication'|'view-cached-summary',
   opts:{enrolled:boolean; sessionValid:boolean; approvedOfflineDraft:boolean; patientGrantCurrent:boolean}):ActionResult {
  if(!opts.enrolled||!opts.sessionValid||!opts.patientGrantCurrent)return 'blocked';
  if(action==='draft-note'&&opts.approvedOfflineDraft)return 'local-draft-only';
  return 'online-verification-required';
}
