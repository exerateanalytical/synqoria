import {type TriageWriteResult,TriageTransportError} from './triage-client';
import {TriageAuthorizedTasks} from './triage-authorized-tasks';
/** Priority is entered by a qualified clinician; this class never computes or suggests a triage priority. */
export class TriagePriorityDesk {
 constructor(private readonly tasks:TriageAuthorizedTasks){}
 setClinicianPriority(documentedPriority:string,rationale:string):Promise<TriageWriteResult>{
  if(!documentedPriority.trim()||!rationale.trim())throw new TriageTransportError('invalid',422);
  return this.tasks.perform('CLINICIAN_PRIORITY_SET',{text:documentedPriority,rationale});
 }
 reviseClinicianPriority(documentedPriority:string,rationale:string):Promise<TriageWriteResult>{
  if(!documentedPriority.trim()||!rationale.trim())throw new TriageTransportError('invalid',422);
  return this.tasks.perform('CLINICIAN_PRIORITY_REVISED',{text:documentedPriority,rationale});
 }
 linkClinicalEvidence(note:string):Promise<TriageWriteResult>{
  if(!note.trim())throw new TriageTransportError('invalid',422);
  return this.tasks.perform('PRIORITY_EVIDENCE_LINK',{text:note});
 }
}
