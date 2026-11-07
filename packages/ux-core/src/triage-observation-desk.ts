import {type TriageObservationInput,type TriageWriteResult,TriageTransportError} from './triage-client';
import {TriageAuthorizedTasks} from './triage-authorized-tasks';
/** Typed evidence capture only; no clinical interpretation, scoring or default values. */
export class TriageObservationDesk {
 constructor(private readonly tasks:TriageAuthorizedTasks){}
 async record(input:TriageObservationInput):Promise<TriageWriteResult>{
  if(input.replaces!==undefined)throw new TriageTransportError('invalid',422);
  return this.tasks.perform('OBSERVATION_RECORD',{observation:{...input}});
 }
 async correct(input:TriageObservationInput,rationale:string):Promise<TriageWriteResult>{
  if(!input.replaces||typeof rationale!=='string'||!rationale.trim())throw new TriageTransportError('invalid',422);
  return this.tasks.perform('OBSERVATION_CORRECTION',{observation:{...input},rationale});
 }
 async declined(reason:string):Promise<TriageWriteResult>{
  if(!reason.trim())throw new TriageTransportError('invalid',422);
  return this.tasks.perform('MEASUREMENT_DECLINED',{text:reason});
 }
 async deviceSource(text:string):Promise<TriageWriteResult>{
  if(!text.trim())throw new TriageTransportError('invalid',422);
  return this.tasks.perform('ASSESSMENT_DEVICE_SOURCE',{text});
 }
}
