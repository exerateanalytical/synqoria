"use client";
import {useState} from "react";
import {ActionButton,ActionError,Badge,Empty,Field,FailureView,FlFrame,Loading,Notice,Pager,fmtTime,styles,toneFor,useFl,useLoad} from "./ui";
import {call,newKey} from "../../lib/fleet/client";
import type {Failure,Page} from "../../lib/fleet/client";
import {enumLabel} from "../../lib/fleet/i18n";

type Tenant={id:string;tenantKey:string;name:string;region:string;residencyPolicy:string;status:string;activeCells:number;cells:number};
export type Cell={id:string;tenantId:string;tenantKey:string;tenantName:string;cellKey:string;environment:string;region:string;tier:string;capacityUnits:number;state:string;placementEpoch:number;releaseKey:string|null;
 schemaVersion:number;stateReason:string|null;version:number;health:string|null;healthObservedAt:string|null};
const STATES=["PROVISIONING","QUALIFIED","ACTIVE","DRAINING","FENCED","MIGRATING","RETIRED","FAILED"];
const MANUAL_TARGETS=["ACTIVE","DRAINING","FENCED","RETIRED","FAILED"];
const STALE_MS=5*60*1000;

function Body(){
 const {t,lang}=useFl();
 const [state,setState]=useState("");
 const [page,setPage]=useState(0);
 const [sel,setSel]=useState("");
 const [to,setTo]=useState("");
 const [reason,setReason]=useState("");
 const [fail,setFail]=useState<Failure|null>(null);
 const [msg,setMsg]=useState<string|null>(null);
 const [busy,setBusy]=useState(false);
 const tenants=useLoad<Page<Tenant>>(s=>call("GET","tenants",{query:{size:50},signal:s}),[]);
 const cells=useLoad<Page<Cell>>(s=>call("GET","cells",{query:{state,page,size:15},signal:s}),[state,page]);
 const cell=cells.data?.items.find(c=>c.id===sel);
 const why=!cell?t("disabled.select"):!to?t("disabled.input"):reason.trim().length<5?t("disabled.input"):null;
 const apply=async()=>{
  if(!cell)return;setBusy(true);setFail(null);setMsg(null);
  const r=await call("POST",`cells/${cell.id}/transition`,{body:{expectedVersion:cell.version,to,reason:reason.trim()},key:newKey()});
  setBusy(false);
  if(r.ok){setMsg(t("fl.transitioned"));setTo("");setReason("");cells.reload();tenants.reload();}else setFail(r);
 };
 const staleHealth=(c:Cell)=>!c.healthObservedAt||Date.parse(c.healthObservedAt)<Date.now()-STALE_MS;
 return <>
  <section className={styles.panel} aria-labelledby="tn-h">
   <h2 id="tn-h">{t("fl.tenants")}</h2>
   {tenants.phase==="loading"&&!tenants.data?<Loading/>:tenants.phase==="failed"||!tenants.data?<FailureView failure={tenants.failure!} onRetry={tenants.reload}/>:
    tenants.data.items.length===0?<Empty message="fl.noTenants" hint={false}/>:
    <ul className={styles.hub}>{tenants.data.items.map(x=><li key={x.id} className={styles.card}>
     <h3>{x.name}</h3><p className={styles.hint}><span className={styles.mono}>{x.tenantKey}</span> · {x.region}</p>
     <p>{t("fl.activeCells")}: <strong>{x.activeCells}</strong> / {x.cells} <Badge tone={toneFor(x.status)}>{x.status}</Badge></p>
    </li>)}</ul>}
  </section>
  <section className={styles.panel} aria-labelledby="ce-h">
   <h2 id="ce-h">{t("fl.cells")}</h2>
   <div className={styles.filters}>
    <Field id="fl-state" label={t("fl.filterState")}>
     <select id="fl-state" className={styles.input} value={state} onChange={e=>{setState(e.target.value);setPage(0);}}>
      <option value="">{t("common.any")}</option>{STATES.map(x=><option key={x} value={x}>{enumLabel(lang,"cell",x)}</option>)}
     </select>
    </Field>
    <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={()=>{cells.reload();tenants.reload();}}>{t("common.refresh")}</button>
   </div>
   {cells.phase==="loading"&&!cells.data?<Loading/>:cells.phase==="failed"||!cells.data?<FailureView failure={cells.failure!} onRetry={cells.reload}/>:
    cells.data.items.length===0?<Empty message="fl.none" hint={false}/>:<>
    <div className={styles.tableWrap}><table className={styles.table}>
     <thead><tr><th>{t("fl.cells")}</th><th>{t("fl.environment")}</th><th>{t("common.status")}</th><th>{t("fl.epoch")}</th><th>{t("fl.release")}</th><th>{t("fl.health")}</th><th>{t("common.actions")}</th></tr></thead>
     <tbody>{cells.data.items.map(c=><tr key={c.id} className={sel===c.id?styles.sel:undefined}>
      <td><strong>{c.tenantName}</strong><br/><span className={styles.mono}>{c.cellKey}</span> · {c.region} · {c.tier}</td><td>{c.environment}</td>
      <td><Badge tone={toneFor(c.state)}>{enumLabel(lang,"cell",c.state)}</Badge></td><td className={styles.mono}>{c.placementEpoch}</td>
      <td>{c.releaseKey??t("fl.noRelease")}</td>
      <td>{c.health?<Badge tone={staleHealth(c)?"warn":toneFor(c.health)}>{enumLabel(lang,"health",c.health)}</Badge>:<Badge tone="neutral">{enumLabel(lang,"health","UNKNOWN")}</Badge>}
       {c.healthObservedAt&&<span className={styles.hint} style={{display:"block"}}>{fmtTime(lang,c.healthObservedAt)}{staleHealth(c)?` · ${t("fl.stale")}`:""}</span>}</td>
      <td><button type="button" className={`${styles.btn} ${styles.btnSecondary}`} aria-pressed={sel===c.id} onClick={()=>{setSel(c.id);setMsg(null);setFail(null);}}>{t("common.select")}</button></td>
     </tr>)}</tbody>
    </table></div>
    <ul className={styles.cards}>{cells.data.items.map(c=><li key={c.id} className={styles.card}>
     <strong>{c.tenantName}</strong> <Badge tone={toneFor(c.state)}>{enumLabel(lang,"cell",c.state)}</Badge>
     <dl><dt>{t("fl.cells")}</dt><dd className={styles.mono}>{c.cellKey}</dd><dt>{t("fl.epoch")}</dt><dd>{c.placementEpoch}</dd><dt>{t("fl.release")}</dt><dd>{c.releaseKey??t("fl.noRelease")}</dd>
     <dt>{t("fl.health")}</dt><dd>{enumLabel(lang,"health",c.health??"UNKNOWN")}</dd></dl>
     <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} aria-pressed={sel===c.id} onClick={()=>{setSel(c.id);setMsg(null);setFail(null);}}>{t("common.select")}</button>
    </li>)}</ul>
    <Pager page={page} size={15} total={cells.data.total} onPage={setPage}/>
   </>}
  </section>
  <section className={styles.panel} aria-labelledby="tr-h">
   <h2 id="tr-h">{t("fl.transition")}{cell?`: ${cell.cellKey}`:""}</h2>
   {!cell?<p className={styles.hint}>{t("fl.pick")}</p>:<div className={styles.formGrid}>
    <Field id="fl-to" label={t("fl.to")}>
     <select id="fl-to" className={styles.input} value={to} onChange={e=>setTo(e.target.value)}>
      <option value="">{t("common.none")}</option>{MANUAL_TARGETS.map(x=><option key={x} value={x}>{enumLabel(lang,"cell",x)}</option>)}
     </select>
    </Field>
    <Field id="fl-reason" label={t("common.reason")}><input id="fl-reason" className={styles.input} maxLength={300} value={reason} onChange={e=>setReason(e.target.value)}/></Field>
   </div>}
   <div className={styles.actions}><ActionButton label={t("fl.transitionAction")} busy={busy} reason={why} onClick={apply}/></div>
   <ActionError failure={fail} onReload={()=>{cells.reload();}}/>
   {msg&&<Notice>{msg}</Notice>}
  </section>
 </>;
}
export default function Fleet(){return <FlFrame active="fleet" title="fl.title" intro="fl.intro"><Body/></FlFrame>;}
