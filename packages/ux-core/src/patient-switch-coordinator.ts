import {emptyPatientContext,transitionPatientContext,type PatientContext} from './patient-transition';
/** P02-B0006. Framework-neutral integration seam; no live backend or device wiring. */
export interface PatientSwitchPorts {
 /** Must erase cached patient data and sensitive telemetry BEFORE new authorization. */
 clearSensitivePatientState():Promise<void>;
 /** Must call trusted backend authorization. UI may never grant itself access. */
 authorizePatient(targetId:string,signal:AbortSignal):Promise<boolean>;
}
export class PatientSwitchCoordinator {
 private state:PatientContext=emptyPatientContext;
 private generation=0;
 private controller:AbortController|null=null;
 private disposed=false;
 constructor(private readonly ports:PatientSwitchPorts){}
 snapshot():PatientContext{return this.state;}
 request(targetId:string):PatientContext {
  if(this.disposed)return this.state;
  this.state=transitionPatientContext(this.state,{type:'request-switch',targetId});return this.state;
 }
 markDirty():void {if(!this.disposed)this.state=transitionPatientContext(this.state,{type:'mark-dirty'});}
 cancel():void {if(!this.disposed)this.state=transitionPatientContext(this.state,{type:'cancel'});}
 discardConfirmed():void {if(!this.disposed)this.state=transitionPatientContext(this.state,{type:'discard'});}
 saveRequested():void {if(!this.disposed)this.state=transitionPatientContext(this.state,{type:'request-save-draft'});}
 serverSaveConfirmed(ok:boolean):void {
  if(!this.disposed)this.state=transitionPatientContext(this.state,{type:'draft-save-confirmed',confirmedByServer:ok});
 }
 async executeReauthorization():Promise<PatientContext> {
  if(this.disposed||this.state.stage!=='reauthorizing')return this.state;
  const target=this.state.targetId;
  const gen=++this.generation;
  this.controller?.abort();
  const controller=new AbortController();this.controller=controller;
  try {
   await this.ports.clearSensitivePatientState();
   if(this.disposed||gen!==this.generation||controller.signal.aborted)return this.state;
   const granted=await this.ports.authorizePatient(target,controller.signal);
   if(this.disposed||gen!==this.generation||controller.signal.aborted)return this.state;
   this.state=granted
     ?transitionPatientContext(this.state,{type:'authorize-target',targetId:target,backendAccessVerified:true})
     :transitionPatientContext(this.state,{type:'authorization-denied'});
  }catch {
   if(!this.disposed&&gen===this.generation)this.state=transitionPatientContext(this.state,{type:'authorization-denied'});
  }
  return this.state;
 }
 /** Logout, facility change or component teardown must invalidate pending approvals. */
 dispose():void {this.disposed=true;this.generation++;this.controller?.abort();this.state=emptyPatientContext;}
}
