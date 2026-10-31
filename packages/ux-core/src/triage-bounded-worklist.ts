import {type TriageCaseSummary,type TriageView,TriageTransportError} from './triage-client';
/** Bounded explicit case-ID batches; this is NOT an unscoped facility-wide search endpoint. */
export interface TriageWorklistBatchPort {
 worklist(patientId:string,view:TriageView,caseIds:readonly string[],filter?:{stationOrClinician?:string;after?:string},signal?:AbortSignal):Promise<{cases:readonly TriageCaseSummary[]}>;
}
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
export class TriageBoundedWorklist {
 private generation=0;private active:AbortController|null=null;
 constructor(private readonly port:TriageWorklistBatchPort){}
 clear(){this.generation++;this.active?.abort();this.active=null;}
 /** One verified patient, maximum 200 explicit IDs and 4 separate scoped requests; no server-side global query. */
 async load(patientId:string,view:TriageView,ids:readonly string[],filter?:{stationOrClinician?:string;after?:string}):Promise<readonly TriageCaseSummary[]>{
  this.clear();const gen=this.generation;
  if(!UUID.test(patientId)||ids.length<1||ids.length>200||ids.some(i=>!UUID.test(i))||new Set(ids).size!==ids.length)
   throw new TriageTransportError('invalid',400);
  const ctrl=new AbortController();this.active=ctrl;const seen=new Set<string>(),byId=new Map<string,TriageCaseSummary>();
  try{
   for(let i=0;i<ids.length;i+=50){
    const chunk=ids.slice(i,i+50),result=await this.port.worklist(patientId,view,chunk,filter,ctrl.signal);
    if(ctrl.signal.aborted||gen!==this.generation)throw new TriageTransportError('unavailable',0);
    for(const item of result.cases){if(!chunk.includes(item.caseId)||seen.has(item.caseId))throw new TriageTransportError('unavailable',0);
     seen.add(item.caseId);byId.set(item.caseId,Object.freeze({...item}));}
   }
   return Object.freeze(ids.filter(id=>byId.has(id)).map(id=>byId.get(id)!));
  }catch(e){throw e;}
  finally{if(this.active===ctrl)this.active=null;}
 }
 dispose(){this.clear();}
}
