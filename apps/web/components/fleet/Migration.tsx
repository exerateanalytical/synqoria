"use client";
import {useState} from "react";
import {ActionButton,ActionError,Badge,Empty,Field,FailureView,FlFrame,Loading,Notice,fmtTime,styles,toneFor,useFl,useLoad} from "./ui";
import {call,newKey} from "../../lib/fleet/client";
import type {Failure,Page} from "../../lib/fleet/client";
import {enumLabel} from "../../lib/fleet/i18n";
import type {Cell} from "./Fleet";

type Route={id:string;targetCell:string;targetCellKey:string;routeKey:string;placementEpoch:number;version:number;updatedAt:string};
type Mig={id:string;sourceCell:string;sourceKey:string;targetCell:string;targetKey:string;routeId:string;routeKey:string;routeEpoch:number;state:string;copyEvidence:string|null;catchupLagMs:number|null;
 fenceEvidence:string|null;switchEpoch:number|null;plannedBy:string;version:number;updatedAt:string};
const NEXT:Record<string,string>={PREPARED:"COPY",COPYING:"CATCH_UP",CAUGHT_UP:"FREEZE",FROZEN:"FENCE",FENCED:"VERIFY",VERIFIED:"SWITCH",SWITCHED:"OBSERVE",OBSERVING:"RETIRE"};
const HEX=/^[0-9a-f]{64}$/;

