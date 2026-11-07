/**
 * Shared P03 verified operations session source for server-side Next handlers.
 * Explicit issuer and audience must be configured by the deployment; browser
 * requests cannot choose them. Never return a bearer token to a caller.
 *
 * No JWKS stale-cache fallback: issuer outages deny fresh session attestations.
 * This does not replace an issuer-backed revocation/introspection check.
 */
import {fetchIssuerJwks,verifyOperationsJwt,IdentityDenied} from "./verified-oidc-session.mjs";

const REQUIRED_SCOPE_PERMISSIONS = new Set(["m38:sync_read","m38:sync_submit"]);
const MAX_ISSUER_LENGTH=512, MAX_AUDIENCE_LENGTH=180;

/** Verified server-side identity and hospital scope, not clinical authorization. */
export async function verifiedOperationsSession(token,{
 issuer,audience,fetchImpl=fetch,now=Date.now(),requiredPermission=null,
}={}){
 if(typeof issuer!=="string"||!issuer||issuer.length>MAX_ISSUER_LENGTH||
    typeof audience!=="string"||!audience||audience.length>MAX_AUDIENCE_LENGTH)
  throw new IdentityDenied("identity_config_invalid");
 if(requiredPermission!==null&&!REQUIRED_SCOPE_PERMISSIONS.has(requiredPermission))
  throw new IdentityDenied("identity_config_invalid");
 // Check basic cookie shape before network lookup, preventing unauthenticated
 // callers from creating unnecessary identity-provider traffic.
 if(typeof token!=="string"||token.length<80||token.length>16000||
    token.split(".").length!==3)throw new IdentityDenied("identity_denied");
 const jwks=await fetchIssuerJwks(issuer,{fetchImpl});
 const session=verifyOperationsJwt(token,{issuer,audience,jwks,now});
 if(requiredPermission&&!session.grants.includes(requiredPermission))
  throw new IdentityDenied("permission_denied");
 return Object.freeze({
  userId:session.userId,
  scope:session.scope,
  expiresAt:session.expiresAt,
 });
}
