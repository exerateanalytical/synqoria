import {ScopedClinicalStore} from './scoped-clinical-store';
import type {ClinicalRead} from './clinical-api-client';
/** Abort-and-epoch-coordinated read pipeline; never replays stale or unauthorized results. */
export class PatientReadPipeline {
 private controller:AbortController|null=null;
 private current=0;
 private activeEpoch:number|null=null;
 constructor(private readonly store:ScopedClinicalStore,
  private readonly transport:(id:string,signal:AbortSignal)=>Promise<ClinicalRead>){}
 async select(id:string,facility:string,serverVerified:boolean):Promise<boolean>{
  this.invalidate();if(!serverVerified)return false;
  let epoch:number;
  try{epoch=this.store.begin(id,facility,true);this.activeEpoch=epoch;}catch{return false;}
  const turn=++this.current;
  const controller=new AbortController();this.controller=controller;
  try{
   const response=await this.transport(id,controller.signal);
   if(controller.signal.aborted||turn!==this.current)return false;
   if(response.status!=='ok'){this.store.clear();return false;}
   return this.store.commit(epoch,id,facility,true,response);
  }catch{if(turn===this.current)this.store.clear();return false;}
 }
 value(id:string,facility:string,sessionVerified:boolean):ClinicalRead|null{
  return this.activeEpoch===null?null:this.store.read(this.activeEpoch,id,facility,sessionVerified);
 }
 invalidate():void {this.current++;this.controller?.abort();this.controller=null;this.activeEpoch=null;this.store.clear();}
}
