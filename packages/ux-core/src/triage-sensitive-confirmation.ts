import {TriageAuthorizedTasks} from './triage-authorized-tasks';
import {type TriagePayload,type TriageWriteResult,TriageTransportError} from './triage-client';
/** One-use human confirmation for high-impact UI flows. Server-side authorization remains authoritative. */
export type SensitiveTriageAction='SAFETY_HOLD_RELEASE'|'ASSESSMENT_ATTEST'|'HANDOVER_FINALIZED'|'TRIAGE_CLOSED';
const sensitive=new Set<string>(['SAFETY_HOLD_RELEASE','ASSESSMENT_ATTEST','HANDOVER_FINALIZED','TRIAGE_CLOSED']);
export interface TriageConfirmIntent {readonly action:SensitiveTriageAction;readonly caseId:string;readonly revision:number;readonly expiresAtMs:number;}
export class TriageSensitiveConfirmation {
 private intent:TriageConfirmIntent|null=null;
 private busy=false;
 constructor(private readonly tasks:TriageAuthorizedTasks,private readonly context:()=>{caseId:string|null;revision:number|null},private readonly now:()=>number){}
 request(action:SensitiveTriageAction):TriageConfirmIntent{
  const {caseId,revision}=this.context();
  if(!sensitive.has(action)||!caseId||!Number.isInteger(revision)||revision===null||revision<1||!this.tasks.allowed(action))
   throw new TriageTransportError('denied',403);
  this.intent=Object.freeze({action,caseId,revision:revision!,expiresAtMs:this.now()+30000});return this.intent;
 }
 /** User must confirm the exact action and same server revision within 30 seconds; no deferred/offline replay. */
 async confirm(intent:TriageConfirmIntent,payload:TriagePayload={}):Promise<TriageWriteResult>{
  if(this.busy||!this.intent||intent!==this.intent||this.now()>=intent.expiresAtMs||!this.tasks.allowed(intent.action))
   throw new TriageTransportError('unavailable',0);
  const ctx=this.context();if(ctx.caseId!==intent.caseId||ctx.revision!==intent.revision){this.cancel();throw new TriageTransportError('conflict',409);}
  this.intent=null;this.busy=true;
  try{return await this.tasks.perform(intent.action,payload);}finally{this.busy=false;}
 }
 cancel(){this.intent=null;}
 dispose(){this.cancel();}
}
