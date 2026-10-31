/** P02-B0021: ensures a late HTTP response cannot re-authorize after patient or facility switch. */
export class PatientRequestFence {
 private epoch=0;
 private activePatient:string|null=null;
 private activeFacility:string|null=null;
 begin(patientId:string, facilityId:string):number {
  if(!patientId||!facilityId)throw new Error('A verified scope is required');
  this.invalidate();this.activePatient=patientId;this.activeFacility=facilityId;return this.epoch;
 }
 accepts(epoch:number,patientId:string,facilityId:string,sessionVerified:boolean):boolean {
  return sessionVerified===true&&Number.isSafeInteger(epoch)&&epoch===this.epoch
    &&patientId===this.activePatient&&facilityId===this.activeFacility;
 }
 invalidate():void {this.epoch++;this.activePatient=null;this.activeFacility=null;}
}
