import {readCellHealth} from "../../../../lib/clinical-cell.mjs";
export const runtime="nodejs";
export const dynamic="force-dynamic";
export async function GET(request:Request):Promise<Response>{return readCellHealth(request);}
