"use client";
import {useMemo,useState} from "react";
import Link from "next/link";
import {ClipboardList,FileText,Filter,LockKeyhole,Search,Stethoscope,ShieldCheck} from "lucide-react";
import "./canonical-directory.css";
type R={id:string;module:string;name:string;layout:string;primary_action:string};
type Impl={id:string;category:string};
const layoutNames:Record<string,string>={F:"Form",L:"Worklist",D:"Detail",X:"Review",B:"Board",T:"Analytics",H:"Dashboard"};
export default function CanonicalRouteDirectory({screens,implemented}:{screens:readonly R[];implemented:readonly Impl[]}){
 const [search,setSearch]=useState("");
 const [module,setModule]=useState("all");
 const [filter,setFilter]=useState("all");
 const [sort,setSort]=useState<"module"|"name">("module");
 const status=new Map(implemented.map(x=>[x.id,x.category]));
 const modules=[...new Set(screens.map(s=>s.module))];
 const visible=useMemo(()=>screens.filter(s=>{
  const match=[s.id,s.module,s.name,s.primary_action].join(" ").toLowerCase().includes(search.trim().toLowerCase());
  const type=status.get(s.id)||"locked";
  return match&&(module==="all"||module===s.module)&&(filter==="all"||filter===type);
 }).sort((a,b)=>sort==="name"?a.name.localeCompare(b.name):a.module.localeCompare(b.module)||a.id.localeCompare(b.id)),[screens,search,module,filter,sort,status]);
 const stats={total:screens.length,read:implemented.filter(x=>x.category==="verified-api-read-subset").length,
 functional:implemented.filter(x=>x.category==="nonclinical-functional").length,
 locked:screens.length-implemented.length};
 const labels:Record<string,string>={"verified-api-read-subset":"Real API read subset","nonclinical-functional":"Functional nonclinical","locked":"Domain service not integrated"};
 return <main className="syn-dir" data-route-directory="261">
  <header className="syn-dir-head"><p>SYNQORIA · Africa medical intelligence synchronized</p><h1>Hospital application screens</h1><p>All canonical application URLs are navigable. A navigable route is not proof of a live clinical domain: protected workflows remain locked until authoritative APIs, identity and patient-safety requirements are implemented.</p>
   <div className="syn-dir-quick"><Link href="/workforce">Workforce</Link><Link href="/hospital-documents">Hospital documents</Link><Link href="/ui/catalog">UI specifications</Link></div>
  </header>
  <section className="syn-dir-metrics" aria-label="Route implementation metrics">
   <article><ClipboardList size={23}/><span>Canonical URLs</span><strong>{stats.total} / 261</strong><small>Route resolution only</small></article>
   <article><Stethoscope size={23}/><span>Scoped API-read subsets</span><strong>{stats.read}</strong><small>Not fully clinical</small></article>
   <article><ShieldCheck size={23}/><span>Functional nonclinical routes</span><strong>{stats.functional}</strong><small>Safe read/navigation</small></article>
   <article><LockKeyhole size={23}/><span>Remaining domain integrations</span><strong>{stats.locked}</strong><small>Release locked</small></article>
  </section>
  <section className="syn-dir-controls" aria-label="Search and filter real routes">
   <label><Search size={18}/><span>Find a screen</span><input aria-label="Search canonical screens" value={search} maxLength={110} onChange={e=>setSearch(e.target.value)} type="search" placeholder="Name, code, module or workflow"/></label>
   <label><Filter size={18}/><span>Module</span><select aria-label="Filter by module" value={module} onChange={e=>setModule(e.target.value)}><option value="all">All 40 modules + shared</option>{modules.map(m=><option key={m} value={m}>{m}</option>)}</select></label>
   <label><span>Implementation</span><select aria-label="Filter by implementation" value={filter} onChange={e=>setFilter(e.target.value)}><option value="all">All statuses</option><option value="verified-api-read-subset">API read subsets</option><option value="nonclinical-functional">Functional nonclinical</option><option value="locked">Locked domains</option></select></label>
   <label><span>Sort</span><select aria-label="Sort screen directory" value={sort} onChange={e=>setSort(e.target.value as "module"|"name")}><option value="module">Module / ID</option><option value="name">Screen name</option></select></label>
  </section>
  <section className="syn-dir-panel"><h2>Canonical screen routes</h2><p role="status" aria-live="polite">{visible.length} matching screens · {stats.locked} domain integrations outstanding</p>
   <div className="syn-dir-table-wrap"><table><thead><tr><th scope="col">Screen</th><th scope="col">Module</th><th scope="col">Screen type</th><th scope="col">Runtime state</th><th scope="col">Route</th></tr></thead><tbody>{visible.map(s=>{
    const type=status.get(s.id)||"locked";
    return <tr key={s.id}><td><strong>{s.id}</strong><span>{s.name}</span></td><td>{s.module}</td><td>{layoutNames[s.layout]||s.layout}</td><td>{labels[type]}</td><td><Link href={"/workforce/clinical/"+s.id}>Open application route</Link></td></tr>;
   })}{visible.length===0&&<tr><td colSpan={5}>No canonical screens match the current filters.</td></tr>}</tbody></table></div>
  </section>
  <footer className="syn-dir-note"><FileText size={18}/><p>No patient data is created, updated, issued or signed by an unintegrated screen. Backend and clinical acceptance remain separately gated. <Link href="/ui/catalog/workflows">View canonical workflow dependencies.</Link></p></footer>
 </main>;
}
