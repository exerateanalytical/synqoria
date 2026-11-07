import {notFound} from "next/navigation";
import {SchedulingWorkbench,type Screen} from "../../../../components/scheduling/SchedulingWorkbench";
const ids:Screen[]=["M02-S01","M02-S02","M02-S03","M02-S04","M02-S05"];
export function generateStaticParams(){return ids.map(screen=>({screen}));}
export default async function SchedulingScreen({params}:{params:Promise<{screen:string}>}){
 const {screen}=await params;
 if(!ids.includes(screen as Screen))notFound();
 return <main id="main"><SchedulingWorkbench screen={screen as Screen}/></main>;
}
