import type {ClinicalRead} from './clinical-api-client';
/** Volatile, patient-and-facility-scoped read store; no persistence and no presumed offline authority. */
export class ScopedClinicalStore {
 private generation=0;
 private scope:{patient:string;facility:string;generation:number}|null=null;
 private value:Extract<ClinicalRead,{status:'ok'}>|null=null;
 begin(patientId:string,facilityId:string,serverSessionVerified:boolean):number{
  this.clear();
  if(!serverSessionVerified||!patientId||!facilityId)throw new Error('Trusted patient context required');
  this.scope={patient:patientId,facility:facilityId,generation:this.generation};
  return this.generation;
 }
 commit(epoch:number,patientId:string,facilityId:string,serverSessionVerified:boolean,result:ClinicalRead):boolean{
  if(!serverSessionVerified||!this.scope||epoch!==this.generation||patientId!==this.scope.patient||facilityId!==this.scope.facility
      ||result.status!=='ok'||result.patientId!==patientId){return false;}
  this.value={...result};return true;
 }
 read(epoch:number,patientId:string,facilityId:string,verified:boolean):Extract<ClinicalRead,{status:'ok'}>|null{
  return verified&&this.scope&&epoch===this.generation&&patientId===this.scope.patient&&facilityId===this.scope.facility&&this.value?{...this.value}:null;
 }
 clear():void {this.generation++;this.scope=null;this.value=null;}
}
