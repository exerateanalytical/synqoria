import test from "node:test";
import assert from "node:assert/strict";
import {postgresOidcReplayStore,OidcReplayStoreError,OIDC_REPLAY_RESERVE_SQL,OIDC_REPLAY_CONSUME_SQL} from "../apps/web/lib/oidc-replay-postgres.mjs";
import {readFileSync} from "node:fs";

const key="synqoria:oidc:state:v1:"+"a".repeat(64),now=Date.parse("2026-11-05T20:00:00Z");
const fail=async f=>await assert.rejects(f,OidcReplayStoreError);
test("Postgres adapter parameterizes both atomic commands without leaking OAuth state or PHI",async()=>{
 const calls=[];
 const client={query:async(text,values)=>{calls.push({text,values});return {rowCount:1,rows:[{state_key:key}]};}};
 const adapter=postgresOidcReplayStore(client,{now:()=>now});
 assert.equal(await adapter.reserve(key,now+300000),true);
 assert.equal(await adapter.consume(key,now+1000),true);
 assert.equal(calls.length,2);
 assert.equal(calls[0].text,OIDC_REPLAY_RESERVE_SQL);
 assert.equal(calls[1].text,OIDC_REPLAY_CONSUME_SQL);
 assert.deepEqual(calls[0].values,[key,300000]);
 assert.deepEqual(calls[1].values,[key]);
 assert.ok(calls.every(x=>x.text.includes("synqoria_identity.oidc_replay_state")));
 assert.ok(!calls[0].text.includes(key)); // SQL parameter, not interpolation
 assert.match(calls[0].text,/ON CONFLICT\(state_key\) DO NOTHING/);
 assert.match(calls[1].text,/expires_at > clock_timestamp\(\)/);
});
test("duplicate reservations and expired/consumed states are denied by DB rowCount",async()=>{
 const client={query:async()=>({rowCount:0,rows:[]})};
 const store=postgresOidcReplayStore(client,{now:()=>now});
 assert.equal(await store.reserve(key,now+300000),false);
 assert.equal(await store.consume(key,now+1000),false);
});
test("missing DB, malformed keys, invalid TTL and query failures deny without fallback",async()=>{
 assert.throws(()=>postgresOidcReplayStore(null),OidcReplayStoreError);
 const calls=[];
 const store=postgresOidcReplayStore({query:async(...args)=>{calls.push(args);return {rowCount:1,rows:[{}]};}},{now:()=>now});
 await fail(()=>store.reserve(key.slice(0,-1)+"z",now+1000));
 await fail(()=>store.reserve(key,now-1));
 await fail(()=>store.reserve(key,now+400000));
 await fail(()=>store.consume("attacker",now+1000));
 assert.equal(calls.length,0);
 const offline=postgresOidcReplayStore({query:async()=>{throw Error("connection down")}},{now:()=>now});
 await fail(()=>offline.reserve(key,now+300000));
 await fail(()=>offline.consume(key,now+1000));
 const invalid=postgresOidcReplayStore({query:async()=>({rowCount:2,rows:[{},{}]})},{now:()=>now});
 await fail(()=>invalid.consume(key,now+1000));
});
test("identity SQL schema has no clinical fields and forced RLS without grant or permissive policy",()=>{
 const sql=readFileSync(new URL("../services/identity/db/oidc_replay_state_candidate.sql",import.meta.url),"utf8");
 assert.match(sql,/CREATE TABLE IF NOT EXISTS synqoria_identity\.oidc_replay_state/);
 assert.match(sql,/ENABLE ROW LEVEL SECURITY/);
 assert.match(sql,/FORCE ROW LEVEL SECURITY/);
 assert.doesNotMatch(sql,/^\s*CREATE POLICY /mi);
 assert.doesNotMatch(sql,/^\s*GRANT /mi);
 assert.doesNotMatch(sql,/patient_id|refresh_token|access_token|authorization_code/i);
});
