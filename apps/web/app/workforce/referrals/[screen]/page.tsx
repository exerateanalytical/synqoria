import {notFound} from "next/navigation";
import {ReferralWorkbench,type Screen} from "../../../../components/referrals/ReferralWorkbench";
const ids:Screen[]=["M16-S01","M16-S02","M16-S03","M16-S04","M16-S05"];
export function generateStaticParams(){return ids.map(screen=>({screen}));}
export default async function ReferralScreen({params}:{params:Promise<{screen:string}>}){
 const {screen}=await params;
 if(!ids.includes(screen as Screen))notFound();
 return <main><ReferralWorkbench screen={screen as Screen}/></main>;
}
