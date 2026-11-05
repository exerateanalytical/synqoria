import Link from "next/link";
import flows from "../../../../../registers/ui-cross-module-workflow-14.json";
import "../catalog.css";
export default function ClinicalWorkflowMap(){
 return <main className="syn-catalog"><nav className="syn-crumb" aria-label="Breadcrumb"><Link href="/ui/catalog">Canonical screens</Link><span>/</span><span aria-current="page">Workflow maps</span></nav>
 <header className="syn-catalog-head"><div><p className="syn-kicker">SYNQORIA · UI/UX Master v1.1</p><h1>Fourteen cross-module workflow maps</h1><p>These routes represent the authority’s sequence and exception map. They are navigational prototypes; clicking a step does not create a hospital session, clinical action or permission.</p></div><span className="syn-chip">All clinical mutations locked</span></header>
 {flows.flows.map(flow=><section className="syn-panel" key={flow.id} id={flow.id}><div className="syn-section-head"><h2>{flow.id} · {flow.title}</h2><span className="syn-chip">{flow.path.length} linked screens</span></div>
  <h3>Normal sequence</h3><p>{flow.normalPath}</p><nav className="syn-quick" aria-label={flow.title+" ordered screen links"}>{flow.path.map((id,i)=><Link key={id} href={"/ui/catalog/"+id}>{i+1}. {id}</Link>)}</nav>
  <h3>Mandatory alternatives and exceptions</h3><p>{flow.exceptionPath}</p>
  {flow.alternates.length>0&&<nav className="syn-quick" aria-label={flow.title+" alternative screen links"}>{flow.alternates.map(id=><Link key={id} href={"/ui/catalog/"+id}>Exception · {id}</Link>)}</nav>}
  <p className="syn-help">Route navigation does not imply authorization. Patient identity, encounter scope, service ownership, server conflict checks and audit remain mandatory.</p>
 </section>)}
 <section className="syn-panel"><h2>Specialty and clinical safety boundaries</h2><p>M17 specialty workflows must use shared clinical order and result ownership. Medication safety, pharmacy handover and transfusion mismatch cannot be replaced with UI-only state changes. This catalogue contains no clinical treatment protocols.</p></section>
 </main>;
}