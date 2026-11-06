import {ops001ServerAction} from "../../../../../lib/ops001-runtime-bridge.mjs";
export const runtime="nodejs";
export const dynamic="force-dynamic";
/** G-S01 — redirect only from a server-installed, verified OIDC runtime. */
export async function GET(request:Request):Promise<Response>{
 return ops001ServerAction("login",request);
}
