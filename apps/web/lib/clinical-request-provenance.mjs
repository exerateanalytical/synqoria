/**
 * SYNQORIA P03-B0058: clinical BFF request provenance gate.
 *
 * No cross-site embedding, forged browser Host, downgrade to HTTP or
 * cross-origin unsafe requests may enter the hospital cell BFF. Configured
 * only from the trusted web deployment origin, not caller headers.
 * This is defense in depth beside Secure SameSite staff cookies.
 */
export class ClinicalRequestProvenanceError extends Error{
 constructor(){super("clinical_request_provenance_denied");this.name="ClinicalRequestProvenanceError";}
}
const deny=()=>{throw new ClinicalRequestProvenanceError();};
const allowedMethods=new Set(["GET","HEAD","POST","PUT","PATCH","DELETE"]);
export function requireClinicalRequestProvenance(request,publicOrigin){
 if(!request||typeof request.url!=="string"||!request.headers||
    typeof request.headers.get!=="function"||typeof publicOrigin!=="string")deny();
 let trusted,actual;
 try{trusted=new URL(publicOrigin);actual=new URL(request.url);}catch{deny();}
 if(trusted.protocol!=="https:"||trusted.origin+"/"!==publicOrigin||
    trusted.port||trusted.username||trusted.password||trusted.pathname!=="/"||
    trusted.search||trusted.hash||!trusted.hostname.includes(".")||
    actual.protocol!=="https:"||actual.origin!==trusted.origin||
    actual.port||actual.username||actual.password||actual.hash||
    !allowedMethods.has(request.method))deny();
 const host=request.headers.get("host");
 if(host!==null&&host.toLowerCase()!==trusted.host.toLowerCase())deny();
 const site=request.headers.get("sec-fetch-site");
 if(site!==null&&!["same-origin","none"].includes(site))deny();
 const origin=request.headers.get("origin");
 if(origin!==null&&origin!==trusted.origin)deny();
 if(!["GET","HEAD"].includes(request.method)&&origin!==trusted.origin)deny();
 return Object.freeze({allowed:true,method:request.method});
}
