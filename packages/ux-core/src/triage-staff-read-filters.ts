import {type TriageView,TriageTransportError} from './triage-client';
/** Explicit read scoping; view selector may never become an unrestricted search query. */
export interface TriageStaffReadFilter {readonly view:TriageView;readonly caseIds:readonly string[];
 readonly filter?:{stationOrClinician?:string;after?:string};}
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
export function triageStaffReadFilter(view:TriageView,caseIds:readonly string[],extra?:{stationOrClinician?:string;after?:string}):TriageStaffReadFilter{
 const allowed=['FACILITY_QUEUE','STATION_QUEUE','PENDING_HANDOVERS','MANUAL_ESCALATIONS','PENDING_REASSESSMENTS','SAFETY_HOLDS','CLINICIAN_WORKLIST','ARRIVALS_SINCE'];
 if(!allowed.includes(view)||caseIds.length<1||caseIds.length>50||caseIds.some(v=>!uuid.test(v))||new Set(caseIds).size!==caseIds.length)
  throw new TriageTransportError('invalid',400);
 const byStation=view==='STATION_QUEUE'||view==='CLINICIAN_WORKLIST';const byTime=view==='ARRIVALS_SINCE';
 if(byStation!==Boolean(extra?.stationOrClinician)||byTime!==Boolean(extra?.after)||
   (byStation&&!uuid.test(extra!.stationOrClinician!))||
   (byTime&&!(typeof extra!.after==='string'&&/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ$/.test(extra!.after)&&!Number.isNaN(Date.parse(extra!.after))&&new Date(extra!.after).toISOString().replace('.000Z','Z')===extra!.after)))
  throw new TriageTransportError('invalid',400);
 return Object.freeze({view,caseIds:Object.freeze([...caseIds]),...(byStation?{filter:Object.freeze({stationOrClinician:extra!.stationOrClinician})}:{}),
  ...(byTime?{filter:Object.freeze({after:extra!.after})}:{})});
}
