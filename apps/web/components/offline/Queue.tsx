"use client";
import {useState} from "react";
import {Badge,Empty,Field,FailureView,Loading,OfFrame,Pager,fmtTime,styles,toneFor,useLoad,useOf} from "./ui";
import {call} from "../../lib/offline/client";
import type {Page} from "../../lib/offline/client";
import {enumLabel,safetyLabel} from "../../lib/offline/i18n";

type Cmd={id:string;deviceId:string;deviceLabel:string;clientCommandId:string;deviceSeq:number;operationType:string;recordType:string;recordKey:string;baseVersion:number;baseEpoch:number;fields:string[];
 payloadHash:string;createdLocalAt:string;expiresAt:string;receivedAt:string;status:string;outcomeCode:string;appliedVersion:number|null;payload?:Record<string,unknown>};
const STATUSES=["APPLIED","CONFLICT","REJECTED","EXPIRED","RESOLVED"];

function Body(){
 const {t,lang}=useOf();
 const [status,setStatus]=useState("");
 const [page,setPage]=useState(0);
 const [open,setOpen]=useState("");
 const list=useLoad<Page<Cmd>>(s=>call("GET","commands",{query:{status,page,size:15},signal:s}),[status,page]);
 const detail=useLoad<Cmd|null>(s=>open?call<Cmd>("GET",`commands/${open}`,{signal:s}):Promise.resolve({ok:true as const,status:200,data:null}),[open]);
 return <>
  <section className={styles.panel} aria-labelledby="q-h">
   <h2 id="q-h">{t("nav.queue")}</h2>
   <div className={styles.filters}>
    <Field id="q-status" label={t("q.filterStatus")}>
     <select id="q-status" className={styles.input} value={status} onChange={e=>{setStatus(e.target.value);setPage(0);}}>
      <option value="">{t("common.any")}</option>{STATUSES.map(x=><option key={x} value={x}>{enumLabel(lang,"cmd",x)}</option>)}
     </select>
    </Field>
    <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={list.reload}>{t("common.refresh")}</button>
   </div>
   {list.phase==="loading"&&!list.data?<Loading/>:list.phase==="failed"||!list.data?<FailureView failure={list.failure!} onRetry={list.reload}/>:
    list.data.items.length===0?<Empty/>:<>
    <div className={styles.tableWrap}><table className={styles.table}>
     <thead><tr><th>{t("common.device")}</th><th>{t("common.operation")}</th><th>{t("common.record")}</th><th>{t("q.base")}</th><th>{t("common.status")}</th><th>{t("q.received")}</th><th>{t("common.actions")}</th></tr></thead>
     <tbody>{list.data.items.map(c=><tr key={c.id} className={open===c.id?styles.sel:undefined}>
      <td>{c.deviceLabel} <span className={styles.mono}>#{c.deviceSeq}</span></td><td className={styles.mono}>{c.operationType}</td><td>{c.recordType} / {c.recordKey}</td>
      <td className={styles.mono}>v{c.baseVersion} · e{c.baseEpoch}</td>
      <td><Badge tone={toneFor(c.status)}>{enumLabel(lang,"cmd",c.status)}</Badge><span className={styles.hint} style={{display:"block"}}>{safetyLabel(lang,c.outcomeCode)}</span></td>
      <td>{fmtTime(lang,c.receivedAt)}</td>
      <td><button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={()=>setOpen(c.id)}>{t("common.details")}</button></td>
     </tr>)}</tbody>
    </table></div>
    <ul className={styles.cards}>{list.data.items.map(c=><li key={c.id} className={styles.card}>
     <strong className={styles.mono}>{c.operationType}</strong> <Badge tone={toneFor(c.status)}>{enumLabel(lang,"cmd",c.status)}</Badge>
     <dl><dt>{t("common.record")}</dt><dd>{c.recordType} / {c.recordKey}</dd><dt>{t("q.base")}</dt><dd>v{c.baseVersion} · e{c.baseEpoch}</dd><dt>{t("q.outcome")}</dt><dd>{safetyLabel(lang,c.outcomeCode)}</dd></dl>
     <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={()=>setOpen(c.id)}>{t("common.details")}</button>
    </li>)}</ul>
    <Pager page={page} size={15} total={list.data.total} onPage={setPage}/>
   </>}
  </section>
  <section className={styles.panel} aria-labelledby="qd-h" aria-live="polite">
   <h2 id="qd-h">{t("common.details")}</h2>
   {!open?<p className={styles.hint}>{t("q.select")}</p>:detail.phase==="loading"&&!detail.data?<Loading/>:detail.phase==="failed"?<FailureView failure={detail.failure!} onRetry={detail.reload}/>:
    !detail.data?<Empty/>:<dl className={styles.kv}>
     <dt>{t("q.key")}</dt><dd className={styles.mono}>{detail.data.clientCommandId}</dd>
     <dt>{t("q.base")}</dt><dd>v{detail.data.baseVersion} · {t("q.epoch")} {detail.data.baseEpoch}</dd>
     <dt>{t("q.applied")}</dt><dd>{detail.data.appliedVersion===null?"—":`v${detail.data.appliedVersion}`}</dd>
     <dt>{t("q.created")}</dt><dd>{fmtTime(lang,detail.data.createdLocalAt)}</dd>
     <dt>{t("q.expires")}</dt><dd>{fmtTime(lang,detail.data.expiresAt)}</dd>
     <dt>{t("q.fields")}</dt><dd>{detail.data.fields.length?detail.data.fields.join(", "):"—"}</dd>
     <dt>{t("q.outcome")}</dt><dd>{safetyLabel(lang,detail.data.outcomeCode)}</dd>
     <dt>{t("q.payload")}</dt><dd><pre className={styles.pre}>{JSON.stringify(detail.data.payload??{},null,1)}</pre></dd>
    </dl>}
  </section>
 </>;
}
export default function Queue(){return <OfFrame active="queue" title="q.title" intro="q.intro"><Body/></OfFrame>;}
