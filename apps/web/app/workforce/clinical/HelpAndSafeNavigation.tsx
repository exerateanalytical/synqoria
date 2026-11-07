"use client";
import {useState} from "react";
import Link from "next/link";
import "./clinical-read.css";
type LinkInfo={id:string;title:string;titleFr:string;summary:string;summaryFr:string;href:string;tags:string[]};
const helpTopics:LinkInfo[]=[
 {id:"identity",title:"Patient identity and scope",titleFr:"Identité et accès patient",summary:"Use verified patient and hospital-cell context. A 404 can mean unavailable or unauthorized; never infer a patient exists.",summaryFr:"Utilisez un contexte patient et établissement vérifié. Un résultat 404 ne confirme jamais l'existence du patient.",href:"/workforce/clinical/M01-S03",tags:["patient","identity","access"]},
 {id:"triage",title:"Triage case and safety",titleFr:"Triage et sécurité",summary:"Read current triage status only when the authorized patient context is active. No clinical priority or assessment may be inferred.",summaryFr:"Consultez le triage uniquement avec un patient autorisé. N'inférez jamais une priorité clinique.",href:"/workforce/clinical/M03-S03",tags:["triage","emergency","safety"]},
 {id:"documents",title:"Clinical forms and document printing",titleFr:"Formulaires et impressions",summary:"Find 662 unique sample template codes. These are not issued reports, receipts, consents or prescriptions.",summaryFr:"Consultez les 662 codes de modèles. Ce ne sont pas des documents médicaux, financiers ou légaux émis.",href:"/hospital-documents",tags:["documents","prescription","receipt","consent"]},
 {id:"readiness",title:"Service interruption and readiness",titleFr:"Interruption de service et disponibilité",summary:"Check whether the configured clinical cell is ready. A network or dependency failure must stop protected clinical actions.",summaryFr:"Vérifiez l'état de la cellule. Une panne réseau ou de dépendance bloque les opérations cliniques.",href:"/workforce/clinical/G-S10",tags:["offline","network","service","downtime"]},
 {id:"preferences",title:"Accessibility and display preferences",titleFr:"Accessibilité et affichage",summary:"Choose language, time format, contrast, density and reduced motion without storing clinical data.",summaryFr:"Choisissez la langue, le format horaire, le contraste et la densité sans données cliniques.",href:"/workforce/clinical/G-S06",tags:["accessibility","language","settings"]},
 {id:"workflows",title:"Canonical hospital workflow guide",titleFr:"Guide des parcours hospitaliers",summary:"Review 14 approved baseline workflow maps and protected exception routes before continuing a clinical handover.",summaryFr:"Consultez les 14 parcours de référence et leurs exceptions avant une transmission clinique.",href:"/ui/catalog/workflows",tags:["workflow","handover","admission","discharge"]},
 {id:"support",title:"Institutional support and access requests",titleFr:"Assistance institutionnelle et accès",summary:"Contact the hospital’s approved helpdesk outside this unintegrated interface. Do not submit patient details in unverified channels.",summaryFr:"Contactez l'assistance autorisée de l'hôpital. Ne partagez jamais de données patient par canal non vérifié.",href:"/workforce",tags:["helpdesk","support","request","access"]}
];
export default function HelpAndSafeNavigation({mode}:{mode:"help"|"notFound"}){
 const [lang,setLang]=useState<"en"|"fr">("en"),[search,setSearch]=useState("");
 const topics=helpTopics.filter(x=>[x.title,x.titleFr,x.summary,x.summaryFr,x.id,...x.tags].join(" ").toLowerCase().includes(search.toLowerCase().trim()));
 const help=mode==="help";
 return <main className="syn-live-workspace" lang={lang} data-real-screen-id={help?"G-S11":"G-S12"}>
  <nav className="syn-live-breadcrumb"><Link href="/workforce/clinical">{lang==="fr"?"Opérations":"Operations"}</Link><span>/</span><strong>{help?"G-S11":"G-S12"}</strong></nav>
  <header className="syn-live-header"><p className="syn-live-kicker">{help?"G-S11 · Staff help":"G-S12 · Safe navigation"}</p>
   <h1>{help?lang==="fr"?"Aide et assistance":"Help and support":lang==="fr"?"Fonction introuvable ou retirée":"Feature unavailable or retired"}</h1>
   <p>{help?lang==="fr"?"Recherchez les fonctions et règles de sécurité disponibles. Les demandes d'assistance ne sont pas transmises par cette page.":"Search available functions and safe operating guidance. Support requests are not transmitted from this page.":
     lang==="fr"?"Aucune information sur une ressource protégée ne peut être révélée. Retournez à une fonction autorisée.":"No protected resource information can be disclosed. Return to an authorized entry point."}</p>
   <div className="syn-live-languages"><button type="button" aria-pressed={lang==="en"} onClick={()=>setLang("en")}>English</button><button type="button" aria-pressed={lang==="fr"} onClick={()=>setLang("fr")}>Français</button></div>
  </header>
  <section className="syn-live-panel" aria-label="Operational help and navigation">
   {help&&<label className="syn-live-help-search">{lang==="fr"?"Rechercher une rubrique":"Search support topics"}<input type="search" autoComplete="off" value={search} onChange={e=>setSearch(e.target.value)} maxLength={100} placeholder={lang==="fr"?"patient, triage, documents, réseau":"patient, triage, documents, network"}/></label>}
   <p role="status">{topics.length} {lang==="fr"?"rubriques disponibles":"available topics"}</p>
   <div className="syn-live-index-grid">{topics.map(x=><article key={x.id} className="syn-live-index-item"><h3><Link href={x.href}>{lang==="fr"?x.titleFr:x.title}</Link></h3><p>{lang==="fr"?x.summaryFr:x.summary}</p></article>)}</div>
   <div className="syn-live-locked"><h3>{lang==="fr"?"Assistance authentifiée":"Authorized support only"}</h3><p>{lang==="fr"?"Aucun ticket ou dossier patient n'est créé par cette page.":"This page neither opens a support ticket nor discloses a patient record."}</p></div>
  </section>
 </main>;
}