function Body(){
 const {t,lang}=useFl();
 const [routeId,setRouteId]=useState("");const [src,setSrc]=useState("");const [tgt,setTgt]=useState("");
 const [open,setOpen]=useState("");const [ev,setEv]=useState("");const [lag,setLag]=useState("");const [sd,setSd]=useState("");const [td,setTd]=useState("");const [epoch,setEpoch]=useState("");const [rb,setRb]=useState("");
 const [fail,setFail]=useState<Failure|null>(null);const [msg,setMsg]=useState<string|null>(null);const [busy,setBusy]=useState(false);
 const routes=useLoad<Page<Route>&{directoryVersion:number}>(s=>call("GET","routes",{query:{size:50},signal:s}),[]);
 const migs=useLoad<Page<Mig>>(s=>call("GET","migrations",{query:{size:20},signal:s}),[]);
 const cells=useLoad<Page<Cell>>(s=>call("GET","cells",{query:{size:100},signal:s}),[]);
 const m=migs.data?.items.find(x=>x.id===open);
 const act=async(p:ReturnType<typeof call>,done:string)=>{
  setBusy(true);setFail(null);setMsg(null);
  const r=await p;setBusy(false);
  if(r.ok){setMsg(done);routes.reload();migs.reload();cells.reload();}else setFail(r);
 };
 const route=routes.data?.items.find(r=>r.id===routeId);
 const planWhy=!routeId||!src||!tgt?t("disabled.select"):src===tgt?t("disabled.input"):null;
 const next=m?NEXT[m.state]:undefined;
 const stepWhy=!m?t("disabled.select"):!next?t("disabled.status",{status:enumLabel(lang,"mig",m.state)}):
  next==="COPY"&&!ev.trim()?t("disabled.input"):next==="FENCE"&&!ev.trim()?t("disabled.input"):next==="CATCH_UP"&&lag.trim()===""?t("disabled.input"):
  next==="VERIFY"&&(!HEX.test(sd)||!HEX.test(td))?t("disabled.input"):next==="SWITCH"&&!(Number(epoch)>=1)?t("disabled.input"):null;
 const body=()=>({expectedVersion:m!.version,step:next,evidence:ev.trim()||null,sourceDigest:sd||null,targetDigest:td||null,lagMs:lag.trim()===""?null:Number(lag),expectedEpoch:epoch.trim()===""?null:Number(epoch)});
 const final=m&&["RETIRED","ROLLED_BACK","FAILED"].includes(m.state);
 const rbWhy=!m?t("disabled.select"):m.state==="RETIRED"?t("mg.irreversible"):final?t("disabled.status",{status:enumLabel(lang,"mig",m.state)}):rb.trim().length<5?t("disabled.input"):null;
 return <>
  <section className={styles.panel} aria-labelledby="rt-h">
   <h2 id="rt-h">{t("mg.routes")}</h2>
   {routes.phase==="loading"&&!routes.data?<Loading/>:routes.phase==="failed"||!routes.data?<FailureView failure={routes.failure!} onRetry={routes.reload}/>:<>
    <p className={styles.hint}>{t("mg.directory")}: <strong className={styles.mono}>{routes.data.directoryVersion}</strong></p>
    {routes.data.items.length===0?<Empty message="mg.routeNone" hint={false}/>:
    <ul className={styles.list}>{routes.data.items.map(r=><li key={r.id} className={styles.card}><strong className={styles.mono}>{r.routeKey}</strong>
     <p className={styles.hint}>{t("mg.target")}: <span className={styles.mono}>{r.targetCellKey}</span> · {t("fl.epoch")} {r.placementEpoch} · {fmtTime(lang,r.updatedAt)}</p>
     <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} aria-pressed={routeId===r.id} onClick={()=>{setRouteId(r.id);setSrc(r.targetCell);setEpoch(String(r.placementEpoch));}}>{t("common.select")}</button></li>)}</ul>}
   </>}
  </section>
  <section className={styles.panel} aria-labelledby="pl-h">
   <h2 id="pl-h">{t("mg.plan")}{route?`: ${route.routeKey}`:""}</h2>
   <div className={styles.formGrid}>
    <Field id="mg-src" label={t("mg.source")}><select id="mg-src" className={styles.input} value={src} onChange={e=>setSrc(e.target.value)}><option value="">{t("common.none")}</option>{(cells.data?.items??[]).map(c=><option key={c.id} value={c.id}>{c.cellKey} · {enumLabel(lang,"cell",c.state)}</option>)}</select></Field>
    <Field id="mg-tgt" label={t("mg.targetCell")}><select id="mg-tgt" className={styles.input} value={tgt} onChange={e=>setTgt(e.target.value)}><option value="">{t("common.none")}</option>{(cells.data?.items??[]).filter(c=>c.state==="QUALIFIED").map(c=><option key={c.id} value={c.id}>{c.cellKey} · {c.region}</option>)}</select></Field>
   </div>
   <div className={styles.actions}><ActionButton label={t("mg.planAction")} busy={busy} reason={planWhy} onClick={()=>act(call("POST","migrations",{body:{sourceCell:src,targetCell:tgt,routeId},key:newKey()}),t("mg.planned"))}/></div>
  </section>
  <section className={styles.panel} aria-labelledby="ml-h">
   <h2 id="ml-h">{t("mg.list")}</h2>
   {migs.phase==="loading"&&!migs.data?<Loading/>:migs.phase==="failed"||!migs.data?<FailureView failure={migs.failure!} onRetry={migs.reload}/>:
    migs.data.items.length===0?<Empty message="mg.none" hint={false}/>:
    <ul className={styles.list}>{migs.data.items.map(x=><li key={x.id} className={styles.card}>
     <strong className={styles.mono}>{x.sourceKey} → {x.targetKey}</strong> <Badge tone={toneFor(x.state)}>{enumLabel(lang,"mig",x.state)}</Badge>
     <p className={styles.hint}>{x.routeKey} · {t("fl.epoch")} {x.routeEpoch}{x.switchEpoch!==null?` (→ ${x.switchEpoch})`:""}{NEXT[x.state]?` · ${t("mg.next")}: ${enumLabel(lang,"step",NEXT[x.state])}`:""}</p>
     <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} aria-pressed={open===x.id} onClick={()=>{setOpen(x.id);setEpoch(String(x.routeEpoch));setMsg(null);setFail(null);}}>{t("common.select")}</button>
    </li>)}</ul>}
  </section>
  <section className={styles.panel} aria-labelledby="ms-h">
   <h2 id="ms-h">{t("mg.next")}{m&&next?`: ${enumLabel(lang,"step",next)}`:""}</h2>
   {!m?<p className={styles.hint}>{t("mg.pick")}</p>:<>
    {next==="SWITCH"&&<p className={styles.hint}>{t("mg.switchNote")}</p>}
    <div className={styles.formGrid}>
     <Field id="mg-ev" label={t("mg.evidence")}><input id="mg-ev" className={styles.input} maxLength={160} value={ev} onChange={e=>setEv(e.target.value)}/></Field>
     <Field id="mg-lag" label={t("mg.lag")}><input id="mg-lag" inputMode="numeric" className={styles.input} value={lag} onChange={e=>setLag(e.target.value)}/></Field>
     <Field id="mg-sd" label={t("mg.sourceDigest")} wide><input id="mg-sd" className={`${styles.input} ${styles.mono}`} value={sd} onChange={e=>setSd(e.target.value.trim().toLowerCase())}/></Field>
     <Field id="mg-td" label={t("mg.targetDigest")} wide><input id="mg-td" className={`${styles.input} ${styles.mono}`} value={td} onChange={e=>setTd(e.target.value.trim().toLowerCase())}/></Field>
     <Field id="mg-ep" label={t("mg.epochSeen")}><input id="mg-ep" inputMode="numeric" className={styles.input} value={epoch} onChange={e=>setEpoch(e.target.value)}/></Field>
     <Field id="mg-rb" label={t("mg.rollbackReason")} wide><input id="mg-rb" className={styles.input} maxLength={300} value={rb} onChange={e=>setRb(e.target.value)}/></Field>
    </div>
    <div className={styles.actions}>
     <ActionButton label={next?enumLabel(lang,"step",next):t("mg.next")} busy={busy} reason={stepWhy} onClick={()=>act(call("POST",`migrations/${m.id}/steps`,{body:body(),key:newKey()}),t("mg.done"))}/>
     <ActionButton label={t("mg.rollback")} tone="danger" busy={busy} reason={rbWhy} onClick={()=>act(call("POST",`migrations/${m.id}/rollback`,{body:{expectedVersion:m.version,reason:rb.trim(),expectedEpoch:epoch.trim()===""?null:Number(epoch)},key:newKey()}),t("mg.done"))}/>
    </div>
   </>}
   <ActionError failure={fail} onReload={()=>{routes.reload();migs.reload();}}/>
   {msg&&<Notice>{msg}</Notice>}
  </section>
 </>;
}
export default function Migration(){return <FlFrame active="migration" title="mg.title" intro="mg.intro"><Body/></FlFrame>;}
