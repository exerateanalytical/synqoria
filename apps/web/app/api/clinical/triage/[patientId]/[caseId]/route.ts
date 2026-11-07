import {readTriageCase} from "../../../../../../lib/clinical-cell.mjs";
export const runtime="nodejs";
export const dynamic="force-dynamic";
export async function GET(request:Request,{params}:{params:Promise<{patientId:string;caseId:string}>}):Promise<Response>{
 const {patientId,caseId}=await params;
 return readTriageCase(request,patientId,caseId);
}
