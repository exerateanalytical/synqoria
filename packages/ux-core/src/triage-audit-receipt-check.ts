import {type TriageAuditRecord,type TriageWriteResult,TriageTransportError} from './triage-client';
/** Confirm a transaction acknowledgement using audit revision/digest, without exposing patient names or notes. */
export interface TriageReceiptEvidence {readonly revision:number;readonly digest:string;readonly operation:string;}
export function triageReceiptEvidence(receipt:TriageWriteResult,action:string,history:readonly TriageAuditRecord[]):TriageReceiptEvidence {
 if(!Number.isInteger(receipt.revision)||receipt.revision<1||typeof receipt.eventDigest!=='string'||
  !/^[a-f0-9]{64}$/.test(receipt.eventDigest)||!(/^[A-Z_]{2,80}$/.test(action)))throw new TriageTransportError('unavailable',0);
 const found=history.filter(r=>r.revision===receipt.revision);
 if(found.length!==1||found[0]!.operation!==action||found[0]!.digest!==receipt.eventDigest)
  throw new TriageTransportError('unavailable',0);
 return Object.freeze({revision:receipt.revision,digest:receipt.eventDigest,operation:action});
}
