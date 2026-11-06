import Link from "next/link";
import {notFound} from "next/navigation";
import catalog from "../../../../../lib/operations-screen-catalog.json";
import styles from "../../operations.module.css";

type Params={params:Promise<{module:string;screen:string}>};
export function generateStaticParams(){
 return catalog.modules.flatMap(module=>module.screens.map(screen=>({
  module:module.id,screen:screen.id
 })));
}
export default async function OperationsCanonicalScreen({params}:Params){
 const {module,screen}=await params;
 const m=catalog.modules.find(x=>x.id===module);
 const s=m?.screens.find(x=>x.id===screen);
 if(!m||!s)notFound();
 const fields=s.dataInputGroups.split(/,|;/).map(x=>x.trim()).filter(Boolean);
 return <main className={styles.root} id="main">
  <a className={styles.skip} href="#content">Skip to content / Passer au contenu</a>
  <nav aria-label="Breadcrumb" className={styles.crumb}>
   <Link href="/workforce/operations">Operations / Modules</Link>
   <span aria-hidden="true">/</span><span>{m.title}</span>
   <span aria-hidden="true">/</span><span aria-current="page">{s.id}</span>
  </nav>
  <header className={styles.header} id="content">
   <p className={styles.eyebrow}>{m.id} · {s.id} · {m.titleFr}</p>
   <h1>{s.name}</h1>
   <p className={styles.fr}>{s.nameFr}</p>
   <p className={styles.warning} role="status">
    <strong>Locked / Accès verrouillé.</strong> This screen is a verified design
    mapping, not a live patient, financial or operational workstation.
    No form, approval, delivery or signature is permitted without verified hospital access.
   </p>
  </header>
  <section className={styles.module} aria-labelledby="required-title">
   <h2 id="required-title">Required workflow information / Informations du flux</h2>
   <p>The fields below are canonical source-design groups, not active data-entry fields.</p>
   <dl className={styles.requirements}>
    {fields.map((field,index)=><div key={index}>
     <dt>{`Group ${index+1}`}</dt><dd>{field}</dd>
    </div>)}
   </dl>
   <div className={styles.actions}>
    <button type="button" disabled aria-disabled="true"
      aria-describedby="action-reason">{s.primaryAction}</button>
    <p id="action-reason" className={styles.caption}>Action disabled until trusted
     staff and facility authorization, transactional API and module-specific evidence are available.</p>
   </div>
  </section>
  <nav className={styles.screenList} aria-label="Other module screens">
   {m.screens.map(item=><Link key={item.id}
    aria-current={item.id===s.id?"page":undefined}
    href={`/workforce/operations/${m.id}/${item.id}`}>{item.id} — {item.name}</Link>)}
  </nav>
 </main>;
}
