"use client";
import {useState} from "react";
import {ActionButton,ActionError,Badge,Empty,Field,FailureView,Loading,Notice,Pager,RlFrame,fmtTime,styles,toneFor,useLoad,useRl} from "./ui";
import {call,newKey} from "../../lib/reliability/client";
import type {Failure,Page} from "../../lib/reliability/client";
import {enumLabel} from "../../lib/reliability/i18n";

type Rec={id:string;scenario:string;exercise:boolean;baseSnapshot:string;targetLsn:number;targetTime:string|null;outageStartedAt:string;state:string;plannedBy:string;authorizedBy:string|null;version:number;
 steps?:{step:string;evidenceRef:string|null;at:string}[]};
type Bk={id:string;snapshotId:string;walEnd:number};
const NEXT:Record<string,string>={AUTHORIZED:"FENCE",FENCED:"RESTORE_DB",DB_RESTORED:"RECONCILE_OBJECTS",OBJECTS_RECONCILED:"RECOVER_AUDIT",AUDIT_RECOVERED:"RECONCILE_EFFECTS",EFFECTS_RECONCILED:"BEGIN_VALIDATION"};
const HEX=/^[0-9a-f]{64}$/;
const SCENARIOS=["DATABASE_FAILURE","APPLICATION_FAILURE","INFRASTRUCTURE_LOSS","CONNECTIVITY_INTERRUPTION","ISOLATED_CELL_FAILURE"];

