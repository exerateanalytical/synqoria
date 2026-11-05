import {notFound,redirect} from "next/navigation";
import ClinicalReadWorkspace from "../ClinicalReadWorkspace";
import type {LiveScreenId} from "../ClinicalReadWorkspace";
import StaffPreferences from "../StaffPreferences";
export const dynamic="force-dynamic";
const ids=["G-S03","G-S06","G-S10","M01-S03","M03-S03","M31-S01","M31-S03"] as const;
export function generateStaticParams(){return ids.map(screenId=>({screenId}));}
export default async function OperationalScreen({params}:{params:Promise<{screenId:string}>}){
 const {screenId}=await params;
 if(!ids.some(x=>x===screenId))notFound();
 if(screenId==="G-S06")return <StaffPreferences/>;
 if(screenId==="M31-S01")redirect("/hospital-documents");
 if(screenId==="M31-S03")redirect("/ui/printables");
 return <ClinicalReadWorkspace screenId={screenId as LiveScreenId}/>;
}
