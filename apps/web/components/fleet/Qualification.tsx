"use client";
import {useState} from "react";
import {ActionButton,ActionError,Badge,Empty,Field,FailureView,FlFrame,Loading,Notice,styles,toneFor,useFl,useLoad} from "./ui";
import {call,newKey} from "../../lib/fleet/client";
import type {Failure,Page} from "../../lib/fleet/client";
import {enumLabel} from "../../lib/fleet/i18n";
import type {Cell} from "./Fleet";

const CHECKS=["DATABASE","NETWORK","IDENTITY","STORAGE","CAPACITY","MONITORING","BACKUP","MIGRATION_COMPAT"];
type Check={checkKey:string;result:string;evidenceRef:string;detail:string|null;recordedBy:string};
type Qual={id:string;targetCell:string;status:string;startedBy:string;completedBy:string|null;version:number;checks:Check[]};

function Body(){
 const {t,lang}=useFl();
 const [cellId,setCellId]=useState("");
 const [checkKey,setCheckKey]=useState("DATABASE");
 const [result,setResult]=useState("PASS");
 const [evidence,setEvidence]=useState("");
 const [detail,setDetail]=useState("");
 const [fail,setFail]=useState<Failure|null>(null);
 const [msg,setMsg]=useState<string|null>(null);
 const [busy,setBusy]=useState(false);
 const cells=useLoad<Page<Cell>>(s=>call("GET","cells",{query:{size:100},signal:s}),[]);
 const quals=useLoad<Qual[]>(s=>cellId?call<Qual[]>("GET",`cells/${cellId}/qualifications`,{signal:s}):Promise.resolve({ok:true as const,status:200,data:[]}),[cellId]);
 const open=(quals.data??[]).find(q=>q.status==="OPEN");
 const cell=cells.data?.items.find(c=>c.id===cellId);
 const act=async(p:ReturnType<typeof call>,done:string)=>{
  setBusy(true);setFail(null);setMsg(null);
  const r=await p;setBusy(false);
  if(r.ok){setMsg(done);quals.reload();cells.reload();setEvidence("");setDetail("");}else setFail(r);
 };
 const startWhy=!cell?t("disabled.select"):!["PROVISIONING","QUALIFIED"].includes(cell.state)?t("disabled.status",{status:enumLabel(lang,"cell",cell.state)}):open?t("disabled.status",{status:enumLabel(lang,"qual","OPEN")}):null;
 const recordWhy=!open?t("ql.none"):!evidence.trim()?t("disabled.input"):null;
 const missing=open?CHECKS.filter(k=>!open.checks.some(c=>c.checkKey===k)):[];
 const completeWhy=!open?t("ql.none"):missing.length>0?`${t("ql.missing")}: ${missing.map(k=>enumLabel(lang,"check",k)).join(", ")}`:null;
 return <>
  <section className={styles.panel} aria-labelledby="qc-h">
   <h2 id="qc-h">{t("ql.cell")}</h2>
   {cells.phase==="failed"?<FailureView failure={cells.failure!} onRetry={cells.reload}/>:
   <Field id="ql-cell" label={t("ql.cell")}>
    <select id="ql-cell" className={styles.input} value={cellId} onChange={e=>{setCellId(e.target.value);setMsg(null);setFail(null);}}>
     <option value="">{t("ql.pickCell")}</option>
     {(cells.data?.items??[]).map(c=><option key={c.id} value={c.id}>{c.tenantName} · {c.cellKey} · {enumLabel(lang,"cell",c.state)}</option>)}
    </select>
   </Field>}
   <div className={styles.actions}><ActionButton label={t("ql.start")} busy={busy} reason={startWhy} onClick={()=>act(call("POST",`cells/${cellId}/qualifications`,{key:newKey()}),t("ql.started"))}/></div>
   <ActionError failure={fail} onReload={quals.reload}/>
   {msg&&<Notice>{msg}</Notice>}
  </section>
  <section className={styles.panel} aria-labelledby="qk-h" aria-live="polite">
   <h2 id="qk-h">{t("ql.history")}</h2>
   {!cellId?<p className={styles.hint}>{t("disabled.select")}</p>:quals.phase==="loading"&&!quals.data?<Loading/>:quals.phase==="failed"||!quals.data?<FailureView failure={quals.failure!} onRetry={quals.reload}/>:
    quals.data.length===0?<Empty message="ql.none" hint={false}/>:quals.data.map(q=><div key={q.id} className={styles.card}>
     <h3><Badge tone={toneFor(q.status)}>{enumLabel(lang,"qual",q.status)}</Badge></h3>
     <div className={styles.tableWrap}><table className={styles.table}>
      <thead><tr><th>{t("ql.checkKey")}</th><th>{t("ql.result")}</th><th>{t("ql.evidence")}</th></tr></thead>
      <tbody>{CHECKS.map(k=>{const c=q.checks.find(x=>x.checkKey===k);return <tr key={k}>
       <td>{enumLabel(lang,"check",k)}</td>
       <td>{c?<Badge tone={toneFor(c.result)}>{enumLabel(lang,"result",c.result)}</Badge>:<Badge tone="neutral">{t("ql.missing")}</Badge>}</td>
       <td className={styles.mono}>{c?c.evidenceRef:"—"}</td></tr>;})}</tbody>
     </table></div>
     <ul className={styles.cards}>{CHECKS.map(k=>{const c=q.checks.find(x=>x.checkKey===k);return <li key={k} className={styles.card}><strong>{enumLabel(lang,"check",k)}</strong> {c?<Badge tone={toneFor(c.result)}>{enumLabel(lang,"result",c.result)}</Badge>:<Badge tone="neutral">{t("ql.missing")}</Badge>}</li>;})}</ul>
    </div>)}
  </section>
  <section className={styles.panel} aria-labelledby="qr-h">
   <h2 id="qr-h">{t("ql.record")}</h2>
   <div className={styles.formGrid}>
    <Field id="ql-key" label={t("ql.checkKey")}><select id="ql-key" className={styles.input} value={checkKey} onChange={e=>setCheckKey(e.target.value)}>{CHECKS.map(k=><option key={k} value={k}>{enumLabel(lang,"check",k)}</option>)}</select></Field>
    <Field id="ql-res" label={t("ql.result")}><select id="ql-res" className={styles.input} value={result} onChange={e=>setResult(e.target.value)}><option value="PASS">{enumLabel(lang,"result","PASS")}</option><option value="FAIL">{enumLabel(lang,"result","FAIL")}</option></select></Field>
    <Field id="ql-ev" label={t("ql.evidence")} wide><input id="ql-ev" className={styles.input} maxLength={160} value={evidence} onChange={e=>setEvidence(e.target.value)}/></Field>
    <Field id="ql-dt" label={t("ql.detail")} wide><input id="ql-dt" className={styles.input} maxLength={300} value={detail} onChange={e=>setDetail(e.target.value)}/></Field>
   </div>
   <div className={styles.actions}>
    <ActionButton label={t("ql.record")} busy={busy} reason={recordWhy} onClick={()=>open&&act(call("POST",`qualifications/${open.id}/checks`,{body:{checkKey,result,evidenceRef:evidence.trim(),detail:detail.trim()||null},key:newKey()}),t("ql.recorded"))}/>
    <ActionButton label={t("ql.complete")} tone="secondary" busy={busy} reason={completeWhy} onClick={()=>open&&act(call("POST",`qualifications/${open.id}/completion`,{body:{expectedVersion:open.version},key:newKey()}),t("ql.completed"))}/>
   </div>
  </section>
 </>;
}
export default function Qualification(){return <FlFrame active="qualification" title="ql.title" intro="ql.intro"><Body/></FlFrame>;}
