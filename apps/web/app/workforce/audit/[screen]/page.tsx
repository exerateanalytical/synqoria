import {notFound,redirect} from "next/navigation";
const ids=["M34-S01","M34-S02","M34-S03","M34-S04","M34-S05"] as const;
export function generateStaticParams(){return ids.map(screen=>({screen}));}
export default async function LegacyM34Route({params}:{params:Promise<{screen:string}>}){
 const {screen}=await params;
 if(!(ids as readonly string[]).includes(screen))notFound();
 redirect("/workforce/clinical/"+screen);
}
