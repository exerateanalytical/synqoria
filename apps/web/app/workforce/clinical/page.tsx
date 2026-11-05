import Link from "next/link";
import "./clinical-read.css";
const sections=[
 {id:"G-S03",title:"Facility binding",description:"Resolve a live authorized facility, never caller-provided tenant data.",level:"API-backed subset"},
 {id:"G-S06",title:"Staff preferences",description:"Language, time format, contrast, density and motion; saved without clinical data.",level:"Functional nonclinical"},
 {id:"G-S10",title:"Connectivity and readiness",description:"Check real configured cell liveness and dependency readiness.",level:"API-backed subset"},
 {id:"M01-S03",title:"Patient identity profile",description:"Retrieve authorized patient identity status and source revision only.",level:"API-backed subset"},
 {id:"M03-S01",title:"Restricted triage queue",description:"Read a real authorized explicit-case worklist with view filters and no clinical mutations.",level:"API-backed subset"},
 {id:"M03-S03",title:"Triage assessment",description:"Read authorized patient-scoped case workflow metadata; no clinical mutation.",level:"API-backed subset"},
 {id:"G-S11",title:"Help and support",description:"Search real application guidance and open verified feature entry points.",level:"Functional help"},
 {id:"G-S12",title:"Not found and retired routes",description:"Return users to safe routes without disclosing protected information.",level:"Functional safe navigation"},
 {id:"M31-S01",title:"Document template catalogue",description:"Browse and filter the reconciled 662-code hospital template library.",level:"Functional catalogue"},
 {id:"M31-S03",title:"Document template preview",description:"Preview bilingual and print-safe structured forms; issuance locked.",level:"Functional preview"}
];
export default function ClinicalOperationsIndex(){
 return <main className="syn-live-workspace" data-real-screen-index="true">
  <nav className="syn-live-breadcrumb"><Link href="/workforce">Workforce</Link><span>/</span><span>Clinical operations</span></nav>
  <header className="syn-live-header"><p className="syn-live-kicker">SYNQORIA · real code, no demo patients</p><h1>Operational staff screens</h1><p>These screens use real code paths and, where available, scoped server APIs. A browser-visible screen is not evidence of production authentication or clinical release. Unintegrated data sources deny access rather than generating sample patient data.</p></header>
  <section className="syn-live-panel"><h2>Screen implementation and API readiness</h2><div className="syn-live-index-grid">{sections.map(item=><article key={item.id} className="syn-live-index-item"><small>{item.id} · {item.level}</small><h3><Link href={"/workforce/clinical/"+item.id}>{item.title}</Link></h3><p>{item.description}</p></article>)}</div>
   <section className="syn-live-locked"><h3>Clinical commands are not activated</h3><p>Production OIDC/MFA, hospital-cell provisioning, exact field dictionaries, clinical approval, source database integration and validated domain commands are prerequisites for full clinical operation.</p></section>
   <p><Link href="/ui/catalog">See the 261-screen specification and implementation register</Link></p>
  </section>
 </main>;
}
