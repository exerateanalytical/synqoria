import {notFound} from "next/navigation";
import {PrivacyWorkbench,type Screen} from "../../../../components/privacy/PrivacyWorkbench";
import styles from "../privacy.module.css";

const IDS:Screen[]=["M32-S01","M32-S02","M32-S03","M32-S04","M32-S05"];
export function generateStaticParams(){return IDS.map(screen=>({screen}));}
export default async function PrivacyScreen({params}:{params:Promise<{screen:string}>}){
 const {screen}=await params;
 if(!IDS.includes(screen as Screen))notFound();
 return <main className={styles.root} id="main">
  <a className={styles.skip} href="#main">Skip to content / Passer au contenu</a>
  <PrivacyWorkbench screen={screen as Screen}/>
 </main>;
}
