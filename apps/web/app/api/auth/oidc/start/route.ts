import {dispatchVerifiedStaffAuthRoute} from "../../../../../lib/verified-staff-auth-route.mjs";

export const runtime="nodejs";
export const dynamic="force-dynamic";
export const revalidate=0;

/** Staff OIDC authorization-code + PKCE start. No demo credentials or
 * arbitrary redirect URLs are accepted; the verified runtime must be
 * installed by server-side bootstrap. Unconfigured deployments return 503.
 */
export async function GET(request:Request):Promise<Response>{
 return dispatchVerifiedStaffAuthRoute("start",request);
}
