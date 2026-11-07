"use client";
import {useState} from "react";
import {ActionButton,ActionError,AnFrame,Badge,Empty,Field,FailureView,Loading,Notice,Pager,fmtTime,styles,toneFor,useAn,useLoad} from "./ui";
import {call,newKey} from "../../lib/analytics/client";
import type {Failure,Page} from "../../lib/analytics/client";
import {enumLabel} from "../../lib/analytics/i18n";

type Run={id:string;reportKey:string;nameEn:string;nameFr:string;periodStart:string;periodEnd:string;status:string};
type Exp={id:string;runId:string;reportKey:string;periodStart:string;periodEnd:string;format:string;status:string;purpose:string;artifactSha256:string|null;artifactBytes:number|null;
 expiresAt:string;version:number;downloads:number;createdAt:string};
type Dl={filename:string;sha256:string;watermark:string;content:string};

/** Saves text through a Blob. The content is the server's CSV (already formula-safe); nothing is evaluated. */
function saveCsv(d:Dl){
 const url=URL.createObjectURL(new Blob([d.content],{type:"text/csv;charset=utf-8"}));
 const a=document.createElement("a");a.href=url;a.download=d.filename;document.body.appendChild(a);a.click();a.remove();
 setTimeout(()=>URL.revokeObjectURL(url),1000);
}

function Body(){
 const {t,lang}=useAn();
 const [page,setPage]=useState(0);
 const [runId,setRunId]=useState("");
 const [purpose,setPurpose]=useState("");
 const [note,setNote]=useState("");
 const [fail,setFail]=useState<Failure|null>(null);
 const [msg,setMsg]=useState<string|null>(null);
 const [busy,setBusy]=useState(false);
 const runs=useLoad<Page<Run>>(s=>call("GET","runs",{query:{status:"SUCCEEDED",size:100},signal:s}),[]);
 const exps=useLoad<Page<Exp>>(s=>call("GET","exports",{query:{page,size:10},signal:s}),[page]);
 const finish=async(p:Promise<{ok:boolean}&Record<string,any>>,after?:(d:any)=>void)=>{
  setBusy(true);setFail(null);setMsg(null);
  const r:any=await p;setBusy(false);
  if(r.ok){after?.(r.data);exps.reload();}else setFail(r);
 };
 const request=()=>finish(call("POST",`runs/${runId}/exports`,{body:{format:"CSV",purpose:purpose.trim()},key:newKey()}),()=>{setMsg(t("enum.export.REQUESTED"));setPurpose("");});
 const decide=(e:Exp,approve:boolean)=>finish(call("POST",`exports/${e.id}/decision`,{body:{expectedVersion:e.version,approve,note:approve?"":note.trim()},key:newKey()}));
 const download=(e:Exp)=>finish(call<Dl>("POST",`exports/${e.id}/download`,{key:newKey()}),(d:Dl)=>{saveCsv(d);setMsg(t("ex.downloaded"));});
 const reqReason=!runId?t("ex.pickRun"):purpose.trim().length<5?t("disabled.input"):null;
 const nm=(x:{nameEn:string;nameFr:string})=>lang==="fr"?x.nameFr:x.nameEn;
 const rejectReason=note.trim().length<3?t("disabled.input"):null;
 return <>
  <section className={styles.panel} aria-labelledby="req-h">
   <h2 id="req-h">{t("ex.request")}</h2>
   {runs.phase==="loading"&&!runs.data?<Loading/>:runs.phase==="failed"||!runs.data?<FailureView failure={runs.failure!} onRetry={runs.reload}/>:
   <div className={styles.formGrid}>
    <Field id="ex-run" label={t("ex.run")}>
     <select id="ex-run" className={styles.input} value={runId} onChange={e=>setRunId(e.target.value)}>
      <option value="">{t("ex.pickRun")}</option>
      {runs.data.items.map(r=><option key={r.id} value={r.id}>{nm(r)} · {r.periodStart} → {r.periodEnd}</option>)}
     </select>
    </Field>
    <Field id="ex-format" label={t("ex.format")} hint={t("ex.csvOnly")}>
     <select id="ex-format" className={styles.input} value="CSV" disabled><option value="CSV">CSV</option></select>
    </Field>
    <Field id="ex-purpose" label={t("ex.purpose")} wide>
     <textarea id="ex-purpose" className={styles.input} rows={2} maxLength={500} value={purpose} onChange={e=>setPurpose(e.target.value)}/>
    </Field>
   </div>}
   <div className={styles.actions}><ActionButton label={t("ex.requestAction")} reason={reqReason} busy={busy} onClick={request}/></div>
   <ActionError failure={fail} onReload={exps.reload}/>
   {msg&&<Notice>{msg}</Notice>}
  </section>
  <section className={styles.panel} aria-labelledby="eh-h">
   <h2 id="eh-h">{t("ex.history")}</h2>
   <Field id="ex-note" label={t("ex.rejectReason")}><input id="ex-note" className={styles.input} maxLength={300} value={note} onChange={e=>setNote(e.target.value)}/></Field>
   {exps.phase==="loading"&&!exps.data?<Loading/>:exps.phase==="failed"||!exps.data?<FailureView failure={exps.failure!} onRetry={exps.reload}/>:
    exps.data.items.length===0?<Empty message="ex.noExports" hint={false}/>:<>
    <ul className={styles.list}>{exps.data.items.map(e=><li key={e.id} className={styles.card}>
     <strong className={styles.mono}>{e.reportKey}</strong> {e.periodStart} → {e.periodEnd} <Badge tone={toneFor(e.status)}>{enumLabel(lang,"export",e.status)}</Badge>
     <p>{e.purpose}</p>
     <p className={styles.hint}>{t("ex.expires")}: {fmtTime(lang,e.expiresAt)} · {t("ex.downloads",{n:e.downloads})}
      {e.artifactBytes!==null&&<> · {t("ex.size",{n:e.artifactBytes})}</>}</p>
     {e.artifactSha256&&<p className={styles.hint}>{t("ex.fingerprint")}: <span className={styles.mono}>{e.artifactSha256.trim()}</span></p>}
     <div className={styles.actions}>
      <ActionButton label={t("ex.download")} tone="secondary" busy={busy} reason={e.status!=="READY"?t("disabled.status",{status:enumLabel(lang,"export",e.status)}):null} onClick={()=>download(e)}/>
      <ActionButton label={t("ex.approveAction")} busy={busy} reason={e.status!=="REQUESTED"?t("disabled.status",{status:enumLabel(lang,"export",e.status)}):null} onClick={()=>decide(e,true)}/>
      <ActionButton label={t("ex.rejectAction")} tone="danger" busy={busy} reason={e.status!=="REQUESTED"?t("disabled.status",{status:enumLabel(lang,"export",e.status)}):rejectReason} onClick={()=>decide(e,false)}/>
     </div>
    </li>)}</ul>
    <Pager page={page} size={10} total={exps.data.total} onPage={setPage}/>
   </>}
  </section>
 </>;
}
export default function Exports(){
 return <AnFrame active="exports" title="ex.title" intro="ex.intro"><Body/></AnFrame>;
}
