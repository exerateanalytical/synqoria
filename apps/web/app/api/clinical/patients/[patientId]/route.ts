import {readPatient} from "../../../../../lib/clinical-cell.mjs";
export const runtime="nodejs";
export const dynamic="force-dynamic";
export async function GET(request:Request,{params}:{params:Promise<{patientId:string}>}):Promise<Response>{
 const {patientId}=await params;
 return readPatient(request,patientId);
}
