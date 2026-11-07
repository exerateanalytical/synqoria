"use client";
import {useCallback,useEffect,useState} from "react";
import Link from "next/link";
import {call,uuid,code,type EventRow,type RequestRow,type HoldRow,
 type ChainStatus,type IntegrityManifest} from "../../lib/audit/client";
import styles from "../../app/workforce/audit/audit.module.css";

export type Screen="M34-S01"|"M34-S02"|"M34-S03"|"M34-S04"|"M34-S05";
type Locale="en"|"fr";
const screens:{id:Screen;en:string;fr:string}[]=[
 {id:"M34-S01",en:"Audit search",fr:"Recherche dans l'audit"},
 {id:"M34-S02",en:"Audit event detail",fr:"Détail de l'événement d'audit"},
 {id:"M34-S03",en:"Clinical provenance timeline",fr:"Chronologie de provenance clinique"},
 {id:"M34-S04",en:"Audit evidence export",fr:"Exportation des preuves d'audit"},
 {id:"M34-S05",en:"Retention hold and disposal review",fr:"Contrôle des mesures de conservation"}
];
const texts={
 en:{
  title:"Audit and evidence governance",language:"Language",refresh:"Refresh",loading:"Loading authorized evidence…",
  noAccess:"You are not authorized to view this evidence.",signIn:"Sign in with hospital identity",
  failed:"The audit service is unavailable or returned an error.",none:"No authorized records match.",
  select:"Select event",open:"Inspect",stream:"Stream UUID",subject:"Opaque subject",action:"Action",
  outcome:"Outcome",sequence:"Sequence",recorded:"Recorded",search:"Filter by action, outcome or subject",
  details:"Immutable evidence detail",source:"Source reference",hash:"Event SHA-256",
  previous:"Previous digest",input:"Evidence payload digest",proof:"Independent attestation",
  timeline:"Verified provenance timeline",verify:"Verify complete stream integrity",
  good:"Chain integrity verified",bad:"Integrity not verified. Do not use this stream as trusted evidence.",
  export:"Request controlled evidence manifest",purpose:"Purpose code",recipient:"Recipient code",
  from:"From sequence",to:"To sequence",expires:"Expiry (UTC)",request:"Submit request",
  approve:"Approve independently",deny:"Deny request",reason:"Decision reason",
  selected:"Selected record",noSelection:"Select a request before reviewing.",
  manifest:"Inspect approved integrity manifest",notSigned:"This is a digest manifest, not a signed clinical export.",
  holds:"External retention/legal holds",retention:"Request retention review",
  policy:"Retention policy code",holdActive:"Active hold",holdEnded:"Rescinded",
  noDelete:"A retention approval never deletes medical or audit records.",
  status:"Status",version:"Version",review:"Review",busy:"Saving",success:"Action accepted by the service.",
  invalid:"Enter valid UUIDs, codes, ranges and a future expiry.",error:"Request rejected: ",
  warning:"Metadata only. Source attestations, legal holds and original clinical documents are controlled by independent institutional authorities.",
  notProven:"Integrity checks do not establish a qualified digital signature or legal certification."
 },
 fr:{
  title:"Gouvernance de l'audit et des preuves",language:"Langue",refresh:"Actualiser",loading:"Chargement des preuves autorisées…",
  noAccess:"Vous n'êtes pas autorisé à consulter ces preuves.",signIn:"Connexion avec l'identité hospitalière",
  failed:"Le service d'audit est indisponible ou a renvoyé une erreur.",none:"Aucun enregistrement autorisé correspondant.",
  select:"Choisir l'événement",open:"Examiner",stream:"UUID du flux",subject:"Sujet de référence",action:"Action",
  outcome:"Résultat",sequence:"Séquence",recorded:"Enregistré le",search:"Filtrer par action, résultat ou sujet",
  details:"Détail des preuves immuables",source:"Référence source",hash:"SHA-256 de l'événement",
  previous:"Empreinte précédente",input:"Empreinte des données",proof:"Attestation indépendante",
  timeline:"Chronologie de provenance vérifiée",verify:"Vérifier l'intégrité du flux complet",
  good:"Intégrité de la chaîne vérifiée",bad:"Intégrité non vérifiée. Ne pas utiliser ce flux comme preuve fiable.",
  export:"Demander un manifeste de preuves contrôlé",purpose:"Code de finalité",recipient:"Code du destinataire",
  from:"Séquence initiale",to:"Séquence finale",expires:"Expiration (UTC)",request:"Soumettre",
  approve:"Approuver indépendamment",deny:"Refuser la demande",reason:"Motif de décision",
  selected:"Enregistrement sélectionné",noSelection:"Sélectionnez une demande pour décider.",
  manifest:"Examiner le manifeste d'intégrité approuvé",notSigned:"Ceci est un manifeste d'empreintes, pas un export clinique signé.",
  holds:"Mesures de conservation imposées",retention:"Demander une révision de conservation",
  policy:"Code de politique de conservation",holdActive:"Mesure active",holdEnded:"Mesure levée",
  noDelete:"Une approbation de conservation ne supprime jamais les dossiers médicaux ou d'audit.",
  status:"État",version:"Version",review:"Examiner",busy:"Enregistrement",success:"Action acceptée par le service.",
  invalid:"Vérifiez UUID, codes, plages et expiration future.",error:"Demande rejetée : ",
  warning:"Métadonnées uniquement. Attestations, mesures légales et documents cliniques demeurent sous contrôle des autorités institutionnelles indépendantes.",
  notProven:"Les empreintes ne constituent pas une signature numérique qualifiée ou une certification légale."
 }
} as const;
type Fields={
 streamId:string;subject:string;filter:string;purpose:string;recipient:string;
 fromSequence:string;toSequence:string;expiresAt:string;policy:string;reason:string
};
const initial:Fields={streamId:"",subject:"",filter:"",purpose:"AUDIT_REVIEW",recipient:"REGULATOR",
 fromSequence:"1",toSequence:"1",expiresAt:"",policy:"REVIEW_REQUIRED",reason:"AUDIT_REQUEST"};
