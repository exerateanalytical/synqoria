import {type TriageAuditRecord,TriageTransportError} from './triage-client';
import {TriageCaseSession} from './triage-case-session';

/** Read-only bounded pagination; holds no notes, no identifiers beyond the current volatile case scope. */
export class TriageAuditPager {
 private entries:readonly TriageAuditRecord[]=[];private cursor:number|null=0;
 private loading=false;private generation=0;private readonly unsubscribe:()=>void;
 private boundCaseId:string|null=null;
 constructor(private readonly session:TriageCaseSession){
  this.unsubscribe=session.subscribe(current=>{
   if(current.status!=='ready'||current.case?.caseId!==this.boundCaseId){
    if(current.status!=='ready'||this.boundCaseId!==null)this.clear();
    if(current.status==='ready')this.boundCaseId=current.case!.caseId;
   }
  });
 }
 snapshot(){return Object.freeze({history:this.entries,nextAfterRevision:this.cursor,loading:this.loading});}
 clear(){this.generation++;this.entries=[];this.cursor=0;this.loading=false;this.boundCaseId=null;}
 async loadNext():Promise<readonly TriageAuditRecord[]>{
  const current=this.session.snapshot();
  if(current.status!=='ready'||!current.case||this.loading||this.cursor===null)throw new TriageTransportError('unavailable',0);
  if(this.entries.length>=1000)throw new TriageTransportError('unavailable',0);
  const generation=this.generation,caseId=current.case.caseId;this.loading=true;
  try{
   const response=await this.session.history(this.cursor);
   const next=this.session.snapshot();
   if(generation!==this.generation||next.status!=='ready'||next.case?.caseId!==caseId)throw new TriageTransportError('unavailable',0);
   if(response.history.length+this.entries.length>1000)throw new TriageTransportError('unavailable',0);
   this.entries=Object.freeze([...this.entries,...response.history].map(event=>Object.freeze({...event})));
   this.cursor=response.nextAfterRevision;return this.entries;
  }finally{if(this.generation===generation)this.loading=false;}
 }
 dispose(){this.unsubscribe();this.clear();}
}
