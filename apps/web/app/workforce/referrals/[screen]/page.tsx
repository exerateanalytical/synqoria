import {notFound,redirect} from "next/navigation";
const ids=["M16-S01","M16-S02","M16-S03","M16-S04","M16-S05"] as const;
export function generateStaticParams(){return ids.map(screen=>({screen}));}
export default async function ReferralLegacyRoute({params}:{params:Promise<{screen:string}>}){
 const {screen}=await params;
 if(!(ids as readonly string[]).includes(screen))notFound();
 redirect("/workforce/clinical/"+screen);
}
