"use client";
import {useCallback,useEffect,useState} from "react";
import Link from "next/link";
import {api,uuid,code,type Row} from "../../lib/scheduling/client";
import styles from "../../app/workforce/scheduling/scheduling.module.css";

export type Screen="M02-S01"|"M02-S02"|"M02-S03"|"M02-S04"|"M02-S05";
type Locale="en"|"fr";
const screens:{id:Screen;en:string;fr:string}[]=[
 {id:"M02-S01",en:"Appointment calendar",fr:"Calendrier des rendez-vous"},
 {id:"M02-S02",en:"Appointment booking",fr:"Réservation d'un rendez-vous"},
 {id:"M02-S03",en:"Appointment details",fr:"Détails du rendez-vous"},
 {id:"M02-S04",en:"Waitlist and rescheduling",fr:"Liste d'attente et reprogrammation"},
 {id:"M02-S05",en:"Resource availability",fr:"Disponibilité des ressources"}
];
const dict={
 en:{language:"Language",reload:"Refresh",subject:"Verified patient UUID",proof:"Independent patient proof UUID",
  resource:"Resource UUID",resourceCode:"Resource code",resourceType:"Resource type",slot:"Available slot UUID",
  start:"Start time",end:"End time",book:"Book appointment",publish:"Publish availability",
  addResource:"Register resource",createWait:"Add to waitlist",offer:"Offer replacement slot",
  accept:"Accept slot offer",move:"Reschedule selected appointment",
  actions:"Lifecycle actions",confirm:"Confirm",arrive:"Mark arrived",fulfil:"Fulfil",cancel:"Cancel",
  noShow:"No-show",selected:"Selected record",select:"Select",status:"Status",date:"Scheduled/created",
  records:"Live hospital-scoped records",empty:"No authorized records found.",invalid:"Check UUIDs, dates and resource codes.",
  error:"Request denied: ",done:"Action accepted by the service.",busy:"Saving",session:"Hospital sign-in required.",
  forbidden:"Your assigned role cannot access these records.",unavailable:"Scheduling service unavailable.",
  warning:"Appointments require separately issued patient-identity evidence. No patient identity or consent is created by scheduling.",
  details:"Record details",offline:"Offline: commands unavailable.",resources:"Published resources and slots",
  work:"Select a row to inspect permitted actions",guidance:"No mock appointment data are displayed.",
  notCertified:"Institutional patient identity and scheduling release approval are outstanding."},
 fr:{language:"Langue",reload:"Actualiser",subject:"UUID du patient vérifié",proof:"UUID de preuve d'identité indépendante",
  resource:"UUID de la ressource",resourceCode:"Code de ressource",resourceType:"Type de ressource",slot:"UUID du créneau disponible",
  start:"Début du créneau",end:"Fin du créneau",book:"Réserver le rendez-vous",publish:"Publier la disponibilité",
  addResource:"Ajouter une ressource",createWait:"Ajouter à la liste d'attente",offer:"Proposer un autre créneau",
  accept:"Accepter le créneau proposé",move:"Reprogrammer le rendez-vous sélectionné",
  actions:"Actions de cycle",confirm:"Confirmer",arrive:"Marquer arrivé",fulfil:"Clore la consultation",cancel:"Annuler",
  noShow:"Absence",selected:"Élément sélectionné",select:"Sélectionner",status:"État",date:"Planifié/créé",
  records:"Enregistrements autorisés de l'hôpital",empty:"Aucun enregistrement autorisé.",invalid:"Vérifiez UUID, dates et codes.",
  error:"Requête refusée : ",done:"Action acceptée par le service.",busy:"Enregistrement",session:"Connexion hospitalière nécessaire.",
  forbidden:"Votre rôle ne permet pas l'accès à ces données.",unavailable:"Service de planification indisponible.",
  warning:"Les rendez-vous exigent une preuve d'identité patient délivrée séparément. Ce module ne crée ni identité ni consentement.",
  details:"Détails",offline:"Hors connexion : modifications indisponibles.",resources:"Ressources et créneaux publiés",
  work:"Choisissez une ligne pour consulter les actions autorisées",guidance:"Aucune donnée fictive n'est affichée.",
  notCertified:"L'identité patient institutionnelle et l'acceptation opérationnelle restent en attente."}
} as const;
type Form={patientRef:string;proofId:string;resourceId:string;slotId:string;resourceCode:string;
 resourceType:string;startsAt:string;endsAt:string;offeredSlotId:string};
