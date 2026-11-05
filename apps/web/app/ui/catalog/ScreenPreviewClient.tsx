"use client";
import {useId, useRef, useState} from "react";
import type {KeyboardEvent, ReactNode} from "react";
import Link from "next/link";
import "./interaction.css";
import {IdentityRegistrationPreview,isIdentityPreview} from "./IdentityRegistrationPreview";

export type ScreenContract = {
 screenId:string; module:string; title:string; layout:"F"|"L"|"D"|"X"|"B"|"T"|"H";
 fieldGroups:string[]; tabs:string[]; controls:string[]; filters:string[];
 primaryAction:string; saveMode:string; authorization:string; release:string;
 contentStatus:string; uiStateIds:string[];
};
type ScreenState="initial"|"loading"|"empty"|"ready"|"dirty"|"validating"|"confirmed"|"validation-error"|"denied"|"offline"|"conflict"|"unknown"|"failure"|"retired";
const states: {id:ScreenState;label:string;message:string}[]=[
 {id:"initial",label:"S00 Initial",message:"Identity, facility and patient access must be established before data is shown."},
 {id:"loading",label:"S01 Loading",message:"Loading view. Clinical commands remain unavailable."},
 {id:"empty",label:"S02 Empty",message:"No authorized records are connected to this preview."},
 {id:"ready",label:"S03 Ready",message:"A server-authoritative result is required to enter the real ready state."},
 {id:"dirty",label:"S04 Dirty",message:"Unsaved demonstration entries exist in memory only."},
 {id:"validating",label:"S05 Validating",message:"No clinical command has been submitted."},
 {id:"confirmed",label:"S06 Confirmed",message:"Confirmation requires a server-issued result and version. Not available."},
 {id:"validation-error",label:"S07 Validation error",message:"Check the displayed demo input errors. No backend validation performed."},
 {id:"denied",label:"S08 Access denied",message:"This preview has no authorization to retrieve clinical records."},
 {id:"offline",label:"S09 Offline/stale",message:"Live connectivity and authorized offline capabilities have not been established."},
 {id:"conflict",label:"S10 Conflict",message:"Server revision comparison is unavailable; do not overwrite data."},
 {id:"unknown",label:"S11 Outcome unknown",message:"A missing response cannot be treated as clinical success or failure."},
 {id:"failure",label:"S12 Service failure",message:"Use approved downtime and support procedures. No clinical retry available."},
 {id:"retired",label:"S13 Retired/not found",message:"Return to a permitted workspace without exposing patient existence."}
];
const safeFields=(s:ScreenContract)=>s.fieldGroups.map((name,i)=>({id:"group-"+i,name}));
function PreviewAlert({children}:{children:ReactNode}){return <div className="syn-action-note" role="status">{children}</div>}
function SafeAction({label}:{label:string}){return <button type="button" className="syn-locked" disabled title="Requires authorization, authoritative API and clinical acceptance">{label}<span className="syn-lock-reason">Requires verified clinical connection</span></button>}
function SectionHeader({title,sub}:{title:string;sub?:string}){return <div className="syn-section-title"><h3>{title}</h3>{sub&&<p>{sub}</p>}</div>}
function EmptyRecords({label="records"}:{label?:string}){return <div className="syn-empty"><strong>No authorized {label} loaded</strong><p>Real records, totals and statuses are intentionally withheld. This screen is a local interaction specimen, not a clinical data source.</p></div>}
function FormArea({contract,values,onSet,review,onReview,onReset}:{contract:ScreenContract;values:Record<string,string>;onSet:(key:string,value:string)=>void;review:boolean;onReview:()=>void;onReset:()=>void}){
 const fields=safeFields(contract);
 return <div><SectionHeader title={review?"Review demonstration entries":"Information capture"} sub="Synthetic examples only. Do not enter real patient, staff or financial details."/>
 {review?<div className="syn-review-grid">{fields.map(f=><div key={f.id}><span>{f.name}</span><strong>{values[f.id]?.trim()?"Demonstration entry provided":"Not entered"}</strong></div>)}</div>:<form className="syn-spec-form" onSubmit={e=>{e.preventDefault();onReview();}} noValidate>
 {fields.map(f=><label key={f.id} htmlFor={contract.screenId+"-"+f.id}><span>{f.name}</span><input id={contract.screenId+"-"+f.id} type="text" autoComplete="off" spellCheck={false} value={values[f.id]??""} onChange={e=>onSet(f.id,e.target.value)} maxLength={200} placeholder="Demonstration input only" aria-describedby={contract.screenId+"-demo-help"}/></label>)}
 <p id={contract.screenId+"-demo-help"} className="syn-help syn-span">Field types, clinical units, requiredness and terminology must be approved in the screen-specific contract; this demo does not invent them.</p>
 <div className="syn-buttons syn-span"><button className="syn-preview-primary" type="submit">Review demonstration entries</button><button className="syn-preview-secondary" type="button" onClick={onReset}>Clear demo draft</button></div>
 </form>}
 <div className="syn-safety-actions"><SafeAction label={contract.primaryAction}/><span>Final clinical submission is locked. Data is never sent, stored or synchronized.</span></div>
 </div>;
}
function FilterControls({query,onQuery,filterOpen,onFilterOpen,applied,onApply,onClear,scope,onScope,status,onStatus,from,onFrom,to,onTo}:{query:string;onQuery:(v:string)=>void;filterOpen:boolean;onFilterOpen:(v:boolean)=>void;applied:boolean;onApply:()=>void;onClear:()=>void;scope:string;onScope:(v:string)=>void;status:string;onStatus:(v:string)=>void;from:string;onFrom:(v:string)=>void;to:string;onTo:(v:string)=>void}){
 return <section aria-label="Search and filters" className="syn-filters"><div className="syn-filter-toolbar">
 <label className="syn-search"><span>Search authorized results</span><input type="search" value={query} onChange={e=>onQuery(e.target.value)} placeholder="Search once connected"/></label>
 <button type="button" className="syn-preview-secondary" onClick={()=>onFilterOpen(!filterOpen)} aria-expanded={filterOpen} aria-controls="syn-filter-panel">{filterOpen?"Hide filters":"Show filters"}</button>
 <button type="button" className="syn-preview-secondary" onClick={onClear}>Clear all</button>
 </div>
 {filterOpen&&<div id="syn-filter-panel" className="syn-filter-grid">
 <label>Scope<select value={scope} onChange={e=>onScope(e.target.value)}><option value="">All permitted scopes</option><option value="current">Current facility (preview)</option><option value="assigned">Assigned to me (preview)</option></select></label>
 <label>Status<select value={status} onChange={e=>onStatus(e.target.value)}><option value="">All statuses</option><option value="open">Open (preview)</option><option value="closed">Closed (preview)</option></select></label>
 <label>Date from<input type="date" value={from} onChange={e=>onFrom(e.target.value)}/></label><label>Date to<input type="date" min={from||undefined} value={to} onChange={e=>onTo(e.target.value)}/></label><button type="button" className="syn-preview-primary" onClick={onApply}>Apply filters</button>
 </div>}
 {applied&&<p className="syn-filter-applied" role="status">Filters applied to the preview. No server query was made.</p>}
 </section>;
}
function WorklistArea({contract}:{contract:ScreenContract}){
 const [query,setQuery]=useState("");const [open,setOpen]=useState(false);const [scope,setScope]=useState("");const [status,setStatus]=useState("");const [from,setFrom]=useState("");const [to,setTo]=useState("");const [applied,setApplied]=useState(false);const [sort,setSort]=useState("recent");const [pageSize,setPageSize]=useState("25");
 const clear=()=>{setQuery("");setScope("");setStatus("");setFrom("");setTo("");setApplied(false);};
 return <div><SectionHeader title="Authorized worklist" sub="Filter and sorting controls are interactive; patient records and counts require a protected service."/>
 <FilterControls query={query} onQuery={setQuery} filterOpen={open} onFilterOpen={setOpen} applied={applied} onApply={()=>setApplied(true)} onClear={clear} scope={scope} onScope={setScope} status={status} onStatus={setStatus} from={from} onFrom={setFrom} to={to} onTo={setTo}/>
 <div className="syn-record-options"><label>Sort by<select value={sort} onChange={e=>setSort(e.target.value)}><option value="recent">Most recent</option><option value="oldest">Oldest</option><option value="name">Name / label</option></select></label><label>Results per page<select value={pageSize} onChange={e=>setPageSize(e.target.value)}><option>10</option><option>25</option><option>50</option></select></label><span>Showing 0 authorized results</span></div>
 <div role="region" aria-label="Result columns" className="syn-table-scroll"><table><caption>Expected information columns — not patient data</caption><thead><tr>{contract.fieldGroups.map((g,i)=><th key={i} scope="col">{g}</th>)}<th scope="col">Action</th></tr></thead><tbody><tr><td colSpan={contract.fieldGroups.length+1}>No authorized data connected</td></tr></tbody></table></div>
 <div className="syn-paging"><button type="button" disabled>Previous page</button><span>Page 1 of 1 · 0 records</span><button type="button" disabled>Next page</button></div><SafeAction label={contract.primaryAction}/></div>;
}
function BoardArea({contract}:{contract:ScreenContract}){
 const [view,setView]=useState<"board"|"list">("board");const [date,setDate]=useState("");const [filterOpen,setFilterOpen]=useState(false);
 return <div><SectionHeader title="Work board and resource context"/><div className="syn-filter-toolbar"><label>Date<input type="date" value={date} onChange={e=>setDate(e.target.value)}/></label><button type="button" aria-pressed={view==="board"} className="syn-preview-secondary" onClick={()=>setView("board")}>Board view</button><button type="button" aria-pressed={view==="list"} className="syn-preview-secondary" onClick={()=>setView("list")}>List view</button><button type="button" className="syn-preview-secondary" aria-expanded={filterOpen} onClick={()=>setFilterOpen(!filterOpen)}>{filterOpen?"Hide filters":"Show filters"}</button></div>
 {filterOpen&&<div className="syn-filter-grid"><label>Location<select><option>Permitted locations only</option></select></label><label>Staff / resource<select><option>Authorized resources only</option></select></label><label>Status<select><option>All permitted statuses</option><option>Assigned</option><option>Waiting</option></select></label></div>}
 <div className={view==="board"?"syn-board":"syn-list-view"}>{contract.fieldGroups.slice(0,6).map((g,i)=><section className="syn-board-lane" key={i}><h4>{g}</h4><p>No authorized items to display</p></section>)}</div><SafeAction label={contract.primaryAction}/></div>;
}
function TimelineArea({contract}:{contract:ScreenContract}){
 const [from,setFrom]=useState("");const [to,setTo]=useState("");const [scope,setScope]=useState("");const [metric,setMetric]=useState(contract.fieldGroups[0]??"Metric");
 return <div><SectionHeader title="Time range and provenance" sub="No synthetic clinical metrics or graphs are invented."/><div className="syn-filter-grid"><label>From<input type="date" value={from} onChange={e=>setFrom(e.target.value)}/></label><label>To<input type="date" value={to} min={from||undefined} onChange={e=>setTo(e.target.value)}/></label><label>Scope<select value={scope} onChange={e=>setScope(e.target.value)}><option value="">Current permitted scope</option><option value="facility">Facility (preview)</option><option value="assigned">Assigned (preview)</option></select></label><label>Measure<select value={metric} onChange={e=>setMetric(e.target.value)}>{contract.fieldGroups.map(g=><option key={g}>{g}</option>)}</select></label><SafeAction label="Export authorized data"/></div><EmptyRecords label="time series"/><div className="syn-table-scroll"><table><caption>Accessible data-table alternative</caption><thead><tr><th scope="col">Timestamp</th><th scope="col">Value and unit</th><th scope="col">Source and version</th></tr></thead><tbody><tr><td colSpan={3}>No authorized metrics available</td></tr></tbody></table></div></div>;
}
function DetailArea({contract}:{contract:ScreenContract}){
 return <div><SectionHeader title="Record details" sub="Field values, provenance, updates and attachments are permission-gated."/><dl className="syn-detail-list">{contract.fieldGroups.map((g,i)=><div key={i}><dt>{g}</dt><dd>Requires authorized server read</dd></div>)}</dl><div className="syn-safety-actions"><SafeAction label={contract.primaryAction}/><SafeAction label="View authorized attachments"/></div></div>;
}
function ReviewArea({contract,reason,onReason}:{contract:ScreenContract;reason:string;onReason:(v:string)=>void}){
 return <div><SectionHeader title="Comparison and decision" sub="No actual patient versions are loaded. Human review and separation of duties remain mandatory."/><div className="syn-compare">{contract.fieldGroups.map((g,i)=><div key={i}><h4>{g}</h4><div className="syn-review-grid"><p>Current: not loaded</p><p>Proposed: not loaded</p></div></div>)}</div><label className="syn-reason">Review rationale (demonstration only)<textarea value={reason} onChange={e=>onReason(e.target.value)} rows={4} maxLength={500} placeholder="No patient identifiers; preview only"/></label><div className="syn-safety-actions"><SafeAction label={contract.primaryAction}/><SafeAction label="Request independent review"/></div></div>;
}
function DashboardArea({contract,related,tab}:{contract:ScreenContract;related:{id:string;name:string}[];tab:string}){
 return <div><SectionHeader title={tab==="Tasks"?"Assigned tasks and exceptions":tab} sub="Quick actions precede secondary statistics. No patient data connected."/><div className="syn-quick-grid">{related.filter(x=>x.id!==contract.screenId).slice(0,6).map(x=><Link key={x.id} href={"/ui/catalog/"+encodeURIComponent(x.id)}><span>{x.id}</span><strong>{x.name}</strong></Link>)}</div>{tab==="Tasks"?<EmptyRecords label="assigned tasks"/>:tab==="Exceptions"?<EmptyRecords label="urgent exceptions"/>:<EmptyRecords label="service status reports"/>}<div className="syn-detail-list"><div><strong>Urgent exceptions</strong><p>No connected alert feed</p></div><div><strong>Service availability</strong><p>Unverified — clinical services locked</p></div></div></div>;
}
function WorkflowArea({contract}:{contract:ScreenContract}){
 return <div><SectionHeader title={contract.title+" — workflow"} sub="Design-stage sequence; clinical transitions still require approved module-specific contracts."/><ol className="syn-steps"><li><strong>Authenticate and establish scope</strong><p>Verify staff identity, role, tenant, facility, patient and current encounter when applicable.</p></li><li><strong>Load authoritative context</strong><p>Retrieve current source version, allergies, consent and service status as required by the workflow.</p></li><li><strong>Review {contract.fieldGroups.join("; ")}</strong><p>Apply reviewed field dictionary, validation and terminology. No clinical values are inferred.</p></li><li><strong>{contract.primaryAction}</strong><p>Locked pending server authorization, clinical decision rules, idempotency and transactional audit.</p></li><li><strong>Confirm durable outcome</strong><p>Show server response, version, provenance, recoverable unknown outcomes and next permitted action.</p></li></ol></div>;
}
function Tabs({contract,active,onSelect,children}:{contract:ScreenContract;active:number;onSelect:(n:number)=>void;children:ReactNode}){
 const tabRefs=useRef<(HTMLButtonElement|null)[]>([]);
 const base=useId().replace(/:/g,"");
 function onKey(e:KeyboardEvent<HTMLButtonElement>,i:number){
  let n=i;if(e.key==="ArrowRight")n=(i+1)%contract.tabs.length;else if(e.key==="ArrowLeft")n=(i+contract.tabs.length-1)%contract.tabs.length;else if(e.key==="Home")n=0;else if(e.key==="End")n=contract.tabs.length-1;else return;
  e.preventDefault();onSelect(n);tabRefs.current[n]?.focus();
 }
 return <div><div className="syn-real-tabs" role="tablist" aria-label={contract.title+" sections"}>{contract.tabs.map((tab,i)=><button key={tab} type="button" ref={node=>{tabRefs.current[i]=node}} role="tab" id={base+"tab"+i} aria-controls={base+"panel"} aria-selected={active===i} tabIndex={active===i?0:-1} onClick={()=>onSelect(i)} onKeyDown={e=>onKey(e,i)}>{tab}</button>)}</div><div role="tabpanel" id={base+"panel"} aria-labelledby={base+"tab"+active} tabIndex={0} className="syn-tabpanel">{children}</div></div>;
}
export function ScreenPreviewClient({contract,related}:{contract:ScreenContract;related:{id:string;name:string}[]}){
 const [active,setActive]=useState(0);const [values,setValues]=useState<Record<string,string>>({});const [reason,setReason]=useState("");const [review,setReview]=useState(false);const [state,setState]=useState<ScreenState>("initial");const [detailsOpen,setDetailsOpen]=useState(false);const [helpOpen,setHelpOpen]=useState(false);
 const setField=(key:string,value:string)=>{setValues(v=>({...v,[key]:value}));setReview(false);setState("dirty");};
 const clear=()=>{setValues({});setReview(false);setReason("");setState("initial");};
 const current=states.find(s=>s.id===state)!;
 const isFieldForm=contract.layout==="F";
 function mainPanel():ReactNode{
  if(contract.tabs[active]==="Workflow")return <WorkflowArea contract={contract}/>;
  if(contract.layout==="F")return <FormArea contract={contract} values={values} onSet={setField} review={active===1||review} onReview={()=>{setReview(true);setActive(1)}} onReset={clear}/>;
  if(contract.layout==="L")return active===1?<div><SectionHeader title="Filter and result permissions"/><p>Search, scope, status, sort, clear and pagination controls are available in Results. Only authorized queries may return records.</p><WorklistArea contract={contract}/></div>:<WorklistArea contract={contract}/>;
  if(contract.layout==="D")return active===1?<DetailArea contract={contract}/>:active===2?<EmptyRecords label="audited history"/>:<DetailArea contract={contract}/>;
  if(contract.layout==="X")return active===1?<div><DetailArea contract={contract}/></div>:<ReviewArea contract={contract} reason={reason} onReason={setReason}/>;
  if(contract.layout==="B")return <BoardArea contract={contract}/>;
  if(contract.layout==="T")return <TimelineArea contract={contract}/>;
  if(contract.layout==="H")return <DashboardArea contract={contract} related={related} tab={contract.tabs[active]}/>;
  return <EmptyRecords/>;
 }
 if(isIdentityPreview(contract.screenId))return <IdentityRegistrationPreview screenId={contract.screenId} primaryAction={contract.primaryAction}/>;
 return <div className="syn-ui-preview" data-screen-id={contract.screenId}>
 <div className="syn-preview-toolbar"><span className="syn-chip">UI specimen · {contract.layout}</span><button type="button" className="syn-preview-secondary" onClick={()=>setDetailsOpen(v=>!v)} aria-expanded={detailsOpen}>Control inventory ({contract.controls.length})</button><button type="button" className="syn-preview-secondary" onClick={()=>setHelpOpen(v=>!v)} aria-expanded={helpOpen}>Help & safety</button></div>
 {detailsOpen&&<section className="syn-inventory"><h3>Registered layout controls</h3><ul>{contract.controls.map(x=><li key={x}>{x}</li>)}</ul><p>Proposed shared layout controls are not a substitute for the per-screen field/role review.</p></section>}
 {helpOpen&&<section className="syn-inventory"><h3>Help and safety</h3><p>No real patient, staff or financial data. Preview inputs are memory-only and are cleared when this component unmounts. Do not enter identifiable information. No clinical command or download is available.</p><Link href="/ui/catalog">Return to catalogue</Link></section>}
 <Tabs contract={contract} active={active} onSelect={setActive}>{mainPanel()}</Tabs>
 <section className="syn-state-lab" aria-label="Preview state coverage"><div className="syn-section-title"><h3>Interface state lab</h3><p>Demonstrate the expected explanation for each of the 14 canonical states. This selector does not change server state.</p></div><label>Preview a state<select value={state} onChange={e=>setState(e.target.value as ScreenState)}>{states.map(s=><option key={s.id} value={s.id}>{s.label}</option>)}</select></label><PreviewAlert><strong>{current.label}</strong><p>{current.message}</p></PreviewAlert></section>
 <p className="syn-preview-disclaimer">Design-stage interaction specimen only. UI route presence does not establish a completed screen, clinical validation, server connectivity or release authorization.</p>
 </div>;
}
