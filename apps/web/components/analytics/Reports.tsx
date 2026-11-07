"use client";
import {useState} from "react";
import {ActionButton,ActionError,AnFrame,Badge,Empty,Field,FailureView,Loading,Notice,Pager,fmtTime,styles,toneFor,useAn,useLoad} from "./ui";
import {Figure} from "./parts";
import {call,newKey} from "../../lib/analytics/client";
import type {Failure,Page} from "../../lib/analytics/client";
import {enumLabel} from "../../lib/analytics/i18n";

type Report={id:string;reportKey:string;versionNo:number;nameEn:string;nameFr:string;metricKeys:string[];maxPeriodDays:number;requiredPermission:string;status:string};
type Run={id:string;reportKey:string;reportVersion:number;nameEn:string;nameFr:string;periodStart:string;periodEnd:string;trigger:string;status:string;errorCode:string|null;createdAt:string;finishedAt:string|null;result?:Result|null};
type Section={metricKey:string;metricVersion?:number;domain?:string;state:string;rows:{dimensionKey:string;currency:string|null;state:string;numerator:string|null;denominator:string|null;value:string|null;cohortSize:number;unknownCount:number;stale:boolean}[]};
type Result={sections:Section[];periodStart:string;periodEnd:string};

function Body(){
 const {t,lang}=useAn();
 const [page,setPage]=useState(0);
 const [sel,setSel]=useState<string>("");
 const [from,setFrom]=useState("");
 const [to,setTo]=useState("");
 const [open,setOpen]=useState<string>("");
 const [fail,setFail]=useState<Failure|null>(null);
 const [note,setNote]=useState<string|null>(null);
 const [busy,setBusy]=useState(false);
 const reps=useLoad<Page<Report>>(s=>call("GET","reports",{query:{status:"APPROVED",size:100},signal:s}),[]);
 const runs=useLoad<Page<Run>>(s=>call("GET","runs",{query:{page,size:10},signal:s}),[page]);
 const detail=useLoad<Run|null>(s=>open?call<Run>("GET",`runs/${open}`,{signal:s}):Promise.resolve({ok:true as const,status:200,data:null}),[open]);
 const report=reps.data?.items.find(r=>r.id===sel);
 const days=from&&to?Math.round((Date.parse(to)-Date.parse(from))/86400000)+1:0;
 const reason=!report?t("disabled.select"):!from||!to||days<1?t("disabled.input"):days>report.maxPeriodDays?t("err.invalid_period"):null;
 const run=async()=>{
  if(!report)return;setBusy(true);setFail(null);setNote(null);
  const r=await call<Run>("POST",`reports/${report.id}/runs`,{body:{periodStart:from,periodEnd:to},key:newKey()});
  setBusy(false);
  if(r.ok){setNote(t("rp.requested")+" "+t("rp.queuedNote"));runs.reload();setOpen(r.data.id);}else setFail(r);
 };
 const process=async()=>{
  setBusy(true);setFail(null);setNote(null);
  const r=await call("POST","runs/process",{query:{limit:10},key:newKey()});
  setBusy(false);
  if(r.ok){runs.reload();detail.reload();}else setFail(r);
 };
 if(reps.phase==="loading"&&!reps.data)return <Loading/>;
 if(reps.phase==="failed"||!reps.data)return <FailureView failure={reps.failure!} onRetry={reps.reload}/>;
 const nm=(x:{nameEn:string;nameFr:string})=>lang==="fr"?x.nameFr:x.nameEn;
 return <>
  <section className={styles.panel} aria-labelledby="cat-h">
   <h2 id="cat-h">{t("rp.catalogue")}</h2>
   {reps.data.items.length===0?<Empty message="rp.noReports" hint={false}/>:
   <ul className={styles.hub}>{reps.data.items.map(r=><li key={r.id} className={styles.card}>
    <h3>{nm(r)}</h3>
    <p className={styles.hint}><span className={styles.mono}>{r.reportKey}</span> · v{r.versionNo} · {t("rp.maxDays",{n:r.maxPeriodDays})}</p>
    <p className={styles.hint}>{t("rp.metrics")}: {r.metricKeys.join(", ")}</p>
    <p className={styles.hint}>{t("rp.requires",{perm:r.requiredPermission})}</p>
    <button type="button" className={`${styles.btn} ${sel===r.id?"":styles.btnSecondary}`} aria-pressed={sel===r.id} onClick={()=>setSel(r.id)}>{t("rp.run")}</button>
   </li>)}</ul>}
  </section>
  <section className={styles.panel} aria-labelledby="run-h">
   <h2 id="run-h">{t("rp.run")}{report?`: ${nm(report)}`:""}</h2>
   <div className={styles.formGrid}>
    <Field id="rp-from" label={t("rp.periodStart")}><input id="rp-from" type="date" className={styles.input} value={from} max={to||undefined} onChange={e=>setFrom(e.target.value)}/></Field>
    <Field id="rp-to" label={t("rp.periodEnd")}><input id="rp-to" type="date" className={styles.input} value={to} min={from||undefined} onChange={e=>setTo(e.target.value)}/></Field>
   </div>
   <div className={styles.actions}>
    <ActionButton label={t("rp.run")} reason={reason} busy={busy} onClick={run}/>
    <ActionButton label={t("rp.process")} tone="secondary" busy={busy} onClick={process}/>
   </div>
   <ActionError failure={fail}/>
   {note&&<Notice>{note}</Notice>}
  </section>
  <section className={styles.panel} aria-labelledby="hist-h">
   <h2 id="hist-h">{t("rp.history")}</h2>
   {runs.phase==="loading"&&!runs.data?<Loading/>:runs.phase==="failed"||!runs.data?<FailureView failure={runs.failure!} onRetry={runs.reload}/>:
    runs.data.items.length===0?<Empty message="rp.noRuns" hint={false}/>:<>
    <div className={styles.tableWrap}><table className={styles.table}>
     <thead><tr><th>{t("rp.catalogue")}</th><th>{t("common.from")}</th><th>{t("common.to")}</th><th>{t("common.status")}</th><th>{t("common.updated")}</th><th>{t("common.actions")}</th></tr></thead>
     <tbody>{runs.data.items.map(r=><tr key={r.id} className={open===r.id?styles.sel:undefined}>
      <td>{nm(r)} <span className={styles.hint}>v{r.reportVersion}</span></td><td>{r.periodStart}</td><td>{r.periodEnd}</td>
      <td><Badge tone={toneFor(r.status)}>{enumLabel(lang,"run",r.status)}</Badge>{r.errorCode&&<span className={styles.hint}> {t("rp.error")}: {r.errorCode}</span>}</td>
      <td>{fmtTime(lang,r.finishedAt??r.createdAt)}</td>
      <td><button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={()=>setOpen(r.id)}>{t("common.details")}</button></td>
     </tr>)}</tbody>
    </table></div>
    <ul className={styles.cards}>{runs.data.items.map(r=><li key={r.id} className={styles.card}>
     <strong>{nm(r)}</strong>
     <dl><dt>{t("common.from")}</dt><dd>{r.periodStart} → {r.periodEnd}</dd><dt>{t("common.status")}</dt><dd>{enumLabel(lang,"run",r.status)}</dd></dl>
     <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={()=>setOpen(r.id)}>{t("common.details")}</button>
    </li>)}</ul>
    <Pager page={page} size={10} total={runs.data.total} onPage={setPage}/>
   </>}
  </section>
  <section className={styles.panel} aria-labelledby="res-h" aria-live="polite">
   <h2 id="res-h">{t("rp.result")}</h2>
   {!open?<p className={styles.hint}>{t("rp.pick")}</p>:detail.phase==="loading"&&!detail.data?<Loading/>:detail.phase==="failed"?<FailureView failure={detail.failure!} onRetry={detail.reload}/>:
    !detail.data?<Empty/>:detail.data.status!=="SUCCEEDED"?<p><Badge tone={toneFor(detail.data.status)}>{enumLabel(lang,"run",detail.data.status)}</Badge> {detail.data.status==="QUEUED"?t("rp.queuedNote"):""}</p>:
    (detail.data.result?.sections??[]).map(s=><div key={s.metricKey}>
     <h3>{t("rp.section",{key:s.metricKey,v:s.metricVersion??"—"})} <Badge tone={toneFor(s.state==="AVAILABLE"?"OK":"INCOMPLETE")}>{enumLabel(lang,"state",s.state)}</Badge></h3>
     {s.rows.length>0&&<div className={styles.tableWrap}><table className={styles.table}>
      <thead><tr><th>{t("an.dimension")}</th><th>{t("an.currency")}</th><th>{t("an.metric")}</th><th>{t("an.cohort")}</th></tr></thead>
      <tbody>{s.rows.map((x,i)=><tr key={i}><td>{x.dimensionKey}</td><td>{(x.currency??"").trim()||"—"}</td>
       <td><Figure row={{...x,unit:x.denominator!==null?"PERCENT":"COUNT"}}/></td><td>{x.state==="SUPPRESSED"?"—":x.cohortSize}</td></tr>)}</tbody>
     </table></div>}
    </div>)}
  </section>
 </>;
}
export default function Reports(){
 return <AnFrame active="reports" title="rp.title" intro="rp.intro"><Body/></AnFrame>;
}
