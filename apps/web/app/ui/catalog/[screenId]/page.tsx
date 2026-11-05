import Link from "next/link";
import {notFound} from "next/navigation";
import registry from "../../../../../../registers/canonical-screens.json";
import {buildScreenContract} from "../screen-contract.mjs";
import {ScreenPreviewClient} from "../ScreenPreviewClient";
import type {ScreenContract} from "../ScreenPreviewClient";
import "../catalog.css";

type CanonicalScreen={id:string;module:string;name:string;layout:string;data_input_groups:string;primary_action:string};
const screens=registry.screen_register as CanonicalScreen[];
const modules=Array.from(new Set(screens.map(s=>s.module)));
export function generateStaticParams(){return screens.map(s=>({screenId:s.id}));}
export default async function ScreenCatalogueDetail({params}:{params:Promise<{screenId:string}>}){
 const {screenId}=await params;
 const s=screens.find(x=>x.id===screenId);
 if(!s)notFound();
 const peers=screens.filter(x=>x.module===s.module);
 const i=peers.findIndex(x=>x.id===s.id);
 const contract=buildScreenContract(s) as ScreenContract;
 const href=(id:string)=>"/ui/catalog/"+encodeURIComponent(id);
 return <div className="syn-ui">
  <a className="syn-skip" href="#screen-content">Skip to screen content</a>
  <aside className="syn-rail" aria-label="Canonical module catalogue">
   <Link className="syn-brand" href="/ui/catalog">SYNQORIA <small>Africa medical intelligence synchronized</small></Link>
   <nav className="syn-modules">{modules.map(m=><a key={m} href={"/ui/catalog#"+m.toLowerCase()} aria-current={m===s.module?"page":undefined}>{m==="SHARED"?"Shared":m}<span>{screens.filter(x=>x.module===m).length}</span></a>)}</nav>
  </aside>
  <div className="syn-body">
   <header className="syn-top"><div><strong>UI component laboratory</strong><span>Unconnected prototype · All clinical operations locked</span></div><div className="syn-top-actions"><Link href="/ui/catalog">All 261 screens</Link><Link href="/ui/catalog/components">Component gallery</Link><span className="syn-status">Not clinically accepted</span></div></header>
   <main className="syn-main" id="screen-content">
    <nav className="syn-crumb" aria-label="Breadcrumb"><Link href="/ui/catalog">Catalogue</Link><span>/</span><span>{s.module}</span><span>/</span><strong aria-current="page">{s.id}</strong></nav>
    <header className="syn-title"><div><p className="syn-kicker">{s.module} · {s.id} · {s.layout} layout</p><h1>{s.name}</h1><p>Each canonical data group is represented. Shared controls remain proposals until per-screen field, clinical, permission and workflow reviews are completed.</p></div><span className="syn-chip">Design-stage preview</span></header>
    <section className="syn-alert" role="status"><strong>No connected clinical system</strong><p>Do not enter real patient information. Interface entries are demonstration-only, never stored or sent. Final clinical actions, reports and exports remain locked.</p></section>
    <div className="syn-quick"><a href="#screen-ui">Screen workspace</a><a href="#related">Module navigation</a><Link href="/ui/catalog/components">Dialogs, controls and print catalogue</Link></div>
    <div className="syn-layout"><div className="syn-column"><section id="screen-ui" className="syn-panel"><div className="syn-section-head"><h2>{s.name}</h2><span className="syn-chip">S00–S13 states</span></div><ScreenPreviewClient contract={contract} related={peers.map(x=>({id:x.id,name:x.name}))}/></section></div>
     <div className="syn-column"><section className="syn-panel" id="related"><h2>Related {s.module} screens</h2><nav className="syn-related">{peers.map(x=><Link key={x.id} href={href(x.id)} aria-current={x.id===s.id?"page":undefined}><span>{x.id}</span>{x.name}</Link>)}</nav></section><section className="syn-panel"><h2>Scope and gate status</h2><p>Field dictionary, role permissions, server-bound transitions, French localization, browser visual testing and clinical review must still be approved for this screen.</p><p><strong>Release:</strong> denied</p></section></div></div>
    <nav className="syn-pager" aria-label="Previous and next screens">{i>0?<Link href={href(peers[i-1].id)}>Previous: {peers[i-1].name}</Link>:<span/>}{i+1<peers.length?<Link href={href(peers[i+1].id)}>Next: {peers[i+1].name}</Link>:<span/>}</nav>
   </main>
  </div>
 </div>;
}
