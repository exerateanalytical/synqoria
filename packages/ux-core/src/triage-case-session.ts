import {triageClient, type TriageClinicalTransport, type TriageCaseSummary,
 type TriagePayload, type TriageWriteResult, type TriageAuditPage, TriageTransportError} from './triage-client';

/** Volatile patient/case context for M03. No storage and no implicit session authorization. */
export type CaseSessionStatus='closed'|'loading'|'ready'|'mutating'|'refresh-required';
export interface CaseSessionView {readonly status:CaseSessionStatus;readonly patientId:string|null;readonly case:TriageCaseSummary|null;}
export type CaseSessionObserver=(snapshot:CaseSessionView)=>void;
export class TriageCaseSession {
 private patientId:string|null=null;private caseId:string|null=null;
 private current:TriageCaseSummary|null=null;
 private status:CaseSessionStatus='closed';
 private generation=0;
 private active:AbortController|null=null;
 private mutation=false;
 private readonly observers=new Set<CaseSessionObserver>();
 constructor(private readonly client:ReturnType<typeof triageClient>){}
 subscribe(observer:CaseSessionObserver):()=>void{
  this.observers.add(observer);observer(this.snapshot());return ()=>this.observers.delete(observer);
 }
 snapshot():CaseSessionView{return Object.freeze({status:this.status,patientId:this.patientId,case:this.current});}
 private emit(){const s=this.snapshot();for(const observer of this.observers)observer(s);}
 /** Called on patient/facility/session switch or app backgrounding. Cancels in-flight reads and writes. */
 close(){this.generation++;this.active?.abort();this.active=null;this.patientId=null;this.caseId=null;this.current=null;this.status='closed';this.mutation=false;this.emit();}
 async open(patientId:string,caseId:string):Promise<TriageCaseSummary>{
  this.close();const generation=this.generation;this.patientId=patientId;this.caseId=caseId;this.status='loading';this.emit();
  const controller=new AbortController();this.active=controller;
  try{
   const summary=await this.client.summary(patientId,caseId,controller.signal);
   if(this.generation!==generation||controller.signal.aborted)throw new TriageTransportError('unavailable',0);
   this.current=Object.freeze({...summary});this.status='ready';this.emit();return summary;
  }catch(error){if(this.generation===generation)this.close();throw error;}
  finally{if(this.active===controller)this.active=null;}
 }
 async refresh():Promise<TriageCaseSummary>{
  if(!this.caseId||!this.patientId||this.mutation||!['ready','refresh-required'].includes(this.status))throw new TriageTransportError('unavailable',0);
  const generation=this.generation,patient=this.patientId,caseId=this.caseId,previousRevision=this.current?.revision??0;
  const controller=new AbortController();this.active?.abort();this.active=controller;
  this.status='loading';this.emit();
  try{
   const summary=await this.client.summary(patient,caseId,controller.signal);
   if(generation!==this.generation||controller.signal.aborted)throw new TriageTransportError('unavailable',0);
   if(summary.revision<previousRevision)throw new TriageTransportError('unavailable',0);
   this.current=Object.freeze({...summary});this.status='ready';this.emit();return summary;
  }catch(error){if(this.generation===generation){this.current=null;this.status='refresh-required';this.emit();}throw error;}
  finally{if(this.active===controller)this.active=null;}
 }
 async command(commandId:string,action:string,payload:TriagePayload={}):Promise<TriageWriteResult>{
  if(!this.patientId||!this.current||this.status!=='ready'||this.mutation)throw new TriageTransportError('unavailable',0);
  const patient=this.patientId,current=this.current,generation=this.generation;
  this.mutation=true;this.status='mutating';this.emit();
  const controller=new AbortController();this.active=controller;
  try{
   const answer=await this.client.command({patientId:patient,caseId:current.caseId,expectedRevision:current.revision},commandId,action,payload,controller.signal);
   if(this.generation!==generation||controller.signal.aborted)throw new TriageTransportError('unavailable',0);
   if(answer.revision!==current.revision+1)throw new TriageTransportError('unavailable',0);
   // Only a new server-authoritative summary may provide stage, assignment or status.
   this.current=null;this.status='refresh-required';this.emit();return answer;
  }catch(error){
   if(this.generation===generation){
    if(error instanceof TriageTransportError&&['reauthenticate','denied'].includes(error.category))this.close();
    else {this.current=null;this.status='refresh-required';this.emit();}
   }
   throw error;
  }finally{if(this.generation===generation){this.mutation=false;if(this.active===controller)this.active=null;}}
 }
 async history(afterRevision=0):Promise<TriageAuditPage>{
  if(!this.patientId||!this.current||this.status!=='ready'||this.mutation)throw new TriageTransportError('unavailable',0);
  const generation=this.generation,patient=this.patientId,caseId=this.current.caseId;
  const controller=new AbortController();this.active?.abort();this.active=controller;
  try{
   const page=await this.client.history(patient,caseId,afterRevision,controller.signal);
   if(generation!==this.generation||controller.signal.aborted)throw new TriageTransportError('unavailable',0);
   return page;
  }catch(error){if(this.generation===generation && error instanceof TriageTransportError&&['denied','reauthenticate'].includes(error.category))this.close();throw error;}
  finally{if(this.active===controller)this.active=null;}
 }
}
/** Injectable trusted transport: host must establish real identity; a token string alone is never an authorization decision. */
export function makeTriageCaseSession(origin:string,transport:TriageClinicalTransport){return new TriageCaseSession(triageClient(origin,transport));}
