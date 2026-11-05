import test from "node:test";
import assert from "node:assert/strict";
import {createExpiredStaffSessionJanitor,StaffJanitorError,STAFF_SESSION_SWEEP_SQL} from "../apps/web/lib/expired-staff-session-janitor.mjs";
const good=Array.from({length:3},(_,i)=>({session_digest:String(i+1).repeat(64)}));
const error=fn=>assert.rejects(fn,StaffJanitorError);
test("P03-B0046 issues bounded, parameterized SKIP LOCKED expiry-only cleanup",async()=>{
 const calls=[];
 const janitor=createExpiredStaffSessionJanitor({query:async(sql,params)=>{
  calls.push({sql,params});return {rowCount:3,rows:good};
 }});
 const r=await janitor.sweep({limit:4});
 assert.deepEqual(r,{removed:3,mayHaveMore:false});
 assert.equal(Object.isFrozen(r),true);
 assert.deepEqual(calls,[{sql:STAFF_SESSION_SWEEP_SQL,params:[4]}]);
 assert.match(calls[0].sql,/expires_at <= clock_timestamp\(\)/);
 assert.match(calls[0].sql,/FOR UPDATE SKIP LOCKED/);
 assert.ok(!JSON.stringify(r).includes(good[0].session_digest));
});
test("P03-B0046 empty sweep, exact cap, and malformed limit",async()=>{
 const zero=createExpiredStaffSessionJanitor({query:async()=>({rowCount:0,rows:[]})});
 assert.deepEqual(await zero.sweep(),{removed:0,mayHaveMore:false});
 const full=createExpiredStaffSessionJanitor({query:async()=>({rowCount:3,rows:good})});
 assert.equal((await full.sweep({limit:3})).mayHaveMore,true);
 for(const limit of [0,501,-1,1.5,Infinity,"1",null]){
  await error(()=>zero.sweep({limit}));
 }
});
test("P03-B0046 refuses malformed data, hidden attributes, duplicated digest and DB failures",async()=>{
 for(const data of [
  {rowCount:2,rows:good},
  {rowCount:1,rows:[{session_digest:"x"}]},
  {rowCount:1,rows:[{session_digest:good[0].session_digest,actorId:"PHI"}]},
  {rowCount:2,rows:[good[0],good[0]]},
  {rowCount:501,rows:good},
  {rowCount:1,rows:null}
 ]){
  const janitor=createExpiredStaffSessionJanitor({query:async()=>data});
  await error(()=>janitor.sweep({limit:500}));
 }
 const offline=createExpiredStaffSessionJanitor({query:async()=>{throw Error("database offline");}});
 await error(()=>offline.sweep());
 assert.throws(()=>createExpiredStaffSessionJanitor(null),StaffJanitorError);
});
