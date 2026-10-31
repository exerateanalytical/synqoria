import {triageClient,type TriageClinicalTransport,type TriageCaseSummary,TriageTransportError} from './triage-client';
import {type TriageStaffScope} from './triage-staff-workflow';

/** Intake is an irreversible patient-linked write. A lost acknowledgement is NOT permission to create a new command ID. */
export type ArrivalStatus='locked'|'ready'|'submitting'|'uncertain'|'confirmed';
export interface ArrivalSnapshot {readonly status:ArrivalStatus;readonly patientId:string|null;
 readonly caseId:string|null;readonly confirmedCase:TriageCaseSummary|null;}
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
interface Attempt {readonly patientId:string;readonly caseId:string;readonly commandId:string;readonly appointment?:string;}
export class TriageArrivalSession {
 private status:ArrivalStatus='locked';private staff:TriageStaffScope|null=null;
 private attempt:Attempt|null=null;private confirmed:TriageCaseSummary|null=null;
 private generation=0;private request:AbortController|null=null;
 private readonly client:ReturnType<typeof triageClient>;
 constructor(origin:string,transport:TriageClinicalTransport){this.client=triageClient(origin,transport);}
 snapshot():ArrivalSnapshot{return Object.freeze({status:this.status,patientId:this.attempt?.patientId??null,
  caseId:this.attempt?.caseId??null,confirmedCase:this.confirmed});}
 clear(){this.generation++;this.request?.abort();this.request=null;this.staff=null;this.attempt=null;this.confirmed=null;this.status='locked';}
 bind(scope:TriageStaffScope){
  this.clear();
  if(!scope||[scope.tenantId,scope.facilityId,scope.actorId,scope.shiftId].some(x=>!uuid.test(x))||!scope.epoch?.trim())throw new TriageTransportError('unavailable',0);
  this.staff=Object.freeze({...scope});this.status='ready';
 }
 async start(patientId:string,caseId:string,commandId:string,appointment?:string):Promise<TriageCaseSummary>{
  if(this.status!=='ready'||!this.staff||![patientId,caseId,commandId,...(appointment?[appointment]:[])].every(x=>uuid.test(x)))
   throw new TriageTransportError('invalid',400);
  const attempt=Object.freeze({patientId,caseId,commandId,...(appointment?{appointment}:{})});
  this.attempt=attempt;return this.execute();
 }
 /** Explicit operator retry after an uncertain result; must replay the original operation identifier. */
 async retry():Promise<TriageCaseSummary>{
  if(this.status!=='uncertain'||!this.staff||!this.attempt)throw new TriageTransportError('unavailable',0);
  return this.execute();
 }
 private async execute():Promise<TriageCaseSummary>{
  if(!this.attempt||!this.staff||!['ready','uncertain'].includes(this.status))throw new TriageTransportError('unavailable',0);
  const attempt=this.attempt,version=this.generation;
  this.status='submitting';const request=new AbortController();this.request=request;
  try{
   const result=await this.client.intake(attempt.patientId,attempt.caseId,attempt.commandId,attempt.appointment,request.signal);
   if(version!==this.generation||request.signal.aborted)throw new TriageTransportError('unavailable',0);
   if(result.revision!==1||!result.eventDigest)throw new TriageTransportError('unavailable',0);
   const caseView=await this.client.summary(attempt.patientId,attempt.caseId,request.signal);
   if(version!==this.generation||request.signal.aborted)throw new TriageTransportError('unavailable',0);
   // A later revision may be legitimate; a revision below the confirmed intake is not.
   if(caseView.revision<result.revision||caseView.caseId!==attempt.caseId)throw new TriageTransportError('unavailable',0);
   this.confirmed=Object.freeze({...caseView});this.status='confirmed';return this.confirmed;
  }catch(error){
   if(version===this.generation){
    if(error instanceof TriageTransportError&&['reauthenticate','denied'].includes(error.category))this.clear();
    else{this.confirmed=null;this.status='uncertain';}
   }
   throw error;
  }finally{if(this.request===request)this.request=null;}
 }
 dispose(){this.clear();}
}
