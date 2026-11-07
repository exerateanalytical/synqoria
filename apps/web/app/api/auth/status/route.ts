import {ops001PublicStatusResponse} from "../../../../lib/ops001-runtime-bridge.mjs";
export const runtime="nodejs";
export const dynamic="force-dynamic";
export async function GET():Promise<Response>{return ops001PublicStatusResponse();}
