/**
 * SYNQORIA P03-B0071: server bootstrap from approved deployment credentials.
 *
 * Requires explicitly enabled, validated clinical safety config and runtime
 * trust; there is NO guest fallback. Call once per Node server process at
 * startup, not from an HTTP route or browser-supplied provider selector.
 * Missing pg runtime dependency or any role/RLS check fails closed.
 */
import {createRequire} from "node:module";
import {requireClinicalIdentitySafety} from "./clinical-identity-safety.mjs";
import {loadTrustedStaffIdentityConfig} from "./oidc-deployment-config.mjs";
import {initializeVerifiedStaffRoutes} from "./staff-route-bootstrap.mjs";
import {createRestrictedIdentityPools} from "./identity-production-sql.mjs";
import {createRestrictedIdentityTransport} from "./identity-restricted-transport.mjs";
import {verifyTrustedIdentityProvider} from "./identity-provider-preflight.mjs";
export class IdentityStartupError extends Error{
 constructor(){super("identity_startup_denied");this.name="IdentityStartupError";}
}
const deny=()=>{throw new IdentityStartupError();};
const pgLoader=()=>createRequire(import.meta.url)("pg");
let ongoing=null;
let result=null;
let permanentlyFailed=false;
/** Only enabled exact "1" triggers live identity startup. */
export async function initializeProductionStaffIdentity({
 env=process.env,loadPg=pgLoader,fetcher=fetch,now=Date.now
}={}){
 if(!env||typeof env!=="object")deny();
 const enabled=env.SYNQORIA_IDENTITY_BOOTSTRAP_ENABLED;
 if(enabled===undefined||enabled==="0"){
  if(result!==null||ongoing!==null)deny();
  return Object.freeze({enabled:false,ready:false});
 }
 if(enabled!=="1"||permanentlyFailed)deny();
 if(result!==null)return result;
 if(ongoing!==null)return ongoing;
 if(typeof loadPg!=="function"||typeof fetcher!=="function"||
    typeof now!=="function")deny();
 ongoing=(async()=>{
  let pools=null;
  try{
   requireClinicalIdentitySafety(env);
   const config=loadTrustedStaffIdentityConfig(env);
   await verifyTrustedIdentityProvider(config,{fetcher});
   const loaded=await loadPg();
   pools=await createRestrictedIdentityPools({env,Pool:loaded?.Pool});
   const transport=createRestrictedIdentityTransport(config,{fetcher});
   await initializeVerifiedStaffRoutes({
    env,replaySql:pools.replaySql,sessionSql:pools.sessionSql,
    auditSql:pools.auditSql,fetcher:transport,now
   });
   result=Object.freeze({enabled:true,ready:true});
   return result;
  }catch{
   permanentlyFailed=true;
   if(pools!==null){try{await pools.close();}catch{/* deny startup */}}
   deny();
  }
 })();
 try{return await ongoing;}finally{ongoing=null;}
}
