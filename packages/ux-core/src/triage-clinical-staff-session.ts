import {TriageStaffWorkflow} from './triage-staff-workflow';
import {TriageAuthorizedTasks,type TriagePermissionLease,type TriageTaskRuntimePort} from './triage-authorized-tasks';
import {type TriageClinicalTransport,type TriageView,type TriagePayload,type TriageWriteResult,TriageTransportError} from './triage-client';
/** One volatile staff M03 session that joins scoped worklist, selected case, permissions and authoritative writes. */
export class TriageClinicalStaffSession {
 readonly workflow:TriageStaffWorkflow;
 readonly tasks:TriageAuthorizedTasks;
 private lease:TriagePermissionLease|null=null;
 private disposed=false;
 constructor(origin:string,transport:TriageClinicalTransport,runtime:TriageTaskRuntimePort){
  this.workflow=new TriageStaffWorkflow(origin,transport);
  this.tasks=new TriageAuthorizedTasks(this.workflow,runtime);
 }
 bind(lease:TriagePermissionLease){if(this.disposed)throw new TriageTransportError('unavailable',0);
  this.tasks.bind(lease);this.lease=lease;}
 async worklist(patientId:string,view:TriageView,caseIds:readonly string[],filter?:{stationOrClinician?:string;after?:string}){
  if(!this.lease)throw new TriageTransportError('reauthenticate',401);
  return this.workflow.load(patientId,view,caseIds,filter);
 }
 async select(caseId:string){if(!this.lease)throw new TriageTransportError('reauthenticate',401);
  return this.workflow.select(caseId);}
 async perform(action:string,payload:TriagePayload={}):Promise<TriageWriteResult>{
  if(!this.lease)throw new TriageTransportError('reauthenticate',401);
  return this.tasks.perform(action,payload);
 }
 async audit(afterRevision=0){if(!this.lease)throw new TriageTransportError('reauthenticate',401);
  return this.workflow.caseSession.history(afterRevision);}
 snapshot(){return Object.freeze({state:this.workflow.snapshot(),bound:this.lease!==null});}
 onOfflineOrBackground(){this.lease=null;this.tasks.clear();}
 onFacilityShiftOrActorChanged(){this.onOfflineOrBackground();}
 dispose(){this.onOfflineOrBackground();this.workflow.dispose();this.disposed=true;}
}
