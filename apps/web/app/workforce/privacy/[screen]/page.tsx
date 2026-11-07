import {notFound,redirect} from "next/navigation";
const ids=["M32-S01","M32-S02","M32-S03","M32-S04","M32-S05"] as const;
export function generateStaticParams(){return ids.map(screen=>({screen}));}
export default async function LegacyM32Route({params}:{params:Promise<{screen:string}>}){
 const {screen}=await params;
 if(!(ids as readonly string[]).includes(screen))notFound();
 redirect("/workforce/clinical/"+screen);
}
