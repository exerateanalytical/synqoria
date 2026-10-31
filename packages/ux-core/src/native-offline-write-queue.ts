/** P02-B0077. Offline clinical writes cannot be queued or replayed until reviewed durable policy exists. */
export type OfflineIntent='registration'|'medication'|'allergy'|'triage'|'encounter'|'appointment'|'read_only_shell';
export class NativeOfflineWriteQueue {
 private readonly items:never[]=[];
 request(intent:OfflineIntent,online:boolean,verifiedSession:boolean):{queued:false;reason:string}{
  if(!['registration','medication','allergy','triage','encounter','appointment','read_only_shell'].includes(intent))throw new Error('Unknown intent');
  if(intent==='read_only_shell')return {queued:false,reason:'SHELL_ONLY_NO_CLINICAL_DATA'};
  return {queued:false,reason:online&&verifiedSession?'CLINICAL_RELEASE_LOCKED':'OFFLINE_WRITE_DENIED'};
 }
 get pendingWrites(){return this.items.length;}
 replay(){return {sent:0,reason:'REPLAY_DISABLED'} as const;}
}
