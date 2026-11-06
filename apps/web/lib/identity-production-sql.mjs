/**
 * SYNQORIA P03-B0069: deployment-owned, role-separated TLS PostgreSQL pools.
 *
 * Requires externally provisioned, independently restricted PostgreSQL roles.
 * Pool construction does not grant roles, install migrations, or prove RLS.
 * initializeVerifiedStaffRoutes additionally attests role and policy state
 * using these same client objects before permitting any auth route.
 */
import {createConnectionAttestedSqlClient} from "./identity-attested-db-query.mjs";
const SPECS=Object.freeze([
 Object.freeze({key:"replaySql",env:"SYNQORIA_IDENTITY_REPLAY_DSN",role:"synqoria_oidc_replay_runtime"}),
 Object.freeze({key:"sessionSql",env:"SYNQORIA_IDENTITY_SESSION_DSN",role:"synqoria_staff_session_runtime"}),
 Object.freeze({key:"auditSql",env:"SYNQORIA_IDENTITY_AUDIT_DSN",role:"synqoria_identity_audit_writer"})
]);
export class IdentityProductionSqlError extends Error{
 constructor(){super("identity_production_sql_denied");this.name="IdentityProductionSqlError";}
}
const deny=()=>{throw new IdentityProductionSqlError();};
const forbidden=/(\.localhost|\.local|\.invalid|\.test|\.example)$/i;
function parseDsn(value,role){
 if(typeof value!=="string"||value.length<55||value.length>4096)deny();
 let url;
 try{url=new URL(value);}catch{deny();}
 if(url.protocol!=="postgresql:"||url.username!==role||
   !url.password||url.password.length<24||url.password.length>1024||
   !url.hostname.includes(".")||forbidden.test(url.hostname)||
   /^[\d.:]+$/.test(url.hostname)||url.search||url.hash||
   !/^\/[a-z][a-z0-9_]{2,62}$/.test(url.pathname)||
   (url.port!==""&&(!/^\d{2,5}$/.test(url.port)||Number(url.port)>65535))||
   url.href!==value)deny();
 return url;
}
function trustedCa(pem){
 if(typeof pem!=="string"||pem.length<100||pem.length>65536||
   !/^-----BEGIN CERTIFICATE-----\r?\n(?:[A-Za-z0-9+/=]+\r?\n)+-----END CERTIFICATE-----\r?\n?$/.test(pem))deny();
 return pem;
}
/**
 * pg.Pool is supplied by the trusted server entrypoint, never from the HTTP
 * request. Pinned CA + rejectUnauthorized cannot be disabled by a DSN option.
 * One connection per role per process prevents unverified pool fanout.
 */
export async function createRestrictedIdentityPools({env,Pool}={}){
 if(!env||typeof env!=="object"||typeof Pool!=="function")deny();
 const ca=trustedCa(env.SYNQORIA_IDENTITY_DB_CA_PEM);
 const endpoints=SPECS.map(s=>({...s,url:parseDsn(env[s.env],s.role)}));
 if(new Set(endpoints.map(x=>x.url.href)).size!==3)deny();
 const pools=[];
 try{
  for(const item of endpoints){
   const p=new Pool({
    connectionString:item.url.href,
    ssl:{ca,rejectUnauthorized:true,servername:item.url.hostname},
    max:1,min:0,connectionTimeoutMillis:5000,
    idleTimeoutMillis:15000,allowExitOnIdle:false,
    application_name:"synqoria-identity-"+item.key
   });
   if(!p||typeof p.connect!=="function"||typeof p.end!=="function")deny();
   pools.push(p);
  }
 }catch{
  await Promise.allSettled(pools.map(p=>p.end()));
  deny();
 }
 let clients;
 try{clients=endpoints.map((item,i)=>createConnectionAttestedSqlClient(pools[i],item.role));}
 catch{await Promise.allSettled(pools.map(p=>p.end()));deny();}
 let closed=false;
 return Object.freeze({
  replaySql:clients[0],sessionSql:clients[1],auditSql:clients[2],
  async close(){
   if(closed)return;
   closed=true;
   const results=await Promise.allSettled(pools.map(p=>p.end()));
   if(results.some(x=>x.status==="rejected"))deny();
  }
 });
}
