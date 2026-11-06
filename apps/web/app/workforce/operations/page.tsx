import Link from "next/link";
import catalog from "../../../lib/operations-screen-catalog.json";
import styles from "./operations.module.css";

/** Fixed 24px outline geometry avoids cross-workspace React type skew. */
const outlines:Record<string,string>={
 M21:"M3 3h2l2 12h12l2-8H6 M9 20h.01 M18 20h.01",
 M22:"m12 2 9 5-9 5-9-5 9-5 M3 7v10l9 5 9-5V7 M12 12v10",
 M23:"M14 7a6 6 0 0 1 8-3l-4 4 2 2 4-4a6 6 0 0 1-8 8L6 24l-5-5 10-10a6 6 0 0 1 3-2",
 M25:"M3 6h12v11H3z M15 10h4l3 4v3h-7 M7 20h.01 M18 20h.01",
 M29:"M4 22V4h12v18 M16 10h4v12 M8 8h.01 M12 8h.01 M8 12h.01 M12 12h.01 M8 16h.01 M12 16h.01",
 M31:"M5 2h9l5 5v15H5z M14 2v5h5 M8 12h8 M8 16h8",
 M37:"M18 8a6 6 0 0 0-12 0c0 8-3 8-3 10h18c0-2-3-2-3-10 M10 22h4"
};
function OperationIcon({id}:{id:string}){
 return <svg viewBox="0 0 24 24" width="24" height="24" fill="none"
  stroke="currentColor" strokeWidth="1.7" strokeLinecap="round"
  strokeLinejoin="round" focusable="false" aria-hidden="true">
  <path d={outlines[id]}/>
 </svg>;
}
export default function SevenOperationsCatalogue(){
 return <main className={styles.root} id="main">
  <a className={styles.skip} href="#modules">Skip to modules / Passer aux modules</a>
  <header className={styles.header}>
   <p className={styles.eyebrow}>SYNQORIA / Hospital operations / Gestion hospitalière</p>
   <h1>Operations modules / Modules opérationnels</h1>
   <p>Seven canonical modules and 35 functional staff workstations, available only after
    a verified hospital login, authoritative facility/shift grants and database-backed permissions.</p>
   <p className={styles.warning} role="status">
    Hospital records and actions remain inaccessible without configured identity, roster,
    clinical or supplier evidence and a protected operational service.
    Accès soumis à autorisation institutionnelle.
   </p>
  </header>
  <section id="modules" className={styles.modules} aria-label="Seven operations modules">
   {catalog.modules.map(module=>{
    return <article className={styles.module} key={module.id}>
     <div className={styles.moduleHead}><OperationIcon id={module.id}/>
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
  <footer className={styles.footer}>Secure workstations use a server-only session gateway. Orders and approvals require
   verified institutional authorizations. No automated clinical decisions.</footer>
 </main>;
}
