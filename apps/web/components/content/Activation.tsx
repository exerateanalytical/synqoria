"use client";
import {useRef,useState} from "react";
import {call,newKey} from "../../lib/content/client";
import type {Failure,Page} from "../../lib/content/client";
import {enumLabel,safetyLabel} from "../../lib/content/i18n";
import {ActionButton,ActionError,Badge,ContentFrame,Empty,FailureView,Field,fmtTime,Loading,Notice,Pager,styles,toneFor,useContent,useLoad} from "./ui";

type Act={id:string;subjectType:string;subjectKey:string;subjectId:string;status:string;activatedBy:string;activatedAt:string;previousId:string|null;endReason:string|null;impact:any;version:number};
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function Inner(){
 const {t,lang}=useContent();
 const [type,setType]=useState("RULE_PACKAGE");const [subject,setSubject]=useState("");
 const [impact,setImpact]=useState<any>(null);
 const [histKey,setHistKey]=useState("");const [page,setPage]=useState(0);
 const [rbFor,setRbFor]=useState<Act|null>(null);const [rbReason,setRbReason]=useState("");
 const [failure,setFailure]=useState<Failure|null>(null);const [done,setDone]=useState<string|null>(null);const [busy,setBusy]=useState(false);
 const key=useRef(newKey());
 const active=useLoad<{items:Act[]}>(s=>call("GET","activations/active",{signal:s}),[done]);
 const hist=useLoad<Page<Act>>(s=>call("GET","activations",{signal:s,query:{subjectKey:histKey.trim()||undefined,page,size:25}}),[histKey,page,done]);

 async function run<T>(fn:()=>Promise<import("../../lib/content/client").Result<T>>,ok?:(d:T)=>void){
  if(busy)return;setBusy(true);setFailure(null);setDone(null);
  const r=await fn();setBusy(false);
  if(!r.ok){setFailure(r);return;}
  ok?.(r.data);
 }
 const idReason=!UUID.test(subject.trim())?t("disabled.input"):null;
 const activateReason=idReason??(!impact||impact.subjectId!==subject.trim()?t("ac.noImpact"):!impact.canActivate?t("ac.blocked"):null);
 const rows=active.data?.items??[];
 const currentOfSubject=rows.find(r=>r.subjectType===type&&r.subjectKey===impact?.subjectKey);

 return <div className={styles.cols}>
  <div>
   <section className={styles.panel} aria-label={t("ac.candidate")}>
    <h2>{t("ac.candidate")}</h2>
    <form onSubmit={e=>e.preventDefault()} noValidate>
     <div className={styles.formGrid}>
      <Field id="ac-t" label={t("ac.type")}><select id="ac-t" className={styles.input} value={type} onChange={e=>{setType(e.target.value);setImpact(null);key.current=newKey();}}>
       {["RULE_PACKAGE","FORMULARY","CODE_SYSTEM"].map(x=><option key={x} value={x}>{enumLabel(lang,"subject",x)}</option>)}</select></Field>
      <Field id="ac-s" label={t("ac.subject")}><input id="ac-s" className={`${styles.input} ${styles.mono}`} value={subject} onChange={e=>{setSubject(e.target.value.trim());setImpact(null);key.current=newKey();}}/></Field>
     </div>
     <p className={styles.hint}>{t("ac.dryRunNote")}</p>
     <div className={styles.actions}>
      <ActionButton label={t("ac.check")} tone="secondary" reason={idReason} busy={busy} onClick={()=>void run<any>(()=>call("GET","activations/impact",{query:{subjectType:type,subjectId:subject.trim()}}),d=>setImpact(d))}/>
      <ActionButton label={t("ac.activate")} reason={activateReason} busy={busy}
       onClick={()=>void run(()=>call("POST","activations",{key:key.current,body:{subjectType:type,subjectId:subject.trim(),expectedActiveId:currentOfSubject?.id??null}}),()=>{key.current=newKey();setImpact(null);setDone(t("ac.done"));})}/>
     </div>
    </form>
    <ActionError failure={failure} onReload={()=>{setFailure(null);setImpact(null);active.reload();}}/>
    {done&&<Notice>{done}</Notice>}
    {impact&&<div role="status" aria-live="polite">
     <h3>{impact.canActivate?t("ac.canActivate"):t("ac.blocked")} <Badge tone={impact.canActivate?"ok":"danger"}>{impact.subjectKey}</Badge></h3>
     {impact.blockers.length>0&&<><h4>{t("ac.blockers")}</h4><ul className={styles.list}>{impact.blockers.map((b:string)=><li key={b} className={styles.card}>{safetyLabel(lang,b)}</li>)}</ul></>}
     <dl className={styles.kv}>
      <dt>{t("ac.currentlyActive")}</dt><dd className={styles.mono}>{impact.currentlyActive??t("common.none")}</dd>
      {impact.changes?.rulesAdded&&<><dt>{t("ac.rulesAdded")}</dt><dd>{impact.changes.rulesAdded.join(", ")||t("common.none")}</dd></>}
      {impact.changes?.rulesRemoved&&<><dt>{t("ac.rulesRemoved")}</dt><dd>{impact.changes.rulesRemoved.join(", ")||t("common.none")}</dd></>}
     </dl>
    </div>}
   </section>

   <section className={styles.panel} aria-label={t("ac.active")}>
    <h2>{t("ac.active")}</h2>
    {active.phase==="loading"&&!active.data&&<Loading/>}
    {active.phase==="failed"&&active.failure&&<FailureView failure={active.failure} onRetry={active.reload}/>}
    {active.data&&rows.length===0&&active.phase!=="failed"&&<Empty message="ac.none" hint={false}/>}
    {rows.length>0&&<ul className={styles.list}>{rows.map(r=><li key={r.id} className={styles.card}>
     <Badge tone="ok">{enumLabel(lang,"subject",r.subjectType)}</Badge> <strong>{r.subjectKey}</strong>
     <div className={styles.hint}>{t("ac.since")} {fmtTime(lang,r.activatedAt)} · <span className={styles.mono}>{r.activatedBy}</span></div>
     <div className={styles.actions}><ActionButton label={t("ac.rollback")} tone="danger" reason={r.previousId?null:t("err.nothing_to_roll_back")} onClick={()=>{setRbFor(r);setRbReason("");}}/></div>
    </li>)}</ul>}
    {rbFor&&<form className={styles.formGrid} onSubmit={e=>e.preventDefault()}>
     <Field id="rb-r" label={`${t("ac.rollbackReason")} — ${rbFor.subjectKey}`} wide><input id="rb-r" className={styles.input} autoFocus maxLength={300} value={rbReason} onChange={e=>setRbReason(e.target.value)}/></Field>
     <div className={styles.actions}>
      <ActionButton label={t("ac.rollback")} tone="danger" busy={busy} reason={rbReason.trim()?null:t("err.reason_required")}
       onClick={()=>void run(()=>call("POST",`activations/${rbFor.id}/rollback`,{body:{expectedVersion:rbFor.version,reason:rbReason.trim()}}),()=>{setRbFor(null);setRbReason("");setDone(t("ac.done"));})}/>
      <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={()=>setRbFor(null)}>{t("common.cancel")}</button></div>
    </form>}
   </section>
  </div>

  <section className={styles.panel} aria-label={t("ac.history")}>
   <h2>{t("ac.history")}</h2>
   <Field id="ah-k" label={t("pk.key")}><input id="ah-k" className={styles.input} value={histKey} maxLength={300} onChange={e=>{setHistKey(e.target.value);setPage(0);}}/></Field>
   <div className={styles.actions}><button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={hist.reload}>{t("common.refresh")}</button></div>
   <div aria-busy={hist.phase==="loading"} style={{marginTop:12}}>
    {hist.phase==="loading"&&!hist.data&&<Loading/>}
    {hist.phase==="failed"&&hist.failure&&<FailureView failure={hist.failure} onRetry={hist.reload}/>}
    {hist.data&&hist.data.items.length===0&&hist.phase!=="failed"&&<Empty/>}
    {hist.data&&hist.data.items.length>0&&hist.phase!=="failed"&&<>
     <ol className={styles.timeline}>{hist.data.items.map(a=><li key={a.id}>
      <Badge tone={toneFor(a.status)}>{enumLabel(lang,"activation",a.status)}</Badge> <strong>{a.subjectKey}</strong> · {enumLabel(lang,"subject",a.subjectType)}
      <div className={styles.hint}>{fmtTime(lang,a.activatedAt)} · <span className={styles.mono}>{a.activatedBy}</span>{a.endReason?` · ${a.endReason}`:""}</div></li>)}</ol>
     <Pager page={hist.data.page} size={hist.data.size} total={hist.data.total} onPage={setPage}/></>}
   </div>
  </section>
 </div>;
}
export function Activation(){return <ContentFrame active="activation" title="ac.title" intro="ac.intro"><Inner/></ContentFrame>;}
