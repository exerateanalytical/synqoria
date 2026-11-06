import test from "node:test";
import assert from "node:assert/strict";
import {initializeVerifiedStaffRoutes,StaffRouteInitializationError}
 from "../apps/web/lib/staff-route-bootstrap.mjs";
const deny=x=>assert.rejects(x,StaffRouteInitializationError);
test("B0068 missing independent SQL principals cannot initialize any login routes",async()=>{
 await deny(()=>initializeVerifiedStaffRoutes());
 await deny(()=>initializeVerifiedStaffRoutes({env:{},replaySql:{},sessionSql:{},auditSql:{}}));
});
test("B0068 a shared connection or user-controlled transport cannot initialize staff identity",async()=>{
 const db={query:async()=>({rowCount:0,rows:[]})};
 await deny(()=>initializeVerifiedStaffRoutes({
  env:{},replaySql:db,sessionSql:db,auditSql:db,fetcher:fetch,now:Date.now
 }));
 await deny(()=>initializeVerifiedStaffRoutes({
  env:{},replaySql:{query:async()=>({rowCount:0,rows:[]})},
  sessionSql:{query:async()=>({rowCount:0,rows:[]})},
  auditSql:{query:async()=>({rowCount:0,rows:[]})},
  fetcher:null,now:Date.now
 }));
});
