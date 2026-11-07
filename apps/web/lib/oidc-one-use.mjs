/**
 * SYNQORIA P03 one-use OIDC authorization state coordinator.
 *
 * Storage is an injected durable, shared, atomic TTL adapter, NOT this module's
 * memory. Required adapter semantics: reserve(key, expiresAt) atomically inserts
 * only if absent; consume(key, now) atomically deletes/claims exactly once and
 * returns true only for an unexpired reservation. The production implementation
 * must be backed by a real ACID/atomic shared store with restart/replay tests.
 *
 * No staff login is issued, and neither access tokens nor PHI are stored here.
 */
import {createHash} from "node:crypto";
import {beginOidcAuthorization,inspectOidcAuthorizationCallback,OidcFlowError} from "./oidc-login-flow.mjs";

const deny=()=>{throw new OidcFlowError();};
const MAX_STATE_LENGTH=128;
const STATE_PATTERN=/^[A-Za-z0-9_-]{32,128}$/;
const DIGEST_PREFIX="synqoria:oidc:state:v1:";
const isInteger=x=>Number.isSafeInteger(x)&&x>0;
const hasAdapter=store=>store!==null&&typeof store==="object"&&
 typeof store.reserve==="function"&&typeof store.consume==="function";
function stateDigest(state,issuer,clientId){
 if(typeof state!=="string"||state.length>MAX_STATE_LENGTH||!STATE_PATTERN.test(state)||
    typeof issuer!=="string"||typeof clientId!=="string")deny();
 return DIGEST_PREFIX+createHash("sha256").update(JSON.stringify([issuer,clientId,state]),"utf8").digest("hex");
}
async function strictlyTrue(promise){
 try{return await promise===true;}catch{return false;}
}
/**
 * Runs the normal server-side begin flow, then requires a shared atomic
 * reservation before exposing any redirect/cookie to the caller.
 * If reservation fails, no authorization URL or cookie is returned.
 */
export async function beginOneUseOidcAuthorization(config,store,{now=Date.now()}={}){
 if(!hasAdapter(store)||!isInteger(now))deny();
 const prepared=beginOidcAuthorization(config,{now});
 const redirect=new URL(prepared.authorizationUrl);
 const state=redirect.searchParams.get("state");
 const key=stateDigest(state,config.issuer,config.clientId);
 // Expiry is controlled by server time, not a browser or IdP query parameter.
 const saved=await strictlyTrue(store.reserve(key,now+300000));
 if(!saved)deny();
 return prepared;
}
/**
 * Consumes a previously reserved state exactly once after cryptographic sealed
 * cookie + callback checks and BEFORE token exchange. The adapter must enforce
 * expiry using the caller's server-owned time and an atomic compare/delete.
 *
 * A result is merely an ephemeral code-exchange input. Authoritative signed
 * token verification, IdP revocation and workforce scope still follow.
 */
export async function consumeOneUseOidcAuthorization(callbackUrl,cookie,config,store,{now=Date.now()}={}){
 if(!hasAdapter(store)||!isInteger(now))deny();
 const proof=inspectOidcAuthorizationCallback(callbackUrl,cookie,config,{now});
 // inspect above rejects duplicate state parameters, unknown query keys,
 // redirect mismatches, expired/tampered cookies and mixed IdP issuer.
 const state=new URL(callbackUrl).searchParams.get("state");
 const key=stateDigest(state,proof.issuer,proof.clientId);
 const consumed=await strictlyTrue(store.consume(key,now));
 if(!consumed)deny();
 return proof;
}
