"use client";
import {useState} from "react";
import {ActionButton,ActionError,Badge,Empty,Field,FailureView,Loading,Notice,Pager,RlFrame,fmtTime,styles,toneFor,useLoad,useRl} from "./ui";
import {call,newKey,UUID_RE} from "../../lib/reliability/client";
import type {Failure,Page} from "../../lib/reliability/client";
import {enumLabel} from "../../lib/reliability/i18n";

type Inc={id:string;number:number;title:string;severity:string;status:string;serviceKey:string|null;impact:string;ownerId:string|null;escalationLevel:number;declaredAt:string;version:number;ackOverdue:boolean;ackSlaMinutes?:number;
 timeline?:{kind:string;text:string;actorId:string;at:string}[]};
type Board={services:{serviceKey:string;name:string}[]};

function Body(){
 const {t,lang}=useRl();
 const [page,setPage]=useState(0);const [sel,setSel]=useState("");
 const [title,setTitle]=useState("");const [sev,setSev]=useState("SEV3");const [svc,setSvc]=useState("");const [impact,setImpact]=useState("");
 const [text,setText]=useState("");const [kind,setKind]=useState("NOTE");const [owner,setOwner]=useState("");const [ev,setEv]=useState("");
 const [fail,setFail]=useState<Failure|null>(null);const [msg,setMsg]=useState<string|null>(null);const [busy,setBusy]=useState(false);
 const list=useLoad<Page<Inc>>(s=>call("GET","incidents",{query:{page,size:10},signal:s}),[page]);
 const detail=useLoad<Inc|null>(s=>sel?call<Inc>("GET",`incidents/${sel}`,{signal:s}):Promise.resolve({ok:true as const,status:200,data:null}),[sel]);
 const board=useLoad<Board>(s=>call<Board>("GET","health-board",{signal:s}),[]);
 const act=async(p:ReturnType<typeof call>,done?:string)=>{
  setBusy(true);setFail(null);setMsg(null);
  const r=await p;setBusy(false);
  if(r.ok){if(done)setMsg(done);list.reload();detail.reload();}else setFail(r);
 };
 const i=detail.data;
 const body=(extra:Record<string,unknown>)=>({expectedVersion:i!.version,...extra});
 const st=(...allowed:string[])=>!i?t("disabled.select"):!allowed.includes(i.status)?t("disabled.status",{status:enumLabel(lang,"inc",i.status)}):null;
 const post=(path:string,extra:Record<string,unknown>)=>i&&act(call("POST",`incidents/${i.id}/${path}`,{body:body(extra),key:newKey()}));
 return <>
  <section className={styles.panel} aria-labelledby="d-h">
   <h2 id="d-h">{t("in.declare")}</h2>
   <div className={styles.formGrid}>
    <Field id="in-t" label={t("in.titleField")} wide><input id="in-t" className={styles.input} maxLength={200} value={title} onChange={e=>setTitle(e.target.value)}/></Field>
    <Field id="in-s" label={t("in.severity")}><select id="in-s" className={styles.input} value={sev} onChange={e=>setSev(e.target.value)}>{["SEV1","SEV2","SEV3","SEV4"].map(x=><option key={x} value={x}>{enumLabel(lang,"sev",x)}</option>)}</select></Field>
    <Field id="in-v" label={t("in.service")}><select id="in-v" className={styles.input} value={svc} onChange={e=>setSvc(e.target.value)}><option value="">{t("common.none")}</option>{(board.data?.services??[]).map(x=><option key={x.serviceKey} value={x.serviceKey}>{x.name}</option>)}</select></Field>
    <Field id="in-i" label={t("in.impact")} wide><input id="in-i" className={styles.input} maxLength={500} value={impact} onChange={e=>setImpact(e.target.value)}/></Field>
   </div>
   <div className={styles.actions}><ActionButton label={t("in.declareAction")} busy={busy} reason={!title.trim()||!impact.trim()?t("disabled.input"):null} onClick={()=>act(call("POST","incidents",{body:{title:title.trim(),severity:sev,serviceKey:svc||null,impact:impact.trim()},key:newKey()}),t("in.declared"))}/></div>
   <ActionError failure={fail} onReload={()=>{list.reload();detail.reload();}}/>
   {msg&&<Notice>{msg}</Notice>}
  </section>
  <section className={styles.panel} aria-labelledby="l-h">
   <h2 id="l-h">{t("in.list")}</h2>
   {list.phase==="loading"&&!list.data?<Loading/>:list.phase==="failed"||!list.data?<FailureView failure={list.failure!} onRetry={list.reload}/>:
    list.data.items.length===0?<Empty message="in.none" hint={false}/>:<>
    <ul className={styles.list}>{list.data.items.map(x=><li key={x.id} className={styles.card}>
     <strong>#{x.number} {x.title}</strong> <Badge tone={toneFor(x.status)}>{enumLabel(lang,"inc",x.status)}</Badge> <Badge tone={x.severity==="SEV1"?"danger":"neutral"}>{enumLabel(lang,"sev",x.severity)}</Badge>
     {x.ackOverdue&&<> <Badge tone="danger">{t("in.overdue")}</Badge></>}
     <p className={styles.hint}>{x.serviceKey??"—"} · {fmtTime(lang,x.declaredAt)}</p>
     <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} aria-pressed={sel===x.id} onClick={()=>{setSel(x.id);setMsg(null);setFail(null);}}>{t("common.select")}</button>
    </li>)}</ul>
    <Pager page={page} size={10} total={list.data.total} onPage={setPage}/>
   </>}
  </section>
  <section className={styles.panel} aria-labelledby="w-h" aria-live="polite">
   <h2 id="w-h">{t("nav.incidents")}</h2>
   {!sel?<p className={styles.hint}>{t("in.pick")}</p>:detail.phase==="loading"&&!i?<Loading/>:detail.phase==="failed"?<FailureView failure={detail.failure!} onRetry={detail.reload}/>:!i?<Empty/>:<>
    <p><strong>#{i.number} {i.title}</strong> · {i.impact}</p>
    <p className={styles.hint}>{t("in.sla",{n:i.ackSlaMinutes??0})} · {t("in.level")}: {i.escalationLevel} · {t("in.owner")}: <span className={styles.mono}>{i.ownerId?i.ownerId.slice(0,8):"—"}</span></p>
    <h3>{t("in.timeline")}</h3>
    <ul className={styles.timeline}>{(i.timeline??[]).map((x,k)=><li key={k}><span className={styles.mono}>{x.kind}</span> {x.text} <span className={styles.hint}>{fmtTime(lang,x.at)}</span></li>)}</ul>
    <div className={styles.formGrid}>
     <Field id="in-n" label={t("in.statusText")} wide><input id="in-n" className={styles.input} maxLength={500} value={text} onChange={e=>setText(e.target.value)}/></Field>
     <Field id="in-k" label={t("in.noteKind")}><select id="in-k" className={styles.input} value={kind} onChange={e=>setKind(e.target.value)}><option value="NOTE">{t("in.noteNote")}</option><option value="ACTION">{t("in.noteAction")}</option></select></Field>
     <Field id="in-o" label={t("in.assign")}><input id="in-o" className={`${styles.input} ${styles.mono}`} value={owner} onChange={e=>setOwner(e.target.value.trim())}/></Field>
     <Field id="in-e" label={t("in.verifyEvidence")} wide><input id="in-e" className={styles.input} maxLength={300} value={ev} onChange={e=>setEv(e.target.value)}/></Field>
    </div>
    <div className={styles.actions}>
     <ActionButton label={t("in.ack")} busy={busy} reason={st("OPEN")} onClick={()=>post("acknowledgement",{})}/>
     <ActionButton label={t("in.assignAction")} tone="secondary" busy={busy} reason={st("OPEN","ACKNOWLEDGED","MITIGATING","MITIGATED")??(UUID_RE.test(owner)?null:t("disabled.input"))} onClick={()=>post("assignment",{ownerId:owner})}/>
     <ActionButton label={t("in.add")} tone="secondary" busy={busy} reason={st("OPEN","ACKNOWLEDGED","MITIGATING","MITIGATED")??(text.trim()?null:t("disabled.input"))} onClick={()=>i&&act(call("POST",`incidents/${i.id}/notes`,{body:{kind,text:text.trim()},key:newKey()}))}/>
     <ActionButton label={t("in.escalate")} tone="secondary" busy={busy} reason={st("OPEN","ACKNOWLEDGED","MITIGATING","MITIGATED")??(text.trim().length<5?t("disabled.input"):null)} onClick={()=>post("escalation",{reason:text.trim()})}/>
     <ActionButton label={t("in.mitigating")} tone="secondary" busy={busy} reason={st("ACKNOWLEDGED")??(text.trim().length<5?t("disabled.input"):null)} onClick={()=>post("status",{to:"MITIGATING",text:text.trim()})}/>
     <ActionButton label={t("in.mitigated")} tone="secondary" busy={busy} reason={st("ACKNOWLEDGED","MITIGATING")??(text.trim().length<5?t("disabled.input"):null)} onClick={()=>post("status",{to:"MITIGATED",text:text.trim()})}/>
     <ActionButton label={t("in.verify")} busy={busy} reason={st("MITIGATED")??(ev.trim().length<5?t("disabled.input"):null)} onClick={()=>post("resolution",{verificationEvidence:ev.trim()})}/>
    </div>
   </>}
  </section>
 </>;
}
export default function Incidents(){return <RlFrame active="incidents" title="in.title" intro="in.intro"><Body/></RlFrame>;}
