/** Explicit locally observable sync status. Never infer success from a timeout or spinner.
 * This is presentation logic, not an offline write queue or server synchronization engine. */
export type SyncReceipt = Readonly<{
 operationId:string;
 stage:'draft-local'|'pending-sync'|'outcome-unknown'|'saved-server'|'synchronized';
 serverReceiptId:string|null;
}>;
const idPattern=/^[A-Za-z0-9_-]{8,128}$/;
export function startSyncReceipt(operationId:string):SyncReceipt{
 if(!idPattern.test(operationId))throw new Error('Invalid opaque operation identifier');
 return {operationId,stage:'draft-local',serverReceiptId:null};
}
export type SyncReceiptEvent =
 | {kind:'queued'} | {kind:'transport-uncertain'}
 | {kind:'server-confirmed';operationId:string;receiptId:string;verifiedServerResponse:boolean}
 | {kind:'remote-projection-confirmed';operationId:string;receiptId:string;verifiedServerResponse:boolean};
export function advanceSyncReceipt(receipt:SyncReceipt,event:SyncReceiptEvent):SyncReceipt {
 if(event.kind==='queued' && receipt.stage==='draft-local')return {...receipt,stage:'pending-sync'};
 if(event.kind==='transport-uncertain' && (receipt.stage==='pending-sync'||receipt.stage==='outcome-unknown'))
  return {...receipt,stage:'outcome-unknown'};
 if(event.kind==='server-confirmed' && (receipt.stage==='pending-sync'||receipt.stage==='outcome-unknown')
   && event.operationId===receipt.operationId && event.verifiedServerResponse && idPattern.test(event.receiptId))
  return {...receipt,stage:'saved-server',serverReceiptId:event.receiptId};
 if(event.kind==='remote-projection-confirmed' && receipt.stage==='saved-server'
   && event.operationId===receipt.operationId && event.receiptId===receipt.serverReceiptId && event.verifiedServerResponse)
  return {...receipt,stage:'synchronized'};
 return receipt;
}
