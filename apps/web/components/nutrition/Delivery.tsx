"use client";
import {useRef,useState} from "react";
import {call,newKey} from "../../lib/nutrition/client";
import type {AwaitingMeal,Failure,Incident,Page,Tray} from "../../lib/nutrition/client";
import {enumLabel,safetyLabel} from "../../lib/nutrition/i18n";
import {ActionButton,ActionError,Badge,Empty,FailureView,Field,fmtTime,Loading,Notice,NutritionFrame,Pager,styles,toneFor,useLoad,useNutrition} from "./ui";

const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
type Tab="trays"|"load"|"incidents";

function Inner(){
 const {t,lang}=useNutrition();
 const [tab,setTab]=useState<Tab>("trays");
 const [ward,setWard]=useState("");
 const [status,setStatus]=useState("");
 const [page,setPage]=useState(0);
 const [selected,setSelected]=useState<string|null>(null);
 const [failure,setFailure]=useState<Failure|null>(null);
 const [done,setDone]=useState<string|null>(null);
 const [busy,setBusy]=useState(false);
 const [load,setLoad]=useState({meal:"",code:""});
 const [scan,setScan]=useState({ref:"",method:"WRISTBAND_SCAN"});
 const [exc,setExc]=useState({type:"LATE",severity:"LOW",description:""});
 const [incStatus,setIncStatus]=useState("");
 const [incPage,setIncPage]=useState(0);
 const [incForm,setIncForm]=useState<Record<string,string>>({});
 const loadKey=useRef(newKey());

 const trays=useLoad<Page<Tray>>(signal=>call("GET","trays",{signal,query:{ward:ward.trim().toUpperCase()||undefined,status:status||undefined,page,size:25}}),[ward,status,page]);
 const awaiting=useLoad<AwaitingMeal[]>(signal=>call("GET","trays/awaiting",{signal,query:{ward:ward.trim().toUpperCase()||undefined}}),[ward]);
 const incidents=useLoad<Page<Incident>>(signal=>call("GET","incidents",{signal,query:{status:incStatus||undefined,page:incPage,size:25}}),[incStatus,incPage]);
 const detail=useLoad<Tray>(signal=>selected?call("GET",`trays/${selected}`,{signal}):Promise.resolve({ok:false,kind:"notfound",status:0,code:"none",details:[]} as Failure),[selected]);
 const tray=selected?detail.data:undefined;

 const refreshAll=()=>{trays.reload();awaiting.reload();incidents.reload();if(selected)detail.reload();};
 async function run<T>(fn:()=>Promise<import("../../lib/nutrition/client").Result<T>>,after?:(d:T)=>void){
  if(busy)return;
  setBusy(true);setFailure(null);setDone(null);
  const r=await fn();
  setBusy(false);
  if(!r.ok){setFailure(r);refreshAll();return;}
  setDone(t("dl.done"));after?.(r.data);refreshAll();
 }
 const step=(path:string,extra?:object)=>run<Tray>(()=>call("POST",`trays/${tray!.id}/${path}`,{body:{expectedVersion:tray!.version,...extra},key:newKey()}));
 const why=(allowed:string[]):string|null=>!tray?t("disabled.select"):allowed.includes(tray.status)?null:t("disabled.status",{status:enumLabel(lang,"tray",tray.status)});
 const loadReason=!load.meal?t("disabled.select"):!/^[A-Z0-9_-]{3,40}$/.test(load.code.trim().toUpperCase())?t("err.malformed_request"):null;
 const deliverReason=why(["IN_TRANSIT"])??(!UUID.test(scan.ref.trim())?t("err.identity_confirmation_required"):null);
 const excReason=why(["LOADED","IN_TRANSIT","DELIVERED"])??(!exc.description.trim()?t("err.reason_required"):null);

 return <>
  <div role="tablist" aria-label={t("app.title")} className={styles.tabs}>
   {([["trays","dl.tab.trays"],["load","dl.tab.load"],["incidents","dl.tab.incidents"]] as const).map(([id,k])=>
    <button key={id} role="tab" id={`dl-tab-${id}`} aria-selected={tab===id} aria-controls={`dl-panel-${id}`} className={styles.tab} type="button" onClick={()=>setTab(id)}>{t(k)}</button>)}
   <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={refreshAll}>{t("common.refresh")}</button>
  </div>
  <ActionError failure={failure} onReload={()=>{refreshAll();setFailure(null);}}/>
  {done&&<Notice>{done}</Notice>}

  {tab==="trays"&&<div className={styles.cols} role="tabpanel" id="dl-panel-trays" aria-labelledby="dl-tab-trays">
   <section className={styles.panel} aria-label={t("dl.tab.trays")}>
    <div className={styles.filters}>
     <Field id="dl-ward" label={t("pr.ward")}><input id="dl-ward" className={styles.input} value={ward} maxLength={32} onChange={e=>{setWard(e.target.value);setPage(0);}}/></Field>
     <Field id="dl-status" label={t("dl.status")}><select id="dl-status" className={styles.input} value={status} onChange={e=>{setStatus(e.target.value);setPage(0);}}>
      <option value="">{t("common.any")}</option>{["LOADED","IN_TRANSIT","DELIVERED","ACKNOWLEDGED","EXCEPTION","RETURNED"].map(s=><option key={s} value={s}>{enumLabel(lang,"tray",s)}</option>)}</select></Field>
    </div>
    <div aria-busy={trays.phase==="loading"} style={{marginTop:12}}>
     {trays.phase==="loading"&&!trays.data&&<Loading/>}
     {trays.phase==="failed"&&trays.failure&&<FailureView failure={trays.failure} onRetry={trays.reload}/>}
     {trays.data&&trays.data.items.length===0&&trays.phase!=="failed"&&<Empty message="dl.noTrays"/>}
     {trays.data&&trays.data.items.length>0&&trays.phase!=="failed"&&<>
      <ul className={styles.list}>{trays.data.items.map(x=><li key={x.id}>
       <button type="button" className={styles.row} aria-pressed={selected===x.id} onClick={()=>{setSelected(x.id);setFailure(null);setDone(null);}}>
        <span><Badge tone={toneFor(x.status)}>{enumLabel(lang,"tray",x.status)}</Badge> <strong>{x.patientLabel}</strong> <span className={styles.hint}>{x.wardCode} · {x.bedCode}</span></span>
        <span className={styles.hint}><span className={styles.mono}>{x.trayCode}</span> · {enumLabel(lang,"meal",x.mealType)} · {enumLabel(lang,"diet",x.dietCategory)}</span>
       </button></li>)}</ul>
      <Pager page={trays.data.page} size={trays.data.size} total={trays.data.total} onPage={setPage}/></>}
    </div>
   </section>
   <section className={styles.panel} aria-label={t("dl.deliver")} aria-live="polite">
    {!selected&&<p>{t("dl.selectHint")}</p>}
    {selected&&detail.phase==="loading"&&!tray&&<Loading/>}
    {selected&&detail.phase==="failed"&&detail.failure&&<FailureView failure={detail.failure} onRetry={detail.reload}/>}
    {tray&&<>
     <h2><Badge tone={toneFor(tray.status)}>{enumLabel(lang,"tray",tray.status)}</Badge> {tray.patientLabel}</h2>
     {tray.mealStatus==="HELD"&&<div className={`${styles.banner} ${styles.bannerDanger}`} role="alert"><strong>{t("dl.safetyBlocked")}</strong>{tray.holdReason&&<p>{safetyLabel(lang,tray.holdReason)}</p>}</div>}
     <dl className={styles.kv}>
      <dt>{t("pr.ward")}</dt><dd>{tray.wardCode} · {tray.bedCode}</dd>
      <dt>{t("dl.trayCode")}</dt><dd className={styles.mono}>{tray.trayCode}</dd>
      <dt>{t("pr.meal")}</dt><dd>{enumLabel(lang,"meal",tray.mealType)} · {enumLabel(lang,"diet",tray.dietCategory)} · {enumLabel(lang,"texture",tray.texture)}</dd>
      <dt>{t("dl.responsible")}</dt><dd><span className={styles.mono}>{[tray.loadedBy,tray.dispatchedBy,tray.deliveredBy,tray.acknowledgedBy].filter(Boolean).join(" → ")}</span></dd>
      <dt>{t("dl.delivered")}</dt><dd>{fmtTime(lang,tray.deliveredAt)} {tray.identityMethod?`· ${enumLabel(lang,"identity",tray.identityMethod)}`:""}</dd>
      <dt>{t("common.version")}</dt><dd>{tray.version}</dd>
     </dl>
     <div className={styles.actions}>
      <ActionButton label={t("dl.dispatch")} onClick={()=>void step("dispatch")} reason={why(["LOADED"])} busy={busy}/>
      <ActionButton label={t("dl.acknowledge")} tone="secondary" onClick={()=>void step("acknowledge")} reason={why(["DELIVERED"])} busy={busy}/>
      <ActionButton label={t("dl.return")} tone="secondary" onClick={()=>void step("return")} reason={why(["LOADED","IN_TRANSIT","EXCEPTION"])} busy={busy}/>
     </div>
     <h3>{t("dl.deliver")}</h3>
     <p className={styles.actionNote}>{t("dl.identityWarning")}</p>
     <form onSubmit={e=>{e.preventDefault();void step("deliver",{scannedPatientRef:scan.ref.trim(),identityMethod:scan.method});}} noValidate>
      <div className={styles.formGrid}>
       <Field id="dl-scan" label={t("dl.scan")}><input id="dl-scan" className={`${styles.input} ${styles.mono}`} autoComplete="off" value={scan.ref} onChange={e=>setScan({...scan,ref:e.target.value})}/></Field>
       <Field id="dl-method" label={t("dl.method")}><select id="dl-method" className={styles.input} value={scan.method} onChange={e=>setScan({...scan,method:e.target.value})}>
        {["WRISTBAND_SCAN","DUAL_VERBAL"].map(m=><option key={m} value={m}>{enumLabel(lang,"identity",m)}</option>)}</select></Field>
      </div>
      <div className={styles.actions}><ActionButton label={t("dl.deliver")} onClick={()=>void step("deliver",{scannedPatientRef:scan.ref.trim(),identityMethod:scan.method})} reason={deliverReason} busy={busy}/></div>
     </form>
     <h3>{t("dl.exception")}</h3>
     <form onSubmit={e=>{e.preventDefault();void step("exceptions",{incidentType:exc.type,severity:exc.severity,description:exc.description.trim()});}} noValidate>
      <div className={styles.formGrid}>
       <Field id="dx-type" label={t("dl.incidentType")}><select id="dx-type" className={styles.input} value={exc.type} onChange={e=>setExc({...exc,type:e.target.value})}>
        {["REFUSED","LATE","MISSING_TRAY","OTHER","DIET_MISMATCH","ALLERGEN_RISK"].map(x=><option key={x} value={x}>{enumLabel(lang,"incidentType",x)}</option>)}</select></Field>
       <Field id="dx-sev" label={t("dl.severity")}><select id="dx-sev" className={styles.input} value={exc.severity} onChange={e=>setExc({...exc,severity:e.target.value})}>
        {["LOW","MEDIUM","HIGH","CRITICAL"].map(x=><option key={x} value={x}>{enumLabel(lang,"severity",x)}</option>)}</select></Field>
       <Field id="dx-desc" label={t("dl.description")} wide><textarea id="dx-desc" className={styles.input} maxLength={500} value={exc.description} onChange={e=>setExc({...exc,description:e.target.value})}/></Field>
      </div>
      <div className={styles.actions}><ActionButton label={t("dl.exception")} tone="danger" onClick={()=>void step("exceptions",{incidentType:exc.type,severity:exc.severity,description:exc.description.trim()})} reason={excReason} busy={busy}/></div>
     </form>
     {(tray.incidents??[]).length>0&&<><h3>{t("dl.incidents")}</h3><ul className={styles.list}>{tray.incidents!.map(i=><li key={i.id} className={styles.card}>
      <Badge tone={toneFor(i.severity)}>{enumLabel(lang,"severity",i.severity)}</Badge> <Badge tone={toneFor(i.status)}>{enumLabel(lang,"incident",i.status)}</Badge> {enumLabel(lang,"incidentType",i.incidentType)}
      <div className={styles.hint}>{i.description}</div></li>)}</ul></>}
     <h3>{t("dl.events")}</h3>
     <ol className={styles.timeline}>{(tray.events??[]).map(ev=><li key={ev.eventId}><strong>{ev.action}</strong> → {ev.toStatus}
      <div className={styles.hint}>{fmtTime(lang,ev.occurredAt)} · <span className={styles.mono}>{ev.actorId}</span></div></li>)}</ol>
    </>}
   </section>
  </div>}

  {tab==="load"&&<section className={styles.panel} role="tabpanel" id="dl-panel-load" aria-labelledby="dl-tab-load" aria-label={t("dl.tab.load")}>
   <h2>{t("dl.load")}</h2>
   {awaiting.phase==="loading"&&!awaiting.data&&<Loading/>}
   {awaiting.phase==="failed"&&awaiting.failure&&<FailureView failure={awaiting.failure} onRetry={awaiting.reload}/>}
   {awaiting.data&&awaiting.data.length===0&&awaiting.phase!=="failed"&&<Empty message="dl.noAwaiting" hint={false}/>}
   {awaiting.data&&awaiting.data.length>0&&<form onSubmit={e=>{e.preventDefault();}} noValidate>
    <div className={styles.formGrid}>
     <Field id="ld-meal" label={t("dl.meal")} wide><select id="ld-meal" className={styles.input} value={load.meal} onChange={e=>{setLoad({...load,meal:e.target.value});loadKey.current=newKey();}}>
      <option value="">—</option>{awaiting.data.map(m=><option key={m.id} value={m.id}>{m.patientLabel} · {m.wardCode} {m.bedCode} · {enumLabel(lang,"meal",m.mealType)} · {enumLabel(lang,"diet",m.dietCategory)}</option>)}</select></Field>
     <Field id="ld-code" label={t("dl.trayCode")}><input id="ld-code" className={`${styles.input} ${styles.mono}`} value={load.code} maxLength={40} onChange={e=>{setLoad({...load,code:e.target.value});loadKey.current=newKey();}}/></Field>
    </div>
    <div className={styles.actions}><ActionButton label={t("dl.load")} reason={loadReason} busy={busy}
     onClick={()=>void run<Tray>(()=>call("POST","trays",{key:loadKey.current,body:{mealServiceId:load.meal,trayCode:load.code.trim().toUpperCase()}}),d=>{loadKey.current=newKey();setLoad({meal:"",code:""});setSelected(d.id);setTab("trays");})}/></div>
   </form>}
  </section>}

  {tab==="incidents"&&<section className={styles.panel} role="tabpanel" id="dl-panel-incidents" aria-labelledby="dl-tab-incidents" aria-label={t("dl.tab.incidents")}>
   <div className={styles.filters}>
    <Field id="di-status" label={t("or.filterStatus")}><select id="di-status" className={styles.input} value={incStatus} onChange={e=>{setIncStatus(e.target.value);setIncPage(0);}}>
     <option value="">{t("common.any")}</option>{["OPEN","ESCALATED","RESOLVED"].map(s=><option key={s} value={s}>{enumLabel(lang,"incident",s)}</option>)}</select></Field>
   </div>
   <div aria-busy={incidents.phase==="loading"} style={{marginTop:12}}>
    {incidents.phase==="loading"&&!incidents.data&&<Loading/>}
    {incidents.phase==="failed"&&incidents.failure&&<FailureView failure={incidents.failure} onRetry={incidents.reload}/>}
    {incidents.data&&incidents.data.items.length===0&&incidents.phase!=="failed"&&<Empty message="dl.noIncidents" hint={false}/>}
    {incidents.data&&incidents.data.items.length>0&&incidents.phase!=="failed"&&<>
     <ul className={styles.list}>{incidents.data.items.map(i=>{
      const f=incForm[i.id]??"";
      const reasonEsc=i.status==="OPEN"?(/^[A-Z_]{3,40}$/.test(f.trim().toUpperCase())?null:t("err.malformed_request")):t("disabled.status",{status:enumLabel(lang,"incident",i.status)});
      const reasonRes=i.status==="RESOLVED"?t("disabled.status",{status:enumLabel(lang,"incident",i.status)}):(f.trim()?null:t("err.resolution_note_required"));
      return <li key={i.id} className={styles.card}>
       <Badge tone={toneFor(i.severity)}>{enumLabel(lang,"severity",i.severity)}</Badge> <Badge tone={toneFor(i.status)}>{enumLabel(lang,"incident",i.status)}</Badge> <strong>{enumLabel(lang,"incidentType",i.incidentType)}</strong>
       <div>{i.description}</div>
       <div className={styles.hint}>{fmtTime(lang,i.createdAt)}{i.escalatedToRole?` · ${i.escalatedToRole}`:""}</div>
       {i.status!=="RESOLVED"&&<div className={styles.formGrid} style={{marginTop:8}}>
        <Field id={`inc-${i.id}`} label={`${t("dl.escalateTo")} / ${t("dl.resolveNote")}`} wide><input id={`inc-${i.id}`} className={styles.input} maxLength={500} value={f} onChange={e=>setIncForm({...incForm,[i.id]:e.target.value})}/></Field>
       </div>}
       <div className={styles.actions}>
        <ActionButton label={t("dl.escalate")} tone="secondary" reason={reasonEsc} busy={busy}
         onClick={()=>void run(()=>call("POST",`incidents/${i.id}/escalate`,{body:{expectedVersion:i.version,toRole:f.trim().toUpperCase()},key:newKey()}))}/>
        <ActionButton label={t("dl.resolve")} reason={reasonRes} busy={busy}
         onClick={()=>void run(()=>call("POST",`incidents/${i.id}/resolve`,{body:{expectedVersion:i.version,note:f.trim()},key:newKey()}))}/>
       </div></li>;})}</ul>
     <Pager page={incidents.data.page} size={incidents.data.size} total={incidents.data.total} onPage={setIncPage}/></>}
   </div>
  </section>}
 </>;
}
export function Delivery(){return <NutritionFrame active="delivery" title="dl.title" intro="dl.intro"><Inner/></NutritionFrame>;}
