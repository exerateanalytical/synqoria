import {notFound,redirect} from "next/navigation";
import catalog from "../../../../../lib/operations-screen-catalog.json";
import mortuary from "../../../../../lib/mortuary-screen-catalog.json";
type Params={params:Promise<{module:string;screen:string}>};
export function generateStaticParams(){
 return [...catalog.modules.flatMap(module=>module.screens.map(screen=>({
  module:module.id,screen:screen.id
 }))),...mortuary.screens.map(screen=>({module:"M27",screen:screen.id}))];
}
export default async function LegacyOperationsScreen({params}:Params){
 const {module,screen}=await params;
 const valid=module==="M27"?mortuary.screens.some(x=>x.id===screen):
  catalog.modules.some(x=>x.id===module&&x.screens.some(s=>s.id===screen));
 if(!valid)notFound();
 redirect("/workforce/clinical/"+screen);
}
