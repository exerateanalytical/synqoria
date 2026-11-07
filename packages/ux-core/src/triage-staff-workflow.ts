import {triageClient, type TriageClinicalTransport, type TriageView, type TriageCaseSummary,
 type TriagePayload, type TriageWriteResult, TriageTransportError} from './triage-client';
import {TriageCaseSession} from './triage-case-session';
import {TriageWorklistSession} from './triage-worklist-session';
import {TriageAuditPager} from './triage-audit-pager';

/** Volatile identity context copied from a verified host session, never inferred from URL or local storage.
 * This is a UI safety barrier, NOT a substitute for backend tenant/facility/role authorization.
 */
export interface TriageStaffScope {
 readonly tenantId:string;readonly facilityId:string;readonly actorId:string;readonly shiftId:string;
 readonly epoch:string;
}
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
function assertScope(s:TriageStaffScope){
 if(!s||[s.tenantId,s.facilityId,s.actorId,s.shiftId].some(v=>typeof v!=='string'||!uuid.test(v))||
   typeof s.epoch!=='string'||!s.epoch.trim()||s.epoch.length>128)
  throw new TriageTransportError('unavailable',0);
}
export interface TriageStaffView {readonly context:TriageStaffScope|null;readonly patientId:string|null;
 readonly selectedCase:TriageCaseSummary|null;readonly worklist:readonly TriageCaseSummary[];
 readonly mode:'locked'|'worklist'|'case'|'refresh-required';}
/**
 * Coordinates patient and case transitions across the same workstation.
 * Every context change cancels in-flight commands/reads and clears PHI before the new context is used.
 * Never persists PHI and never queues clinical writes while offline.
 */
export class TriageStaffWorkflow {
 private context:TriageStaffScope|null=null;
 private patientId:string|null=null;
 private selectedCaseId:string|null=null;
 private version=0;
 readonly caseSession:TriageCaseSession;
 readonly worklistSession:TriageWorklistSession;
 readonly auditPager:TriageAuditPager;
 constructor(origin:string,transport:TriageClinicalTransport){
  const client=triageClient(origin,transport);
  this.caseSession=new TriageCaseSession(client);
  this.worklistSession=new TriageWorklistSession(client);
  this.auditPager=new TriageAuditPager(this.caseSession);
 }
 snapshot():TriageStaffView {
  const snap=this.caseSession.snapshot();
  const permitted=snap.status==='ready'&&snap.patientId===this.patientId&&snap.case?.caseId===this.selectedCaseId;
  const work=this.worklistSession.snapshot();
  return Object.freeze({context:this.context,patientId:this.patientId,
   selectedCase:permitted?snap.case:null,
   worklist:work.patientId===this.patientId&&work.state==='ready'?work.cases:[],
   mode:!this.context?'locked':permitted?'case':snap.status==='refresh-required'?'refresh-required':'worklist'});
 }
 /** Always invoke on role, shift, facility, actor, token-generation, logout or foreground-lock change. */
 bind(scope:TriageStaffScope){
  this.reset();assertScope(scope);this.context=Object.freeze({...scope});
 }
 reset(){
  this.version++;this.caseSession.close();this.worklistSession.clear();this.auditPager.clear();
  this.selectedCaseId=null;this.patientId=null;this.context=null;
 }
 private assertOpen():number{if(!this.context)throw new TriageTransportError('reauthenticate',401);return this.version;}
 /** Switches patient context before any network request, so old records cannot be displayed. */
 async load(patientId:string,view:TriageView,caseIds:readonly string[],filter?:{stationOrClinician?:string;after?:string}){
  this.assertOpen();
  this.version++;const version=this.version;
  this.caseSession.close();this.auditPager.clear();this.worklistSession.clear();
  this.selectedCaseId=null;this.patientId=null;
  if(!uuid.test(patientId))throw new TriageTransportError('invalid',400);
  this.patientId=patientId;
  try{
   await this.worklistSession.load(patientId,view,caseIds,filter);
   if(version!==this.version||!this.context)throw new TriageTransportError('unavailable',0);
   return this.snapshot();
  }catch(e){if(version===this.version)this.reset();throw e;}
 }
 /** Case is only opened after it appeared in an explicitly scoped, authorized worklist. */
 async select(caseId:string){
  this.assertOpen();
  if(!this.patientId)throw new TriageTransportError('unavailable',0);
  this.caseSession.close();this.auditPager.clear();this.selectedCaseId=null;
  this.worklistSession.select(caseId);
  const version=++this.version;const patient=this.patientId;
  this.selectedCaseId=caseId;
  try{
   await this.caseSession.open(patient,caseId);
   if(version!==this.version||!this.context)throw new TriageTransportError('unavailable',0);
   return this.snapshot();
  }catch(e){if(version===this.version){this.selectedCaseId=null;this.caseSession.close();}throw e;}
 }
 /** A completed command must be followed by a fresh authorized summary, never an optimistic UI projection. */
 async command(commandId:string,action:string,payload:TriagePayload={}):Promise<TriageWriteResult>{
  const version=this.assertOpen();
  if(!this.selectedCaseId||!this.patientId||this.caseSession.snapshot().status!=='ready')throw new TriageTransportError('unavailable',0);
  try{
   const result=await this.caseSession.command(commandId,action,payload);
   if(version!==this.version||!this.context)throw new TriageTransportError('unavailable',0);
   // Invalidate worklist even if the reread fails; never show stale queue position.
   this.worklistSession.invalidate();this.auditPager.clear();
   await this.caseSession.refresh();
   if(version!==this.version||!this.context)throw new TriageTransportError('unavailable',0);
   return result;
  }catch(error){
   if(version===this.version&&error instanceof TriageTransportError&&['reauthenticate','denied'].includes(error.category))this.reset();
   throw error;
  }
 }
 dispose(){this.reset();this.auditPager.dispose();}
}
