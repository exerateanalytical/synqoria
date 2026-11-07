"use client";
import {useState} from "react";
import {ActionButton,ActionError,Badge,Empty,Field,FailureView,Loading,Notice,OfFrame,Pager,fmtTime,styles,toneFor,useLoad,useOf} from "./ui";
import {call,newKey} from "../../lib/offline/client";
import type {Failure,Page} from "../../lib/offline/client";
import {enumLabel} from "../../lib/offline/i18n";

type Row={id:string;kind:string;status:string;localBaseVersion:number;authoritativeVersion:number;authoritativeEpoch:number;differingFields:string[];resolution:string|null;reasonCode:string|null;
 resolvedBy:string|null;resolvedAt:string|null;resultingVersion:number|null;version:number;createdAt:string;operationType:string;recordType:string;recordKey:string;authorId:string;
 currentHeadVersion:number;currentEpoch:number;localPayload?:Record<string,unknown>};
const REASONS=["CLINICAL_REVIEW","DUPLICATE_ENTRY","SUPERSEDED_BY_NEWER","DATA_ERROR","OTHER"];

function Body(){
 const {t,lang}=useOf();
 const [status,setStatus]=useState("OPEN");
 const [page,setPage]=useState(0);
 const [open,setOpen]=useState("");
 const [resolution,setResolution]=useState("KEEP_AUTHORITATIVE");
 const [reasonCode,setReasonCode]=useState("");
 const [note,setNote]=useState("");
 const [fail,setFail]=useState<Failure|null>(null);
 const [msg,setMsg]=useState<string|null>(null);
 const [busy,setBusy]=useState(false);
 const list=useLoad<Page<Row>>(s=>call("GET","conflicts",{query:{status,page,size:10},signal:s}),[status,page]);
 const d=useLoad<Row|null>(s=>open?call<Row>("GET",`conflicts/${open}`,{signal:s}):Promise.resolve({ok:true as const,status:200,data:null}),[open]);
 const c=d.data;
 const why=!c?t("disabled.select"):c.status!=="OPEN"?t("disabled.status",{status:enumLabel(lang,"conflict",c.status)}):!reasonCode?t("disabled.input"):
  reasonCode==="OTHER"&&note.trim().length<5?t("disabled.input"):null;
 const resolve=async()=>{
  if(!c)return;setBusy(true);setFail(null);setMsg(null);
  const r=await call("POST",`conflicts/${c.id}/resolution`,{body:{expectedVersion:c.version,resolution,reasonCode,note:note.trim()||null,expectedHeadVersion:c.currentHeadVersion},key:newKey()});
  setBusy(false);
  if(r.ok){setMsg(t("cf.resolved"));setReasonCode("");setNote("");list.reload();d.reload();}else setFail(r);
 };
 return <>
  <section className={styles.panel} aria-labelledby="cl-h">
   <h2 id="cl-h">{t("cf.list")}</h2>
   <div className={styles.filters}>
    <Field id="cf-status" label={t("common.status")}>
     <select id="cf-status" className={styles.input} value={status} onChange={e=>{setStatus(e.target.value);setPage(0);}}>
      <option value="">{t("common.any")}</option><option value="OPEN">{enumLabel(lang,"conflict","OPEN")}</option><option value="RESOLVED">{enumLabel(lang,"conflict","RESOLVED")}</option>
     </select>
    </Field>
    <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={list.reload}>{t("common.refresh")}</button>
   </div>
   {list.phase==="loading"&&!list.data?<Loading/>:list.phase==="failed"||!list.data?<FailureView failure={list.failure!} onRetry={list.reload}/>:
    list.data.items.length===0?<Empty message="cf.none" hint={false}/>:<>
    <ul className={styles.list}>{list.data.items.map(x=><li key={x.id} className={styles.card}>
     <strong>{x.recordType} / {x.recordKey}</strong> <Badge tone={toneFor(x.status)}>{enumLabel(lang,"conflict",x.status)}</Badge>
     <p className={styles.hint}>{enumLabel(lang,"kind",x.kind)} · {fmtTime(lang,x.createdAt)}</p>
     <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} aria-pressed={open===x.id} onClick={()=>{setOpen(x.id);setMsg(null);setFail(null);}}>{t("common.details")}</button>
    </li>)}</ul>
    <Pager page={page} size={10} total={list.data.total} onPage={setPage}/>
   </>}
  </section>
  <section className={styles.panel} aria-labelledby="cd-h" aria-live="polite">
   <h2 id="cd-h">{t("nav.conflicts")}</h2>
   {!open?<p className={styles.hint}>{t("cf.pick")}</p>:d.phase==="loading"&&!c?<Loading/>:d.phase==="failed"?<FailureView failure={d.failure!} onRetry={d.reload}/>:!c?<Empty/>:<>
    <p><Badge tone="warn">{enumLabel(lang,"kind",c.kind)}</Badge></p>
    <div className={styles.cols}>
     <div className={styles.card}>
      <h3>{t("cf.local")}</h3>
      <dl className={styles.kv}><dt>{t("common.operation")}</dt><dd className={styles.mono}>{c.operationType}</dd>
       <dt>{t("cf.baseSeen")}</dt><dd>v{c.localBaseVersion}</dd><dt>{t("cf.author")}</dt><dd className={styles.mono}>{c.authorId.slice(0,8)}</dd></dl>
      <pre className={styles.pre}>{JSON.stringify(c.localPayload??{},null,1)}</pre>
     </div>
     <div className={styles.card}>
      <h3>{t("cf.authoritative")}</h3>
      <dl className={styles.kv}><dt>{t("cf.headNow")}</dt><dd>v{c.currentHeadVersion}</dd><dt>{t("cf.epoch")}</dt><dd>{c.currentEpoch}{c.currentEpoch!==c.authoritativeEpoch&&<> <Badge tone="warn">{`e${c.authoritativeEpoch}`}</Badge></>}</dd>
       <dt>{t("cf.differing")}</dt><dd>{c.differingFields.length?c.differingFields.join(", "):"—"}</dd></dl>
     </div>
    </div>
    {c.status==="RESOLVED"?<Notice>{enumLabel(lang,"res",c.resolution)} · {enumLabel(lang,"reason",c.reasonCode)} · {t("cf.decidedBy")} <span className={styles.mono}>{(c.resolvedBy??"").slice(0,8)}</span> · {t("cf.finalVersion")}: {c.resultingVersion===null?"—":`v${c.resultingVersion}`}</Notice>:
    <div className={styles.formGrid}>
     <Field id="cf-res" label={t("cf.resolution")}>
      <select id="cf-res" className={styles.input} value={resolution} onChange={e=>setResolution(e.target.value)}>
       <option value="KEEP_AUTHORITATIVE">{enumLabel(lang,"res","KEEP_AUTHORITATIVE")}</option><option value="ACCEPT_LOCAL">{enumLabel(lang,"res","ACCEPT_LOCAL")}</option>
      </select>
     </Field>
     <Field id="cf-reason" label={t("cf.reasonCode")}>
      <select id="cf-reason" className={styles.input} value={reasonCode} onChange={e=>setReasonCode(e.target.value)}>
       <option value="">{t("common.none")}</option>{REASONS.map(x=><option key={x} value={x}>{enumLabel(lang,"reason",x)}</option>)}
      </select>
     </Field>
     <Field id="cf-note" label={t("cf.note")} wide><input id="cf-note" className={styles.input} maxLength={500} value={note} onChange={e=>setNote(e.target.value)}/></Field>
    </div>}
    {c.status==="OPEN"&&resolution==="ACCEPT_LOCAL"&&<p className={styles.hint}>{t("cf.acceptWarn",{v:c.currentHeadVersion})}</p>}
    {c.status==="OPEN"&&<div className={styles.actions}><ActionButton label={t("cf.resolve")} busy={busy} reason={why} onClick={resolve}/></div>}
    <ActionError failure={fail} onReload={()=>{list.reload();d.reload();}}/>
    {msg&&<Notice>{msg}</Notice>}
   </>}
  </section>
 </>;
}
export default function Conflicts(){return <OfFrame active="conflicts" title="cf.title" intro="cf.intro"><Body/></OfFrame>;}