function Field({label,value,onChange,type="text"}:{label:string;value:string;
 onChange:(text:string)=>void;type?:string}){
 return <label className={styles.field}>{label}<input value={value} type={type}
  onChange={event=>onChange(event.target.value)} autoComplete="off" spellCheck={false}/></label>;
}
export function AuditWorkbench({screen}:{screen:Screen}){
 const [lang,setLang]=useState<Locale>("en");
 const [form,setForm]=useState<Fields>(initial);
 const [records,setRecords]=useState<EventRow[]>([]);
 const [requests,setRequests]=useState<RequestRow[]>([]);
 const [holds,setHolds]=useState<HoldRow[]>([]);
 const [selectedEvent,setSelectedEvent]=useState<EventRow|null>(null);
 const [selectedRequest,setSelectedRequest]=useState<RequestRow|null>(null);
 const [detail,setDetail]=useState<EventRow|null>(null);
 const [chain,setChain]=useState<ChainStatus|null>(null);
 const [manifest,setManifest]=useState<IntegrityManifest|null>(null);
 const [state,setState]=useState<"loading"|"ready"|"signin"|"forbidden"|"error">("loading");
 const [notice,setNotice]=useState("");
 const [busy,setBusy]=useState(false);
 const t=texts[lang];
 const labels=screens.find(s=>s.id===screen)!;
 const change=(field:keyof Fields)=>(v:string)=>setForm(x=>({...x,[field]:v}));
 const collection=screen==="M34-S04"?"exports":screen==="M34-S05"?"retention/reviews":"events";
 const refresh=useCallback(async()=>{
  const result=await call<EventRow[]|RequestRow[]>("GET",collection,{query:{limit:50}});
  if(!result.ok){
   setState(result.status===401?"signin":result.status===403?"forbidden":"error");
   setNotice(result.code);setRecords([]);setRequests([]);setSelectedRequest(null);setSelectedEvent(null);
   return;
  }
  if(collection==="events")setRecords(result.data as EventRow[]);
  else setRequests(result.data as RequestRow[]);
  if(screen==="M34-S05"){
   const h=await call<HoldRow[]>("GET","retention/holds",{query:{limit:50}});
   if(h.ok)setHolds(h.data);
   else{setState(h.status===403?"forbidden":"error");setNotice(h.code);return;}
  }
  setState("ready");
 },[collection,screen]);
 useEffect(()=>{void refresh();},[refresh]);
 const action=async(route:string,body:object)=>{
  if(busy)return;
  setBusy(true);setNotice("");
  try{
   const r=await call<{id:string;status:string;version:number}>("POST",route,{body});
   if(!r.ok){setNotice(t.error+r.code.replaceAll("_"," "));return;}
   await refresh();setNotice(t.success);
   setSelectedRequest(null);
  }finally{setBusy(false);}
 };
 const inspect=async(record:EventRow)=>{
  setSelectedEvent(record);setDetail(null);
  const result=await call<EventRow>("GET","events/"+record.id);
  if(result.ok)setDetail(result.data);
  else setNotice(t.error+result.code);
 };
 const verify=async()=>{
  if(!uuid.test(form.streamId)){setNotice(t.invalid);return;}
  setBusy(true);setChain(null);
  try{
   const result=await call<ChainStatus>("GET","streams/"+form.streamId+"/verify");
   if(result.ok)setChain(result.data);else setNotice(t.error+result.code);
  }finally{setBusy(false);}
 };
 const timeline=async()=>{
  if(!uuid.test(form.streamId)){setNotice(t.invalid);return;}
  setBusy(true);
  try{
   const r=await call<EventRow[]>("GET","streams/"+form.streamId+"/timeline",{query:{limit:100}});
   if(r.ok)setRecords(r.data);else setNotice(t.error+r.code);
  }finally{setBusy(false);}
 };
 const requestExport=()=>{
  const from=Number(form.fromSequence),to=Number(form.toSequence);
  if(!uuid.test(form.streamId)||!code.test(form.purpose)||!code.test(form.recipient)||
   !Number.isSafeInteger(from)||from<1||!Number.isSafeInteger(to)||to<from||to-from>10000||
   !form.expiresAt||Date.parse(form.expiresAt)<=Date.now()){
   setNotice(t.invalid);return;
  }
  void action("exports",{streamId:form.streamId,purpose:form.purpose,recipient:form.recipient,
   fromSequence:from,toSequence:to,expiresAt:new Date(form.expiresAt).toISOString()});
 };
 const requestRetention=()=>{
  if(!uuid.test(form.streamId)||!code.test(form.policy)||!code.test(form.reason)){
   setNotice(t.invalid);return;
  }
  void action("retention/reviews",{streamId:form.streamId,policy:form.policy,reason:form.reason});
 };
 const review=(approve:boolean)=>{
  if(!selectedRequest){setNotice(t.noSelection);return;}
  if(!code.test(form.reason)){setNotice(t.invalid);return;}
  const route=(screen==="M34-S04"?"exports/":"retention/reviews/")+selectedRequest.id+"/review";
  void action(route,{expectedVersion:selectedRequest.version,approve,reason:form.reason});
 };
 const viewManifest=async()=>{
  if(!selectedRequest||selectedRequest.status!=="APPROVED")return;
  setManifest(null);
  const r=await call<IntegrityManifest>("GET","exports/"+selectedRequest.id+"/manifest");
  if(r.ok)setManifest(r.data);else setNotice(t.error+r.code);
 };
 const eventList=records.filter(r=>!form.filter||
   [r.action,r.outcome,r.subject_ref||"",r.stream_id].some(x=>x.toLowerCase().includes(form.filter.toLowerCase())));
 return <section className={styles.workbench} aria-label={labels[lang]}>
  <div className={styles.head}><div>
   <p className={styles.eyebrow}>SYNQORIA · M34 · {screen}</p>
   <h1>{labels[lang]}</h1></div>
   <label className={styles.lang}>{t.language}<select value={lang}
    onChange={e=>setLang(e.target.value as Locale)}>
    <option value="en">English</option><option value="fr">Français</option></select></label>
  </div>
  <p className={styles.notice} role="note">{t.warning} {t.notProven}</p>
  <nav className={styles.nav} aria-label="M34 audit workstations">{screens.map(s=>
   <Link key={s.id} href={"/workforce/audit/"+s.id}
    aria-current={screen===s.id?"page":undefined}>{s.id} — {s[lang]}</Link>)}</nav>
  <div className={styles.toolbar}><h2>{t.title}</h2>
   <button type="button" onClick={()=>void refresh()} disabled={busy}>{t.refresh}</button></div>
  {state==="loading"&&<p role="status">{t.loading}</p>}
  {state==="signin"&&<p role="alert">{t.signIn} · <Link href="/api/operations/auth/login">Sign in</Link></p>}
  {state==="forbidden"&&<p role="alert">{t.noAccess}</p>}
  {state==="error"&&<p role="alert">{t.failed} ({notice})</p>}
  {state==="ready"&&<>
   {["M34-S01","M34-S02","M34-S03"].includes(screen)&&<div className={styles.panel}>
    {screen==="M34-S01"&&<Field label={t.search} value={form.filter} onChange={change("filter")}/>}
    {screen==="M34-S03"&&<div className={styles.fields}>
     <Field label={t.stream} value={form.streamId} onChange={change("streamId")}/>
     <div className={styles.actions}>
      <button type="button" disabled={busy} onClick={()=>void timeline()}>{t.timeline}</button>
      <button type="button" disabled={busy} onClick={()=>void verify()}>{t.verify}</button>
     </div></div>}
    {chain&&<p className={chain.verified?styles.success:styles.error} role="status">
     {chain.verified?t.good:t.bad} ({chain.count})</p>}
    {eventList.length===0?<p className={styles.muted}>{t.none}</p>:
     <div className={styles.tableWrap}><table className={styles.table}>
      <thead><tr><th scope="col">{t.open}</th><th scope="col">{t.action}</th>
       <th scope="col">{t.outcome}</th><th scope="col">{t.sequence}</th>
       <th scope="col">{t.recorded}</th></tr></thead>
      <tbody>{eventList.map(e=><tr key={e.id}>
       <td><button type="button" aria-pressed={selectedEvent?.id===e.id}
        onClick={()=>void inspect(e)}>{e.id.slice(0,8)}</button></td>
       <td>{e.action}</td><td>{e.outcome}</td><td>{e.sequence}</td>
       <td>{new Date(e.recorded_at).toLocaleString(lang)}</td>
      </tr>)}</tbody>
     </table></div>}
    {(detail||selectedEvent)&&<div className={styles.details}>
     <h3>{t.details}</h3><dl>
      {Object.entries(detail||selectedEvent||{}).map(([key,value])=><div key={key}>
       <dt>{key.replaceAll("_"," ")}</dt><dd><code>{String(value??"—")}</code></dd>
      </div>)}</dl></div>}
   </div>}
   {screen==="M34-S04"&&<div className={styles.panel}>
    <h2>{t.export}</h2><p className={styles.muted}>{t.notSigned}</p>
    <form onSubmit={e=>{e.preventDefault();requestExport();}}>
     <div className={styles.fields}>
      <Field label={t.stream} value={form.streamId} onChange={change("streamId")}/>
      <Field label={t.purpose} value={form.purpose} onChange={change("purpose")}/>
      <Field label={t.recipient} value={form.recipient} onChange={change("recipient")}/>
      <Field label={t.from} type="number" value={form.fromSequence} onChange={change("fromSequence")}/>
      <Field label={t.to} type="number" value={form.toSequence} onChange={change("toSequence")}/>
      <Field label={t.expires} type="datetime-local" value={form.expiresAt} onChange={change("expiresAt")}/>
     </div><button type="submit" disabled={busy}>{t.request}</button>
    </form>
    <h3>{t.review}</h3>
    <div className={styles.tableWrap}><table className={styles.table}>
     <thead><tr><th>{t.select}</th><th>{t.stream}</th><th>{t.status}</th><th>{t.version}</th></tr></thead>
     <tbody>{requests.map(e=><tr key={e.id}>
      <td><button type="button" aria-pressed={selectedRequest?.id===e.id}
       onClick={()=>{setSelectedRequest(e);setManifest(null);}}>{e.id.slice(0,8)}</button></td>
      <td><code>{e.stream_id.slice(0,12)}…</code></td><td>{e.status}</td><td>{e.version}</td>
     </tr>)}</tbody></table></div>
    {selectedRequest&&<div className={styles.review}>
     <p>{t.selected} — {selectedRequest.id} ({selectedRequest.status})</p>
     {selectedRequest.status==="PROPOSED"&&<>
      <Field label={t.reason} value={form.reason} onChange={change("reason")}/>
      <div className={styles.actions}>
       <button type="button" disabled={busy} onClick={()=>review(true)}>{t.approve}</button>
       <button type="button" disabled={busy} onClick={()=>review(false)}>{t.deny}</button>
      </div></>}
     {selectedRequest.status==="APPROVED"&&<button type="button" onClick={()=>void viewManifest()}>
      {t.manifest}</button>}
     {manifest&&<div className={styles.details}><dl>{Object.entries(manifest).map(([k,v])=>
      <div key={k}><dt>{k}</dt><dd><code>{String(v)}</code></dd></div>)}</dl></div>}
    </div>}
   </div>}
   {screen==="M34-S05"&&<div className={styles.panel}>
    <h2>{t.holds}</h2><p className={styles.muted}>{t.noDelete}</p>
    <div className={styles.tableWrap}><table className={styles.table}>
     <thead><tr><th>{t.stream}</th><th>{t.reason}</th><th>{t.status}</th></tr></thead>
     <tbody>{holds.map(h=><tr key={h.hold_id}>
      <td><code>{h.stream_id.slice(0,16)}…</code></td><td>{h.reason}</td>
      <td>{h.rescinded_at?t.holdEnded:t.holdActive}</td></tr>)}</tbody>
    </table></div>
    <h3>{t.retention}</h3>
    <form onSubmit={e=>{e.preventDefault();requestRetention();}}>
     <div className={styles.fields}>
      <Field label={t.stream} value={form.streamId} onChange={change("streamId")}/>
      <Field label={t.policy} value={form.policy} onChange={change("policy")}/>
      <Field label={t.reason} value={form.reason} onChange={change("reason")}/>
     </div><button type="submit" disabled={busy}>{t.request}</button>
    </form>
    <div className={styles.tableWrap}><table className={styles.table}>
     <thead><tr><th>{t.select}</th><th>{t.status}</th><th>{t.version}</th></tr></thead>
     <tbody>{requests.map(e=><tr key={e.id}>
      <td><button type="button" aria-pressed={selectedRequest?.id===e.id}
       onClick={()=>setSelectedRequest(e)}>{e.id.slice(0,8)}</button></td>
      <td>{e.status}</td><td>{e.version}</td></tr>)}</tbody>
    </table></div>
    {selectedRequest?.status==="PROPOSED"&&<div className={styles.review}>
     <p>{t.selected}: {selectedRequest.id}</p>
     <Field label={t.reason} value={form.reason} onChange={change("reason")}/>
     <div className={styles.actions}>
      <button type="button" disabled={busy} onClick={()=>review(true)}>{t.approve}</button>
      <button type="button" disabled={busy} onClick={()=>review(false)}>{t.deny}</button>
     </div></div>}
   </div>}
  </>}
  {notice&&state==="ready"&&<p className={styles.alert} role="status" aria-live="polite">{notice}</p>}
  <p className={styles.footer}>{t.notProven} {t.noDelete}</p>
 </section>;
}
