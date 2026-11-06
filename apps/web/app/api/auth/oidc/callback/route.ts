import {dispatchVerifiedStaffAuthRoute} from "../../../../../lib/verified-staff-auth-route.mjs";

export const runtime="nodejs";
export const dynamic="force-dynamic";
export const revalidate=0;

/** OIDC callback: consume cryptographic one-use state, signed IdP claims,
 * active workforce permission, audited opaque session. The shared server
 * runtime owns the complete pipeline; no JWT or password is exposed here.
 */
export async function GET(request:Request):Promise<Response>{
 return dispatchVerifiedStaffAuthRoute("callback",request);
}
