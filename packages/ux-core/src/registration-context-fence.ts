import {VolatileRegistrationSession,type IntakeScope} from './registration-draft-session';
/** P02-B0038. Disconnect or scope switch destroys volatile draft; never persist demographics. */
export class RegistrationContextFence {
 private last:IntakeScope|null=null;
 constructor(private readonly form:VolatileRegistrationSession){}
 enter(verifiedScope:IntakeScope|null,locale:'en'|'fr'):boolean {
  if(!verifiedScope){this.end();return false;}
  const s=verifiedScope;
  if(!s.tenant||!s.facility||!s.actor||!Number.isSafeInteger(s.sessionEpoch)||s.sessionEpoch<0){this.end();return false;}
  if(this.last && this.last.tenant===s.tenant&&this.last.facility===s.facility&&this.last.actor===s.actor&&this.last.sessionEpoch===s.sessionEpoch)return true;
  this.form.clear();this.form.begin(s,locale);this.last={...s};return true;
 }
 end():void{this.form.clear();this.last=null;}
}
