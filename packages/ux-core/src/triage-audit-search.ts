import {type TriageAuditRecord,TriageTransportError} from './triage-client';
/** Bounded search over already-authorized, privacy-minimal audit records. No server-wide discovery, no PHI export. */
export function triageAuditSearch(events:readonly TriageAuditRecord[],query:{operation?:string;fromRevision?:number;toRevision?:number},max=100):readonly TriageAuditRecord[]{
 if(events.length>1000||!Number.isInteger(max)||max<1||max>100||
  (query.operation!==undefined&&!/^[A-Z_]{2,80}$/.test(query.operation))||
  (query.fromRevision!==undefined&&(!Number.isInteger(query.fromRevision)||query.fromRevision<1))||
  (query.toRevision!==undefined&&(!Number.isInteger(query.toRevision)||query.toRevision<1))||
  (query.fromRevision!==undefined&&query.toRevision!==undefined&&query.fromRevision>query.toRevision))
  throw new TriageTransportError('invalid',400);
 const filtered=events.filter(e=>
  (query.operation===undefined||e.operation===query.operation)&&
  (query.fromRevision===undefined||e.revision>=query.fromRevision)&&
  (query.toRevision===undefined||e.revision<=query.toRevision));
 // Data is already privacy-minimal. Project only fixed approved fields.
 return Object.freeze(filtered.slice(0,max).map(x=>Object.freeze({revision:x.revision,operation:x.operation,digest:x.digest,occurredAt:x.occurredAt})));
}
