import {type TriageWriteResult,TriageTransportError} from './triage-client';
import {TriageAuthorizedTasks} from './triage-authorized-tasks';
/** Human-entered intake context; never infer consent or identity verification from user-agent/device. */
export class TriageArrivalEvidenceDesk {
 constructor(private readonly tasks:TriageAuthorizedTasks){}
 reason(text:string):Promise<TriageWriteResult>{return this.enter('ARRIVAL_REASON',text);}
 language(text:string):Promise<TriageWriteResult>{return this.enter('LANGUAGE',text);}
 interpreter(text:string):Promise<TriageWriteResult>{return this.enter('INTERPRETER_REQUEST',text);}
 accessibility(text:string):Promise<TriageWriteResult>{return this.enter('ACCESSIBILITY_SUPPORT',text);}
 identityEvidence(text:string):Promise<TriageWriteResult>{return this.enter('IDENTITY_VERIFICATION',text);}
 channel(text:string):Promise<TriageWriteResult>{return this.enter('ARRIVAL_CHANNEL',text);}
 referral(text:string):Promise<TriageWriteResult>{return this.enter('REFERRAL_REFERENCE',text);}
 attendant(text:string):Promise<TriageWriteResult>{return this.enter('ATTENDANT_CONTACT',text);}
 consentEvidence(text:string):Promise<TriageWriteResult>{return this.enter('CONSENT_EVIDENCE',text);}
 private enter(action:string,text:string):Promise<TriageWriteResult>{if(typeof text!=='string'||!text.trim())throw new TriageTransportError('invalid',422);
  return this.tasks.perform(action,{text});}
}
