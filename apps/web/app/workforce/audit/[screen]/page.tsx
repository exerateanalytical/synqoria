import {notFound} from "next/navigation";
import {AuditWorkbench,type Screen} from "../../../../components/audit/AuditWorkbench";
import styles from "../audit.module.css";
const ids:Screen[]=["M34-S01","M34-S02","M34-S03","M34-S04","M34-S05"];
export function generateStaticParams(){return ids.map(screen=>({screen}));}
export default async function AuditScreen({params}:{params:Promise<{screen:string}>}){
 const {screen}=await params;
 if(!ids.includes(screen as Screen))notFound();
 return <main className={styles.root} id="main">
  <a className={styles.skip} href="#content">Skip to content / Passer au contenu</a>
  <div id="content"><AuditWorkbench screen={screen as Screen}/></div>
 </main>;
}
