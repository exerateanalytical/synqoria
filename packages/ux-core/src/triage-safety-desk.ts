import {type TriageWriteResult,TriageTransportError} from './triage-client';
import {TriageAuthorizedTasks} from './triage-authorized-tasks';
/** Safety holds require explicit human rationale; release remains subject to backend clinical authorization. */
export class TriageSafetyDesk {
 constructor(private readonly tasks:TriageAuthorizedTasks){}
 hold(rationale:string):Promise<TriageWriteResult>{if(!rationale.trim())throw new TriageTransportError('invalid',422);
  return this.tasks.perform('SAFETY_HOLD',{rationale});}
 release(rationale:string):Promise<TriageWriteResult>{if(!rationale.trim())throw new TriageTransportError('invalid',422);
  return this.tasks.perform('SAFETY_HOLD_RELEASE',{rationale});}
 startAssessment():Promise<TriageWriteResult>{return this.tasks.perform('ASSESSMENT_BEGIN');}
 narrative(text:string):Promise<TriageWriteResult>{if(!text.trim())throw new TriageTransportError('invalid',422);
  return this.tasks.perform('ASSESSMENT_NARRATIVE',{text});}
 attest():Promise<TriageWriteResult>{return this.tasks.perform('ASSESSMENT_ATTEST');}
 addendum(text:string):Promise<TriageWriteResult>{if(!text.trim())throw new TriageTransportError('invalid',422);
  return this.tasks.perform('ASSESSMENT_ADDENDUM',{text});}
}
