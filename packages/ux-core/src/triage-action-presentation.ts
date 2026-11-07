import {triageActions,type TriageAction,type TriageCapability} from './triage-client';
/** UI-neutral presentation of the authoritative backend command catalog. No clinical default recommendations. */
export interface TriageActionPresentation {readonly action:string;readonly capability:TriageCapability;readonly input:TriageAction['kind'];
 readonly label:string;readonly confirmationRequired:boolean;readonly accessibleHint:string;}
const translations:Record<string,[string,string]>={
 ARRIVAL_REASON:['Arrival reason','Motif de l’arrivée'],LANGUAGE:['Preferred language','Langue préférée'],
 INTERPRETER_REQUEST:['Request interpreter','Demander un interprète'],ACCESSIBILITY_SUPPORT:['Accessibility support','Assistance à l’accessibilité'],
 IDENTITY_VERIFICATION:['Document identity check','Documenter la vérification d’identité'],ARRIVAL_CHANNEL:['Arrival channel','Mode d’arrivée'],
 REFERRAL_REFERENCE:['Referral reference','Référence du transfert'],ATTENDANT_CONTACT:['Attendant contact','Coordonnées de l’accompagnant'],
 CONSENT_EVIDENCE:['Record consent evidence','Documenter la preuve du consentement'],
 ASSESSMENT_NARRATIVE:['Assessment narrative','Récit de l’évaluation'],ASSESSMENT_DEVICE_SOURCE:['Measurement device source','Source du dispositif de mesure'],
 PATIENT_STATEMENT:['Patient statement','Déclaration du patient'],ALLERGY_REVIEW_REFERENCE:['Allergy review reference','Référence de la vérification des allergies'],
 MEASUREMENT_DECLINED:['Measurement declined','Mesure refusée'],REASSESSMENT_DOCUMENTED:['Document reassessment','Documenter la réévaluation'],
 PRIORITY_EVIDENCE_LINK:['Link priority evidence','Associer une preuve de priorité'],HANDOVER_NOTE:['Handover note','Note de transmission'],
 HANDOVER_DESTINATION:['Handover destination','Destination de transmission'],
 QUEUE_CALL:['Call patient','Appeler le patient'],QUEUE_RECALL:['Recall patient','Rappeler le patient'],QUEUE_RETURN:['Return to queue','Retour à la file'],
 QUEUE_PAUSE:['Pause queue','Suspendre la file'],QUEUE_RESUME:['Resume queue','Reprendre la file'],QUEUE_ENQUEUE:['Add to queue','Ajouter à la file'],
 QUEUE_MOVE_STATION:['Move station','Changer de poste'],QUEUE_PATIENT_UNAVAILABLE:['Mark unavailable','Signaler absent'],
 SAFETY_HOLD:['Place safety hold','Activer la mesure de sécurité'],SAFETY_HOLD_RELEASE:['Release safety hold','Lever la mesure de sécurité'],
 ASSESSMENT_BEGIN:['Begin assessment','Commencer l’évaluation'],ASSESSMENT_ATTEST:['Sign assessment','Signer l’évaluation'],
 ASSESSMENT_ADDENDUM:['Add signed addendum','Ajouter un addenda signé'],OBSERVATION_RECORD:['Record observation','Saisir une observation'],
 OBSERVATION_CORRECTION:['Correct observation','Corriger une observation'],
 CLINICIAN_PRIORITY_SET:['Document clinician priority','Documenter la priorité clinique'],CLINICIAN_PRIORITY_REVISED:['Revise clinician priority','Réviser la priorité clinique'],
 ESCALATION_RAISED:['Raise escalation','Signaler une escalade'],ESCALATION_ACKNOWLEDGED:['Acknowledge escalation','Accuser réception de l’escalade'],
 ESCALATION_RESOLVED:['Resolve escalation','Résoudre l’escalade'],REASSESSMENT_REQUESTED:['Request reassessment','Demander une réévaluation'],
 REASSESSMENT_STARTED:['Start reassessment','Commencer la réévaluation'],REASSESSMENT_COMPLETED:['Complete reassessment','Terminer la réévaluation'],
 CLINICIAN_ASSIGNMENT:['Assign clinician','Affecter un clinicien'],CLINICIAN_REASSIGNMENT:['Reassign clinician','Réaffecter un clinicien'],
 HANDOVER_REQUEST:['Request handover','Demander le transfert'],HANDOVER_FINALIZED:['Finalize handover','Finaliser le transfert'],
 HANDOVER_ACCEPT:['Accept handover','Accepter le transfert'],HANDOVER_REJECT:['Reject handover','Refuser le transfert'],
 RECEIVER_ACKNOWLEDGEMENT:['Acknowledge receipt','Accuser réception'],TRIAGE_CLOSED:['Close triage case','Clôturer le triage']};
const mustConfirm=new Set(['SAFETY_HOLD_RELEASE','ASSESSMENT_ATTEST','HANDOVER_FINALIZED','TRIAGE_CLOSED']);
export function triageActionPresentation(grants:readonly TriageCapability[],locale:'en'|'fr',verified:boolean):readonly TriageActionPresentation[]{
 if(!verified)return Object.freeze([]);
 const descriptions=triageActions.filter(a=>grants.includes(a.capability)).map(a=>{
  const words=a.action.toLowerCase().split('_').join(' ');
  const english=words[0]?.toUpperCase()+words.slice(1);
  const text=translations[a.action]??[english,english];
  return Object.freeze({action:a.action,capability:a.capability,input:a.kind,label:text[locale==='fr'?1:0],
   confirmationRequired:mustConfirm.has(a.action),
   accessibleHint:locale==='fr'?'Vérification de session et confirmation serveur requises':'Verified session and server confirmation required'});
 });
 return Object.freeze(descriptions);
}
