"use client";
import Link from "next/link";
import {useCallback,useEffect,useRef,useState} from "react";
import {ActionButton,Badge,Empty,Field,Notice,fmtTime,styles,toneFor,useOf} from "./ui";
import {bindDevice,forgetBinding,openDevice,readSession,savedBinding} from "../../lib/offline/device";
import type {Device} from "../../lib/offline/device";
import {errorKey,enumLabel} from "../../lib/offline/i18n";
import type {Key} from "../../lib/offline/i18n";
import type {DeviceBinding,QueueEntry,Session,SyncReport} from "../../../../packages/offline-device-sdk/src/index.ts";

const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

/** Operational view of THIS device's offline queue, mounted on the existing synchronization-status screen (no new route). */
export default function DevicePanel(){
 const {t,lang,online}=useOf();
 const [session,setSession]=useState<Session|null>(null);
 const [binding,setBinding]=useState<DeviceBinding|null>(null);
 const [deviceId,setDeviceId]=useState("");
 const [device,setDevice]=useState<Device|null>(null);
 const [entries,setEntries]=useState<QueueEntry[]>([]);
 const [unlocked,setUnlocked]=useState(false);
 const [report,setReport]=useState<SyncReport|null>(null);
 const [suspended,setSuspended]=useState<string|null>(null);
 const [msg,setMsg]=useState<string|null>(null);
 const [err,setErr]=useState<string|null>(null);
 const [confirmWipe,setConfirmWipe]=useState(false);
 const [busy,setBusy]=useState(false);
 const sessionRef=useRef<Session|null>(null);
 const msgFor=(code:string)=>{const k=errorKey(code);return k==="err.generic"?t("err.generic",{code}):t(k);};

 const refresh=useCallback(async(d:Device|null)=>{
  if(!d)return;
  setSuspended(await d.engine.suspendedReason());
  try{setEntries(await d.queue.list());setUnlocked(d.km.unlocked);}catch{setEntries([]);setUnlocked(false);}
 },[]);
 useEffect(()=>{
  let live=true;
  (async()=>{
   const s=await readSession();if(!live)return;
   setSession(s);sessionRef.current=s;
   const b=savedBinding();setBinding(b);
   if(b){const d=await openDevice(b,()=>sessionRef.current);if(!live)return;setDevice(d);await refresh(d);}
  })();
  return()=>{live=false;};
 },[refresh]);

 const run=async(fn:()=>Promise<void>)=>{setBusy(true);setErr(null);setMsg(null);try{await fn();}catch(e){setErr((e as Error).message);}finally{setBusy(false);}};
 const bind=()=>run(async()=>{
  if(!session)throw new Error("no_session");
  const r=await bindDevice(deviceId.trim(),session);
  if(!r.ok)throw new Error(r.code);
  setBinding(r.binding);const d=await openDevice(r.binding,()=>sessionRef.current);setDevice(d);await refresh(d);setMsg(t("dv.bound"));
 });
 const unlock=()=>run(async()=>{if(!device)return;await device.engine.resume();await device.engine.refreshReference();device.scheduler.start();await refresh(device);});    // automatic sync starts only after a verified unlock
 const refreshRef=()=>run(async()=>{if(!device)return;const ok=await device.engine.refreshReference();if(!ok)throw new Error("fleet_unavailable");setMsg(t("dv.refreshed"));});
 const sync=()=>run(async()=>{if(!device)return;const r=await device.engine.drain();setReport(r);if(r.status==="SUSPENDED"&&r.reason)setErr(r.reason);await refresh(device);});
 const lock=()=>run(async()=>{if(!device)return;device.scheduler.stop();await device.lock();await refresh(device);setMsg(t("dv.locknote"));});
 const wipe=()=>run(async()=>{if(!device)return;device.scheduler.stop();await device.wipe();forgetBinding();setBinding(null);setDevice(null);setEntries([]);setConfirmWipe(false);setMsg(t("dv.wiped"));});
 const exportMeta=()=>run(async()=>{
  if(!device)return;
  const blob=new Blob([JSON.stringify(await device.exportMetadata(),null,1)],{type:"application/json"});
  const url=URL.createObjectURL(blob);const a=document.createElement("a");a.href=url;a.download="queue-metadata.json";document.body.appendChild(a);a.click();a.remove();
  setTimeout(()=>URL.revokeObjectURL(url),1000);setMsg(t("dv.exported"));
 });
 useEffect(()=>{
  if(!device)return;
  const onOnline=()=>device.scheduler.nudge();
  window.addEventListener("online",onOnline);
  return()=>{window.removeEventListener("online",onOnline);device.scheduler.stop();};
 },[device]);
 const noDevice=!device?t("disabled.select"):null;

 return <section className={styles.panel} aria-labelledby="dv-h">
  <h2 id="dv-h">{t("dv.title")}</h2>
  <p className={styles.hint}>{t("dv.intro")}</p>
  {!online&&<Notice>{t("dv.offline")}</Notice>}
  <h3>{t("dv.session")}</h3>
  {session?<dl className={styles.kv}><dt>{t("dv.user")}</dt><dd className={styles.mono}>{session.userId.slice(0,8)}</dd><dt>{t("dv.scope")}</dt><dd className={styles.mono}>{session.scope.split("/").map(x=>x.slice(0,8)).join(" / ")}</dd>
   <dt>{t("dv.expires")}</dt><dd>{fmtTime(lang,new Date(session.expiresAt).toISOString())}</dd></dl>:<p className={styles.hint}>{t("dv.noSession")}</p>}
  <h3>{t("dv.binding")}</h3>
  {binding?<p><Badge tone="ok">{t("dv.bound")}</Badge> <span className={styles.mono}>{binding.deviceId.slice(0,8)}</span></p>:<p className={styles.hint}>{t("dv.notBound")}</p>}
  {!binding&&<><Field id="dv-id" label={t("dv.deviceId")}><input id="dv-id" className={`${styles.input} ${styles.mono}`} value={deviceId} onChange={e=>setDeviceId(e.target.value.trim())}/></Field>
   <div className={styles.actions}><ActionButton label={t("dv.verify")} busy={busy} reason={!session?t("dv.noSession"):!UUID.test(deviceId)?t("disabled.input"):null} onClick={bind}/></div></>}
  {suspended&&<p className={`${styles.banner} ${styles.bannerWarn}`} role="status">{t("dv.suspended",{reason:suspended})}</p>}
  {device&&<p role="status">{unlocked?t("dv.unlocked"):t("dv.locked")}</p>}
  <div className={styles.actions}>
   <ActionButton label={t("dv.unlock")} tone="secondary" busy={busy} reason={noDevice??(unlocked?t("dv.unlocked"):!session?t("dv.noSession"):null)} onClick={unlock}/>
   <ActionButton label={t("dv.refresh")} tone="secondary" busy={busy} reason={noDevice??(unlocked?null:t("dv.locked"))} onClick={refreshRef}/>
   <ActionButton label={t("dv.syncNow")} busy={busy} reason={noDevice??(unlocked?null:t("dv.locked"))} onClick={sync}/>
   <ActionButton label={t("dv.lock")} tone="secondary" busy={busy} reason={noDevice??(unlocked?null:t("dv.locked"))} onClick={lock}/>
   <ActionButton label={t("dv.export")} tone="secondary" busy={busy} reason={noDevice??(unlocked?null:t("dv.locked"))} onClick={exportMeta}/>
  </div>
  {report&&<p role="status">{t("dv.report",{sent:report.sent,applied:report.applied,duplicate:report.duplicate,conflicted:report.conflicted,denied:report.denied,expired:report.expired,requeued:report.requeued})}{report.delayMs?` ${t("dv.retry",{s:Math.ceil(report.delayMs/1000)})}`:""}</p>}
  <h3>{t("dv.queue")}</h3>
  {entries.length===0?<Empty message="dv.empty" hint={false}/>:<>
   <div className={styles.tableWrap}><table className={styles.table}>
    <thead><tr><th>#</th><th>{t("dv.operation")}</th><th>{t("common.status")}</th><th>{t("q.expires")}</th></tr></thead>
    <tbody>{entries.map(e=><tr key={e.seq}><td className={styles.mono}>{e.seq}</td><td className={styles.mono}>{e.operationType}</td>
     <td><Badge tone={toneFor(e.state==="CONFLICTED"?"CONFLICT":e.state==="DENIED"?"REJECTED":e.state)}>{enumLabel(lang,"local",e.state)}</Badge>{e.state==="CONFLICTED"&&<Link className={styles.link} href="/workforce/offline/conflicts">{t("nav.conflicts")}</Link>}{e.code&&<span className={styles.hint} style={{display:"block"}}>{t(`code.${e.code}` as Key)!==`code.${e.code}`?t(`code.${e.code}` as Key):e.code}</span>}</td>
     <td>{fmtTime(lang,e.expiresAt)}</td></tr>)}</tbody>
   </table></div>
   <ul className={styles.cards}>{entries.map(e=><li key={e.seq} className={styles.card}><strong className={styles.mono}>#{e.seq} {e.operationType}</strong> <Badge tone={toneFor(e.state==="CONFLICTED"?"CONFLICT":e.state==="DENIED"?"REJECTED":e.state)}>{enumLabel(lang,"local",e.state)}</Badge></li>)}</ul>
  </>}
  <h3>{t("dv.wipe")}</h3>
  <p className={styles.hint}>{t("dv.wipeWarn")}</p>
  <label className={styles.checkRow}><input type="checkbox" checked={confirmWipe} onChange={e=>setConfirmWipe(e.target.checked)}/> {t("dv.wipeWarn")}</label>
  <div className={styles.actions}><ActionButton label={t("dv.wipe")} tone="danger" busy={busy} reason={noDevice??(confirmWipe?null:t("disabled.input"))} onClick={wipe}/></div>
  {err&&<p className={`${styles.banner} ${styles.bannerDanger}`} role="alert">{msgFor(err)}</p>}
  {msg&&<Notice>{msg}</Notice>}
 </section>;
}
