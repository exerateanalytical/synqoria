const {test}=require('node:test');const assert=require('node:assert/strict');
const {startSyncReceipt,advanceSyncReceipt}=require('../dist');
const op='opaque_op_10000',id='server_ack_20000';
test('locally queued write is never silently labelled saved or synchronized',()=>{
 let receipt=startSyncReceipt(op);assert.equal(receipt.stage,'draft-local');
 receipt=advanceSyncReceipt(receipt,{kind:'queued'});assert.equal(receipt.stage,'pending-sync');
 receipt=advanceSyncReceipt(receipt,{kind:'transport-uncertain'});assert.equal(receipt.stage,'outcome-unknown');
 assert.equal(receipt.serverReceiptId,null);
});
test('only matching, verified server acknowledgement may resolve unknown outcome',()=>{
 let s=advanceSyncReceipt(startSyncReceipt(op),{kind:'queued'});
 s=advanceSyncReceipt(s,{kind:'transport-uncertain'});
 for(const e of [
  {kind:'server-confirmed',operationId:'wrong_op_123',receiptId:id,verifiedServerResponse:true},
  {kind:'server-confirmed',operationId:op,receiptId:'untrusted',verifiedServerResponse:false},
 ])assert.equal(advanceSyncReceipt(s,e).stage,'outcome-unknown');
 s=advanceSyncReceipt(s,{kind:'server-confirmed',operationId:op,receiptId:id,verifiedServerResponse:true});
 assert.equal(s.stage,'saved-server');
 assert.equal(advanceSyncReceipt(s,{kind:'remote-projection-confirmed',operationId:op,receiptId:'wrong_ack_555',verifiedServerResponse:true}).stage,'saved-server');
 s=advanceSyncReceipt(s,{kind:'remote-projection-confirmed',operationId:op,receiptId:id,verifiedServerResponse:true});
 assert.equal(s.stage,'synchronized');
});
test('no invented operation identifiers or state shortcuts',()=>{
 assert.throws(()=>startSyncReceipt('patient name'));
 let s=startSyncReceipt(op);assert.equal(advanceSyncReceipt(s,{kind:'transport-uncertain'}),s);
 assert.equal(advanceSyncReceipt(s,{kind:'remote-projection-confirmed',operationId:op,receiptId:id,verifiedServerResponse:true}),s);
});
