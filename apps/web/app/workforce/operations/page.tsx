import Link from "next/link";
import {ShoppingCart,Package,Wrench,Truck,Building2,FileText,Bell} from "lucide-react";
import catalog from "../../../lib/operations-screen-catalog.json";
import styles from "./operations.module.css";

const icons={M21:ShoppingCart,M22:Package,M23:Wrench,M25:Truck,
 M29:Building2,M31:FileText,M37:Bell} as const;
export default function SevenOperationsCatalogue(){
 return <main className={styles.root} id="main">
  <a className={styles.skip} href="#modules">Skip to modules / Passer aux modules</a>
  <header className={styles.header}>
   <p className={styles.eyebrow}>SYNQORIA / Hospital operations / Gestion hospitalière</p>
   <h1>Operations modules / Modules opérationnels</h1>
   <p>Seven canonical modules, 35 mapped workstations. These are nonclinical
    locked UI previews until verified staff/facility identity, database and
    document or device sign-off connections are reviewed.</p>
   <p className={styles.warning} role="status">
    No live staff session, hospital data or executable workflow is connected.
    Les opérations en production ne sont pas autorisées.
   </p>
  </header>
  <section id="modules" className={styles.modules} aria-label="Seven operations modules">
   {catalog.modules.map(module=>{
    const Icon=icons[module.id as keyof typeof icons];
    return <article className={styles.module} key={module.id}>
     <div className={styles.moduleHead}><Icon size={24} strokeWidth={1.8} aria-hidden="true"/>
      <span className={styles.moduleId}>{module.id}</span></div>
     <h2>{module.title}</h2>
     <p className={styles.fr}>{module.titleFr}</p>
     <p className={styles.counter}>{module.screens.length} canonical screens</p>
     <nav aria-label={module.title+" screen navigation"} className={styles.screenList}>
      {module.screens.map(screen=><Link
        href={`/workforce/operations/${module.id}/${screen.id}`}
        key={screen.id}><span>{screen.id}</span> {screen.name}</Link>)}
     </nav>
    </article>;
   })}
  </section>
  <footer className={styles.footer}>Engineering preview. No authenticated records, orders,
   notification dispatches, document signatures or clinical decisions are available.</footer>
 </main>;
}
