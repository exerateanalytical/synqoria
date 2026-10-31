const {test}=require('node:test');const a=require('node:assert/strict');
const {SessionLeaseController}=require('../dist');
const tenant='123e4567-e89b-42d3-a456-426614174000',facility='223e4567-e89b-42d3-a456-426614174001';
test('P02-B0016 lease refresh is server-backed, bounded, expired and invalidated on logout',async()=>{
 const c=new SessionLeaseController();a.equal(c.snapshot(0).kind,'locked');
 const valid=()=>Promise.resolve({confirmed:true,expiresAt:15000,tenantId:tenant,facilityId:facility,capabilities:['patient:read']});
 a.equal((await c.renew(valid,1000)).kind,'verified');a.equal(c.snapshot(15000).kind,'locked');
 a.equal((await c.renew(()=>Promise.resolve({...{confirmed:true,expiresAt:15000,tenantId:tenant,facilityId:facility,capabilities:[]},confirmed:false}),1000)).kind,'locked');
 a.equal((await c.renew(()=>Promise.reject(Error('offline')),1000)).kind,'locked');
});
test('P02-B0016 stale verification cannot restore a logged-out lease',async()=>{
 const c=new SessionLeaseController();let resolve;const pending=c.renew(()=>new Promise(r=>resolve=r),1000);c.invalidate();
 resolve({confirmed:true,expiresAt:15000,tenantId:tenant,facilityId:facility,capabilities:['patient:read']});
 await pending;a.equal(c.snapshot(1200).kind,'locked');
});
