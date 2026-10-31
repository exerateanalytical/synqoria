/** P02-B0030: volatile intake draft, scoped to an authenticated context epoch. Never persist PHI. */
import type {RegistrationDraft} from './registration-draft';
export type IntakeScope={tenant:string;facility:string;actor:string;sessionEpoch:number};
export function blankRegistrationDraft(language:'en'|'fr'):RegistrationDraft{
 return {name:'',birthInformation:'unknown',dateOfBirth:'',estimatedAge:'',language,duplicateReviewCleared:false};
}
function scopeEqual(a:IntakeScope,b:IntakeScope):boolean{
 return a.tenant===b.tenant&&a.facility===b.facility&&a.actor===b.actor&&a.sessionEpoch===b.sessionEpoch;
}
export class VolatileRegistrationSession {
 private scope:IntakeScope|null=null;
 private draft:RegistrationDraft=blankRegistrationDraft('en');
 begin(scope:IntakeScope,language:'en'|'fr'){
  if(!scope.tenant||!scope.facility||!scope.actor||!Number.isSafeInteger(scope.sessionEpoch)||scope.sessionEpoch<0)throw new Error('Invalid verified scope');
  this.scope={...scope};this.draft=blankRegistrationDraft(language);
 }
 update(scope:IntakeScope,patch:Partial<RegistrationDraft>):boolean{
  if(!this.scope||!scopeEqual(this.scope,scope)){this.clear();return false;}
  // Duplicate review is server-controlled, never inherited from UI patches.
  const {duplicateReviewCleared:_ignored,...safe}=patch;
  this.draft={...this.draft,...safe,duplicateReviewCleared:false};return true;
 }
 snapshot(scope:IntakeScope):RegistrationDraft|null{
  if(!this.scope||!scopeEqual(this.scope,scope)){this.clear();return null;}
  return {...this.draft,duplicateReviewCleared:false};
 }
 clear(){this.scope=null;this.draft=blankRegistrationDraft('en');}
}
