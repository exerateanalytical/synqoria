import {readTriageAudit} from "../../../../../../../lib/clinical-cell.mjs";
export const runtime="nodejs";
export const dynamic="force-dynamic";
/** Read-only bounded clinical audit projection; no unreviewed reassessment writes. */
export async function GET(request:Request,{params}:{params:Promise<{patientId:string;caseId:string}>}):Promise<Response>{
 const {patientId,caseId}=await params;
 return readTriageAudit(request,patientId,caseId);
}
