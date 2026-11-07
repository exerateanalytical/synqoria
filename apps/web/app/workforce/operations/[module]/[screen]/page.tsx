import Link from "next/link";
import {notFound} from "next/navigation";
import catalog from "../../../../../lib/operations-screen-catalog.json";
import mortuary from "../../../../../lib/mortuary-screen-catalog.json";
import {MortuaryWorkbench} from "../../../../../components/MortuaryWorkbench";
import {OperationsWorkbench} from "../../../../../components/OperationsWorkbench";
import styles from "../../operations.module.css";

type Params={params:Promise<{module:string;screen:string}>};
export function generateStaticParams(){
 return [
  ...catalog.modules.flatMap(module=>module.screens.map(screen=>({
   module:module.id,screen:screen.id
  }))),
  ...mortuary.screens.map(screen=>({module:"M27",screen:screen.id}))
 ];
}
export default async function OperationsCanonicalScreen({params}:Params){
 const {module,screen}=await params;
 if(module==="M27"){
  const selected=mortuary.screens.find(x=>x.id===screen);
  if(!selected)notFound();
  return <main className={styles.root} id="main">
   <a className={styles.skip} href="#content">Skip to content / Passer au contenu</a>
   <nav aria-label="Breadcrumb" className={styles.crumb}>
    <Link href="/workforce/operations">Operations / Modules</Link>
    <span aria-hidden="true">/</span>
    <Link href="/workforce/mortuary">M27 · Mortuary</Link>
    <span aria-hidden="true">/</span><span aria-current="page">{selected.id}</span>
   </nav>
   <section id="content" aria-label="Scoped mortuary workstation">
    <MortuaryWorkbench screenId={selected.id} name={selected.name}
     nameFr={selected.nameFr} primaryAction={selected.primaryAction}/>
   </section>
   <nav className={styles.screenList} aria-label="Mortuary workstation navigation">
    {mortuary.screens.map(item=><Link key={item.id}
     aria-current={item.id===selected.id?"page":undefined}
     href={`/workforce/operations/M27/${item.id}`}>{item.id} — {item.name}</Link>)}
   </nav>
  </main>;
 }
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
