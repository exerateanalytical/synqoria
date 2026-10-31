import {TriageStaffWorkflow,type TriageStaffScope} from './triage-staff-workflow';
import {triageAction,triageCommandBody,type TriagePayload,type TriageCapability,type TriageWriteResult,TriageTransportError} from './triage-client';

/** Authorization mirrored in the client solely to prevent UI accidents; backend grants remain authoritative. */
export interface TriagePermissionLease {readonly scope:TriageStaffScope;readonly grants:readonly TriageCapability[];
 readonly issuedAtMs:number;readonly expiresAtMs:number;}
export interface TriageTaskRuntimePort {readonly isOnline:()=>boolean;readonly newCommandId:()=>string;readonly now:()=>number;}
export class TriageAuthorizedTasks {
 private lease:TriagePermissionLease|null=null;
 private generation=0;private busy=false;
 constructor(private readonly workflow:TriageStaffWorkflow,private readonly runtime:TriageTaskRuntimePort){}
 bind(lease:TriagePermissionLease){this.clear();
  const {scope,grants,issuedAtMs,expiresAtMs}=lease;
  if(!scope||!Array.isArray(grants)||!Number.isSafeInteger(issuedAtMs)||!Number.isSafeInteger(expiresAtMs)||
    issuedAtMs>this.runtime.now()||expiresAtMs<=this.runtime.now()||expiresAtMs-issuedAtMs>12*60*60*1000||
    !grants.every(x=>typeof x==='string'&&(['triage:intake','triage:queue','triage:manage','triage:assess','triage:sign','triage:prioritize','triage:handover','triage:receive'] as string[]).includes(x)))
    throw new TriageTransportError('reauthenticate',401);
  this.workflow.bind(scope);this.lease=Object.freeze({...lease,grants:Object.freeze([...new Set(grants)])});
 }
 clear(){this.generation++;this.lease=null;this.busy=false;this.workflow.reset();}
 allowed(action:string):boolean{
  if(!this.lease||this.runtime.now()>=this.lease.expiresAtMs||!this.runtime.isOnline()||this.busy)return false;
  const cap=triageAction(action).capability;return this.lease.grants.includes(cap)&&this.workflow.snapshot().mode==='case';
 }
 /** Always await the server authoritative reread done by the workflow. No optimistic presentation. */
 async perform(action:string,payload:TriagePayload={}):Promise<TriageWriteResult>{
  if(!this.allowed(action))throw new TriageTransportError('denied',403);
  const selected=this.workflow.snapshot().selectedCase;
  if(!selected||!this.lease)throw new TriageTransportError('unavailable',0);
  const id=this.runtime.newCommandId();
  triageCommandBody(action,{patientId:this.workflow.snapshot().patientId!,caseId:selected.caseId,expectedRevision:selected.revision},id,payload);
  const epoch=this.generation,lease=this.lease;
  this.busy=true;
  try{
   const receipt=await this.workflow.command(id,action,payload);
   if(epoch!==this.generation||this.lease!==lease||this.runtime.now()>=lease.expiresAtMs||!this.runtime.isOnline())throw new TriageTransportError('unavailable',0);
   const authoritative=this.workflow.snapshot().selectedCase;
   if(!authoritative||authoritative.caseId!==selected.caseId||authoritative.revision<receipt.revision)
     throw new TriageTransportError('unavailable',0);
   return receipt;
  }catch(e){if(e instanceof TriageTransportError&&['denied','reauthenticate'].includes(e.category)&&epoch===this.generation)this.clear();throw e;}
  finally{if(epoch===this.generation)this.busy=false;}
 }
 dispose(){this.clear();}
}
