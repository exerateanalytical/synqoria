/**
 * SYNQORIA P03-B0035 — explicit, ordered, server-only staff-login pipeline.
 *
 * STEP 1: consume one-use PKCE authorization state.
 * STEP 2: exchange code and cryptographically verify OIDC ID/access tokens.
 * STEP 3: confirm live token activity/revocation with confidential IdP.
 * STEP 4: obtain fresh active tenant/facility/shift grants from trusted roster.
 * STEP 5: pass credentials to a trusted, durable server-side session writer.
 *
 * No user-controlled role, source URL, tenant, facility or target session is
 * accepted. No clinical access is granted by this module, and it never stores
 * raw IdP tokens in a browser cookie. No PHI or token appears in responses.
 */
import {consumeOneUseOidcAuthorization} from "./oidc-one-use.mjs";
import {exchangeOneUseOidcCode} from "./oidc-code-exchange.mjs";
import {verifyActiveOidcAccessToken} from "./oidc-token-introspection.mjs";
import {resolveStaffWorkforceGrant} from "./oidc-workforce-grants.mjs";

const REF=/^[A-Za-z0-9_-]{32,128}$/;
export class StaffLoginPipelineError extends Error {
 constructor(){super("staff_login_denied");this.name="StaffLoginPipelineError";}
}
const deny=()=>{throw new StaffLoginPipelineError();};

/**
 * @param {string} callbackUrl - Browser-visible callback request URL, never upstream endpoint.
 * @param {string} cookie - Sealed one-use OIDC flow cookie.
 * @param {object} config - Server-owned, vault-backed, pinned trusted configuration.
 * @param {object} adapters - Durable replay store, HTTP transport and privileged session writer.
 * @returns {Promise<{sessionReference:string,expiresAt:number}>}
 */
export async function finishStaffOidcLogin(callbackUrl,cookie,config,{
 replayStore,fetcher=fetch,issueServerSession,nowMs=Date.now()
}={}){
 if(!config||typeof config!=="object"||!Number.isSafeInteger(nowMs)||
   !replayStore||typeof replayStore.consume!=="function"||
   typeof fetcher!=="function"||typeof issueServerSession!=="function"||
   !config.oidc||!config.token||!config.introspection||!config.workforce||
   !config.jwks||typeof config.apiAudience!=="string"||!config.apiAudience||
   config.oidc.issuer!==config.token.issuer||config.oidc.issuer!==config.introspection.issuer||
   config.oidc.issuer!==config.workforce.issuer||
   config.oidc.clientId!==config.token.clientId||
   config.oidc.clientId!==config.introspection.clientId||
   config.introspection.apiAudience!==config.apiAudience)deny();
 try{
  const proof=await consumeOneUseOidcAuthorization(callbackUrl,cookie,config.oidc,replayStore,{now:nowMs});
  // No retry after a downstream failure: replay reservation was consumed first.
  const signed=await exchangeOneUseOidcCode(proof,config.token,{
   fetcher,jwks:config.jwks,apiAudience:config.apiAudience,nowSeconds:Math.floor(nowMs/1000)
  });
  const introspected=await verifyActiveOidcAccessToken(
   signed.upstreamAccessToken,signed.identity,config.introspection,{
    fetcher,nowSeconds:Math.floor(nowMs/1000)
   }
  );
  const grant=await resolveStaffWorkforceGrant(signed.identity,
   signed.upstreamAccessToken,config.workforce,{fetcher,nowMs});
  const expiresAt=Math.min(grant.expiresAt,introspected.expiresAt*1000,nowMs+600000);
  if(!Number.isSafeInteger(expiresAt)||expiresAt<=nowMs+15000)deny();
  // Must persist a server-side, revocable, opaque reference and hold the IdP
  // bearer there; no direct sealStaffSession or browser bearer serialization.
  const ticket=await issueServerSession(Object.freeze({
    bearer:signed.upstreamAccessToken,subject:signed.identity.subject,
    actorId:grant.actorId,tenantId:grant.tenantId,facilityId:grant.facilityId,
    shiftId:grant.shiftId,role:grant.role,revision:grant.revision,
    capabilities:grant.capabilities,issuedAt:nowMs,expiresAt
  }));
  if(typeof ticket!=="string"||!REF.test(ticket)||
    signed.upstreamAccessToken.includes(ticket)||ticket.includes(signed.upstreamAccessToken))deny();
  return Object.freeze({sessionReference:ticket,expiresAt});
 }catch{deny();}
}
