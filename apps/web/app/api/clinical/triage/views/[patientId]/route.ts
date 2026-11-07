import {readTriageWorklist} from "../../../../../../lib/clinical-cell.mjs";
export const runtime="nodejs";
export const dynamic="force-dynamic";
/** Explicit-case M03 worklist POST: read-only semantics, no clinical writes or token exposure. */
export async function POST(request:Request,{params}:{params:Promise<{patientId:string}>}):Promise<Response>{
 const {patientId}=await params;
 return readTriageWorklist(request,patientId);
}
