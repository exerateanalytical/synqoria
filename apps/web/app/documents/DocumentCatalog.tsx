"use client";
import {useMemo,useState} from "react";
import Link from "next/link";
import "./document-catalog.css";

export type DocumentListItem={id:string;title:string;module:string;media:string;canonicalPrintCode:string|null;groupKeys:string[];audience:string};
export function DocumentCatalog({items}:{items:DocumentListItem[]}){
 const [query,setQuery]=useState(""),[module,setModule]=useState(""),[media,setMedia]=useState(""),[scope,setScope]=useState<"all"|"canonical"|"supplementary">("all"),[sort,setSort]=useState<"module"|"title">("module"),[layout,setLayout]=useState<"cards"|"table">("cards");
 const modules=[...new Set(items.map(i=>i.module))].sort();
 const list=useMemo(()=>{
  const needle=query.trim().toLowerCase();
  const rows=items.filter(i=>(!needle||[i.id,i.title,i.module,...i.groupKeys].join(" ").toLowerCase().includes(needle))&&(!module||i.module===module)&&(!media||i.media===media)&&(scope==="all"||(scope==="canonical"?Boolean(i.canonicalPrintCode):!i.canonicalPrintCode)));
  return rows.sort((a,b)=>sort==="title"?a.title.localeCompare(b.title):a.module.localeCompare(b.module)||a.title.localeCompare(b.title));
 },[items,query,module,media,scope,sort]);
 const reset=()=>{setQuery("");setModule("");setMedia("");setScope("all");setSort("module");};
 return <div className="syn-doc-library">
  <nav aria-label="Document library links"><Link href="/hospital-documents">Complete reconciled catalogue</Link> · <Link href="/ui/printables">376 structured hospital form templates</Link></nav><header className="syn-doc-library-hero">
   <p className="syn-doc-library-overline">SYNQORIA · Hospital documents · Source register v1</p>
   <h1>Hospital documents and form templates</h1>
   <p>Explore the 18 canonical printable outputs and 286 additional candidate forms across 40 modules. Open any item to inspect its full blank document layout and print a clearly labelled sample. No clinical data, signatures, legal consents or genuine payment records can be created here.</p>
   <div className="syn-doc-library-summary"><div><strong>{items.length}</strong><span>Structured templates</span></div><div><strong>{items.filter(i=>i.canonicalPrintCode).length}</strong><span>Canonical print outputs</span></div><div><strong>{modules.length}</strong><span>Modules</span></div></div>
  </header>
  <section className="syn-doc-library-controls" aria-label="Template catalogue filters">
   <div className="syn-doc-library-top"><label className="syn-doc-library-query">Search title, reference or field category<input type="search" value={query} onChange={e=>setQuery(e.target.value)} placeholder="e.g. laboratory, consent, prescription, receipt"/></label>
   <label>Module<select value={module} onChange={e=>setModule(e.target.value)}><option value="">All modules</option>{modules.map(m=><option key={m}>{m}</option>)}</select></label>
   <label>Print media<select value={media} onChange={e=>setMedia(e.target.value)}><option value="">All media</option>{["a4","a5","receipt","label","card"].map(m=><option key={m} value={m}>{m.toUpperCase()}</option>)}</select></label></div>
   <div className="syn-doc-library-toolbar">
    <div role="group" aria-label="Template type">{(["all","canonical","supplementary"] as const).map(v=><button key={v} type="button" aria-pressed={scope===v} onClick={()=>setScope(v)}>{v==="all"?"All templates":v==="canonical"?"P01–P18 canonical":"Additional candidates"}</button>)}</div>
    <div className="syn-doc-library-display"><label>Sort<select value={sort} onChange={e=>setSort(e.target.value as "module"|"title")}><option value="module">Module</option><option value="title">Title</option></select></label><button type="button" aria-pressed={layout==="cards"} onClick={()=>setLayout("cards")}>Cards</button><button type="button" aria-pressed={layout==="table"} onClick={()=>setLayout("table")}>Table</button><button type="button" onClick={reset}>Clear filters</button></div>
   </div>
   <p className="syn-doc-library-count" role="status">Showing {list.length} of {items.length} template definitions</p>
  </section>
  {list.length===0?<section className="syn-doc-library-empty"><h2>No matching document</h2><p>Change the filters to review the registered templates. A missing form must be raised as a scope change, not silently invented.</p><button type="button" onClick={reset}>Reset search</button></section>:layout==="cards"?
   <section className="syn-doc-library-cards" aria-label="Matching document templates">{list.map(item=><article className="syn-doc-library-card" key={item.id}><div className="syn-doc-library-cardmeta"><span>{item.module} · {item.id}</span><span>{item.media.toUpperCase()}</span></div><h2>{item.title}</h2><p>{item.groupKeys.map(g=>g.replace(/([A-Z])/g," $1")).join(" · ")}</p><p className="syn-doc-library-status">{item.canonicalPrintCode?"Canonical output":"Proposed supplementary template"} · Sample only</p><Link href={"/documents/"+encodeURIComponent(item.id)}>Open complete form preview</Link></article>)}</section>:
   <div className="syn-doc-library-table-wrap"><table><caption>Filtered SYNQORIA hospital document catalogue</caption><thead><tr><th scope="col">Reference</th><th scope="col">Document title</th><th scope="col">Module</th><th scope="col">Media</th><th scope="col">Action</th></tr></thead><tbody>{list.map(item=><tr key={item.id}><td>{item.id}</td><td>{item.title}</td><td>{item.module}</td><td>{item.media}</td><td><Link href={"/documents/"+encodeURIComponent(item.id)}>Open sample</Link></td></tr>)}</tbody></table></div>}
  <footer className="syn-doc-library-footer"><p><strong>Release boundary:</strong> All catalogue entries are templates for design and policy review. Patient identity, coding, diagnostic values, medication dosages, fiscal numbering, signatures and disclosure permissions must come from approved source systems and be validated before production use.</p><div><Link href="/ui/catalog">261-screen UI catalogue</Link><Link href="/ui/catalog/workflows">14 workflow maps</Link></div></footer>
 </div>;
}
