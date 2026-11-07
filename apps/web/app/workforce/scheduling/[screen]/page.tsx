import {notFound,redirect} from "next/navigation";
const ids=["M02-S01","M02-S02","M02-S03","M02-S04","M02-S05"] as const;
export function generateStaticParams(){return ids.map(screen=>({screen}));}
export default async function SchedulingLegacyRoute({params}:{params:Promise<{screen:string}>}){
 const {screen}=await params;
 if(!(ids as readonly string[]).includes(screen))notFound();
 redirect("/workforce/clinical/"+screen);
}