const initial:Form={patientRef:"",proofId:"",resourceId:"",slotId:"",
 resourceCode:"",resourceType:"ROOM",startsAt:"",endsAt:"",offeredSlotId:""};
function Field({label,value,onChange,type="text"}:{label:string;value:string;
 onChange:(v:string)=>void;type?:string}){
 return <label className={styles.field}>{label}
 <input type={type} value={value} onChange={e=>onChange(e.target.value)}
  autoComplete="off" spellCheck={false}/></label>;
}
export function SchedulingWorkbench({screen}:{screen:Screen}){
 const [lang,setLang]=useState<Locale>("en"),t=dict[lang];
 const [form,setForm]=useState<Form>(initial);
 const [rows,setRows]=useState<Row[]>([]);
 const [appointments,setAppointments]=useState<Row[]>([]);
 const [selected,setSelected]=useState<Row|null>(null);
 const [busy,setBusy]=useState(false);
 const [state,setState]=useState<"loading"|"ready"|"forbidden"|"signin"|"error">("loading");
 const [message,setMessage]=useState("");
 const current=screens.find(s=>s.id===screen)!;
 const table=screen==="M02-S01"?"slots":screen==="M02-S02"?"slots":
  screen==="M02-S03"?"appointments":screen==="M02-S04"?"waitlist":"resources";
 const change=(key:keyof Form)=>(v:string)=>setForm(f=>({...f,[key]:v}));
 const refresh=useCallback(async()=>{
  const reply=await api<Row[]>("GET",table,{query:{limit:50}});
  if(reply.ok){
   setRows(reply.data);setState("ready");
   setSelected(previous=>reply.data.find(x=>x.id===previous?.id)||null);
   if(screen==="M02-S04"){
    const a=await api<Row[]>("GET","appointments",{query:{limit:50}});
    if(a.ok)setAppointments(a.data);
   }
  }else{
   setRows([]);setSelected(null);setMessage(reply.error);
   setState(reply.status===401?"signin":reply.status===403?"forbidden":"error");
  }
 },[table,screen]);
 useEffect(()=>{void refresh();},[refresh]);
 const run=async(route:string,body:object)=>{
  if(busy)return;
  setBusy(true);setMessage("");
  try{
   const result=await api<{id:string;status:string;version:number}>("POST",route,{body});
   if(!result.ok){setMessage(t.error+result.error.replaceAll("_"," "));return;}
   await refresh();setMessage(t.done+" ["+result.data.status+"]");
  }finally{setBusy(false);}
 };
 const booking=()=>{
  if(!uuid.test(form.patientRef)||!uuid.test(form.proofId)||!uuid.test(form.slotId)){
   setMessage(t.invalid);return;
  }
  void run("appointments",{patientRef:form.patientRef,proofId:form.proofId,slotId:form.slotId});
 };
 const waitlist=()=>{
  if(!uuid.test(form.patientRef)||!uuid.test(form.proofId)||!uuid.test(form.resourceId)){
   setMessage(t.invalid);return;
  }
  void run("waitlist",{patientRef:form.patientRef,proofId:form.proofId,resourceId:form.resourceId});
 };
 const addResource=()=>{
  if(!code.test(form.resourceCode)||!["CLINIC","ROOM","PROVIDER","EQUIPMENT"].includes(form.resourceType)){
   setMessage(t.invalid);return;
  }
  void run("resources",{resourceCode:form.resourceCode,resourceType:form.resourceType});
 };
 const publishSlot=()=>{
  if(!uuid.test(form.resourceId)||!form.startsAt||!form.endsAt||
   !Number.isFinite(Date.parse(form.startsAt))||!Number.isFinite(Date.parse(form.endsAt))||
   Date.parse(form.endsAt)<=Date.parse(form.startsAt)){setMessage(t.invalid);return;}
  void run("slots",{resourceId:form.resourceId,startsAt:new Date(form.startsAt).toISOString(),
   endsAt:new Date(form.endsAt).toISOString()});
 };
 const action=(name:string,slot=false)=>{
  if(!selected){setMessage(t.invalid);return;}
  if(slot&&!uuid.test(form.offeredSlotId)){setMessage(t.invalid);return;}
  const base=table+"/"+selected.id+"/"+name;
  void run(base,slot?{expectedVersion:selected.version,slotId:form.offeredSlotId}:
   {expectedVersion:selected.version});
 };
 const move=(id:string,version:number)=>{
  if(!uuid.test(form.offeredSlotId)){setMessage(t.invalid);return;}
  void run("appointments/"+id+"/reschedule",{expectedVersion:version,slotId:form.offeredSlotId});
 };
 return <section className={styles.root}>
  <div className={styles.head}><div><p className={styles.eyebrow}>SYNQORIA · M02 · {screen}</p>
   <h1>{current[lang]}</h1></div>
   <label className={styles.lang}>{t.language}<select value={lang}
    onChange={e=>setLang(e.target.value as Locale)}>
    <option value="en">English</option><option value="fr">Français</option></select></label></div>
  <p className={styles.warning}>{t.warning}</p>
  <nav className={styles.nav} aria-label="Scheduling workstations">{screens.map(x=>
   <Link key={x.id} href={"/workforce/clinical/"+x.id} aria-current={screen===x.id?"page":undefined}>
    {x.id} — {x[lang]}</Link>)}</nav>
  <div className={styles.head}><h2>{t.records}</h2>
   <button type="button" disabled={busy} onClick={()=>void refresh()}>{t.reload}</button></div>
  {state==="loading"&&<p role="status">Loading / Chargement</p>}
  {state==="signin"&&<p role="alert">{t.session}</p>}
  {state==="forbidden"&&<p role="alert">{t.forbidden}</p>}
  {state==="error"&&<p role="alert">{t.unavailable} ({message})</p>}
  {state==="ready"&&<div className={styles.panel}>
   <p className={styles.muted}>{t.work}</p>
   {!rows.length?<p role="status">{t.empty}</p>:
    <div className={styles.tableWrap}><table className={styles.table}>
     <thead><tr><th>{t.select}</th><th>{t.status}</th><th>{t.date}</th></tr></thead>
     <tbody>{rows.map(row=><tr key={row.id}>
      <td><button type="button" aria-pressed={selected?.id===row.id}
       onClick={()=>{setSelected(row);change("slotId")(row.id);if(row.resource_id)change("resourceId")(row.resource_id);}}>
       {row.id.slice(0,8)}</button></td><td>{row.status}</td>
      <td>{new Date(row.starts_at||row.created_at).toLocaleString(lang)}</td>
     </tr>)}</tbody></table></div>}
   {selected&&<details className={styles.details}><summary>{t.details}: {selected.id}</summary>
    <dl>{Object.entries(selected).map(([k,v])=><div key={k}><dt>{k.replaceAll("_"," ")}</dt>
     <dd>{String(v??"—")}</dd></div>)}</dl></details>}
   {screen==="M02-S01"&&selected&&<Link href="/workforce/clinical/M02-S02">
    {t.book} — {selected.id.slice(0,8)}</Link>}
  </div>}
  {state==="ready"&&<div className={styles.panel}>
   {screen==="M02-S02"&&<form onSubmit={e=>{e.preventDefault();booking();}}>
    <h2>{t.book}</h2><div className={styles.grid}>
     <Field label={t.subject} value={form.patientRef} onChange={change("patientRef")}/>
     <Field label={t.proof} value={form.proofId} onChange={change("proofId")}/>
     <Field label={t.slot} value={form.slotId} onChange={change("slotId")}/>
    </div><button type="submit" disabled={busy}>{t.book}</button></form>}
   {screen==="M02-S03"&&selected&&<div>
    <h2>{t.actions}</h2><p>{t.selected}: {selected.id} ({selected.status})</p>
    <div className={styles.actions}>
     {selected.status==="REQUESTED"&&<button type="button" disabled={busy}
      onClick={()=>action("confirm")}>{t.confirm}</button>}
     {selected.status==="CONFIRMED"&&<>
      <button type="button" disabled={busy} onClick={()=>action("arrive")}>{t.arrive}</button>
      <button type="button" disabled={busy} onClick={()=>action("no-show")}>{t.noShow}</button></>}
     {selected.status==="ARRIVED"&&<button type="button" disabled={busy}
      onClick={()=>action("fulfil")}>{t.fulfil}</button>}
     {["REQUESTED","CONFIRMED","ARRIVED"].includes(selected.status)&&
      <button type="button" disabled={busy} onClick={()=>action("cancel")}>{t.cancel}</button>}
    </div></div>}
   {screen==="M02-S04"&&<>
    <h2>{t.createWait}</h2>
    <form onSubmit={e=>{e.preventDefault();waitlist();}}>
     <div className={styles.grid}>
      <Field label={t.subject} value={form.patientRef} onChange={change("patientRef")}/>
      <Field label={t.proof} value={form.proofId} onChange={change("proofId")}/>
      <Field label={t.resource} value={form.resourceId} onChange={change("resourceId")}/>
     </div><button disabled={busy} type="submit">{t.createWait}</button></form>
    <h2>{t.offer}</h2><div className={styles.grid}>
     <Field label={t.slot} value={form.offeredSlotId} onChange={change("offeredSlotId")}/></div>
    <div className={styles.actions}>
     {selected?.status==="WAITING"&&<button type="button" disabled={busy}
      onClick={()=>action("offer",true)}>{t.offer}</button>}
     {selected?.status==="OFFERED"&&<button type="button" disabled={busy}
      onClick={()=>action("accept")}>{t.accept}</button>}
    </div>
    <h2>{t.move}</h2><p className={styles.muted}>{t.work}</p>
    <div className={styles.tableWrap}><table className={styles.table}>
     <thead><tr><th>{t.select}</th><th>{t.status}</th><th>{t.date}</th></tr></thead>
     <tbody>{appointments.filter(x=>["REQUESTED","CONFIRMED"].includes(x.status)).map(x=>
      <tr key={x.id}><td><button type="button" disabled={busy} onClick={()=>move(x.id,x.version)}>
       {x.id.slice(0,8)}</button></td><td>{x.status}</td>
       <td>{new Date(x.created_at).toLocaleString(lang)}</td></tr>)}</tbody>
    </table></div>
   </>}
   {screen==="M02-S05"&&<>
    <h2>{t.addResource}</h2>
    <form onSubmit={e=>{e.preventDefault();addResource();}}>
     <div className={styles.grid}>
      <Field label={t.resourceCode} value={form.resourceCode} onChange={change("resourceCode")}/>
      <label className={styles.field}>{t.resourceType}<select value={form.resourceType}
       onChange={e=>change("resourceType")(e.target.value)}>
       {["CLINIC","ROOM","PROVIDER","EQUIPMENT"].map(v=><option key={v}>{v}</option>)}</select></label>
     </div><button disabled={busy} type="submit">{t.addResource}</button></form>
    <h2>{t.publish}</h2>
    <form onSubmit={e=>{e.preventDefault();publishSlot();}}>
     <div className={styles.grid}>
      <Field label={t.resource} value={form.resourceId} onChange={change("resourceId")}/>
      <Field label={t.start} type="datetime-local" value={form.startsAt} onChange={change("startsAt")}/>
      <Field label={t.end} type="datetime-local" value={form.endsAt} onChange={change("endsAt")}/>
     </div><button disabled={busy} type="submit">{t.publish}</button></form>
   </>}
  </div>}
  {message&&state==="ready"&&<p className={styles.notice} role="status" aria-live="polite">{message}</p>}
  <p className={styles.footer}>{t.guidance} {t.notCertified}</p>
 </section>;
}
