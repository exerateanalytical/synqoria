"use client";
import {useState} from "react";
import {ActionButton,ActionError,Badge,Empty,Field,FailureView,FlFrame,Loading,Notice,fmtTime,styles,toneFor,useFl,useLoad} from "./ui";
import {call,newKey} from "../../lib/fleet/client";
import type {Failure,Page} from "../../lib/fleet/client";
import {enumLabel} from "../../lib/fleet/i18n";
import type {Cell} from "./Fleet";

type Manifest={id:string;releaseKey:string;artifactDigest:string;minSchema:number;maxSchema:number;status:string;createdBy:string;version:number};
type Roll={id:string;releaseKey:string;artifactDigest:string;status:string;currentStage:number;stageCount:number;stopReason:string|null;version:number;
 cells?:{targetCell:string;cellKey:string;schemaVersion:number;stageNo:number;status:string;evidenceRef:string|null;deployedAt:string|null;health:string|null;healthObservedAt:string|null}[]};
const HEX=/^[0-9a-f]{64}$/;

function Body(){
 const {t,lang}=useFl();
 const [key,setKey]=useState("");const [digest,setDigest]=useState("");const [minS,setMinS]=useState("1");const [maxS,setMaxS]=useState("1");
 const [manifestId,setManifestId]=useState("");const [canary,setCanary]=useState<string[]>([]);const [rest,setRest]=useState<string[]>([]);
 const [open,setOpen]=useState("");const [cellSel,setCellSel]=useState("");const [evRef,setEvRef]=useState("");const [evDigest,setEvDigest]=useState("");const [stopWhy,setStopWhy]=useState("");
 const [fail,setFail]=useState<Failure|null>(null);const [msg,setMsg]=useState<string|null>(null);const [busy,setBusy]=useState(false);
 const manifests=useLoad<Page<Manifest>>(s=>call("GET","releases",{query:{size:50},signal:s}),[]);
 const rollouts=useLoad<Page<Roll>>(s=>call("GET","rollouts",{query:{size:20},signal:s}),[]);
 const cells=useLoad<Page<Cell>>(s=>call("GET","cells",{query:{state:"ACTIVE",size:100},signal:s}),[]);
 const detail=useLoad<Roll|null>(s=>open?call<Roll>("GET",`rollouts/${open}`,{signal:s}):Promise.resolve({ok:true as const,status:200,data:null}),[open]);
 const act=async(p:ReturnType<typeof call>,done?:string)=>{
  setBusy(true);setFail(null);setMsg(null);
  const r=await p;setBusy(false);
  if(r.ok){if(done)setMsg(done);manifests.reload();rollouts.reload();detail.reload();}else setFail(r);
 };
 const toggle=(list:string[],set:(v:string[])=>void,id:string)=>set(list.includes(id)?list.filter(x=>x!==id):[...list,id]);
 const createWhy=!/^[A-Za-z0-9][A-Za-z0-9._-]{1,39}$/.test(key)||!HEX.test(digest)||!(Number(minS)>=1)||!(Number(maxS)>=Number(minS))?t("disabled.input"):null;
 const startWhy=!manifestId?t("disabled.select"):canary.length===0?t("disabled.input"):canary.some(c=>rest.includes(c))?t("disabled.input"):null;
 const ro=detail.data;
 const live=ro&&["CANARY","PROGRESSING"].includes(ro.status);
 const rcell=ro?.cells?.find(c=>c.targetCell===cellSel);
 const approved=(manifests.data?.items??[]).filter(m=>m.status==="APPROVED");
 const stageOpen=(ro?.cells??[]).filter(c=>c.stageNo===ro?.currentStage);
 const stageIncomplete=stageOpen.some(c=>c.status!=="DEPLOYED");
 return <>
  <section className={styles.panel} aria-labelledby="mf-h">
   <h2 id="mf-h">{t("rl.manifests")}</h2>
   <div className={styles.formGrid}>
    <Field id="rl-key" label={t("rl.key")}><input id="rl-key" className={styles.input} value={key} onChange={e=>setKey(e.target.value)}/></Field>
    <Field id="rl-dg" label={t("rl.digest")} wide><input id="rl-dg" className={`${styles.input} ${styles.mono}`} value={digest} onChange={e=>setDigest(e.target.value.trim().toLowerCase())}/></Field>
    <Field id="rl-mn" label={`${t("rl.schemas")} (min)`}><input id="rl-mn" inputMode="numeric" className={styles.input} value={minS} onChange={e=>setMinS(e.target.value)}/></Field>
    <Field id="rl-mx" label={`${t("rl.schemas")} (max)`}><input id="rl-mx" inputMode="numeric" className={styles.input} value={maxS} onChange={e=>setMaxS(e.target.value)}/></Field>
   </div>
   <div className={styles.actions}><ActionButton label={t("rl.create")} busy={busy} reason={createWhy} onClick={()=>act(call("POST","releases",{body:{releaseKey:key,artifactDigest:digest,minSchema:Number(minS),maxSchema:Number(maxS)},key:newKey()}),t("rl.created"))}/></div>
   {manifests.phase==="loading"&&!manifests.data?<Loading/>:manifests.phase==="failed"||!manifests.data?<FailureView failure={manifests.failure!} onRetry={manifests.reload}/>:
    manifests.data.items.length===0?<Empty message="rl.manifestNone" hint={false}/>:
    <ul className={styles.list}>{manifests.data.items.map(m=><li key={m.id} className={styles.card}>
     <strong>{m.releaseKey}</strong> <Badge tone={toneFor(m.status)}>{enumLabel(lang,"manifest",m.status)}</Badge>
     <p className={styles.hint}>{t("rl.schemas")}: {m.minSchema}–{m.maxSchema} · <span className={styles.mono}>{m.artifactDigest.slice(0,16)}…</span></p>
     <div className={styles.actions}>
      <ActionButton label={t("rl.decide")} busy={busy} reason={m.status!=="DRAFT"?t("disabled.status",{status:enumLabel(lang,"manifest",m.status)}):null} onClick={()=>act(call("POST",`releases/${m.id}/decision`,{body:{expectedVersion:m.version,approve:true},key:newKey()}))}/>
      <ActionButton label={t("rl.rejectIt")} tone="danger" busy={busy} reason={m.status!=="DRAFT"?t("disabled.status",{status:enumLabel(lang,"manifest",m.status)}):null} onClick={()=>act(call("POST",`releases/${m.id}/decision`,{body:{expectedVersion:m.version,approve:false},key:newKey()}))}/>
     </div>
    </li>)}</ul>}
  </section>
  <section className={styles.panel} aria-labelledby="st-h">
   <h2 id="st-h">{t("rl.start")}</h2>
   <Field id="rl-man" label={t("rl.manifests")}><select id="rl-man" className={styles.input} value={manifestId} onChange={e=>setManifestId(e.target.value)}>
    <option value="">{t("rl.pickManifest")}</option>{approved.map(m=><option key={m.id} value={m.id}>{m.releaseKey}</option>)}</select></Field>
   <p className={styles.hint}>{t("rl.cellsHelp")}</p>
   <div className={styles.cols}>
    <fieldset className={styles.fieldset}><legend>{t("rl.canary")}</legend>{(cells.data?.items??[]).map(c=><label key={c.id} className={styles.checkRow}><input type="checkbox" checked={canary.includes(c.id)} onChange={()=>toggle(canary,setCanary,c.id)}/> {c.tenantName} · {c.cellKey} · schema {c.schemaVersion}</label>)}</fieldset>
    <fieldset className={styles.fieldset}><legend>{t("rl.rest")}</legend>{(cells.data?.items??[]).map(c=><label key={c.id} className={styles.checkRow}><input type="checkbox" checked={rest.includes(c.id)} onChange={()=>toggle(rest,setRest,c.id)}/> {c.tenantName} · {c.cellKey}</label>)}</fieldset>
   </div>
   <div className={styles.actions}><ActionButton label={t("rl.start")} busy={busy} reason={startWhy} onClick={()=>act(call("POST","rollouts",{body:{manifestId,stages:rest.length?[canary,rest]:[canary]},key:newKey()}),t("rl.started"))}/></div>
   <ActionError failure={fail} onReload={()=>{manifests.reload();rollouts.reload();detail.reload();}}/>
   {msg&&<Notice>{msg}</Notice>}
  </section>
  <section className={styles.panel} aria-labelledby="ro-h">
   <h2 id="ro-h">{t("rl.rollouts")}</h2>
   {rollouts.phase==="loading"&&!rollouts.data?<Loading/>:rollouts.phase==="failed"||!rollouts.data?<FailureView failure={rollouts.failure!} onRetry={rollouts.reload}/>:
    rollouts.data.items.length===0?<Empty message="rl.rolloutNone" hint={false}/>:
    <ul className={styles.list}>{rollouts.data.items.map(r=><li key={r.id} className={styles.card}>
     <strong>{r.releaseKey}</strong> <Badge tone={toneFor(r.status)}>{enumLabel(lang,"rollout",r.status)}</Badge>
     <p className={styles.hint}>{t("rl.currentStage")}: {r.currentStage+1}/{r.stageCount}{r.stopReason?` · ${t("rl.autoStop")} ${r.stopReason}`:""}</p>
     <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} aria-pressed={open===r.id} onClick={()=>{setOpen(r.id);setCellSel("");}}>{t("common.details")}</button>
    </li>)}</ul>}
  </section>
  <section className={styles.panel} aria-labelledby="rd-h" aria-live="polite">
   <h2 id="rd-h">{t("common.details")}</h2>
   {!open?<p className={styles.hint}>{t("rl.pickRollout")}</p>:detail.phase==="loading"&&!ro?<Loading/>:detail.phase==="failed"?<FailureView failure={detail.failure!} onRetry={detail.reload}/>:!ro?<Empty/>:<>
    <div className={styles.tableWrap}><table className={styles.table}>
     <thead><tr><th>{t("fl.cells")}</th><th>{t("rl.stage")}</th><th>{t("common.status")}</th><th>{t("fl.health")}</th><th>{t("common.actions")}</th></tr></thead>
     <tbody>{(ro.cells??[]).map(c=><tr key={c.targetCell} className={cellSel===c.targetCell?styles.sel:undefined}>
      <td className={styles.mono}>{c.cellKey}</td><td>{c.stageNo+1}</td><td><Badge tone={toneFor(c.status)}>{enumLabel(lang,"dep",c.status)}</Badge>{c.deployedAt&&<span className={styles.hint} style={{display:"block"}}>{fmtTime(lang,c.deployedAt)}</span>}</td>
      <td>{enumLabel(lang,"health",c.health??"UNKNOWN")}</td>
      <td><button type="button" className={`${styles.btn} ${styles.btnSecondary}`} aria-pressed={cellSel===c.targetCell} onClick={()=>setCellSel(c.targetCell)}>{t("common.select")}</button></td>
     </tr>)}</tbody>
    </table></div>
    <ul className={styles.cards}>{(ro.cells??[]).map(c=><li key={c.targetCell} className={styles.card}><strong className={styles.mono}>{c.cellKey}</strong> <Badge tone={toneFor(c.status)}>{enumLabel(lang,"dep",c.status)}</Badge>
     <dl><dt>{t("rl.stage")}</dt><dd>{c.stageNo+1}</dd><dt>{t("fl.health")}</dt><dd>{enumLabel(lang,"health",c.health??"UNKNOWN")}</dd></dl>
     <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} aria-pressed={cellSel===c.targetCell} onClick={()=>setCellSel(c.targetCell)}>{t("common.select")}</button></li>)}</ul>
    <div className={styles.formGrid}>
     <Field id="rl-er" label={t("rl.evidenceRef")}><input id="rl-er" className={styles.input} maxLength={160} value={evRef} onChange={e=>setEvRef(e.target.value)}/></Field>
     <Field id="rl-ed" label={t("rl.evidenceDigest")} wide><input id="rl-ed" className={`${styles.input} ${styles.mono}`} value={evDigest} onChange={e=>setEvDigest(e.target.value.trim().toLowerCase())}/></Field>
     <Field id="rl-sr" label={t("rl.stopReason")} wide><input id="rl-sr" className={styles.input} maxLength={300} value={stopWhy} onChange={e=>setStopWhy(e.target.value)}/></Field>
    </div>
    <div className={styles.actions}>
     <ActionButton label={t("rl.deployed")} busy={busy} reason={!live?t("disabled.status",{status:enumLabel(lang,"rollout",ro.status)}):!rcell?t("disabled.select"):!evRef.trim()||!HEX.test(evDigest)?t("disabled.input"):null} onClick={()=>rcell&&act(call("POST",`rollouts/${ro.id}/cells/${rcell.targetCell}/evidence`,{body:{status:"DEPLOYED",evidenceRef:evRef.trim(),artifactDigest:evDigest},key:newKey()}))}/>
     <ActionButton label={t("rl.failedDeploy")} tone="danger" busy={busy} reason={!live?t("disabled.status",{status:enumLabel(lang,"rollout",ro.status)}):!rcell?t("disabled.select"):!evRef.trim()||!HEX.test(evDigest)?t("disabled.input"):null} onClick={()=>rcell&&act(call("POST",`rollouts/${ro.id}/cells/${rcell.targetCell}/evidence`,{body:{status:"FAILED",evidenceRef:evRef.trim(),artifactDigest:evDigest},key:newKey()}))}/>
     <ActionButton label={t("rl.advance")} tone="secondary" busy={busy} reason={!live?t("disabled.status",{status:enumLabel(lang,"rollout",ro.status)}):stageIncomplete?t("err.stage_incomplete"):null} onClick={()=>act(call("POST",`rollouts/${ro.id}/advance`,{body:{expectedVersion:ro.version},key:newKey()}))}/>
     <ActionButton label={t("rl.stop")} tone="danger" busy={busy} reason={!live?t("disabled.status",{status:enumLabel(lang,"rollout",ro.status)}):stopWhy.trim().length<5?t("disabled.input"):null} onClick={()=>act(call("POST",`rollouts/${ro.id}/stop`,{body:{expectedVersion:ro.version,reason:stopWhy.trim()},key:newKey()}))}/>
    </div>
   </>}
  </section>
 </>;
}
export default function Releases(){return <FlFrame active="releases" title="rl.title" intro="rl.intro"><Body/></FlFrame>;}
