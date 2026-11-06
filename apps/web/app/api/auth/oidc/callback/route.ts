import {ops001ServerAction} from "../../../../../lib/ops001-runtime-bridge.mjs";
export const runtime="nodejs";
export const dynamic="force-dynamic";
export async function GET(request:Request):Promise<Response>{
 return ops001ServerAction("callback",request);
}
