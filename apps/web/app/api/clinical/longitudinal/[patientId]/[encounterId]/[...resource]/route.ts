import {readLongitudinal} from "../../../../../../lib/longitudinal-cell.mjs";
import type {ClinicalSourceResource} from "../../../../../../lib/longitudinal-cell.mjs";
export const runtime="nodejs";
export const dynamic="force-dynamic";
/**
 * Strictly allowlisted M04–M06 read routes. Never accept a browser-provided
 * bearer, tenant, facility, clinician, capability or clinical evidence.
 */
export async function GET(request:Request,{params}:{params:Promise<{patientId:string;encounterId:string;resource:string[]}>}):Promise<Response>{
 const {patientId,encounterId,resource:segments}=await params;
 if(!Array.isArray(segments)||segments.length>3)return new Response(JSON.stringify({status:404,code:"not_found"}),{status:404,headers:{"Cache-Control":"no-store, private","Content-Type":"application/json"}});
 let kind:ClinicalSourceResource|null=null,memberId:string|null=null;
 if(segments.length===1&&["summary","audit","note-index","observations"].includes(segments[0]))
  kind=segments[0] as ClinicalSourceResource;
 else if(segments.length===2&&segments[0]==="notes"){
  kind="note";memberId=segments[1];
 }else if(segments.length===3&&segments[0]==="observations"&&segments[2]==="lineage"){
  kind="lineage";memberId=segments[1];
 }
 if(!kind)return new Response(JSON.stringify({status:404,code:"not_found"}),{status:404,headers:{"Cache-Control":"no-store, private","Content-Type":"application/json"}});
 return readLongitudinal(request,patientId,encounterId,kind,memberId);
}
