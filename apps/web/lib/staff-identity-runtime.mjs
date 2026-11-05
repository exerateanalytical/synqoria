/**
 * SYNQORIA P03-B0052 — composed trusted staff identity runtime.
 *
 * One immutable server-owned dependency graph: pinned IdP/JWKS/tenant config,
 * three independently restricted PostgreSQL role clients, cryptographic OIDC,
 * replay reservation, encrypted session database, audit, browser HTTP adapters
 * and per-request scope reauthorization. No fallback to demo sessions.
 *
 * This code DOES NOT open DB connections, deploy a Keycloak realm, install
 * migrations, or grant permission to a patient. Infrastructure supplies actual
 * TLS Postgres clients with distinct least-privilege roles.
 */
import {loadTrustedStaffIdentityConfig} from "./oidc-deployment-config.mjs";
import {postgresOidcReplayStore} from "./oidc-replay-postgres.mjs";
import {createPostgresStaffSessionStore} from "./postgres-staff-session-store.mjs";
import {createIdentitySecurityAuditWriter} from "./identity-security-audit.mjs";
import {createStaffSessionService} from "./oidc-staff-service.mjs";
import {requireAuditedStaffLogin} from "./audited-staff-login.mjs";
import {requireAuditedStaffLogout} from "./audited-staff-logout.mjs";
import {createStaffAuthHttpHandlers} from "./staff-auth-http-handlers.mjs";
import {verifyIdentitySqlClients} from "./identity-db-role-verification.mjs";
import {requireClinicalIdentitySafety} from "./clinical-identity-safety.mjs";
export class StaffIdentityRuntimeError extends Error{
 constructor(){super("staff_identity_runtime_unavailable");this.name="StaffIdentityRuntimeError";}
}
const deny=()=>{throw new StaffIdentityRuntimeError();};

export function createStaffIdentityRuntime({
 env=process.env,replaySql,sessionSql,auditSql,fetcher=fetch,now=Date.now
}={}){
 if(!replaySql||!sessionSql||!auditSql||
    replaySql===sessionSql||replaySql===auditSql||sessionSql===auditSql||
    ![replaySql,sessionSql,auditSql].every(c=>typeof c.query==="function")||
    typeof fetcher!=="function"||typeof now!=="function")deny();
 let cfg,service,replay,session,audit;
 try{
  cfg=loadTrustedStaffIdentityConfig(env);
  replay=postgresOidcReplayStore(replaySql,{now});
  session=createPostgresStaffSessionStore(sessionSql,{env,now});
  audit=createIdentitySecurityAuditWriter(auditSql,{
   pepper:env.SYNQORIA_IDENTITY_AUDIT_PEPPER,now
  });
  service=createStaffSessionService({config:cfg,sessionStore:session,
   replayStore:replay,fetcher,now,publicOrigin:cfg.publicOrigin});
 }catch{deny();}
 const auditedLogin=requireAuditedStaffLogin(service,session,audit);
 const auditedLogout=requireAuditedStaffLogout(service,session,audit,{
  publicOrigin:cfg.publicOrigin
 });
 const serverService=Object.freeze({
  begin:request=>service.begin(request),
  complete:request=>auditedLogin.complete(request),
  authorize:request=>service.authorize(request),
  logout:request=>auditedLogout.logout(request)
 });
 const http=createStaffAuthHttpHandlers(serverService,{
  issuer:cfg.oidc.issuer,publicOrigin:cfg.publicOrigin
 });
 return Object.freeze({
  http,authorize:serverService.authorize,
  // Audited clinical denial writers are never returned to a browser.
  auditWriter:audit,
  configuration:Object.freeze({
   expectedTenantId:cfg.workforce.expectedTenantId,
   expectedFacilityId:cfg.workforce.expectedFacilityId,
   publicOrigin:cfg.publicOrigin
  })
 });
}


/**
 * P03-B0053 deployment entrypoint: no live identity runtime before positive
 * cryptographic and DB-role preflight. The low-level constructor above is
 * retained for hermetic tests and dependency-injected source simulation.
 */
export async function createVerifiedStaffIdentityRuntime(options={}){
 requireClinicalIdentitySafety(options.env);
 await verifyIdentitySqlClients(options);
 return createStaffIdentityRuntime(options);
}
