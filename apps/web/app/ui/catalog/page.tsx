import Link from "next/link";
import registry from "../../../../../registers/canonical-screens.json";
import "./catalog.css";
type Screen={id:string;module:string;name:string;layout:string;data_input_groups:string;primary_action:string};
const screens=registry.screen_register as Screen[];
const modules=Array.from(new Set(screens.map(s=>s.module)));
export default function Catalog(){
 return <main className="syn-catalog">
  <header className="syn-catalog-head"><div><p className="syn-kicker">SYNQORIA · UI design system</p><h1>Clinical interface catalogue</h1><p>261 canonical screen previews across 40 modules and 12 shared screens. Design-only routes; clinical actions remain locked.</p></div><span className="syn-chip">No live patient data</span></header>
  <div className="syn-quick"><Link href="/workforce">Workforce shell</Link><Link href="/portal">Portal shell</Link><Link href="/platform">Platform shell</Link><Link href="/ui/catalog/workflows">14 canonical workflow maps</Link><Link href="/ui/printables">376 structured hospital forms</Link><Link href="/hospital-documents">Complete document catalogue</Link><Link href="/workforce/clinical">Real staff implementation routes (partial)</Link></div>
  {modules.map(module=><section id={module.toLowerCase().replace(/[^a-z0-9]+/g,"-")} key={module} className="syn-panel"><div className="syn-section-head"><h2>{module==="SHARED"?"Shared workspace":module}</h2><span className="syn-chip">{screens.filter(s=>s.module===module).length} screens</span></div><div className="syn-cards">{screens.filter(s=>s.module===module).map(s=><Link key={s.id} href={"/ui/catalog/"+s.id}><span>{s.id}</span><strong>{s.name}</strong><small>{s.layout==="F"?"Form":s.layout==="L"?"List":s.layout==="H"?"Dashboard":"Detail / workflow"} · Preview</small></Link>)}</div></section>)}
 </main>;
}