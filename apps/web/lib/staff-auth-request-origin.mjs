/**
 * SYNQORIA P03-B0051: authoritative public-origin and browser intent guard.
 *
 * Reverse proxy must supply the canonical, non-user-overridable request.url
 * and Host; never substitute X-Forwarded-Host from an untrusted request.
 * OIDC callback alone permits a cross-site browser navigation because the
 * PKCE + one-time state coordinator separately authenticates the callback.
 */
export class StaffAuthRequestOriginError extends Error{
 constructor(){super("staff_auth_request_denied");this.name="StaffAuthRequestOriginError";}
}
const deny=()=>{throw new StaffAuthRequestOriginError();};
const routes=Object.freeze({
 start:{method:"GET",path:"/api/auth/oidc/start"},
 callback:{method:"GET",path:"/api/auth/oidc/callback"},
 logout:{method:"POST",path:"/api/auth/logout"}
});
export function requireCanonicalStaffAuthRequest(request,{
 publicOrigin,operation
}={}){
 const scope=routes[operation];
 if(!scope||!request||typeof request.url!=="string"||
    !request.headers||typeof request.headers.get!=="function"||
    typeof publicOrigin!=="string")deny();
 let publicUrl,actual;
 try{publicUrl=new URL(publicOrigin);actual=new URL(request.url);}catch{deny();}
 if(publicUrl.protocol!=="https:"||publicUrl.origin+"/"!==publicOrigin||
    publicUrl.username||publicUrl.password||publicUrl.port||publicUrl.search||
    publicUrl.hash||publicUrl.pathname!=="/"||
    !publicUrl.hostname.includes(".")||
    publicUrl.hostname.endsWith(".invalid")||
    actual.protocol!=="https:"||actual.origin!==publicUrl.origin||
    actual.username||actual.password||actual.port||
    actual.pathname!==scope.path||actual.hash||
    request.method!==scope.method)deny();
 const host=request.headers.get("host");
 if(host!==null&&host.toLowerCase()!==publicUrl.host.toLowerCase())deny();
 const mode=request.headers.get("sec-fetch-site");
 if(mode!==null&& !["same-origin","none","cross-site","same-site"].includes(mode))deny();
 if(operation!=="callback"&&mode!==null&&!["same-origin","none"].includes(mode))deny();
 if(operation!=="callback"&&actual.search)deny();
 if(operation==="logout"&&request.headers.get("origin")!==publicUrl.origin)deny();
 return Object.freeze({operation,origin:publicUrl.origin});
}
