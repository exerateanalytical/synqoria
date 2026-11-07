"use client";
import {useState} from "react";
import {ActionButton,ActionError,Badge,Empty,Field,FailureView,Loading,Notice,OfFrame,Pager,styles,toneFor,useLoad,useOf} from "./ui";
import {call,newKey,UUID_RE} from "../../lib/offline/client";
import type {Failure,Page} from "../../lib/offline/client";
import {enumLabel} from "../../lib/offline/i18n";
import type {Key} from "../../lib/offline/i18n";

type Tr={id:string;headId:string;recordType:string;recordKey:string;fromOwner:string;toOwner:string;fromEpoch:number;status:string;reason:string;fenceEvidenceRef:string|null;fenceLastVersion:number|null;
 proposedBy:string;approvedBy:string|null;completedEpoch:number|null;abortReason:string|null;version:number;currentHeadVersion:number;currentEpoch:number;frozen:boolean};
type Head={id:string;recordType:string;recordKey:string;ownerRef:string;ownershipEpoch:number;headVersion:number};

function Body(){
 const {t,lang}=useOf();
 const [page,setPage]=useState(0);
 const [headId,setHeadId]=useState("");
 const [dest,setDest]=useState("");
 const [reason,setReason]=useState("");
 const [evidence,setEvidence]=useState("");
 const [sel,setSel]=useState("");
 const [note,setNote]=useState("");
 const [fail,setFail]=useState<Failure|null>(null);
 const [msg,setMsg]=useState<string|null>(null);
 const [busy,setBusy]=useState(false);
 const heads=useLoad<Page<Head>>(s=>call("GET","heads",{query:{size:100},signal:s}),[]);
 const list=useLoad<Page<Tr>>(s=>call("GET","transfers",{query:{page,size:10},signal:s}),[page]);
 const act=async(p:ReturnType<typeof call>,done?:string)=>{
  setBusy(true);setFail(null);setMsg(null);
  const r=await p;setBusy(false);
  if(r.ok){if(done)setMsg(done);list.reload();heads.reload();}else setFail(r);
 };
 const propose=()=>act(call("POST","transfers",{body:{headId,toOwner:dest.trim(),reason:reason.trim()},key:newKey()}),t("tr.proposed"));
 const step=(x:Tr,path:string,body:Record<string,unknown>)=>act(call("POST",`transfers/${x.id}/${path}`,{body:{expectedVersion:x.version,...body},key:newKey()}));
 const proposeWhy=!headId||!UUID_RE.test(dest.trim())||reason.trim().length<5?t("disabled.input"):null;
 const st=(x:Tr,wanted:string)=>x.status!==wanted?t("disabled.status",{status:enumLabel(lang,"transfer",x.status)}):null;
 const open=list.data?.items.find(x=>x.id===sel);
 return <>
  <section className={styles.panel} aria-labelledby="tp-h">
   <h2 id="tp-h">{t("tr.propose")}</h2>
   {heads.phase==="failed"?<FailureView failure={heads.failure!} onRetry={heads.reload}/>:
   <div className={styles.formGrid}>
    <Field id="tr-head" label={t("tr.record")}>
     <select id="tr-head" className={styles.input} value={headId} onChange={e=>setHeadId(e.target.value)}>
      <option value="">{t("tr.pickRecord")}</option>
      {(heads.data?.items??[]).map(h=><option key={h.id} value={h.id}>{h.recordType} / {h.recordKey} · e{h.ownershipEpoch} · v{h.headVersion}</option>)}
     </select>
    </Field>
    <Field id="tr-dest" label={t("tr.destination")}><input id="tr-dest" className={`${styles.input} ${styles.mono}`} value={dest} onChange={e=>setDest(e.target.value)}/></Field>
    <Field id="tr-reason" label={t("tr.reason")} wide><input id="tr-reason" className={styles.input} maxLength={300} value={reason} onChange={e=>setReason(e.target.value)}/></Field>
   </div>}
   <div className={styles.actions}><ActionButton label={t("tr.proposeAction")} busy={busy} reason={proposeWhy} onClick={propose}/></div>
   <ActionError failure={fail} onReload={()=>{list.reload();heads.reload();}}/>
   {msg&&<Notice>{msg}</Notice>}
  </section>
  <section className={styles.panel} aria-labelledby="tl-h">
   <h2 id="tl-h">{t("tr.list")}</h2>
   {list.phase==="loading"&&!list.data?<Loading/>:list.phase==="failed"||!list.data?<FailureView failure={list.failure!} onRetry={list.reload}/>:
    list.data.items.length===0?<Empty message="tr.none" hint={false}/>:<>
    <ul className={styles.list}>{list.data.items.map(x=><li key={x.id} className={styles.card}>
     <strong>{x.recordType} / {x.recordKey}</strong> <Badge tone={toneFor(x.status)}>{enumLabel(lang,"transfer",x.status)}</Badge>
     <p className={styles.hint}>{t(`tr.step.${x.status}` as Key)}</p>
     <dl className={styles.kv}><dt>{t("tr.from")}</dt><dd className={styles.mono}>{x.fromOwner.slice(0,8)}</dd><dt>{t("tr.to")}</dt><dd className={styles.mono}>{x.toOwner.slice(0,8)}</dd>
      <dt>{t("tr.epoch")}</dt><dd>{x.currentEpoch}{x.completedEpoch!==null&&<> → {x.completedEpoch}</>}</dd><dt>{t("tr.frozenNow")}</dt><dd>{x.frozen?"✔":"—"}</dd>
      <dt>{t("common.reason")}</dt><dd>{x.reason}</dd>
      {x.fenceEvidenceRef&&<><dt>{t("tr.evidence")}</dt><dd className={styles.mono}>{x.fenceEvidenceRef} · v{x.fenceLastVersion}</dd></>}</dl>
     <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} aria-pressed={sel===x.id} onClick={()=>{setSel(x.id);setEvidence("");setNote("");}}>{t("common.actions")}</button>
    </li>)}</ul>
    <Pager page={page} size={10} total={list.data.total} onPage={setPage}/>
   </>}
  </section>
  <section className={styles.panel} aria-labelledby="ta-h">
   <h2 id="ta-h">{t("common.actions")}</h2>
   {!open?<p className={styles.hint}>{t("disabled.select")}</p>:<>
    <div className={styles.formGrid}>
     <Field id="tr-ev" label={t("tr.evidence")}><input id="tr-ev" className={styles.input} maxLength={160} value={evidence} onChange={e=>setEvidence(e.target.value)}/></Field>
     <Field id="tr-note" label={t("tr.abortReason")}><input id="tr-note" className={styles.input} maxLength={300} value={note} onChange={e=>setNote(e.target.value)}/></Field>
    </div>
    <p className={styles.hint}>{t("tr.lastVersion")}: v{open.currentHeadVersion}</p>
    <div className={styles.actions}>
     <ActionButton label={t("tr.freeze")} busy={busy} reason={st(open,"PROPOSED")} onClick={()=>step(open,"freeze",{})}/>
     <ActionButton label={t("tr.fence")} busy={busy} reason={st(open,"FROZEN")??(evidence.trim()?null:t("disabled.input"))} onClick={()=>step(open,"fence",{evidenceRef:evidence.trim(),lastSourceVersion:open.currentHeadVersion})}/>
     <ActionButton label={t("tr.approve")} busy={busy} reason={st(open,"FENCED")} onClick={()=>step(open,"decision",{approve:true,reason:null})}/>
     <ActionButton label={t("tr.rejectAction")} tone="danger" busy={busy} reason={st(open,"FENCED")??(note.trim().length<5?t("disabled.input"):null)} onClick={()=>step(open,"decision",{approve:false,reason:note.trim()})}/>
     <ActionButton label={t("tr.complete")} busy={busy} reason={st(open,"APPROVED")} onClick={()=>step(open,"completion",{})}/>
     <ActionButton label={t("tr.abort")} tone="danger" busy={busy} reason={["COMPLETED","ABORTED"].includes(open.status)?t("disabled.status",{status:enumLabel(lang,"transfer",open.status)}):note.trim().length<5?t("disabled.input"):null} onClick={()=>step(open,"abort",{reason:note.trim()})}/>
    </div>
   </>}
  </section>
 </>;
}
export default function Transfers(){return <OfFrame active="transfers" title="tr.title" intro="tr.intro"><Body/></OfFrame>;}
