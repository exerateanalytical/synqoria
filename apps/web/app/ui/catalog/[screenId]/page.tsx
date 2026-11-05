import Link from "next/link";
import { notFound } from "next/navigation";
import registry from "../../../../../../registers/canonical-screens.json";
import "../catalog.css";

type Screen = { id:string; module:string; name:string; layout:string; data_input_groups:string; primary_action:string };
const screens = registry.screen_register as Screen[];
const groups = Array.from(new Set(screens.map(s=>s.module)));
const pathFor = (s:Screen) => "/ui/catalog/"+encodeURIComponent(s.id);
const normalize = (s:string) => s.toLowerCase().replace(/[^a-z0-9]+/g,"-").replace(/^-|-$/g,"");
const safeFields = (s:Screen) => s.data_input_groups.split(",").map(x=>x.trim()).filter(Boolean).slice(0,12);
export function generateStaticParams(){return screens.map(s=>({screenId:s.id}));}
export default async function CanonicalScreen({params}:{params:Promise<{screenId:string}>}){
 const {screenId}=await params;
 const selected=screens.find(s=>s.id===screenId);
 if(!selected)notFound();
 const s=selected;
 const siblings=screens.filter(x=>x.module===s.module);
 const position=siblings.findIndex(x=>x.id===s.id);
 const prev=siblings[position-1], next=siblings[position+1];
 const fields=safeFields(s);
 const isForm=s.layout==="F";
 return <div className="syn-ui">
  <a className="syn-skip" href="#screen-content">Skip to screen</a>
  <aside className="syn-rail" aria-label="Module navigation">
   <Link href="/ui/catalog" className="syn-brand">SYNQORIA <small>Clinical workspace · UI preview</small></Link>
   <nav className="syn-modules">{groups.map(m=><a key={m} href={"/ui/catalog#"+normalize(m)} aria-current={m===s.module?"page":undefined}>{m==="SHARED"?"Shared workspace":m} <span>{screens.filter(x=>x.module===m).length}</span></a>)}</nav>
  </aside>
  <div className="syn-body">
   <header className="syn-top"><div><strong>Hospital workspace</strong><span>Design preview · No patient data connected</span></div><div className="syn-top-actions"><Link href="/ui/catalog">All screens</Link><span className="syn-status">Clinical actions locked</span></div></header>
   <main id="screen-content" className="syn-main">
    <nav className="syn-crumb" aria-label="Breadcrumb"><Link href="/ui/catalog">Screen catalogue</Link><span>/</span><span>{s.module}</span><span>/</span><span aria-current="page">{s.id}</span></nav>
    <div className="syn-title"><div><p className="syn-kicker">{s.module} · {s.id} · Layout {s.layout}</p><h1>{s.name}</h1><p>Canonical interface specimen aligned to the SYNQORIA screen register. Clinical integration and acceptance are pending.</p></div><span className="syn-chip">Preview only</span></div>
    <section className="syn-alert" role="status"><strong>Protected clinical environment</strong><p>Patient identity, facility authorization, consent, audit persistence and clinical write APIs are not connected in this preview. No action below creates or changes a patient record.</p></section>
    <div className="syn-quick" aria-label="Quick actions"><a href="#primary-workspace">Open workspace</a><a href="#details">View details</a><a href="#workflow">Workflow</a><a href="#related">Related screens</a></div>
    <div className="syn-layout">
     <div className="syn-column">
      <section className="syn-panel" id="primary-workspace"><div className="syn-section-head"><div><p className="syn-kicker">Primary workspace</p><h2>{s.name}</h2></div><span className="syn-chip">Draft</span></div>
       <div className="syn-tabs" role="navigation" aria-label="Screen sections"><a href="#primary-workspace" aria-current="page">Overview</a><a href="#details">Details</a><a href="#workflow">Activity & workflow</a><a href="#related">Related</a></div>
       {isForm?<form className="syn-form" aria-label={s.name+" preview"}>{fields.map((f,i)=><label key={i}><span>{f}</span><input type="text" placeholder={"Enter "+f.toLowerCase()} disabled aria-describedby="locked-form" /></label>)}<p id="locked-form" className="syn-help">Form is a non-functional UI preview; inputs are disabled until authorized backend integration.</p><div className="syn-buttons"><button type="button" disabled>{s.primary_action}</button><button type="reset" disabled>Clear</button></div></form>:<div className="syn-list" role="list">{fields.map((f,i)=><div role="listitem" key={i} className="syn-list-row"><div className="syn-list-icon" aria-hidden="true">{String(i+1).padStart(2,"0")}</div><div><strong>{f}</strong><p>Scoped information is available only after authorized clinical service integration.</p></div><span className="syn-chip">Not connected</span></div>)}<button type="button" disabled>{s.primary_action}</button></div>}
      </section>
      <section className="syn-panel" id="details"><div className="syn-section-head"><h2>Details and information groups</h2><span className="syn-chip">{fields.length} groups</span></div><div className="syn-detail-grid">{fields.map((f,i)=><div key={i}><span>Section {i+1}</span><strong>{f}</strong><p>Awaiting authorized data and validated clinical workflow.</p></div>)}</div></section>
     </div>
     <div className="syn-column">
      <section className="syn-panel" id="workflow"><h2>Workflow and safeguards</h2><ol className="syn-steps"><li><strong>Identify context</strong><p>Authenticate and select permitted facility, role and patient scope.</p></li><li><strong>Review details</strong><p>Validate source information and authorization before any clinical decision.</p></li><li><strong>Perform primary action</strong><p>{s.primary_action} — disabled in this design preview.</p></li><li><strong>Confirm and audit</strong><p>Require server-confirmed outcome, idempotency, audit and outbox linkage.</p></li></ol></section>
      <section className="syn-panel" id="related"><h2>Related {s.module} screens</h2><nav className="syn-related">{siblings.map(x=><Link key={x.id} href={pathFor(x)} aria-current={x.id===s.id?"page":undefined}><span>{x.id}</span>{x.name}</Link>)}</nav></section>
     </div>
    </div>
    <nav className="syn-pager" aria-label="Screen pagination">{prev?<Link href={pathFor(prev)}>← {prev.name}</Link>:<span/>}{next?<Link href={pathFor(next)}>{next.name} →</Link>:<span/>}</nav>
   </main>
  </div>
 </div>;
}
