"use client";
import {useMemo,useState} from "react";
import Link from "next/link";
import {FileText,Search,FilterX,ChevronLeft,ChevronRight,Printer,ShieldAlert} from "lucide-react";
import type {PrintTemplate} from "./registry";
import "./library.css";
type Props={templates:PrintTemplate[]};
const PER_PAGE=24;
const priorities=new Set(Array.from({length:18},(_,i)=>"P"+String(i+1).padStart(2,"0")));
export default function PrintLibraryClient({templates}:Props){
 const [query,setQuery]=useState(""),[domain,setDomain]=useState("all"),[kind,setKind]=useState("all"),[coreOnly,setCoreOnly]=useState(false),[page,setPage]=useState(1);
 const domains=useMemo(()=>[...new Map(templates.map(t=>[t.domain,{id:t.domain,en:t.domainEn,fr:t.domainFr}])).values()].sort((a,b)=>a.en.localeCompare(b.en)),[templates]);
 const kinds=useMemo(()=>[...new Set(templates.map(t=>t.kind))].sort(),[templates]);
 const sorted=useMemo(()=>{
  const q=query.trim().toLocaleLowerCase();
  return templates.filter(t=>(domain==="all"||domain===t.domain)&&(kind==="all"||kind===t.kind)&&(!coreOnly||priorities.has(t.id))&&(!q||[t.en,t.fr,t.id,t.domainEn,t.domainFr,t.kind,t.module,...t.sections.flatMap(s=>[s.heading,...s.fields])].join(" ").toLocaleLowerCase().includes(q)))
   .sort((a,b)=>Number(priorities.has(b.id))-Number(priorities.has(a.id))||(priorities.has(a.id)&&priorities.has(b.id)?a.id.localeCompare(b.id):a.domainEn.localeCompare(b.domainEn)||a.en.localeCompare(b.en)));
 },[query,domain,kind,coreOnly,templates]);
 const pages=Math.max(1,Math.ceil(sorted.length/PER_PAGE));
 const current=Math.min(page,pages);
 const display=sorted.slice((current-1)*PER_PAGE,current*PER_PAGE);
 const reset=()=>{setQuery("");setDomain("all");setKind("all");setCoreOnly(false);setPage(1)};
 return <div className="syn-library">
  <header className="syn-library-hero"><div><p className="syn-library-eyebrow"><FileText size={17} aria-hidden="true"/> SYNQORIA · Hospital document template library</p><h1>Hospital forms, reports and printable documents</h1><p>Blank print-ready design specimens covering clinical care, diagnostics, consent, payments, pharmacy, operations and hospital governance. Every document is marked <strong>SAMPLE — NOT VALID</strong> until hospital-specific review and source integration.</p></div><div className="syn-library-count"><strong>{templates.length}</strong><span>Template types</span><strong>{domains.length}</strong><span>Hospital service groups</span></div></header>
  <div className="syn-library-links"><Link href="/hospital-documents">All reconciled hospital forms</Link><Link href="/ui/catalog">261-screen interface catalogue</Link><Link href="/ui/catalog/workflows">14 hospital workflows</Link><Link href="/ui/catalog/components">UI components</Link></div>
  <section className="syn-library-alert" role="status"><ShieldAlert size={21} aria-hidden="true"/><div><strong>Not a clinical, legal or fiscal issuance service</strong><p>All templates contain blank fields and clearly printed sample markings. No patient records, medication orders, receipts, signatures, barcodes or official document identifiers are generated.</p></div></section>
  <section className="syn-library-filters" aria-label="Template search and filters">
   <label className="syn-library-search"><span>Search templates, fields and departments</span><span className="syn-library-input-wrap"><Search size={20} aria-hidden="true"/><input type="search" value={query} onChange={e=>{setQuery(e.target.value);setPage(1)}} placeholder="e.g., laboratory, prescription, consent, invoice, admission" autoComplete="off"/></span></label>
   <label><span>Department</span><select value={domain} onChange={e=>{setDomain(e.target.value);setPage(1)}}><option value="all">All departments</option>{domains.map(x=><option key={x.id} value={x.id}>{x.en}</option>)}</select></label>
   <label><span>Form category</span><select value={kind} onChange={e=>{setKind(e.target.value);setPage(1)}}><option value="all">All document types</option>{kinds.map(x=><option key={x} value={x}>{x.replaceAll("-"," ")}</option>)}</select></label>
   <label className="syn-library-check"><input type="checkbox" checked={coreOnly} onChange={e=>{setCoreOnly(e.target.checked);setPage(1)}}/><span>Only 18 canonical outputs (P01–P18)</span></label>
   <button type="button" className="syn-library-reset" onClick={reset}><FilterX size={18} aria-hidden="true"/> Reset filters</button>
  </section>
  <div className="syn-library-results" role="status"><strong>{sorted.length}</strong> matching forms <span>· Page {current} of {pages}</span></div>
  {display.length?<div className="syn-library-grid">{display.map(t=><article key={t.id} className="syn-library-item"><div className="syn-library-item-top"><span className="syn-library-type"><FileText size={18} aria-hidden="true"/>{t.kind.replaceAll("-"," ")}</span>{t.canonical&&<span className="syn-library-core">Canonical</span>}</div><h2><Link href={"/ui/printables/"+encodeURIComponent(t.id)}>{t.en}</Link></h2><p className="syn-library-fr" lang="fr">{t.fr}</p><p className="syn-library-dept">{t.domainEn}</p><p className="syn-library-fieldcount">{t.sections.length} sections · {t.sections.reduce((n,s)=>n+s.fields.length,0)} documented field groups</p><div className="syn-library-item-bottom"><code>{t.id}</code><Link href={"/ui/printables/"+encodeURIComponent(t.id)} aria-label={"Preview blank "+t.en}><Printer size={18} aria-hidden="true"/> Preview print</Link></div></article>)}</div>:<div className="syn-library-empty"><h2>No matching documents</h2><p>Change the filters or search terms. All registered templates remain available.</p><button type="button" onClick={reset}>Show all templates</button></div>}
  <nav className="syn-library-pagination" aria-label="Document template pages"><button type="button" disabled={current<=1} onClick={()=>setPage(p=>Math.max(1,p-1))}><ChevronLeft size={18} aria-hidden="true"/>Previous</button><span>Page {current} / {pages}</span><button type="button" disabled={current>=pages} onClick={()=>setPage(p=>Math.min(pages,p+1))}>Next<ChevronRight size={18} aria-hidden="true"/></button></nav>
  <footer className="syn-library-footer"><p>Clinical templates require hospital-specific qualified sign-off; fiscal fields and regulated numbering require jurisdiction-specific verification. These catalogued samples may be printed as blank, non-valid proofs only.</p></footer>
 </div>;
}
