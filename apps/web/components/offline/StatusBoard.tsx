"use client";
import {useState} from "react";
import {ActionButton,ActionError,Badge,Empty,Field,FailureView,Loading,Notice,OfFrame,fmtTime,styles,toneFor,useLoad,useOf} from "./ui";
import DevicePanel from "./DevicePanel";
import {call,newKey} from "../../lib/offline/client";
import type {Failure} from "../../lib/offline/client";
import {enumLabel} from "../../lib/offline/i18n";

type Dev={id:string;deviceKey:string;label:string;boundUser:string;status:string;sessionExpiresAt:string;queueLimit:number;lastSeq:number;lastSeenAt:string|null;version:number;openConflicts:number;received:number;stale:boolean;sessionExpired:boolean};
type Status={devices:Dev[];openConflicts:number;frozenRecords:number;openTransfers:number;serverTime:string};

function Body(){
 const {t,lang}=useOf();
 const s=useLoad<Status>(sig=>call<Status>("GET","status",{signal:sig}),[]);
 const [target,setTarget]=useState("");
 const [reason,setReason]=useState("");
 const [fail,setFail]=useState<Failure|null>(null);
 const [msg,setMsg]=useState<string|null>(null);
 const [busy,setBusy]=useState(false);
 if(s.phase==="loading"&&!s.data)return <Loading/>;
 if(s.phase==="failed"||!s.data)return <FailureView failure={s.failure!} onRetry={s.reload}/>;
 const d=s.data;
 const revoke=async()=>{
  const dev=d.devices.find(x=>x.id===target);if(!dev)return;
  setBusy(true);setFail(null);setMsg(null);
  const r=await call("POST",`devices/${dev.id}/revocation`,{body:{expectedVersion:dev.version,reason:reason.trim()},key:newKey()});
  setBusy(false);
  if(r.ok){setMsg(t("st.revoked"));setTarget("");setReason("");s.reload();}else setFail(r);
 };
 const picked=d.devices.find(x=>x.id===target);
 const why=!picked?t("disabled.select"):picked.status!=="ACTIVE"?t("disabled.status",{status:enumLabel(lang,"device",picked.status)}):reason.trim().length<5?t("disabled.input"):null;
 return <>
  <ul className={styles.hub}>
   <li className={styles.card}><h2>{t("st.openConflicts")}</h2><strong className={styles.mono}>{d.openConflicts}</strong></li>
   <li className={styles.card}><h2>{t("st.frozen")}</h2><strong className={styles.mono}>{d.frozenRecords}</strong></li>
   <li className={styles.card}><h2>{t("st.transfers")}</h2><strong className={styles.mono}>{d.openTransfers}</strong></li>
  </ul>
  <p className={styles.hint}>{t("st.localNote")}</p>
  <section className={styles.panel} aria-labelledby="dev-h">
   <h2 id="dev-h">{t("st.devices")}</h2>
   <div className={styles.actions}><button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={s.reload}>{t("common.refresh")}</button></div>
   {d.devices.length===0?<Empty message="st.noDevices" hint={false}/>:<>
    <div className={styles.tableWrap}><table className={styles.table}>
     <thead><tr><th>{t("common.device")}</th><th>{t("common.status")}</th><th>{t("st.lastSeen")}</th><th>{t("st.cursor")}</th><th>{t("st.received")}</th><th>{t("st.openConflicts")}</th><th>{t("st.session")}</th></tr></thead>
     <tbody>{d.devices.map(x=><tr key={x.id} className={target===x.id?styles.sel:undefined}>
      <td><button type="button" className={`${styles.btn} ${styles.btnSecondary}`} aria-pressed={target===x.id} onClick={()=>setTarget(x.id)}>{x.label}</button><br/><span className={styles.mono}>{x.deviceKey}</span></td>
      <td><Badge tone={toneFor(x.status)}>{enumLabel(lang,"device",x.status)}</Badge></td>
      <td>{x.lastSeenAt?fmtTime(lang,x.lastSeenAt):t("st.never")}{x.stale&&x.status==="ACTIVE"&&<> <Badge tone="warn">{t("st.stale")}</Badge></>}</td>
      <td className={styles.mono}>{x.lastSeq}</td><td>{x.received}</td><td>{x.openConflicts}</td>
      <td>{fmtTime(lang,x.sessionExpiresAt)}{x.sessionExpired&&<> <Badge tone="danger">{t("st.sessionExpired")}</Badge></>}</td>
     </tr>)}</tbody>
    </table></div>
    <ul className={styles.cards}>{d.devices.map(x=><li key={x.id} className={styles.card}>
     <strong>{x.label}</strong> <Badge tone={toneFor(x.status)}>{enumLabel(lang,"device",x.status)}</Badge>
     <dl><dt>{t("st.lastSeen")}</dt><dd>{x.lastSeenAt?fmtTime(lang,x.lastSeenAt):t("st.never")}</dd><dt>{t("st.openConflicts")}</dt><dd>{x.openConflicts}</dd>
     <dt>{t("st.session")}</dt><dd>{fmtTime(lang,x.sessionExpiresAt)}</dd></dl>
     <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} aria-pressed={target===x.id} onClick={()=>setTarget(x.id)}>{t("common.details")}</button>
    </li>)}</ul>
   </>}
  </section>
  <section className={styles.panel} aria-labelledby="rev-h">
   <h2 id="rev-h">{t("st.revoke")}{picked?`: ${picked.label}`:""}</h2>
   <Field id="rev-reason" label={t("st.revokeReason")}><input id="rev-reason" className={styles.input} maxLength={300} value={reason} onChange={e=>setReason(e.target.value)}/></Field>
   <div className={styles.actions}><ActionButton label={t("st.revoke")} tone="danger" busy={busy} reason={why} onClick={revoke}/></div>
   <ActionError failure={fail} onReload={s.reload}/>
   {msg&&<Notice>{msg}</Notice>}
  </section>
  <DevicePanel/>
 </>;
}
export default function StatusBoard(){return <OfFrame active="status" title="st.title" intro="st.intro"><Body/></OfFrame>;}
