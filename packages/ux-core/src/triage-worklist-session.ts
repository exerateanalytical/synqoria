import {triageClient, type TriageCaseSummary,type TriageClinicalTransport,type TriageView,
 type TriageViewResult,TriageTransportError} from './triage-client';

/** Authorized, scoped and volatile staff worklist: only explicit patient/case membership is accepted. */
export type TriageWorklistState='empty'|'loading'|'ready'|'unavailable';
export interface TriageWorklistSnapshot {
 readonly state:TriageWorklistState;readonly patientId:string|null;
 readonly cases:readonly TriageCaseSummary[];readonly selectedCaseId:string|null;
}
export class TriageWorklistSession {
 private state:TriageWorklistState='empty';private patientId:string|null=null;
 private rows:readonly TriageCaseSummary[]=[];private selectedCaseId:string|null=null;
 private generation=0;private pending:AbortController|null=null;
 constructor(private readonly client:ReturnType<typeof triageClient>){}
 snapshot():TriageWorklistSnapshot{return {state:this.state,patientId:this.patientId,cases:this.rows,selectedCaseId:this.selectedCaseId};}
 clear(){this.generation++;this.pending?.abort();this.pending=null;this.state='empty';this.patientId=null;this.rows=[];this.selectedCaseId=null;}
 async load(patientId:string,view:TriageView,caseIds:readonly string[],filter?:{stationOrClinician?:string;after?:string}):Promise<TriageViewResult>{
  this.clear();const generation=this.generation;this.state='loading';this.patientId=patientId;
  const controller=new AbortController();this.pending=controller;
  try{
   const value=await this.client.worklist(patientId,view,caseIds,filter,controller.signal);
   if(this.generation!==generation||controller.signal.aborted)throw new TriageTransportError('unavailable',0);
   this.rows=Object.freeze(value.cases.map(row=>Object.freeze({...row})));this.state='ready';return {cases:this.rows};
  }catch(error){if(this.generation===generation){this.clear();if(error instanceof TriageTransportError&&error.category==='unavailable')this.state='unavailable';}throw error;}
  finally{if(this.pending===controller)this.pending=null;}
 }
 select(caseId:string):TriageCaseSummary {
  if(this.state!=='ready'||!this.patientId)throw new TriageTransportError('unavailable',0);
  const row=this.rows.find(item=>item.caseId===caseId);
  if(!row)throw new TriageTransportError('denied',404);
  this.selectedCaseId=row.caseId;return row;
 }
 /** Force fresh backend authorization after any mutation. */
 invalidate(){this.clear();}
}
export function makeTriageWorklistSession(origin:string,transport:TriageClinicalTransport){return new TriageWorklistSession(triageClient(origin,transport));}
