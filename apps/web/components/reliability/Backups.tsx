"use client";
import {useState} from "react";
import {ActionButton,ActionError,Badge,Empty,Field,FailureView,Loading,Notice,Pager,RlFrame,fmtTime,styles,toneFor,useLoad,useRl} from "./ui";
import {call,newKey} from "../../lib/reliability/client";
import type {Failure,Page} from "../../lib/reliability/client";
import {enumLabel} from "../../lib/reliability/i18n";

type Bk={id:string;kind:string;snapshotId:string;walStart:number;walEnd:number;sizeBytes:number;digest:string;keyRef:string;retentionUntil:string;status:string;version:number;expired:boolean;finishedAt:string;objectRefs:string[];
 verification?:{checkKey:string;result:string;evidenceRef:string;observedDigest:string|null}[]};
const CHECKS=["INVENTORY","INTEGRITY","ENCRYPTION_KEY","COMPLETENESS","RESTORE_READINESS"];
const HEX=/^[0-9a-f]{64}$/;
type Cont={continuous:boolean;coveredUpTo:number;gapStart:number|null;gapEnd:number|null};

function Body(){
 const {t,lang}=useRl();
 const [kind,setKind]=useState("");const [status,setStatus]=useState("");const [page,setPage]=useState(0);const [sel,setSel]=useState("");
 const [res,setRes]=useState<Record<string,string>>(Object.fromEntries(CHECKS.map(c=>[c,"PASS"])));
 const [evid,setEvid]=useState<Record<string,string>>({});const [observed,setObserved]=useState("");
 const [base,setBase]=useState("");const [target,setTarget]=useState("");const [cont,setCont]=useState<Cont|null>(null);
 const [fail,setFail]=useState<Failure|null>(null);const [msg,setMsg]=useState<string|null>(null);const [busy,setBusy]=useState(false);
 const list=useLoad<Page<Bk>>(s=>call("GET","backups",{query:{kind,status,page,size:10},signal:s}),[kind,status,page]);
 const fulls=useLoad<Page<Bk>>(s=>call("GET","backups",{query:{kind:"FULL",status:"VERIFIED",size:50},signal:s}),[]);
 const detail=useLoad<Bk|null>(s=>sel?call<Bk>("GET",`backups/${sel}`,{signal:s}):Promise.resolve({ok:true as const,status:200,data:null}),[sel]);
 const b=detail.data;
 const verifyWhy=!b?t("disabled.select"):b.status!=="RECORDED"?t("disabled.status",{status:enumLabel(lang,"bk",b.status)}):CHECKS.some(c=>!(evid[c]??"").trim())?t("disabled.input"):!HEX.test(observed)?t("disabled.input"):null;
 const verify=async()=>{
  if(!b)return;setBusy(true);setFail(null);setMsg(null);
  const checks=CHECKS.map(c=>({checkKey:c,result:res[c],evidenceRef:evid[c].trim(),observedDigest:c==="INTEGRITY"?observed:null}));
  const r=await call("POST",`backups/${b.id}/verification`,{body:{checks},key:newKey()});
  setBusy(false);
  if(r.ok){setMsg(t("bk.verified"));list.reload();detail.reload();fulls.reload();}else setFail(r);
 };
 const walWhy=!base||!/^[0-9]{1,18}$/.test(target)?t("disabled.input"):null;
 const walCheck=async()=>{
  setBusy(true);setFail(null);setCont(null);
  const r=await call<Cont>("GET","wal-continuity",{query:{baseBackupId:base,targetLsn:target}});
  setBusy(false);
  if(r.ok)setCont(r.data);else setFail(r);
 };
 return <>
  <section className={styles.panel} aria-labelledby="c-h">
   <h2 id="c-h">{t("bk.catalogue")}</h2>
   <div className={styles.filters}>
    <Field id="bk-k" label={t("bk.filterKind")}><select id="bk-k" className={styles.input} value={kind} onChange={e=>{setKind(e.target.value);setPage(0);}}><option value="">{t("common.any")}</option>{["FULL","INCREMENTAL","WAL_ARCHIVE"].map(x=><option key={x} value={x}>{enumLabel(lang,"kind",x)}</option>)}</select></Field>
    <Field id="bk-s" label={t("bk.filterStatus")}><select id="bk-s" className={styles.input} value={status} onChange={e=>{setStatus(e.target.value);setPage(0);}}><option value="">{t("common.any")}</option>{["RECORDED","VERIFIED","VERIFICATION_FAILED"].map(x=><option key={x} value={x}>{enumLabel(lang,"bk",x)}</option>)}</select></Field>
    <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={list.reload}>{t("common.refresh")}</button>
   </div>
   {list.phase==="loading"&&!list.data?<Loading/>:list.phase==="failed"||!list.data?<FailureView failure={list.failure!} onRetry={list.reload}/>:
    list.data.items.length===0?<Empty message="bk.none" hint={false}/>:<>
    <div className={styles.tableWrap}><table className={styles.table}>
     <thead><tr><th>{t("bk.snapshot")}</th><th>{t("bk.filterKind")}</th><th>{t("bk.wal")}</th><th>{t("common.status")}</th><th>{t("bk.retention")}</th><th>{t("common.actions")}</th></tr></thead>
     <tbody>{list.data.items.map(x=><tr key={x.id} className={sel===x.id?styles.sel:undefined}>
      <td className={styles.mono}>{x.snapshotId}</td><td>{enumLabel(lang,"kind",x.kind)}</td><td className={styles.mono}>{x.walStart} → {x.walEnd}</td>
      <td><Badge tone={toneFor(x.status)}>{enumLabel(lang,"bk",x.status)}</Badge></td>
      <td>{fmtTime(lang,x.retentionUntil)}{x.expired&&<> <Badge tone="danger">{t("bk.expired")}</Badge></>}</td>
      <td><button type="button" className={`${styles.btn} ${styles.btnSecondary}`} aria-pressed={sel===x.id} onClick={()=>{setSel(x.id);setMsg(null);setFail(null);}}>{t("common.select")}</button></td>
     </tr>)}</tbody>
    </table></div>
    <ul className={styles.cards}>{list.data.items.map(x=><li key={x.id} className={styles.card}><strong className={styles.mono}>{x.snapshotId}</strong> <Badge tone={toneFor(x.status)}>{enumLabel(lang,"bk",x.status)}</Badge>
     <dl><dt>{t("bk.filterKind")}</dt><dd>{enumLabel(lang,"kind",x.kind)}</dd><dt>{t("bk.wal")}</dt><dd className={styles.mono}>{x.walStart} → {x.walEnd}</dd></dl>
     <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} aria-pressed={sel===x.id} onClick={()=>{setSel(x.id);setMsg(null);setFail(null);}}>{t("common.select")}</button></li>)}</ul>
    <Pager page={page} size={10} total={list.data.total} onPage={setPage}/>
   </>}
  </section>
  <section className={styles.panel} aria-labelledby="v-h" aria-live="polite">
   <h2 id="v-h">{t("bk.verification")}</h2>
   {!sel?<p className={styles.hint}>{t("bk.pick")}</p>:detail.phase==="loading"&&!b?<Loading/>:detail.phase==="failed"?<FailureView failure={detail.failure!} onRetry={detail.reload}/>:!b?<Empty/>:<>
    <dl className={styles.kv}><dt>{t("bk.digest")}</dt><dd className={styles.mono}>{b.digest}</dd><dt>{t("bk.key")}</dt><dd className={styles.mono}>{b.keyRef}</dd>
     <dt>{t("bk.objects")}</dt><dd>{b.objectRefs.length}</dd><dt>{t("common.status")}</dt><dd><Badge tone={toneFor(b.status)}>{enumLabel(lang,"bk",b.status)}</Badge> {t("bk.size",{n:b.sizeBytes})}</dd></dl>
    {(b.verification??[]).length>0?<ul className={styles.list}>{b.verification!.map(v=><li key={v.checkKey}><Badge tone={toneFor(v.result)}>{enumLabel(lang,"result",v.result)}</Badge> {enumLabel(lang,"check",v.checkKey)} <span className={styles.hint}>{v.evidenceRef}</span></li>)}</ul>:<p className={styles.hint}>{t("bk.noChecks")}</p>}
    {b.status==="RECORDED"&&<div className={styles.formGrid}>
     {CHECKS.map(c=><div key={c} className={styles.card}>
      <strong>{enumLabel(lang,"check",c)}</strong>
      <Field id={`bk-r-${c}`} label={t("rc.evidence")}><input id={`bk-r-${c}`} className={styles.input} maxLength={160} value={evid[c]??""} onChange={e=>setEvid(v=>({...v,[c]:e.target.value}))}/></Field>
      <Field id={`bk-p-${c}`} label={t("common.status")}><select id={`bk-p-${c}`} className={styles.input} value={res[c]} onChange={e=>setRes(v=>({...v,[c]:e.target.value}))}><option value="PASS">{enumLabel(lang,"result","PASS")}</option><option value="FAIL">{enumLabel(lang,"result","FAIL")}</option></select></Field>
     </div>)}
     <Field id="bk-od" label={t("bk.observed")} wide><input id="bk-od" className={`${styles.input} ${styles.mono}`} value={observed} onChange={e=>setObserved(e.target.value.trim().toLowerCase())}/></Field>
    </div>}
    <div className={styles.actions}><ActionButton label={t("bk.verifyAction")} busy={busy} reason={verifyWhy} onClick={verify}/></div>
    <ActionError failure={fail} onReload={()=>{list.reload();detail.reload();}}/>
    {msg&&<Notice>{msg}</Notice>}
   </>}
  </section>
  <section className={styles.panel} aria-labelledby="w-h" aria-live="polite">
   <h2 id="w-h">{t("bk.walCheck")}</h2>
   <div className={styles.formGrid}>
    <Field id="bk-b" label={t("bk.baseBackup")}><select id="bk-b" className={styles.input} value={base} onChange={e=>{setBase(e.target.value);setCont(null);}}><option value="">{t("common.none")}</option>{(fulls.data?.items??[]).map(x=><option key={x.id} value={x.id}>{x.snapshotId}</option>)}</select></Field>
    <Field id="bk-t" label={t("bk.target")}><input id="bk-t" inputMode="numeric" className={styles.input} value={target} onChange={e=>{setTarget(e.target.value);setCont(null);}}/></Field>
   </div>
   <div className={styles.actions}><ActionButton label={t("bk.walAction")} tone="secondary" busy={busy} reason={walWhy} onClick={walCheck}/></div>
   {cont&&(cont.continuous?<Notice>{t("bk.continuous")}</Notice>:<p className={`${styles.banner} ${styles.bannerDanger}`} role="alert">{t("bk.gap",{a:cont.gapStart??"?",b:cont.gapEnd??"?"})}</p>)}
  </section>
 </>;
}
export default function Backups(){return <RlFrame active="backups" title="bk.title" intro="bk.intro"><Body/></RlFrame>;}
