"use client";
import {useState} from "react";
import {call,newKey} from "../../lib/content/client";
import type {Failure,Page} from "../../lib/content/client";
import {enumLabel,safetyLabel} from "../../lib/content/i18n";
import {ActionButton,ActionError,Badge,ContentFrame,Empty,FailureView,Field,fmtTime,Loading,Notice,Pager,styles,toneFor,useContent,useLoad} from "./ui";

type Pkg={id:string;packageKey:string;versionNo:number;status:string;definitionHash:string;authorId:string;version:number;updatedAt:string;
 validationRuns?:any[];approvals?:any[];history?:any[]};
const QUEUE=["VALIDATED","IN_REVIEW","APPROVED","VALIDATION_FAILED","REJECTED","REVOKED"];

function Section({title,items}:{title:string;items:any[]}){
 const {lang}=useContent();
 if(!items||items.length===0)return null;
 return <div><h4>{title}</h4><ul className={styles.list}>{items.map((x,i)=><li key={i} className={styles.card}>
  {typeof x==="string"?safetyLabel(lang,x):<span className={styles.mono}>{JSON.stringify(x)}</span>}</li>)}</ul></div>;
}

function Inner(){
 const {t,lang}=useContent();
 const [status,setStatus]=useState("IN_REVIEW");const [page,setPage]=useState(0);
 const [sel,setSel]=useState<string|null>(null);
 const [rv,setRv]=useState({role:"CLINICAL",approve:true,reason:"",days:30});
 const [revokeFor,setRevokeFor]=useState<string|null>(null);const [revokeReason,setRevokeReason]=useState("");
 const [failure,setFailure]=useState<Failure|null>(null);const [done,setDone]=useState<string|null>(null);const [busy,setBusy]=useState(false);
 const list=useLoad<Page<Pkg>>(s=>call("GET","packages",{signal:s,query:{status:status||undefined,page,size:25}}),[status,page]);
 const detail=useLoad<Pkg>(s=>sel?call("GET",`packages/${sel}`,{signal:s}):Promise.resolve({ok:false,kind:"notfound",status:0,code:"none",details:[]} as Failure),[sel]);
 const p=sel?detail.data:undefined;
 const report=p?.validationRuns?.[0]?.report;

 async function run<T>(fn:()=>Promise<import("../../lib/content/client").Result<T>>,ok?:()=>void){
  if(busy)return;setBusy(true);setFailure(null);setDone(null);
  const r=await fn();setBusy(false);
  if(!r.ok){setFailure(r);if(r.code==="stale_version")detail.reload();return;}
  ok?.();list.reload();detail.reload();
 }
 const why=(allowed:string[]):string|null=>!p?t("disabled.select"):allowed.includes(p.status)?null:t("disabled.status",{status:enumLabel(lang,"package",p.status)});
 const reviewReason=why(["IN_REVIEW"])??(!rv.reason.trim()?t("err.reason_required"):rv.days<1||rv.days>365?t("disabled.input"):null);

 return <div className={styles.cols}>
  <section className={styles.panel} aria-label={t("ap.queue")}>
   <h2>{t("ap.queue")}</h2>
   <Field id="aq-s" label={t("tm.status")}><select id="aq-s" className={styles.input} value={status} onChange={e=>{setStatus(e.target.value);setPage(0);}}>
    <option value="">{t("common.any")}</option>{QUEUE.map(s=><option key={s} value={s}>{enumLabel(lang,"package",s)}</option>)}</select></Field>
   <div className={styles.actions}><button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={list.reload}>{t("common.refresh")}</button></div>
   <div aria-busy={list.phase==="loading"} style={{marginTop:12}}>
    {list.phase==="loading"&&!list.data&&<Loading/>}
    {list.phase==="failed"&&list.failure&&<FailureView failure={list.failure} onRetry={list.reload}/>}
    {list.data&&list.data.items.length===0&&list.phase!=="failed"&&<Empty/>}
    {list.data&&list.data.items.length>0&&list.phase!=="failed"&&<>
     <ul className={styles.list}>{list.data.items.map(x=><li key={x.id}>
      <button type="button" className={styles.row} aria-pressed={sel===x.id} onClick={()=>{setSel(x.id);setFailure(null);setDone(null);setRevokeFor(null);}}>
       <span><Badge tone={toneFor(x.status)}>{enumLabel(lang,"package",x.status)}</Badge> <strong>{x.packageKey}</strong> v{x.versionNo}</span>
       <span className={styles.hint}>{t("common.updated")} {fmtTime(lang,x.updatedAt)}</span></button></li>)}</ul>
     <Pager page={list.data.page} size={list.data.size} total={list.data.total} onPage={setPage}/></>}
   </div>
  </section>

  <div>
   <ActionError failure={failure} onReload={()=>{detail.reload();setFailure(null);}}/>
   {done&&<Notice>{done}</Notice>}
   <section className={styles.panel} aria-label={t("ap.report")} aria-live="polite">
    {!sel&&<p>{t("ap.select")}</p>}
    {sel&&detail.phase==="loading"&&!p&&<Loading/>}
    {sel&&detail.phase==="failed"&&detail.failure&&<FailureView failure={detail.failure} onRetry={detail.reload}/>}
    {p&&<>
     <h2><Badge tone={toneFor(p.status)}>{enumLabel(lang,"package",p.status)}</Badge> {p.packageKey} v{p.versionNo}</h2>
     <p className={styles.hint}>{t("ap.stepNote")}</p>
     <h3>{t("ap.report")}</h3>
     {!report?<p>{t("ap.noReport")}</p>:<>
      {[...report.schemaErrors,...report.goldenFailures,...report.integrityErrors,...report.dependencyErrors].length===0&&<Notice>{t("ap.passed")}</Notice>}
      <Section title={t("ap.schema")} items={report.schemaErrors}/><Section title={t("ap.golden")} items={report.goldenFailures}/>
      <Section title={t("ap.integrity")} items={report.integrityErrors}/><Section title={t("ap.deps")} items={report.dependencyErrors}/>
      <Section title={t("ap.regress")} items={report.regressionChanges}/></>}
     <div className={styles.actions}>
      <ActionButton label={t("ap.submit")} reason={why(["VALIDATED"])} busy={busy} onClick={()=>void run(()=>call("POST",`packages/${p.id}/submit`,{body:{expectedVersion:p.version}}),()=>setDone(t("ap.submit")))}/>
     </div>
     <h3>{t("ap.decision")}</h3>
     <form onSubmit={e=>e.preventDefault()} noValidate>
      <div className={styles.formGrid}>
       <Field id="rv-role" label={t("ap.role")}><select id="rv-role" className={styles.input} value={rv.role} onChange={e=>setRv({...rv,role:e.target.value})}>
        {["CLINICAL","POLICY"].map(r=><option key={r} value={r}>{enumLabel(lang,"role",r)}</option>)}</select></Field>
       <Field id="rv-dec" label={t("ap.decision")}><select id="rv-dec" className={styles.input} value={rv.approve?"A":"R"} onChange={e=>setRv({...rv,approve:e.target.value==="A"})}>
        <option value="A">{t("ap.approve")}</option><option value="R">{t("ap.reject")}</option></select></Field>
       <Field id="rv-days" label={t("ap.validFor")}><input id="rv-days" type="number" min={1} max={365} className={styles.input} value={rv.days} onChange={e=>setRv({...rv,days:Number(e.target.value)})}/></Field>
       <Field id="rv-why" label={t("common.reason")} wide><input id="rv-why" className={styles.input} maxLength={500} value={rv.reason} onChange={e=>setRv({...rv,reason:e.target.value})}/></Field>
      </div>
      <div className={styles.actions}><ActionButton label={t("ap.record")} tone={rv.approve?"primary":"danger"} reason={reviewReason} busy={busy}
       onClick={()=>void run(()=>call("POST",`packages/${p.id}/review`,{body:{expectedVersion:p.version,role:rv.role,approve:rv.approve,reason:rv.reason.trim(),validForDays:rv.days}}),()=>{setRv({...rv,reason:""});setDone(t("ap.record"));})}/></div>
     </form>
     <h3>{t("ap.approvals")}</h3>
     {(p.approvals??[]).length===0?<p>{t("common.none")}</p>:<ul className={styles.list}>{p.approvals!.map(a=><li key={a.id} className={styles.card}>
      <Badge tone={a.revokedAt?"danger":a.decision==="APPROVE"?"ok":"danger"}>{a.revokedAt?t("ap.revoked"):a.decision==="APPROVE"?t("ap.approve"):t("ap.reject")}</Badge> {enumLabel(lang,"role",a.role)}
      <div>{a.reason}</div><div className={styles.hint}><span className={styles.mono}>{a.reviewerId}</span> · {t("ap.expires")} {fmtTime(lang,a.expiresAt)}</div>
      {revokeFor===a.id?<div className={styles.formGrid}>
       <Field id={`rk-${a.id}`} label={t("ap.revokeReason")} wide><input id={`rk-${a.id}`} className={styles.input} autoFocus maxLength={500} value={revokeReason} onChange={e=>setRevokeReason(e.target.value)}/></Field>
       <div className={styles.actions}><ActionButton label={t("ap.revoke")} tone="danger" busy={busy} reason={revokeReason.trim()?null:t("err.reason_required")}
        onClick={()=>void run(()=>call("POST",`approvals/${a.id}/revoke`,{body:{reason:revokeReason.trim()}}),()=>{setRevokeFor(null);setRevokeReason("");setDone(t("ap.revoked"));})}/>
        <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={()=>setRevokeFor(null)}>{t("common.cancel")}</button></div></div>
       :<div className={styles.actions}><ActionButton label={t("ap.revoke")} tone="secondary" reason={a.revokedAt?t("disabled.status",{status:t("ap.revoked")}):null} onClick={()=>{setRevokeFor(a.id);setRevokeReason("");}}/></div>}
     </li>)}</ul>}
    </>}
   </section>
  </div>
 </div>;
}
export function Approvals(){return <ContentFrame active="approvals" title="ap.title" intro="ap.intro"><Inner/></ContentFrame>;}
