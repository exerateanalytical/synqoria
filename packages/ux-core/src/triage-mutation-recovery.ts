import {triageAction,triageCommandBody,type TriagePayload,type TriageCaseSummary,type TriageWriteResult,type TriageAuditRecord,TriageTransportError} from './triage-client';

/** A volatile idempotency recovery coordinator. No PHI is persisted, and recovery never guesses whether a write committed. */
export interface TriageRecoveryPort {
 summary(patientId:string,caseId:string,signal?:AbortSignal):Promise<TriageCaseSummary>;
 history(patientId:string,caseId:string,afterRevision?:number,signal?:AbortSignal):Promise<{history:readonly TriageAuditRecord[];nextAfterRevision:number|null}>;
 command(scope:{patientId:string;caseId:string;expectedRevision:number},commandId:string,action:string,payload:TriagePayload,signal?:AbortSignal):Promise<TriageWriteResult>;
}
export type RecoveryState='locked'|'ready'|'submitting'|'uncertain'|'reconciling'|'confirmed';
interface Pending {readonly patientId:string;readonly caseId:string;readonly commandId:string;readonly action:string;readonly payload:TriagePayload;readonly revision:number;}
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
export class TriageMutationRecovery {
 private state:RecoveryState='locked';private scope:{patientId:string;caseId:string}|null=null;
 private pending:Pending|null=null;private generation=0;private active:AbortController|null=null;
 private readonly port:TriageRecoveryPort;
 constructor(port:TriageRecoveryPort){this.port=port;}
 snapshot(){return Object.freeze({state:this.state,hasPending:this.pending!==null,revision:this.pending?.revision??null});}
 bind(patientId:string,caseId:string){this.clear();if(!uuid.test(patientId)||!uuid.test(caseId))throw new TriageTransportError('invalid',400);
  this.scope=Object.freeze({patientId,caseId});this.state='ready';}
 clear(){this.generation++;this.active?.abort();this.active=null;this.scope=null;this.pending=null;this.state='locked';}
 /** The caller must supply a server-authenticated current case; its identity and revision are checked before the first write. */
 async submit(current:TriageCaseSummary,commandId:string,action:string,payload:TriagePayload={}):Promise<TriageWriteResult>{
  if(this.state!=='ready'||!this.scope||!uuid.test(commandId)||current.caseId!==this.scope.caseId||current.revision<1)
   throw new TriageTransportError('unavailable',0);
  triageAction(action);triageCommandBody(action,{...this.scope,expectedRevision:current.revision},commandId,payload);
  this.pending=Object.freeze({patientId:this.scope.patientId,caseId:this.scope.caseId,commandId,action,payload:structuredClone(payload),revision:current.revision});
  return this.attempt();
 }
 private async attempt():Promise<TriageWriteResult>{
  if(!this.pending||!this.scope||!['ready','uncertain'].includes(this.state))throw new TriageTransportError('unavailable',0);
  const p=this.pending,gen=this.generation,controller=new AbortController();this.active=controller;this.state='submitting';
  try{
   const receipt=await this.port.command({patientId:p.patientId,caseId:p.caseId,expectedRevision:p.revision},p.commandId,p.action,p.payload,controller.signal);
   if(gen!==this.generation||controller.signal.aborted)throw new TriageTransportError('unavailable',0);
   if(receipt.revision!==p.revision+1)throw new TriageTransportError('unavailable',0);
   // A receipt is verified against persisted audit evidence before confirmation.
   this.state='uncertain';return await this.reconcileReceipt(receipt,gen);
  }catch(err){if(gen===this.generation){
   if(err instanceof TriageTransportError&&['reauthenticate','denied'].includes(err.category))this.clear();
   else this.state='uncertain';
  }throw err;}finally{if(this.active===controller)this.active=null;}
 }
 private async reconcileReceipt(receipt:TriageWriteResult,gen:number):Promise<TriageWriteResult>{
  const p=this.pending!;const ctrl=new AbortController();this.active=ctrl;
  const history=await this.port.history(p.patientId,p.caseId,p.revision,ctrl.signal);
  if(gen!==this.generation||ctrl.signal.aborted)throw new TriageTransportError('unavailable',0);
  const event=history.history.find(x=>x.revision===receipt.revision);
  if(!event||event.operation!==p.action||event.digest!==receipt.eventDigest){throw new TriageTransportError('unavailable',0);}
  const summary=await this.port.summary(p.patientId,p.caseId,ctrl.signal);
  if(gen!==this.generation||ctrl.signal.aborted||summary.caseId!==p.caseId||summary.revision<receipt.revision)throw new TriageTransportError('unavailable',0);
  this.state='confirmed';this.pending=null;return receipt;
 }
 /** Only explicit operator-driven recovery, never automatic blind resend after an uncertain network outcome. */
 async recover():Promise<'confirmed'|'retry-allowed'|'unresolved'>{
  if(this.state!=='uncertain'||!this.pending||!this.scope)throw new TriageTransportError('unavailable',0);
  const p=this.pending,gen=this.generation,ctrl=new AbortController();this.active=ctrl;this.state='reconciling';
  try{
   const summary=await this.port.summary(p.patientId,p.caseId,ctrl.signal);
   if(gen!==this.generation||ctrl.signal.aborted||summary.caseId!==p.caseId)throw new TriageTransportError('unavailable',0);
   // If the revision advanced, only an exactly matching audit operation at the expected revision can confirm it.
   if(summary.revision>p.revision){
    const page=await this.port.history(p.patientId,p.caseId,p.revision,ctrl.signal);
    if(gen!==this.generation||ctrl.signal.aborted)throw new TriageTransportError('unavailable',0);
    const event=page.history.find(x=>x.revision===p.revision+1);
    if(event&&event.operation===p.action){
     // Audit operation alone is NOT proof that this command ID committed. Require original receipt digest.
     this.state='uncertain';return 'unresolved';
    }
    this.state='uncertain';return 'unresolved';
   }
   if(summary.revision!==p.revision){this.state='uncertain';return 'unresolved';}
   this.state='uncertain';return 'retry-allowed';
  }catch(e){if(gen===this.generation){if(e instanceof TriageTransportError&&['denied','reauthenticate'].includes(e.category))this.clear();else this.state='uncertain';}throw e;}
  finally{if(this.active===ctrl)this.active=null;}
 }
 /** Reuse the same command ID only after an authorized reread proves the revision has not advanced. */
 async retry():Promise<TriageWriteResult>{
  if(await this.recover()!=='retry-allowed')throw new TriageTransportError('conflict',409);
  return this.attempt();
 }
 dispose(){this.clear();}
}
