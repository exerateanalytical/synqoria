/**
 * SYNQORIA P03-B0034 — Authoritative workforce/shift entitlement boundary.
 *
 * A valid OIDC token, MFA assertion or self-asserted role never grants access
 * to a hospital cell. The server alone provisions this HTTPS workforce source,
 * the expected tenant and the expected facility. No patient grants are issued.
 *
 * Source contract V1 is intentionally exact: source must check its own live
 * staff roster, privilege revision, shift and facility membership before
 * returning the single active actor/facility grant.
 */
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[89ab0-9a-f]{12}$/i;
const SCOPE=/^[a-z][a-z0-9:._-]{2,63}$/;
const SUBJECT=/^[A-Za-z0-9@._:|~-]{2,255}$/;
const ROLE=/^[a-z][a-z0-9_-]{1,63}$/;
const JWT=/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/;
const ISO=/^\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\d|3[01])T(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\dZ$/;
const keys="actorId,capabilities,facilityId,issuer,revision,role,shiftEndsAt,shiftId,shiftStartsAt,status,subject,tenantId";
const MAX_BYTES=16384;

export class WorkforceAuthorizationError extends Error {
 constructor(){super("workforce_authorization_denied");this.name="WorkforceAuthorizationError";}
}
const deny=()=>{throw new WorkforceAuthorizationError();};
const validUuid=x=>typeof x==="string"&&UUID.test(x);
function trustedOrigin(origin){
 if(typeof origin!=="string"||origin.length>260)deny();
 let u;
 try{u=new URL(origin);}catch{deny();}
 if(u.protocol!=="https:"||u.username||u.password||u.port||u.search||u.hash||
    u.pathname!=="/"||u.origin+"/"!==origin||!u.hostname.includes(".")||
    ["localhost","127.0.0.1"].includes(u.hostname)||
    [".localhost",".invalid",".test",".example",".local"].some(s=>u.hostname.endsWith(s))||
    /^\d{1,3}(?:\.\d{1,3}){3}$/.test(u.hostname))deny();
 return u.origin;
}
function strictUtc(s){
 if(typeof s!=="string"||!ISO.test(s))return null;
 const n=Date.parse(s);
 if(!Number.isFinite(n)||new Date(n).toISOString()!==s.replace("Z",".000Z"))return null;
 return n;
}
async function safeResponse(response){
 if(response?.status!==200||response.redirected===true||!response.body||
    !response.headers?.get("content-type")?.toLowerCase().startsWith("application/json")||
    !response.headers?.get("cache-control")?.toLowerCase().includes("no-store"))deny();
 const length=response.headers.get("content-length");
 if(length!==null&&(!/^\d{1,5}$/.test(length)||Number(length)>MAX_BYTES))deny();
 const reader=response.body.getReader(),chunks=[];let count=0;
 try{
  for(;;){
   const entry=await reader.read();
   if(entry.done)break;
   count+=entry.value.byteLength;
   if(count>MAX_BYTES){await reader.cancel();deny();}
   chunks.push(entry.value);
  }
 }catch{deny();}finally{reader.releaseLock();}
 const all=new Uint8Array(count);let offset=0;
 for(const c of chunks){all.set(c,offset);offset+=c.byteLength;}
 try{
  const dto=JSON.parse(new TextDecoder("utf-8",{fatal:true}).decode(all));
  if(!dto||typeof dto!=="object"||Array.isArray(dto))deny();
  return dto;
 }catch{deny();}
}
function strictGrant(dto,proof,{issuer,expectedTenantId,expectedFacilityId,nowMs}){
 if(Object.keys(dto).sort().join(",")!==keys||
    dto.status!=="ACTIVE"||dto.issuer!==issuer||dto.subject!==proof.subject||
    !validUuid(dto.actorId)||!validUuid(dto.tenantId)||!validUuid(dto.facilityId)||
    !validUuid(dto.shiftId)||dto.tenantId!==expectedTenantId||
    dto.facilityId!==expectedFacilityId||typeof dto.role!=="string"||!ROLE.test(dto.role)||
    !Number.isSafeInteger(dto.revision)||dto.revision<1||dto.revision>2147483647||
    !Array.isArray(dto.capabilities)||dto.capabilities.length<1||dto.capabilities.length>64||
    new Set(dto.capabilities).size!==dto.capabilities.length||
    !dto.capabilities.every(x=>typeof x==="string"&&SCOPE.test(x) &&
       Array.isArray(proof.accessScopes)&&proof.accessScopes.includes(x)))deny();
 const start=strictUtc(dto.shiftStartsAt),end=strictUtc(dto.shiftEndsAt);
 if(start===null||end===null||start>nowMs||end<=nowMs||
    end-start>24*3600_000||nowMs-start>24*3600_000)deny();
 return Object.freeze({
  actorId:dto.actorId,tenantId:dto.tenantId,facilityId:dto.facilityId,
  shiftId:dto.shiftId,expiresAt:end,revision:dto.revision,role:dto.role,
  capabilities:Object.freeze([...dto.capabilities])
 });
}
/** Staff authority is a server provisioned service, never a supplied client URL. */
export async function resolveStaffWorkforceGrant(proof,accessToken,config,{
 fetcher=fetch,nowMs=Date.now()
}={}){
 if(!proof||typeof proof!=="object"||!SUBJECT.test(proof.subject)||
    !Array.isArray(proof.accessScopes)||proof.accessScopes.length>64||
    !Number.isSafeInteger(nowMs)||!config||typeof config!=="object"||
    typeof config.issuer!=="string"||!config.issuer||
    !validUuid(config.expectedTenantId)||!validUuid(config.expectedFacilityId)||
    typeof accessToken!=="string"||accessToken.length>16384||!JWT.test(accessToken)||
    typeof fetcher!=="function")deny();
 const origin=trustedOrigin(config.workforceOrigin);
 const body=JSON.stringify({issuer:config.issuer,subject:proof.subject});
 const controller=new AbortController(),timeout=setTimeout(()=>controller.abort(),7000);
 try{
  const response=await fetcher(origin+"/internal/v1/staff/grants/active",{
   method:"POST",headers:{Accept:"application/json","Content-Type":"application/json",
    Authorization:"Bearer "+accessToken},
   body,redirect:"error",credentials:"omit",cache:"no-store",signal:controller.signal
  });
  const dto=await safeResponse(response);
  return strictGrant(dto,proof,{...config,nowMs});
 }catch{deny();}
 finally{clearTimeout(timeout);}
}
