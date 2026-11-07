import {type TriageWriteResult,TriageTransportError} from './triage-client';
import {TriageAuthorizedTasks} from './triage-authorized-tasks';
/** Record clinician-specified escalation/reassessment events only; no AI-originated urgency rules. */
export class TriageEscalationDesk {
 constructor(private readonly tasks:TriageAuthorizedTasks){}
 raise(rationale:string):Promise<TriageWriteResult>{if(!rationale.trim())throw new TriageTransportError('invalid',422);
  return this.tasks.perform('ESCALATION_RAISED',{rationale});}
 acknowledge():Promise<TriageWriteResult>{return this.tasks.perform('ESCALATION_ACKNOWLEDGED');}
 resolve(rationale:string):Promise<TriageWriteResult>{if(!rationale.trim())throw new TriageTransportError('invalid',422);
  return this.tasks.perform('ESCALATION_RESOLVED',{rationale});}
 requestReassessment():Promise<TriageWriteResult>{return this.tasks.perform('REASSESSMENT_REQUESTED');}
 startReassessment():Promise<TriageWriteResult>{return this.tasks.perform('REASSESSMENT_STARTED');}
 documentReassessment(text:string):Promise<TriageWriteResult>{if(!text.trim())throw new TriageTransportError('invalid',422);
  return this.tasks.perform('REASSESSMENT_DOCUMENTED',{text});}
 completeReassessment():Promise<TriageWriteResult>{return this.tasks.perform('REASSESSMENT_COMPLETED');}
}
