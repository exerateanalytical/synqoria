import Link from "next/link";
import {notFound} from "next/navigation";
import catalog from "../../../../../lib/operations-screen-catalog.json";
import {OperationsWorkbench} from "../../../../../components/OperationsWorkbench";
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
 return <main className={styles.root} id="main">
  <a className={styles.skip} href="#content">Skip to content / Passer au contenu</a>
  <nav aria-label="Breadcrumb" className={styles.crumb}>
   <Link href="/workforce/operations">Operations / Modules</Link>
   <span aria-hidden="true">/</span><span>{m.title}</span>
   <span aria-hidden="true">/</span><span aria-current="page">{s.id}</span>
  </nav>
  <section id="content" aria-label="Hospital operations workstation">
   <OperationsWorkbench moduleId={m.id} screenId={s.id}
    name={s.name} nameFr={s.nameFr}
    action={s.primaryAction} inputGroups={s.dataInputGroups}/>
  </section>
  <nav className={styles.screenList} aria-label="Other module workstations">
   {m.screens.map(item=><Link key={item.id}
    aria-current={item.id===s.id?"page":undefined}
    href={`/workforce/operations/${m.id}/${item.id}`}>
    {item.id} — {item.name}</Link>)}
  </nav>
 </main>;
}
