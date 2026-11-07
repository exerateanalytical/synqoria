"use client";
import {useRef,useState} from "react";
import {call,newKey} from "../../lib/content/client";
import type {Failure,Page} from "../../lib/content/client";
import {enumLabel} from "../../lib/content/i18n";
import {ActionButton,ActionError,Badge,ContentFrame,Empty,FailureView,Field,fmtTime,Loading,Notice,Pager,styles,toneFor,useContent,useLoad} from "./ui";

type Entry={id:string;entryKey:string;versionNo:number;productVersionId:string;productCode:string;productDisplayEn?:string;formulation:string;route:string;permittedUse:string;
 restrictions:string[];status:string;supersedesId:string|null;authorId:string;approverId:string|null;decisionNote:string|null;version:number;updatedAt:string;history?:any[]};
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const STATUSES=["DRAFT","SUBMITTED","APPROVED","REJECTED","SUPERSEDED","WITHDRAWN"];

function Inner(){
 const {t,lang}=useContent();
 const [status,setStatus]=useState("");const [q,setQ]=useState("");const [page,setPage]=useState(0);
 const [sel,setSel]=useState<string|null>(null);
 const [mode,setMode]=useState<"new"|"edit">("new");
 const blank={key:"",pv:"",pc:"",form:"",route:"ORAL",use:"",restr:""};
 const [f,setF]=useState(blank);
 const [failure,setFailure]=useState<Failure|null>(null);const [done,setDone]=useState<string|null>(null);const [busy,setBusy]=useState(false);
 const [rejecting,setRejecting]=useState(false);const [note,setNote]=useState("");
 const key=useRef(newKey());
 const list=useLoad<Page<Entry>>(s=>call("GET","formulary",{signal:s,query:{status:status||undefined,q:q.trim()||undefined,page,size:25}}),[status,q,page]);
 const detail=useLoad<Entry>(s=>sel?call("GET",`formulary/${sel}`,{signal:s}):Promise.resolve({ok:false,kind:"notfound",status:0,code:"none",details:[]} as Failure),[sel]);
 const e=sel?detail.data:undefined;
 const edit=(patch:Partial<typeof f>)=>{setF(x=>({...x,...patch}));key.current=newKey();setFailure(null);};
 const restr=f.restr.split("\n").map(x=>x.trim()).filter(Boolean);
 const formReason=mode==="new"&&(!/^[A-Za-z0-9._-]{1,64}$/.test(f.key)||!UUID.test(f.pv)||!/^[A-Za-z0-9._-]{1,64}$/.test(f.pc))?t("disabled.input")
  :!f.form.trim()||!/^[A-Z0-9_]{2,40}$/.test(f.route)||!f.use.trim()||restr.length>20?t("disabled.input"):null;

 async function run<T>(fn:()=>Promise<import("../../lib/content/client").Result<T>>,ok?:(d:T)=>void){
  if(busy)return;setBusy(true);setFailure(null);setDone(null);
  const r=await fn();setBusy(false);
  if(!r.ok){setFailure(r);if(r.code==="stale_version")detail.reload();return;}
  ok?.(r.data);list.reload();if(sel)detail.reload();
 }
 const submitForm=()=>mode==="new"
  ?run<Entry>(()=>call("POST","formulary",{key:key.current,body:{entryKey:f.key,productVersionId:f.pv,productCode:f.pc,formulation:f.form.trim(),route:f.route,permittedUse:f.use.trim(),restrictions:restr}}),d=>{key.current=newKey();setSel(d.id);setF(blank);setDone(t("fm.create"));})
  :run<Entry>(()=>call("POST",`formulary/${e!.id}/update`,{key:key.current,body:{expectedVersion:e!.version,formulation:f.form.trim(),route:f.route,permittedUse:f.use.trim(),restrictions:restr}}),()=>{key.current=newKey();setMode("new");setF(blank);setDone(t("fm.save"));});
 const step=(path:string,body:object)=>run(()=>call("POST",`formulary/${e!.id}/${path}`,{body}),()=>{setRejecting(false);setNote("");setDone(t("common.updated"));});
 const why=(allowed:string[]):string|null=>!e?t("disabled.select"):allowed.includes(e.status)?null:t("disabled.status",{status:enumLabel(lang,"formulary",e.status)});
 function startEdit(){if(!e)return;setMode("edit");setF({key:e.entryKey,pv:e.productVersionId,pc:e.productCode,form:e.formulation,route:e.route,use:e.permittedUse,restr:e.restrictions.join("\n")});key.current=newKey();}

 return <div className={styles.cols}>
  <section className={styles.panel} aria-label={t("fm.list")}>
   <h2>{t("fm.list")}</h2>
   <div className={styles.filters}>
    <Field id="fm-q" label={t("fm.search")}><input id="fm-q" type="search" className={styles.input} value={q} maxLength={100} onChange={ev=>{setQ(ev.target.value);setPage(0);}}/></Field>
    <Field id="fm-s" label={t("tm.status")}><select id="fm-s" className={styles.input} value={status} onChange={ev=>{setStatus(ev.target.value);setPage(0);}}>
     <option value="">{t("common.any")}</option>{STATUSES.map(s=><option key={s} value={s}>{enumLabel(lang,"formulary",s)}</option>)}</select></Field>
   </div>
   <div className={styles.actions}><button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={list.reload}>{t("common.refresh")}</button></div>
   <div aria-busy={list.phase==="loading"} style={{marginTop:12}}>
    {list.phase==="loading"&&!list.data&&<Loading/>}
    {list.phase==="failed"&&list.failure&&<FailureView failure={list.failure} onRetry={list.reload}/>}
    {list.data&&list.data.items.length===0&&list.phase!=="failed"&&<Empty/>}
    {list.data&&list.data.items.length>0&&list.phase!=="failed"&&<>
     <ul className={styles.list}>{list.data.items.map(x=><li key={x.id}>
      <button type="button" className={styles.row} aria-pressed={sel===x.id} onClick={()=>{setSel(x.id);setFailure(null);setRejecting(false);}}>
       <span><Badge tone={toneFor(x.status)}>{enumLabel(lang,"formulary",x.status)}</Badge> <strong>{x.entryKey}</strong> v{x.versionNo}</span>
       <span>{x.productDisplayEn} · {x.formulation} · {x.route}</span>
      </button></li>)}</ul>
     <Pager page={list.data.page} size={list.data.size} total={list.data.total} onPage={setPage}/></>}
   </div>
  </section>

  <div>
   <section className={styles.panel} aria-label={t("fm.new")}>
    <h2>{mode==="new"?t("fm.new"):t("fm.save")}</h2>
    <form onSubmit={ev=>{ev.preventDefault();}} noValidate>
     <div className={styles.formGrid}>
      <Field id="ff-k" label={t("fm.key")}><input id="ff-k" className={styles.input} disabled={mode==="edit"} value={f.key} onChange={ev=>edit({key:ev.target.value.trim()})}/></Field>
      <Field id="ff-pc" label={t("fm.productCode")}><input id="ff-pc" className={styles.input} disabled={mode==="edit"} value={f.pc} onChange={ev=>edit({pc:ev.target.value.trim()})}/></Field>
      <Field id="ff-pv" label={t("fm.productVersion")} wide><input id="ff-pv" className={`${styles.input} ${styles.mono}`} disabled={mode==="edit"} value={f.pv} onChange={ev=>edit({pv:ev.target.value.trim()})}/></Field>
      <Field id="ff-f" label={t("fm.formulation")}><input id="ff-f" className={styles.input} maxLength={80} value={f.form} onChange={ev=>edit({form:ev.target.value})}/></Field>
      <Field id="ff-r" label={t("fm.route")}><input id="ff-r" className={styles.input} maxLength={40} value={f.route} onChange={ev=>edit({route:ev.target.value.trim().toUpperCase()})}/></Field>
      <Field id="ff-u" label={t("fm.use")} wide><textarea id="ff-u" className={styles.input} maxLength={500} value={f.use} onChange={ev=>edit({use:ev.target.value})}/></Field>
      <Field id="ff-x" label={t("fm.restrictions")} wide><textarea id="ff-x" className={styles.input} value={f.restr} onChange={ev=>edit({restr:ev.target.value})}/></Field>
     </div>
     <div className={styles.actions}>
      <ActionButton label={mode==="new"?t("fm.create"):t("fm.save")} reason={formReason} busy={busy} onClick={()=>void submitForm()}/>
      {mode==="edit"&&<button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={()=>{setMode("new");setF(blank);}}>{t("common.cancel")}</button>}
     </div>
    </form>
    <ActionError failure={failure} onReload={()=>{detail.reload();list.reload();setFailure(null);}}/>
    {done&&<Notice>{done}</Notice>}
   </section>

   <section className={styles.panel} aria-label={t("common.details")} aria-live="polite">
    {!sel&&<p>{t("fm.select")}</p>}
    {sel&&detail.phase==="loading"&&!e&&<Loading/>}
    {sel&&detail.phase==="failed"&&detail.failure&&<FailureView failure={detail.failure} onRetry={detail.reload}/>}
    {e&&<>
     <h2><Badge tone={toneFor(e.status)}>{enumLabel(lang,"formulary",e.status)}</Badge> {e.entryKey} v{e.versionNo}</h2>
     <dl className={styles.kv}>
      <dt>{t("fm.product")}</dt><dd><span className={styles.mono}>{e.productCode}</span> {e.productDisplayEn}</dd>
      <dt>{t("fm.formulation")}</dt><dd>{e.formulation}</dd><dt>{t("fm.route")}</dt><dd>{e.route}</dd><dt>{t("fm.use")}</dt><dd>{e.permittedUse}</dd>
      <dt>{t("fm.restrictions")}</dt><dd>{e.restrictions.join("; ")||t("common.none")}</dd>
      <dt>{t("fm.authoredBy")}</dt><dd className={styles.mono}>{e.authorId}</dd><dt>{t("fm.approvedBy")}</dt><dd className={styles.mono}>{e.approverId??"—"}</dd>
      {e.supersedesId&&<><dt>{t("fm.supersedes")}</dt><dd className={styles.mono}>{e.supersedesId}</dd></>}
      {e.decisionNote&&<><dt>{t("common.note")}</dt><dd>{e.decisionNote}</dd></>}
      <dt>{t("common.version")}</dt><dd>{e.version}</dd>
     </dl>
     <div className={styles.actions}>
      <ActionButton label={t("fm.save")} tone="secondary" reason={why(["DRAFT"])} onClick={startEdit}/>
      <ActionButton label={t("fm.submit")} reason={why(["DRAFT"])} busy={busy} onClick={()=>void step("submit",{expectedVersion:e.version})}/>
      <ActionButton label={t("fm.approve")} reason={why(["SUBMITTED"])} busy={busy} onClick={()=>void step("decision",{expectedVersion:e.version,approve:true})}/>
      <ActionButton label={t("fm.reject")} tone="danger" reason={why(["SUBMITTED"])} onClick={()=>setRejecting(true)}/>
      <ActionButton label={t("fm.withdraw")} tone="secondary" reason={why(["DRAFT","APPROVED"])} busy={busy} onClick={()=>void step("withdraw",{expectedVersion:e.version})}/>
     </div>
     {rejecting&&<div className={styles.formGrid} style={{marginTop:12}}>
      <Field id="fm-note" label={t("fm.rejectReason")} wide><input id="fm-note" className={styles.input} autoFocus maxLength={300} value={note} onChange={ev=>setNote(ev.target.value)}/></Field>
      <div className={styles.actions}><ActionButton label={t("fm.reject")} tone="danger" busy={busy} reason={note.trim()?null:t("err.reason_required")} onClick={()=>void step("decision",{expectedVersion:e.version,approve:false,note:note.trim()})}/>
       <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={()=>setRejecting(false)}>{t("common.cancel")}</button></div>
     </div>}
     <h3>{t("fm.history")}</h3>
     <ol className={styles.timeline}>{(e.history??[]).map((h:any)=><li key={h.eventId}><strong>{h.action}</strong> {h.fromStatus?`${h.fromStatus} → `:""}{h.toStatus}
      <div className={styles.hint}>{fmtTime(lang,h.occurredAt)} · <span className={styles.mono}>{h.actorId}</span></div></li>)}</ol>
    </>}
   </section>
  </div>
 </div>;
}
export function Formulary(){return <ContentFrame active="formulary" title="fm.title" intro="fm.intro"><Inner/></ContentFrame>;}
