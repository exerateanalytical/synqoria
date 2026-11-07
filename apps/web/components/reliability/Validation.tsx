"use client";
import {useState} from "react";
import {ActionButton,ActionError,Badge,Empty,Field,FailureView,Loading,Notice,RlFrame,fmtTime,styles,toneFor,useLoad,useRl} from "./ui";
import {call,newKey} from "../../lib/reliability/client";
import type {Failure,Page} from "../../lib/reliability/client";
import {enumLabel} from "../../lib/reliability/i18n";

const ITEMS=["SMOKE_TESTS","IDENTITY_CONTINUITY","CLINICAL_RECONCILIATION","FINANCIAL_RECONCILIATION","AUDIT_CONTINUITY","EGRESS_STATE"];
type Rec={id:string;scenario:string;exercise:boolean;state:string;version:number;baseSnapshot:string;
 validations?:{itemKey:string;result:string;evidenceRef:string;residualRisk:string|null}[];measurement?:{source:string;rtoSeconds:number;rpoSeconds:number|null}|null};
type Meas={recoveryId:string;scenario:string;source:string;rtoSeconds:number;rpoSeconds:number|null;measuredAt:string};
type Board={services:{serviceKey:string;name:string}[]};

function Body(){
 const {t,lang}=useRl();
 const [sel,setSel]=useState("");const [item,setItem]=useState("SMOKE_TESTS");const [result,setResult]=useState("PASS");const [ev,setEv]=useState("");const [risk,setRisk]=useState("");const [scope,setScope]=useState<string[]>([]);
 const [fail,setFail]=useState<Failure|null>(null);const [msg,setMsg]=useState<string|null>(null);const [busy,setBusy]=useState(false);
 const list=useLoad<Page<Rec>>(s=>call("GET","recoveries",{query:{size:50},signal:s}),[]);
 const detail=useLoad<Rec|null>(s=>sel?call<Rec>("GET",`recoveries/${sel}`,{signal:s}):Promise.resolve({ok:true as const,status:200,data:null}),[sel]);
 const board=useLoad<Board>(s=>call<Board>("GET","health-board",{signal:s}),[]);
 const meas=useLoad<Page<Meas>>(s=>call("GET","recovery-measurements",{query:{size:20},signal:s}),[]);
 const validating=(list.data?.items??[]).filter(x=>x.state==="VALIDATING"||x.state==="REOPENED");
 const r=detail.data;
 const act=async(p:ReturnType<typeof call>,done:string)=>{
  setBusy(true);setFail(null);setMsg(null);
  const x=await p;setBusy(false);
  if(x.ok){setMsg(done);list.reload();detail.reload();meas.reload();setEv("");setRisk("");}else setFail(x);
 };
 const recorded=(k:string)=>r?.validations?.find(v=>v.itemKey===k);
 const missing=ITEMS.filter(k=>recorded(k)?.result!=="PASS");
 const recordWhy=!r?t("disabled.select"):r.state!=="VALIDATING"?t("disabled.status",{status:enumLabel(lang,"rec",r.state)}):recorded(item)?t("disabled.status",{status:enumLabel(lang,"result",recorded(item)!.result)}):!ev.trim()?t("disabled.input"):null;
 const reopenWhy=!r?t("disabled.select"):r.state!=="VALIDATING"?t("disabled.status",{status:enumLabel(lang,"rec",r.state)}):missing.length>0?`${t("va.missing")}: ${missing.map(k=>enumLabel(lang,"item",k)).join(", ")}`:scope.length===0?t("disabled.input"):null;
 const toggle=(k:string)=>setScope(s=>s.includes(k)?s.filter(x=>x!==k):[...s,k]);
 const mins=(s:number|null)=>s===null?"—":t("va.minutes",{n:Math.round(s/60)});
 return <>
  <section className={styles.panel} aria-labelledby="pk-h">
   <h2 id="pk-h">{t("va.pick")}</h2>
   {list.phase==="loading"&&!list.data?<Loading/>:list.phase==="failed"||!list.data?<FailureView failure={list.failure!} onRetry={list.reload}/>:
    validating.length===0?<Empty message="va.none" hint={false}/>:
    <ul className={styles.list}>{validating.map(x=><li key={x.id} className={styles.card}>
     <strong>{enumLabel(lang,"scenario",x.scenario)}</strong> <Badge tone={toneFor(x.state)}>{enumLabel(lang,"rec",x.state)}</Badge> {!x.exercise&&<Badge tone="danger">{t("rc.real")}</Badge>}
     <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} aria-pressed={sel===x.id} onClick={()=>{setSel(x.id);setMsg(null);setFail(null);}}>{t("common.select")}</button>
    </li>)}</ul>}
  </section>
  <section className={styles.panel} aria-labelledby="it-h" aria-live="polite">
   <h2 id="it-h">{t("va.items")}</h2>
   {!sel?<p className={styles.hint}>{t("disabled.select")}</p>:detail.phase==="loading"&&!r?<Loading/>:detail.phase==="failed"?<FailureView failure={detail.failure!} onRetry={detail.reload}/>:!r?<Empty/>:<>
    <ul className={styles.list}>{ITEMS.map(k=>{const v=recorded(k);return <li key={k} className={styles.card}>
     <strong>{enumLabel(lang,"item",k)}</strong> {v?<Badge tone={toneFor(v.result)}>{enumLabel(lang,"result",v.result)}</Badge>:<Badge tone="neutral">{t("va.missing")}</Badge>}
     {v&&<p className={styles.hint}>{v.evidenceRef}{v.residualRisk?` · ${t("va.residual")}: ${v.residualRisk}`:""}</p>}</li>;})}</ul>
    {r.state==="VALIDATING"&&<div className={styles.formGrid}>
     <Field id="va-i" label={t("va.item")}><select id="va-i" className={styles.input} value={item} onChange={e=>setItem(e.target.value)}>{ITEMS.map(k=><option key={k} value={k}>{enumLabel(lang,"item",k)}</option>)}</select></Field>
     <Field id="va-r" label={t("va.result")}><select id="va-r" className={styles.input} value={result} onChange={e=>setResult(e.target.value)}><option value="PASS">{enumLabel(lang,"result","PASS")}</option><option value="FAIL">{enumLabel(lang,"result","FAIL")}</option></select></Field>
     <Field id="va-e" label={t("common.evidence")} wide><input id="va-e" className={styles.input} maxLength={160} value={ev} onChange={e=>setEv(e.target.value)}/></Field>
     <Field id="va-k" label={t("va.residual")} wide><input id="va-k" className={styles.input} maxLength={300} value={risk} onChange={e=>setRisk(e.target.value)}/></Field>
    </div>}
    <div className={styles.actions}><ActionButton label={t("va.record")} busy={busy} reason={recordWhy} onClick={()=>r&&act(call("POST",`recoveries/${r.id}/validation`,{body:{itemKey:item,result,evidenceRef:ev.trim(),residualRisk:risk.trim()||null},key:newKey()}),t("va.recorded"))}/></div>
    <fieldset className={styles.fieldset}><legend>{t("va.scope")}</legend>
     {(board.data?.services??[]).map(s=><label key={s.serviceKey} className={styles.checkRow}><input type="checkbox" checked={scope.includes(s.serviceKey)} onChange={()=>toggle(s.serviceKey)}/> {s.name} <span className={styles.mono}>{s.serviceKey}</span></label>)}
    </fieldset>
    <div className={styles.actions}><ActionButton label={t("va.reopen")} busy={busy} reason={reopenWhy} onClick={()=>r&&act(call("POST",`recoveries/${r.id}/reopening`,{body:{expectedVersion:r.version,scope},key:newKey()}),t("va.reopened"))}/></div>
    {r.measurement&&<Notice>{t("va.rto")}: {mins(r.measurement.rtoSeconds)} · {t("va.rpo")}: {mins(r.measurement.rpoSeconds)} · {enumLabel(lang,"source",r.measurement.source)}</Notice>}
    <ActionError failure={fail} onReload={()=>{list.reload();detail.reload();}}/>
    {msg&&<Notice>{msg}</Notice>}
   </>}
  </section>
  <section className={styles.panel} aria-labelledby="ms-h">
   <h2 id="ms-h">{t("va.measure")}</h2>
   <p className={styles.hint}>{t("va.noGuarantee")}</p>
   {meas.phase==="loading"&&!meas.data?<Loading/>:meas.phase==="failed"||!meas.data?<FailureView failure={meas.failure!} onRetry={meas.reload}/>:
    meas.data.items.length===0?<Empty message="va.noMeasure" hint={false}/>:
    <ul className={styles.list}>{meas.data.items.map(m=><li key={m.recoveryId} className={styles.card}>
     <strong>{enumLabel(lang,"scenario",m.scenario)}</strong> <Badge tone={m.source==="REAL"?"danger":"info"}>{enumLabel(lang,"source",m.source)}</Badge>
     <p>{t("va.rto")}: <strong>{mins(m.rtoSeconds)}</strong> · {t("va.rpo")}: <strong>{mins(m.rpoSeconds)}</strong> · {fmtTime(lang,m.measuredAt)}</p>
    </li>)}</ul>}
  </section>
 </>;
}
export default function Validation(){return <RlFrame active="validation" title="va.title" intro="va.intro"><Body/></RlFrame>;}
