"use client";
import {useEffect,useId,useRef,useState} from "react";
import type {KeyboardEvent,ReactNode} from "react";
import Link from "next/link";
import "./specific-screen.css";

export type SpecificField={id:string;label:string;widget:"text"|"email"|"tel"|"date"|"textarea"|"restricted"|"readonly";tabIndex:number;requiredness:string;permission:string;source:string};
export type SpecificScreenSpec={
 id:string;module:string;moduleName:string;title:string;layout:"F"|"L"|"D"|"X"|"B"|"T"|"H";
 eligibilityNote:string;authorityWorkflow:string;tabs:string[];fields:SpecificField[];
 controls:string[];filters:string[];primaryAction:string;modalContracts:string[];
 printContractCandidates:string[];navigation:{modulePeers:string[]};
 runtime:{clinicalCommand:string;readSource:string;writes:string;exports:string;sessionPersistence:string;browserStorage:string;fields:string;workflow:string;acceptance:string;screens:string};
};
const states=[
 {key:"S00",label:"Initial / authorization",message:"Server identity, organization and permitted scope are not connected."},
 {key:"S01",label:"Loading",message:"No source fetch is active. Clinical commands remain locked."},
 {key:"S02",label:"Empty",message:"No authorized records are connected to this interface."},
 {key:"S03",label:"Ready",message:"A real ready state requires the authoritative source version."},
 {key:"S04",label:"Dirty",message:"Any demonstration input exists only in volatile memory."},
 {key:"S05",label:"Validating",message:"No actual clinical validation or submission was made."},
 {key:"S06",label:"Confirmed",message:"Confirmation requires a durable server receipt and source revision."},
 {key:"S07",label:"Validation error",message:"Approved field dictionary and clinical validation rules are not connected."},
 {key:"S08",label:"Denied",message:"Access denied until current role and scope permissions are verified."},
 {key:"S09",label:"Offline",message:"No authorized offline clinical mutation is available."},
 {key:"S10",label:"Conflict",message:"A stale version must be reconciled against the authoritative record."},
 {key:"S11",label:"Unknown outcome",message:"Do not retry a mutation under a new key or claim success."},
 {key:"S12",label:"Service failure",message:"Use approved service-downtime procedure; never silently continue."},
 {key:"S13",label:"Not found or retired",message:"A safe deep-link response must not disclose a patient’s existence."}
] as const;
const unique = (values:string[])=>[...new Set(values)];
function LockedAction({label,reason="Requires verified authorization and server source"}:{label:string;reason?:string}){return <button type="button" disabled className="syn-specific-locked" title={reason}>{label}<small>{reason}</small></button>}
function Empty({kind}:{kind:string}){return <section className="syn-specific-empty" role="status"><strong>No authorized {kind} loaded</strong><p>Data remains unavailable until current tenant, facility, actor and record access are established. No sample patient values or financial figures are invented.</p></section>}
function TabBar({spec,active,onChange,children}:{spec:SpecificScreenSpec;active:number;onChange:(n:number)=>void;children:ReactNode}){
 const refs=useRef<(HTMLButtonElement|null)[]>([]),base=useId().replace(/:/g,"");
 function onKey(e:KeyboardEvent<HTMLButtonElement>,i:number){
  let n=i;if(e.key==="ArrowRight")n=(i+1)%spec.tabs.length;else if(e.key==="ArrowLeft")n=(i+spec.tabs.length-1)%spec.tabs.length;else if(e.key==="Home")n=0;else if(e.key==="End")n=spec.tabs.length-1;else return;
  e.preventDefault();onChange(n);refs.current[n]?.focus();
 }
 return <div><div role="tablist" aria-label={spec.title+" sections"} className="syn-specific-tabs">{spec.tabs.map((name,i)=><button key={i} type="button" id={base+"-tab-"+i} aria-controls={base+"-panel"} role="tab" aria-selected={active===i} tabIndex={active===i?0:-1} onClick={()=>onChange(i)} onKeyDown={e=>onKey(e,i)} ref={node=>{refs.current[i]=node;}}>{name}</button>)}</div><div role="tabpanel" tabIndex={0} aria-labelledby={base+"-tab-"+active} id={base+"-panel"} className="syn-specific-tabpanel">{children}</div></div>;
}
function PreviewField({field,value,onChange}:{field:SpecificField;value:string;onChange:(v:string)=>void}){
 const id=useId().replace(/:/g,"");
 const locked=field.widget==="restricted"||field.widget==="readonly";
 const props={id,disabled:locked,value,autoComplete:"off" as const,"aria-describedby":id+"-help",onChange:(e:{target:{value:string}})=>onChange(e.target.value)};
 const label=field.label;
 return <label htmlFor={id} className="syn-specific-field" data-canonical-field={field.id}><span>{label}</span>
  {field.widget==="textarea"?<textarea {...props} maxLength={300} rows={4} placeholder="Demonstration text only"/>:
   <input {...props} type={locked?"text":field.widget} maxLength={160} spellCheck={false} placeholder={locked?"Server/clinical contract required":"Synthetic values only"}/>}
  <small id={id+"-help"}>{locked?"Locked until approved field dictionary and clinical/permission validation.":"Demonstration only. Requiredness, valid ranges and source are not yet approved."}</small>
 </label>;
}
function FormSpecimen({spec,tab,values,setField,onReview,onClear}:{spec:SpecificScreenSpec;tab:number;values:Record<string,string>;setField:(id:string,v:string)=>void;onReview:()=>void;onClear:()=>void}){
 if(tab===spec.tabs.length-1)return <div>
  <h3>Review demonstration entry</h3>
  <p>No review of real clinical data is possible. The sections below show only whether an illustrative input was entered.</p>
  <dl className="syn-specific-attrs">{spec.fields.map(f=><div key={f.id}><dt>{f.label}</dt><dd>{f.widget==="restricted"?"Restricted":values[f.id]?.trim()?"Synthetic entry supplied":"Not entered"}</dd></div>)}</dl>
  <section className="syn-specific-notice"><strong>Required independent safety checks</strong><p>{spec.authorityWorkflow}</p><p>All real inputs must pass approved validation, current source revision, RBAC, tenant/patient scope, idempotency, durable audit and service verification before action.</p></section><LockedAction label={spec.primaryAction}/></div>;
 const fields=spec.fields.filter(f=>f.tabIndex===tab);
 return <div><h3>{spec.tabs[tab]}</h3><p>Fields taken from the canonical screen register. Unknown clinical constraints remain locked or unapproved.</p>
  <form className="syn-specific-form" noValidate onSubmit={e=>{e.preventDefault();onReview();}}>
  {fields.map(f=><PreviewField key={f.id} field={f} value={values[f.id]??""} onChange={v=>setField(f.id,v)}/>)}
  <div className="syn-specific-form-actions"><button className="syn-specific-primary" type="submit">Review synthetic draft</button><button type="button" className="syn-specific-secondary" onClick={onClear}>Clear demo draft</button></div></form></div>;
}
function Filters({spec}:{spec:SpecificScreenSpec}){
 const [q,setQ]=useState(""),[open,setOpen]=useState(false),[scope,setScope]=useState(""),[status,setStatus]=useState(""),[from,setFrom]=useState(""),[to,setTo]=useState(""),[sort,setSort]=useState("recent"),[size,setSize]=useState("25"),[applied,setApplied]=useState(false),[columns,setColumns]=useState(true);
 const [visible,setVisible]=useState<string[]>(spec.fields.map(f=>f.id));
 const reset=()=>{setQ("");setScope("");setStatus("");setFrom("");setTo("");setApplied(false);setVisible(spec.fields.map(f=>f.id));};
 const show=spec.fields.filter(f=>visible.includes(f.id));
 return <div className="syn-specific-worklist">
  <div className="syn-specific-toolbar"><label>Search within authorized results<input type="search" value={q} onChange={e=>setQ(e.target.value)} maxLength={120} placeholder="Authorized search only — no server connected"/></label>
   <button type="button" className="syn-specific-secondary" aria-expanded={open} aria-controls="syn-specific-filter-panel" onClick={()=>setOpen(v=>!v)}>{open?"Hide filters":"Show filters"}</button>
   <button type="button" className="syn-specific-secondary" onClick={reset}>Clear filters</button>
   <button type="button" className="syn-specific-secondary" aria-expanded={columns} onClick={()=>setColumns(v=>!v)}>{columns?"Hide columns":"Choose columns"}</button>
  </div>
  {open&&<section id="syn-specific-filter-panel" className="syn-specific-filter-grid" aria-label="Scoped worklist filters">
   <label>Permitted scope<select value={scope} onChange={e=>setScope(e.target.value)}><option value="">All permitted scopes</option><option value="current">Current facility (preview)</option><option value="assigned">Assigned to me (preview)</option></select></label>
   <label>Review/status<select value={status} onChange={e=>setStatus(e.target.value)}><option value="">All permitted statuses</option><option value="pending">Needs review (preview)</option><option value="active">Active (preview)</option></select></label>
   <label>Date from<input type="date" value={from} onChange={e=>setFrom(e.target.value)}/></label>
   <label>Date to<input type="date" min={from||undefined} value={to} onChange={e=>setTo(e.target.value)}/></label>
   <button type="button" className="syn-specific-primary" onClick={()=>setApplied(true)}>Apply filters</button>
  </section>}
  {columns&&<fieldset className="syn-specific-column-chooser"><legend>Visible information columns</legend>{spec.fields.map(f=><label key={f.id}><input type="checkbox" checked={visible.includes(f.id)} onChange={e=>setVisible(old=>e.target.checked?unique([...old,f.id]):old.length>1?old.filter(x=>x!==f.id):old)}/>{f.label}</label>)}</fieldset>}
  {applied&&<p role="status" className="syn-specific-notice">Local filter choices updated. No hospital query was sent.</p>}
  <div className="syn-specific-toolbar"><label>Sort by<select value={sort} onChange={e=>setSort(e.target.value)}><option value="recent">Newest first</option><option value="oldest">Oldest first</option><option value="label">Label</option></select></label><label>Items per page<select value={size} onChange={e=>setSize(e.target.value)}><option>10</option><option>25</option><option>50</option></select></label></div>
  <div className="syn-specific-data-table"><table><caption>{spec.title}: no authorized results</caption><thead><tr>{show.map(f=><th key={f.id} scope="col">{f.label}</th>)}<th scope="col">Action</th></tr></thead><tbody><tr><td colSpan={show.length+1}>No data connected. Record actions remain unavailable.</td></tr></tbody></table></div>
  <div className="syn-specific-pager"><button type="button" disabled>Previous page</button><span>Page 1 of 1 · 0 records</span><button type="button" disabled>Next page</button></div>
  <LockedAction label={spec.primaryAction}/></div>;
}
function Details({spec,tab}:{spec:SpecificScreenSpec;tab:number}){
 if(tab===spec.tabs.length-1)return <div><h3>Version history and provenance</h3><Empty kind="source revisions"/><p>Audit, actor attribution, effective time, correction history and immutable source linkage require an authorized server projection.</p><LockedAction label="Export authorized history"/></div>;
 const entries=tab===0?spec.fields.slice(0,Math.max(1,Math.ceil(spec.fields.length/2))):spec.fields.slice(Math.max(1,Math.ceil(spec.fields.length/2)));
 return <div><h3>{spec.tabs[tab]}</h3><dl className="syn-specific-attrs">{entries.map(f=><div key={f.id}><dt>{f.label}</dt><dd>Restricted — source not connected</dd></div>)}</dl><LockedAction label={spec.primaryAction}/><LockedAction label="View authorized documents"/></div>;
}
function Compare({spec,tab,reason,onReason}:{spec:SpecificScreenSpec;tab:number;reason:string;onReason:(v:string)=>void}){
 const final=tab===spec.tabs.length-1;
 return <div><h3>{spec.tabs[tab]}</h3>
  {final?<div className="syn-specific-review"><label>Review rationale (synthetic text only)<textarea rows={4} maxLength={300} value={reason} onChange={e=>onReason(e.target.value)} placeholder="Reason must be verified against source policy"/></label><p>Independent reviewer, version conflict, consent and source integrity must be server-confirmed.</p><LockedAction label={spec.primaryAction}/><LockedAction label="Request independent approval"/></div>:
  <div className="syn-specific-compare">{spec.fields.filter((_,i)=>tab===0?i<Math.ceil(spec.fields.length/2):i>=Math.ceil(spec.fields.length/2)).map(f=><section key={f.id}><h4>{f.label}</h4><dl><div><dt>Current authoritative value</dt><dd>Not connected</dd></div><div><dt>Proposed or referenced value</dt><dd>Not connected</dd></div></dl></section>)}</div>}
 </div>;
}
function Board({spec,tab}:{spec:SpecificScreenSpec;tab:number}){
 const [date,setDate]=useState(""),[resource,setResource]=useState(""),[status,setStatus]=useState(""),[view,setView]=useState<"board"|"list">("board");
 return <div><h3>{spec.tabs[tab]}</h3>
  <div className="syn-specific-toolbar"><label>Date<input type="date" value={date} onChange={e=>setDate(e.target.value)}/></label><label>Resource/location<select value={resource} onChange={e=>setResource(e.target.value)}><option value="">Authorized scope only</option><option value="assigned">Assigned resource (preview)</option></select></label><label>Status<select value={status} onChange={e=>setStatus(e.target.value)}><option value="">Any permitted status</option><option value="pending">Pending (preview)</option></select></label><button type="button" className="syn-specific-secondary" aria-pressed={view==="board"} onClick={()=>setView("board")}>Board</button><button type="button" className="syn-specific-secondary" aria-pressed={view==="list"} onClick={()=>setView("list")}>List</button><button type="button" className="syn-specific-secondary" onClick={()=>{setDate("");setResource("");setStatus("");}}>Clear</button></div>
  <div className={view==="board"?"syn-specific-board":"syn-specific-board is-list"}>{spec.fields.map(f=><section className="syn-specific-lane" key={f.id}><h4>{f.label}</h4><p>No authorized items or resource assignments</p></section>)}</div><LockedAction label={spec.primaryAction}/></div>;
}
function Timeline({spec,tab}:{spec:SpecificScreenSpec;tab:number}){
 const [from,setFrom]=useState(""),[to,setTo]=useState(""),[scope,setScope]=useState(""),[metric,setMetric]=useState(spec.fields[0]?.id||"");
 return <div><h3>{spec.tabs[tab]}</h3><div className="syn-specific-toolbar"><label>From<input type="date" value={from} onChange={e=>setFrom(e.target.value)}/></label><label>To<input type="date" value={to} min={from||undefined} onChange={e=>setTo(e.target.value)}/></label><label>Scope<select value={scope} onChange={e=>setScope(e.target.value)}><option value="">Permitted scope only</option><option value="current">Current facility (preview)</option></select></label><label>Measure<select value={metric} onChange={e=>setMetric(e.target.value)}>{spec.fields.map(f=><option key={f.id} value={f.id}>{f.label}</option>)}</select></label></div>
  {tab===0?<Empty kind="time-series measurements"/>:tab===1?<div className="syn-specific-data-table"><table><caption>Accessible measurement table</caption><thead><tr><th scope="col">Timestamp</th><th scope="col">Value / unit</th><th scope="col">Source / revision</th></tr></thead><tbody><tr><td colSpan={3}>No authorized measurements</td></tr></tbody></table></div>:<div><Empty kind="audit/measurement corrections"/><p>Clinical units, reference intervals and source corrections require approved terminology and device evidence.</p></div>}
  <LockedAction label={spec.primaryAction}/><LockedAction label="Export results"/></div>;
}
function Dashboard({spec,tab}:{spec:SpecificScreenSpec;tab:number}){
 const label=tab===0?"assigned tasks":tab===1?"urgent exceptions":"service status";
 return <div><h3>{spec.tabs[tab]}</h3>{tab===0&&<div className="syn-specific-quick">{spec.navigation.modulePeers.filter(id=>id!==spec.id).slice(0,6).map(id=><Link href={"/ui/catalog/"+id} key={id}>{id} · Open task preview</Link>)}</div>}<Empty kind={label}/><div className="syn-specific-attrs">{spec.fields.map(f=><div key={f.id}><strong>{f.label}</strong><p>Server-owned metric or context unavailable</p></div>)}</div><LockedAction label={spec.primaryAction}/></div>;
}
export function SpecificScreenPreview({spec}:{spec:SpecificScreenSpec}){
 const [tab,setTab]=useState(0),[values,setValues]=useState<Record<string,string>>({}),[reason,setReason]=useState(""),[showHelp,setShowHelp]=useState(false),[state,setState]=useState("S00");
 useEffect(()=>{const hidden=()=>{if(document.visibilityState==="hidden"){setValues({});setReason("");}};document.addEventListener("visibilitychange",hidden);return()=>document.removeEventListener("visibilitychange",hidden);},[]);
 const setField=(id:string,v:string)=>setValues(prev=>({...prev,[id]:v}));
 const clear=()=>{setValues({});setReason("");setTab(0);setState("S00");};
 const body=():ReactNode=>{
  switch(spec.layout){
   case "F":return <FormSpecimen spec={spec} tab={tab} values={values} setField={setField} onReview={()=>setTab(spec.tabs.length-1)} onClear={clear}/>;
   case "L":return <Filters spec={spec}/>;
   case "D":return <Details spec={spec} tab={tab}/>;
   case "X":return <Compare spec={spec} tab={tab} reason={reason} onReason={setReason}/>;
   case "B":return <Board spec={spec} tab={tab}/>;
   case "T":return <Timeline spec={spec} tab={tab}/>;
   case "H":return <Dashboard spec={spec} tab={tab}/>;
  }
 };
 return <div className="syn-ui-preview syn-specific" data-specific-screen={spec.id} data-layout={spec.layout}>
  <div className="syn-specific-status"><div><strong>{spec.module} · {spec.moduleName}</strong><p>{spec.eligibilityNote} · Actual access requires server authorization.</p></div><span className="syn-chip">Screen-specific preview</span></div>
  <div className="syn-specific-banner" role="status"><strong>Design specimen only — no patient or transaction data connected</strong><p>Do not enter real information. Clinical actions, exports and signed decisions cannot be performed from this interface.</p></div>
  <div className="syn-specific-quick"><a href="#specific-workspace">Workspace</a><a href="#specific-workflow">Workflow</a><a href="#specific-states">States</a><button type="button" className="syn-specific-secondary" onClick={()=>setShowHelp(v=>!v)} aria-expanded={showHelp}>{showHelp?"Hide help":"Help and action inventory"}</button></div>
  {showHelp&&<section className="syn-specific-help"><h3>Registered UI controls and dialogs</h3><ul>{unique(spec.controls).map(c=><li key={c}>{c}</li>)}</ul><h4>Required protected dialogs</h4><p>{spec.modalContracts.join(" · ")}</p><p>Candidate print outputs: {spec.printContractCandidates.length?spec.printContractCandidates.join(", "):"none specified for this screen"}. All exports locked.</p></section>}
  <section id="specific-workspace" aria-label={spec.title+" interactive specimen"}><TabBar spec={spec} active={tab} onChange={setTab}>{body()}</TabBar></section>
  <section id="specific-workflow" className="syn-specific-workflow"><h3>Module workflow and release preconditions</h3><p>{spec.authorityWorkflow}</p><ol><li>Verify identity, tenant, facility, role and protected resource.</li><li>Fetch current authoritative source and permissions; avoid patient existence disclosure.</li><li>Review the complete {spec.title} data groups and recorded provenance.</li><li>Perform <strong>{spec.primaryAction}</strong> only after approved validation, audit/outbox and source revision checks.</li><li>Confirm durable outcome, handle unknown status, conflict, cancellation and recovery.</li></ol><LockedAction label={spec.primaryAction}/></section>
  <section id="specific-states" className="syn-specific-states"><h3>Interface states</h3><label>Preview interaction state<select value={state} onChange={e=>setState(e.target.value)}>{states.map(s=><option key={s.key} value={s.key}>{s.key} · {s.label}</option>)}</select></label><div role="status" className="syn-specific-notice">{states.find(s=>s.key===state)?.message}</div></section>
  <div className="syn-specific-footer"><button type="button" className="syn-specific-secondary" onClick={clear}>Clear volatile preview state</button><LockedAction label="Print or export authorized record"/><p>Per-screen field types, requiredness, exact clinical action menus, English/French terms, role/permission contracts and clinical acceptance remain pending. The UI is not production-certified.</p></div>
 </div>;
}
