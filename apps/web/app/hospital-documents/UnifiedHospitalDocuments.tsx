"use client";

import {useMemo,useState} from "react";
import Link from "next/link";
import "./document-library.css";

export type UnifiedDocumentItem={
 id:string;title:string;titleFr:string;module:string;department:string;kind:string;
 sources:ReadonlyArray<"structured-form"|"pdf-sample">;
 href:string;alternateHref?:string;canonical:boolean;fields:number;
};
type Props={items:UnifiedDocumentItem[]};
export default function UnifiedHospitalDocuments({items}:Props){
 const [q,setQ]=useState(""),[module,setModule]=useState("all"),[kind,setKind]=useState("all"),[source,setSource]=useState("all"),[page,setPage]=useState(1);
 const modules=useMemo(()=>[...new Set(items.map(x=>x.module))].sort(),[items]);
 const kinds=useMemo(()=>[...new Set(items.map(x=>x.kind))].sort(),[items]);
 const filtered=useMemo(()=>{
  const search=q.trim().toLocaleLowerCase();
  return items.filter(x=>(module==="all"||x.module===module)&&(kind==="all"||x.kind===kind)&&(source==="all"||x.sources.includes(source as "structured-form"|"pdf-sample"))&&(!search||[x.id,x.title,x.titleFr,x.department,x.module,x.kind].join(" ").toLocaleLowerCase().includes(search)))
   .sort((a,b)=>Number(b.canonical)-Number(a.canonical)||a.module.localeCompare(b.module)||a.title.localeCompare(b.title));
 },[items,q,module,kind,source]);
 const perPage=30,totalPages=Math.max(1,Math.ceil(filtered.length/perPage)),current=Math.min(page,totalPages);
 const shown=filtered.slice((current-1)*perPage,current*perPage);
 const clear=()=>{setQ("");setModule("all");setKind("all");setSource("all");setPage(1)};
 const canonical=items.filter(x=>x.canonical).length;
 return <main className="syn-unified-docs">
  <header className="syn-unified-hero">
   <p className="syn-unified-kicker">SYNQORIA · Hospital print and forms</p>
   <h1>Hospital document catalogue</h1>
   <p>Browse the reconciled blank document library. Structured forms and PDF-oriented samples are preserved together, including shared canonical P01–P18 templates. Every preview remains an unissued, watermarked example.</p>
   <div className="syn-unified-stats"><div><strong>{items.length}</strong><span>Unique template identifiers</span></div><div><strong>{modules.length}</strong><span>Canonical modules</span></div><div><strong>{canonical}</strong><span>Core P01–P18 outputs</span></div></div>
  </header>
  <nav className="syn-unified-links" aria-label="Document workspaces"><Link href="/ui/printables">Structured blank forms — 376</Link><Link href="/documents">PDF-oriented document samples — 304</Link><Link href="/ui/catalog">All 261 clinical interface routes</Link></nav>
  <aside className="syn-unified-warning"><strong>Not approved for clinical or financial issuance.</strong><p>Counts represent distinct template codes, not a guarantee of 662 clinically distinct or approved form types. Some records cover similar real-world documents. No live patient, medication, consent, payment, barcode or signature data is connected.</p></aside>
  <section className="syn-unified-filters" aria-label="Document filters">
   <label>Search by code, title or department<input type="search" autoComplete="off" placeholder="Find a form, report, receipt or consent" value={q} onChange={e=>{setQ(e.target.value);setPage(1)}}/></label>
   <label>Hospital module<select value={module} onChange={e=>{setModule(e.target.value);setPage(1)}}><option value="all">All 40 modules</option>{modules.map(x=><option key={x}>{x}</option>)}</select></label>
   <label>Document type<select value={kind} onChange={e=>{setKind(e.target.value);setPage(1)}}><option value="all">Every type</option>{kinds.map(x=><option key={x} value={x}>{x.replaceAll("-"," ")}</option>)}</select></label>
   <label>Template family<select value={source} onChange={e=>{setSource(e.target.value);setPage(1)}}><option value="all">Both families</option><option value="structured-form">Structured blank forms</option><option value="pdf-sample">PDF-oriented samples</option></select></label>
   <button type="button" onClick={clear}>Clear all filters</button>
  </section>
  <p className="syn-unified-results" role="status"><strong>{filtered.length}</strong> matching template identifiers · Page {current} of {totalPages}</p>
  <div className="syn-unified-grid">{shown.map(d=><article className="syn-unified-card" key={d.id}>
    <div className="syn-unified-card-head"><code>{d.id}</code><span>{d.module}</span></div>
    <h2><Link href={d.href}>{d.title}</Link></h2>
    {d.titleFr&&<p lang="fr" className="syn-unified-fr">{d.titleFr}</p>}
    <p>{d.department}</p><p className="syn-unified-kind">{d.kind.replaceAll("-"," ")} · {d.fields} defined fields/groups</p>
    <div className="syn-unified-actions"><Link href={d.href}>View blank sample</Link>{d.alternateHref&&<Link href={d.alternateHref}>Alternative PDF layout</Link>}</div>
   </article>)}</div>
  {shown.length===0&&<section className="syn-unified-empty"><h2>No matching forms</h2><p>Adjust the filters to view the full proposed catalogue.</p><button type="button" onClick={clear}>Clear filters</button></section>}
  <nav className="syn-unified-pages" aria-label="Document pages"><button type="button" disabled={current===1} onClick={()=>setPage(x=>Math.max(1,x-1))}>Previous</button><span>Page {current} / {totalPages}</span><button type="button" disabled={current===totalPages} onClick={()=>setPage(x=>Math.min(totalPages,x+1))}>Next</button></nav>
  <footer><p>All templates require hospital-specific clinical, legal, tax, privacy, accessibility, translation, signing, retention and printer-profile review before real issuance. The two source libraries are preserved for comparison and controlled consolidation.</p></footer>
 </main>;
}
