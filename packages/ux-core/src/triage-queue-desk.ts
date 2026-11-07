import {type TriageWriteResult,TriageTransportError} from './triage-client';
import {TriageAuthorizedTasks} from './triage-authorized-tasks';
/** Facility queue operations must be explicit and server-confirmed. Never derive placement from UI order. */
export class TriageQueueDesk {
 constructor(private readonly tasks:TriageAuthorizedTasks){}
 enqueue(stationId:string):Promise<TriageWriteResult>{return this.tasks.perform('QUEUE_ENQUEUE',{reference:stationId});}
 move(stationId:string):Promise<TriageWriteResult>{return this.tasks.perform('QUEUE_MOVE_STATION',{reference:stationId});}
 call():Promise<TriageWriteResult>{return this.tasks.perform('QUEUE_CALL');}
 recall():Promise<TriageWriteResult>{return this.tasks.perform('QUEUE_RECALL');}
 returnToQueue():Promise<TriageWriteResult>{return this.tasks.perform('QUEUE_RETURN');}
 pause(rationale:string):Promise<TriageWriteResult>{if(!rationale.trim())throw new TriageTransportError('invalid',422);
  return this.tasks.perform('QUEUE_PAUSE',{rationale});}
 resume():Promise<TriageWriteResult>{return this.tasks.perform('QUEUE_RESUME');}
 unavailable(rationale:string):Promise<TriageWriteResult>{if(!rationale.trim())throw new TriageTransportError('invalid',422);
  return this.tasks.perform('QUEUE_PATIENT_UNAVAILABLE',{rationale});}
}