function Body(){
 const {t,lang}=useRl();
 const [page,setPage]=useState(0);const [sel,setSel]=useState("");
 const [scenario,setScenario]=useState("DATABASE_FAILURE");const [exercise,setExercise]=useState(true);const [base,setBase]=useState("");const [lsn,setLsn]=useState("");const [tt,setTt]=useState("");const [outage,setOutage]=useState("");
 const [ev,setEv]=useState("");const [egress,setEgress]=useState(false);const [writers,setWriters]=useState(false);const [rl,setRl]=useState("");const [missing,setMissing]=useState("");const [digest,setDigest]=useState("");const [pending,setPending]=useState("");const [why,setWhy]=useState("");
 const [fail,setFail]=useState<Failure|null>(null);const [msg,setMsg]=useState<string|null>(null);const [busy,setBusy]=useState(false);
 const list=useLoad<Page<Rec>>(s=>call("GET","recoveries",{query:{page,size:10},signal:s}),[page]);
 const bases=useLoad<Page<Bk>>(s=>call("GET","backups",{query:{kind:"FULL",status:"VERIFIED",size:50},signal:s}),[]);
 const detail=useLoad<Rec|null>(s=>sel?call<Rec>("GET",`recoveries/${sel}`,{signal:s}):Promise.resolve({ok:true as const,status:200,data:null}),[sel]);
 const r=detail.data;
 const act=async(p:ReturnType<typeof call>,done?:string)=>{
  setBusy(true);setFail(null);setMsg(null);
  const x=await p;setBusy(false);
  if(x.ok){if(done)setMsg(done);list.reload();detail.reload();}else setFail(x);
 };
 const planWhy=!base||!/^[0-9]{1,18}$/.test(lsn)||!outage?t("disabled.input"):null;
 const plan=()=>act(call("POST","recoveries",{body:{scenario,exercise,baseBackupId:base,targetLsn:Number(lsn),targetTime:tt?new Date(tt).toISOString():null,outageStartedAt:new Date(outage).toISOString(),incidentId:null},key:newKey()}),t("rc.planned"));
 const next=r?NEXT[r.state]:undefined;
 const stepWhy=!r?t("disabled.select"):!next?t("disabled.status",{status:enumLabel(lang,"rec",r.state)}):
  (["FENCE","RESTORE_DB","RECONCILE_OBJECTS","RECONCILE_EFFECTS"].includes(next)&&!ev.trim())?t("disabled.input"):
  next==="FENCE"&&!(egress&&writers)?t("err.fencing_not_confirmed"):next==="RESTORE_DB"&&!/^[0-9]{1,18}$/.test(rl)?t("disabled.input"):next==="RECONCILE_OBJECTS"&&!/^[0-9]+$/.test(missing)?t("disabled.input"):
  next==="RECOVER_AUDIT"&&!HEX.test(digest)?t("disabled.input"):next==="RECONCILE_EFFECTS"&&!/^[0-9]+$/.test(pending)?t("disabled.input"):null;
 const runStep=()=>r&&next&&act(call("POST",`recoveries/${r.id}/steps`,{body:{expectedVersion:r.version,step:next,evidenceRef:ev.trim()||null,egressDisabled:egress,writersStopped:writers,restoredLsn:rl?Number(rl):null,
  objectsMissing:missing?Number(missing):null,auditChainDigest:digest||null,effectsPending:pending?Number(pending):null},key:newKey()}),t("rc.done"));
 const final=r&&["REOPENED","ABORTED"].includes(r.state);
 return <>
  <section className={styles.panel} aria-labelledby="p-h">
   <h2 id="p-h">{t("rc.plan")}</h2>
   {(bases.data?.items??[]).length===0&&bases.phase==="ready"&&<p className={styles.hint}>{t("rc.base0")}</p>}
   <div className={styles.formGrid}>
    <Field id="rc-sc" label={t("rc.scenario")}><select id="rc-sc" className={styles.input} value={scenario} onChange={e=>setScenario(e.target.value)}>{SCENARIOS.map(x=><option key={x} value={x}>{enumLabel(lang,"scenario",x)}</option>)}</select></Field>
    <Field id="rc-bs" label={t("rc.base")}><select id="rc-bs" className={styles.input} value={base} onChange={e=>setBase(e.target.value)}><option value="">{t("common.none")}</option>{(bases.data?.items??[]).map(x=><option key={x.id} value={x.id}>{x.snapshotId} · {x.walEnd}</option>)}</select></Field>
    <Field id="rc-ls" label={t("rc.targetLsn")}><input id="rc-ls" inputMode="numeric" className={styles.input} value={lsn} onChange={e=>setLsn(e.target.value)}/></Field>
    <Field id="rc-tt" label={t("rc.targetTime")}><input id="rc-tt" type="datetime-local" className={styles.input} value={tt} onChange={e=>setTt(e.target.value)}/></Field>
    <Field id="rc-ou" label={t("rc.outage")}><input id="rc-ou" type="datetime-local" className={styles.input} value={outage} onChange={e=>setOutage(e.target.value)}/></Field>
    <label className={styles.checkRow}><input type="checkbox" checked={exercise} onChange={e=>setExercise(e.target.checked)}/> {t("rc.exercise")}</label>
   </div>
   <div className={styles.actions}><ActionButton label={t("rc.planAction")} busy={busy} reason={planWhy} onClick={plan}/></div>
   <ActionError failure={fail} onReload={()=>{list.reload();detail.reload();}}/>
   {msg&&<Notice>{msg}</Notice>}
  </section>
  <section className={styles.panel} aria-labelledby="l-h">
   <h2 id="l-h">{t("rc.list")}</h2>
   {list.phase==="loading"&&!list.data?<Loading/>:list.phase==="failed"||!list.data?<FailureView failure={list.failure!} onRetry={list.reload}/>:
    list.data.items.length===0?<Empty message="rc.none" hint={false}/>:<>
    <ul className={styles.list}>{list.data.items.map(x=><li key={x.id} className={styles.card}>
     <strong>{enumLabel(lang,"scenario",x.scenario)}</strong> <Badge tone={toneFor(x.state)}>{enumLabel(lang,"rec",x.state)}</Badge> {!x.exercise&&<Badge tone="danger">{t("rc.real")}</Badge>}
     <p className={styles.hint}>{x.baseSnapshot} → {x.targetLsn} · {fmtTime(lang,x.outageStartedAt)}</p>
     <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} aria-pressed={sel===x.id} onClick={()=>{setSel(x.id);setMsg(null);setFail(null);}}>{t("common.select")}</button>
    </li>)}</ul>
    <Pager page={page} size={10} total={list.data.total} onPage={setPage}/>
   </>}
  </section>
  <section className={styles.panel} aria-labelledby="s-h" aria-live="polite">
   <h2 id="s-h">{t("rc.next")}{r&&next?`: ${enumLabel(lang,"step",next)}`:""}</h2>
   {!sel?<p className={styles.hint}>{t("rc.pick")}</p>:detail.phase==="loading"&&!r?<Loading/>:detail.phase==="failed"?<FailureView failure={detail.failure!} onRetry={detail.reload}/>:!r?<Empty/>:<>
    <h3>{t("rc.steps")}</h3>
    <ul className={styles.timeline}>{(r.steps??[]).map((s,k)=><li key={k}><span className={styles.mono}>{s.step}</span> {s.evidenceRef??""} <span className={styles.hint}>{fmtTime(lang,s.at)}</span></li>)}</ul>
    <div className={styles.formGrid}>
     <Field id="rc-ev" label={t("rc.evidence")} wide><input id="rc-ev" className={styles.input} maxLength={160} value={ev} onChange={e=>setEv(e.target.value)}/></Field>
     <label className={styles.checkRow}><input type="checkbox" checked={egress} onChange={e=>setEgress(e.target.checked)}/> {t("rc.egress")}</label>
     <label className={styles.checkRow}><input type="checkbox" checked={writers} onChange={e=>setWriters(e.target.checked)}/> {t("rc.writers")}</label>
     <Field id="rc-rl" label={t("rc.restoredLsn")}><input id="rc-rl" inputMode="numeric" className={styles.input} value={rl} onChange={e=>setRl(e.target.value)}/></Field>
     <Field id="rc-ms" label={t("rc.missing")}><input id="rc-ms" inputMode="numeric" className={styles.input} value={missing} onChange={e=>setMissing(e.target.value)}/></Field>
     <Field id="rc-ad" label={t("rc.auditDigest")} wide><input id="rc-ad" className={`${styles.input} ${styles.mono}`} value={digest} onChange={e=>setDigest(e.target.value.trim().toLowerCase())}/></Field>
     <Field id="rc-pe" label={t("rc.pending")}><input id="rc-pe" inputMode="numeric" className={styles.input} value={pending} onChange={e=>setPending(e.target.value)}/></Field>
     <Field id="rc-ab" label={t("rc.abortReason")} wide><input id="rc-ab" className={styles.input} maxLength={300} value={why} onChange={e=>setWhy(e.target.value)}/></Field>
    </div>
    <div className={styles.actions}>
     <ActionButton label={t("rc.authorize")} busy={busy} reason={r.state!=="PLANNED"?t("disabled.status",{status:enumLabel(lang,"rec",r.state)}):null} onClick={()=>act(call("POST",`recoveries/${r.id}/authorization`,{body:{expectedVersion:r.version},key:newKey()}))}/>
     <ActionButton label={next?enumLabel(lang,"step",next):t("rc.next")} busy={busy} reason={stepWhy} onClick={runStep}/>
     <ActionButton label={t("rc.abort")} tone="danger" busy={busy} reason={final?t("disabled.status",{status:enumLabel(lang,"rec",r.state)}):why.trim().length<5?t("disabled.input"):null} onClick={()=>act(call("POST",`recoveries/${r.id}/abort`,{body:{expectedVersion:r.version,reason:why.trim()},key:newKey()}))}/>
    </div>
   </>}
  </section>
 </>;
}
export default function Recovery(){return <RlFrame active="recovery" title="rc.title" intro="rc.intro"><Body/></RlFrame>;}
