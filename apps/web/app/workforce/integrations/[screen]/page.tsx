import {notFound,redirect} from "next/navigation";
const ids=["M35-S01","M35-S02","M35-S03","M35-S04","M35-S05","M35-S06"] as const;
export function generateStaticParams(){return ids.map(screen=>({screen}));}
export default async function IntegrationLegacyRoute({params}:{params:Promise<{screen:string}>}){
 const {screen}=await params;
 if(!(ids as readonly string[]).includes(screen))notFound();
 redirect("/workforce/clinical/"+screen);
}
