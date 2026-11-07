import {type TriageAuditPage,type TriageAuditRecord,TriageTransportError} from './triage-client';
/** Internal audit navigator for staff permitted to view one case; bounded in memory, no export/local persistence. */
export interface TriageAuditReviewPort {history(patient:string,caseId:string,after:number,signal?:AbortSignal):Promise<TriageAuditPage>}
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
export class TriageAuditReview {
 private patient:string|null=null;private caseId:string|null=null;private next=0;
 private events:TriageAuditRecord[]=[];private finished=false;private generation=0;private active:AbortController|null=null;
 constructor(private readonly port:TriageAuditReviewPort){}
 bind(patient:string,caseId:string){this.clear();if(!UUID.test(patient)||!UUID.test(caseId))throw new TriageTransportError('invalid',400);
  this.patient=patient;this.caseId=caseId;}
 clear(){this.generation++;this.active?.abort();this.active=null;this.patient=null;this.caseId=null;this.next=0;this.events=[];this.finished=false;}
 snapshot(){return Object.freeze({events:Object.freeze([...this.events]),hasMore:!this.finished&&this.patient!==null,caseId:this.caseId});}
 async nextPage():Promise<readonly TriageAuditRecord[]>{
  if(!this.patient||!this.caseId||this.finished||this.active||this.events.length>=1000)throw new TriageTransportError('unavailable',0);
  const gen=this.generation,ctrl=new AbortController();this.active=ctrl;
  try{
   const page=await this.port.history(this.patient,this.caseId,this.next,ctrl.signal);
   if(ctrl.signal.aborted||gen!==this.generation)throw new TriageTransportError('unavailable',0);
   if(page.history.length>100||!Array.isArray(page.history)||this.events.length+page.history.length>1000)throw new TriageTransportError('unavailable',0);
   let rev=this.next;
   for(const ev of page.history){if(!Number.isSafeInteger(ev.revision)||ev.revision!==rev+1||!/^([a-f0-9]{64})$/.test(ev.digest)||!/^[A-Z_]{2,80}$/.test(ev.operation))
    throw new TriageTransportError('unavailable',0);rev=ev.revision;}
   if(page.nextAfterRevision!==null&&(page.history.length!==100||page.nextAfterRevision!==rev))throw new TriageTransportError('unavailable',0);
   if(page.history.length===0&&page.nextAfterRevision!==null)throw new TriageTransportError('unavailable',0);
   this.events.push(...page.history.map(ev=>Object.freeze({...ev})));
   this.next=rev;this.finished=page.nextAfterRevision===null||this.events.length>=1000;
   return this.snapshot().events;
  }catch(e){this.events=[];this.next=0;this.finished=false;throw e;}
  finally{if(this.active===ctrl)this.active=null;}
 }
 dispose(){this.clear();}
}
