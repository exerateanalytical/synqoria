import {dispatchVerifiedStaffAuthRoute} from "../../../../lib/verified-staff-auth-route.mjs";

export const runtime="nodejs";
export const dynamic="force-dynamic";
export const revalidate=0;

/** Staff logout is a POST only operation. The trusted identity runtime
 * enforces exact-origin request, server-side revocation, pseudonymous audit,
 * and browser cookie expiration. No user-controlled redirect is accepted.
 */
export async function POST(request:Request):Promise<Response>{
 return dispatchVerifiedStaffAuthRoute("logout",request);
}
