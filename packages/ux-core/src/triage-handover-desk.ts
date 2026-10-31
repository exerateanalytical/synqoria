import {type TriageWriteResult,TriageTransportError} from './triage-client';
import {TriageAuthorizedTasks} from './triage-authorized-tasks';
/** Human clinician decisions; each write is persisted and reread independently by the coordinator. */
export class TriageHandoverDesk {
 constructor(private readonly tasks:TriageAuthorizedTasks){}
 assign(clinicianId:string):Promise<TriageWriteResult>{return this.tasks.perform('CLINICIAN_ASSIGNMENT',{reference:clinicianId});}
 reassign(clinicianId:string,rationale:string):Promise<TriageWriteResult>{
  if(!rationale.trim())throw new TriageTransportError('invalid',422);
  return this.tasks.perform('CLINICIAN_REASSIGNMENT',{reference:clinicianId,rationale});}
 request():Promise<TriageWriteResult>{return this.tasks.perform('HANDOVER_REQUEST');}
 note(text:string):Promise<TriageWriteResult>{if(!text.trim())throw new TriageTransportError('invalid',422);
  return this.tasks.perform('HANDOVER_NOTE',{text});}
 destination(text:string):Promise<TriageWriteResult>{if(!text.trim())throw new TriageTransportError('invalid',422);
  return this.tasks.perform('HANDOVER_DESTINATION',{text});}
 receive():Promise<TriageWriteResult>{return this.tasks.perform('HANDOVER_ACCEPT');}
 reject(reason:string):Promise<TriageWriteResult>{if(!reason.trim())throw new TriageTransportError('invalid',422);
  return this.tasks.perform('HANDOVER_REJECT',{rationale:reason});}
 acknowledge():Promise<TriageWriteResult>{return this.tasks.perform('RECEIVER_ACKNOWLEDGEMENT');}
 finalize():Promise<TriageWriteResult>{return this.tasks.perform('HANDOVER_FINALIZED');}
}
