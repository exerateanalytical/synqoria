import {triageAction,triageClient,type TriageClinicalTransport,type TriageCapability,type TriagePayload,type TriageWriteResult,TriageTransportError} from './triage-client';
import {TriageMutationRecovery} from './triage-mutation-recovery';
/** Integrates actual restricted HTTP transport with audit-confirmed, bounded-scope command recovery. */
export class TriageSecuredCommandJourney {
 private readonly client:ReturnType<typeof triageClient>;
 private readonly recovery:TriageMutationRecovery;
 private scope:{patientId:string;caseId:string;epoch:string;grants:readonly TriageCapability[]}|null=null;
 private generation=0;
 constructor(origin:string,transport:TriageClinicalTransport){this.client=triageClient(origin,transport);this.recovery=new TriageMutationRecovery(this.client);}
 bind(patientId:string,caseId:string,epoch:string,grants:readonly TriageCapability[]){
  this.clear();if(!epoch||epoch.length>128)throw new TriageTransportError('reauthenticate',401);
  this.recovery.bind(patientId,caseId);this.scope=Object.freeze({patientId,caseId,epoch,grants:Object.freeze([...grants])});
 }
 clear(){this.generation++;this.recovery.clear();this.scope=null;}
 async submit(commandId:string,action:string,payload:TriagePayload={}):Promise<TriageWriteResult>{
  const scope=this.scope,generation=this.generation;
  if(!scope)throw new TriageTransportError('reauthenticate',401);
  if(!scope.grants.includes(triageAction(action).capability))throw new TriageTransportError('denied',403);
  const summary=await this.client.summary(scope.patientId,scope.caseId);
  if(!this.scope||this.scope!==scope||this.generation!==generation)throw new TriageTransportError('unavailable',0);
  const receipt=await this.recovery.submit(summary,commandId,action,payload);
  if(!this.scope||this.scope!==scope||this.generation!==generation)throw new TriageTransportError('unavailable',0);
  return receipt;
 }
 async recover():Promise<'confirmed'|'retry-allowed'|'unresolved'>{if(!this.scope)throw new TriageTransportError('reauthenticate',401);
  return this.recovery.recover();}
 async retry():Promise<TriageWriteResult>{if(!this.scope)throw new TriageTransportError('reauthenticate',401);
  return this.recovery.retry();}
 snapshot(){return Object.freeze({bound:this.scope!==null,operation:this.recovery.snapshot().state});}
 dispose(){this.clear();}
}
