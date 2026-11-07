import {notFound} from "next/navigation";
import {IntegrationWorkbench,type Screen} from "../../../../components/integrations/IntegrationWorkbench";
const ids:Screen[]=["M35-S01","M35-S02","M35-S03","M35-S04","M35-S05","M35-S06"];
export function generateStaticParams(){return ids.map(screen=>({screen}));}
export default async function IntegrationScreen({params}:{params:Promise<{screen:string}>}){
 const {screen}=await params;
 if(!ids.includes(screen as Screen))notFound();
 return <main><IntegrationWorkbench screen={screen as Screen}/></main>;
}
