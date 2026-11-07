import {triageActions,type TriageAction,type TriageCapability,type TriageCaseSummary} from './triage-client';
/** Authorization-aware presentation model. Absence of a verified session always returns a locked, PHI-free view. */
export type WorkspaceLocale='en'|'fr';
export interface TriageWorkspaceModel {readonly status:'locked'|'ready'|'on-hold'|'paused'|'closed';
 readonly title:string;readonly stageLabel:string;readonly actions:readonly TriageAction[];
 readonly alert:string|null;readonly revision:number|null;}
const stages:Record<string,[string,string]>={ARRIVED:['Arrived','Arrivé'],WAITING:['Waiting','En attente'],CALLED:['Called','Appelé'],
 ASSESSING:['Assessment in progress','Évaluation en cours'],ASSESSED:['Assessment documented','Évaluation documentée'],
 PRIORITIZED:['Clinician priority documented','Priorité du clinicien documentée'],ASSIGNED:['Assigned','Affecté'],
 HANDED_OVER:['Handed over','Transmis'],CLOSED:['Closed','Clôturé']};
export function triageWorkspaceModel(summary:TriageCaseSummary|null,scopeVerified:boolean,
 grants:readonly TriageCapability[],locale:WorkspaceLocale='en'):TriageWorkspaceModel{
 const fr=locale==='fr';
 if(!scopeVerified||!summary||!Number.isInteger(summary.revision)||summary.revision<1||!stages[summary.stage])return Object.freeze({
  status:'locked',title:fr?'Triage indisponible':'Triage unavailable',stageLabel:'',actions:[],
  alert:fr?'Session vérifiée requise':'Verified session required',revision:null});
 const status=summary.stage==='CLOSED'?'closed':summary.safetyHold?'on-hold':summary.paused?'paused':'ready';
 const actions=triageActions.filter(action=>grants.includes(action.capability)&&
  (status!=='closed'||action.action==='ASSESSMENT_ADDENDUM')&&
  (action.action!=='SAFETY_HOLD_RELEASE'||summary.safetyHold)&&
  (action.action!=='SAFETY_HOLD'||!summary.safetyHold)&&
  (action.action!=='QUEUE_PAUSE'||!summary.paused)&&
  (action.action!=='QUEUE_RESUME'||summary.paused));
 const alert=status==='on-hold'?(fr?'Mesure de sécurité active':'Safety hold active'):
  status==='paused'?(fr?'File suspendue':'Queue paused'):
  status==='closed'?(fr?'Dossier fermé':'Case closed'):null;
 return Object.freeze({status,title:fr?'Dossier de triage':'Triage case',stageLabel:stages[summary.stage]![fr?1:0],
  actions:Object.freeze(actions),alert,revision:summary.revision});
}
