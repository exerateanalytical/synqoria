"use client";
import {useCallback,useEffect,useState} from "react";
import Link from "next/link";
import {api,uuid,code,hex,type Row,type History} from "../../lib/referrals/client";
import styles from "../../app/workforce/referrals/referrals.module.css";
export type Screen="M16-S01"|"M16-S02"|"M16-S03"|"M16-S04"|"M16-S05";
type Locale="en"|"fr";
const screens:{id:Screen;en:string;fr:string}[]=[
 {id:"M16-S01",en:"Referral worklist",fr:"Liste des références"},
 {id:"M16-S02",en:"Referral preparation",fr:"Préparation d'une référence"},
 {id:"M16-S03",en:"Referral acceptance review",fr:"Examen de l'acceptation"},
 {id:"M16-S04",en:"Transition handover",fr:"Transfert et remise du patient"},
 {id:"M16-S05",en:"Referral follow-up and closure",fr:"Suivi et clôture de la référence"}
];
const dictionary={
 en:{language:"Language",refresh:"Refresh",subject:"Verified patient UUID",recipient:"Receiving facility UUID",
  reason:"Referral reason code",summary:"Clinician-signed summary SHA-256",
  consent:"Independent consent proof UUID",proof:"External receiving/handover proof UUID",
  create:"Prepare referral",authorize:"Authorize independently",send:"Send authorized referral",
  accept:"Record receiving acceptance",reject:"Record receiving rejection",
  transfer:"Confirm witnessed handover",attend:"Record verified attendance",
  close:"Close reviewed referral",cancel:"Cancel draft/authorized referral",
  work:"Live hospital referrals",select:"Select",status:"Status",time:"Created",
  detail:"Selected referral",history:"Transition history",empty:"No authorized referrals found.",
  invalid:"Enter valid UUIDs, reason codes, and 64-character SHA-256 digests.",
  denied:"Action denied: ",success:"Server accepted the action.",busy:"Processing",
  signin:"Hospital sign-in required.",forbidden:"Your role cannot access these records.",
  unavailable:"Referral service unavailable.",warning:"Only metadata and evidence digests are stored here. Actual clinical summaries and transfer consent are issued and verified by independent authorities.",
  caution:"Sender, receiving staff and handover must be independently authorized. No unverified clinical data are released.",
  review:"Use independently issued proof bound to this exact referral, patient and receiving facility.",
  noSelection:"Select a referral before an action.",notCertified:"Clinical referral release is not institutionally approved.",
  source:"Source status",to:"New status",version:"Version"},
 fr:{language:"Langue",refresh:"Actualiser",subject:"UUID du patient vérifié",recipient:"UUID de l'établissement receveur",
  reason:"Code du motif de référence",summary:"Empreinte SHA-256 du résumé médical signé",
  consent:"UUID de preuve de consentement indépendante",proof:"UUID de preuve de réception/transfert",
  create:"Préparer la référence",authorize:"Autoriser indépendamment",send:"Envoyer la référence autorisée",
  accept:"Enregistrer l'acceptation",reject:"Enregistrer le refus",
  transfer:"Confirmer le transfert avec témoin",attend:"Enregistrer la présence vérifiée",
  close:"Clore la référence révisée",cancel:"Annuler la référence non finalisée",
  work:"Références hospitalières en temps réel",select:"Sélectionner",status:"État",time:"Créé",
  detail:"Référence sélectionnée",history:"Historique des transitions",empty:"Aucune référence autorisée.",
  invalid:"Saisissez des UUID, des codes et des empreintes SHA-256 valides.",
  denied:"Action refusée : ",success:"Action acceptée par le serveur.",busy:"Traitement",
  signin:"Connexion hospitalière requise.",forbidden:"Votre rôle ne permet pas cet accès.",
  unavailable:"Service de référence indisponible.",warning:"Seules les métadonnées et empreintes sont stockées ici. Les résumés cliniques et consentements sont délivrés par des autorités indépendantes.",
  caution:"Le transfert exige des autorisations indépendantes. Aucune donnée clinique non vérifiée n'est divulguée.",
  review:"Utilisez une preuve indépendante liée à cette référence, au patient et au destinataire.",
  noSelection:"Choisissez une référence avant d'agir.",notCertified:"Le déploiement clinique n'est pas approuvé.",
  source:"État source",to:"Nouvel état",version:"Version"}
} as const;
type Form={patientRef:string;recipientFacility:string;reasonCode:string;summarySha256:string;
 consentProofId:string;transitionProofId:string};
const initial:Form={patientRef:"",recipientFacility:"",reasonCode:"SPECIALIST_REVIEW",
 summarySha256:"",consentProofId:"",transitionProofId:""};
function Field({label,value,onChange}:{label:string;value:string;onChange:(v:string)=>void}){
 return <label className={styles.field}>{label}<input value={value} spellCheck={false}
  autoComplete="off" onChange={e=>onChange(e.target.value)}/></label>;
}
export function ReferralWorkbench({screen}:{screen:Screen}){
 const [lang,setLang]=useState<Locale>("en");const t=dictionary[lang];
 const [form,setForm]=useState<Form>(initial);
 const [rows,setRows]=useState<Row[]>([]);
 const [selected,setSelected]=useState<Row|null>(null);
 const [history,setHistory]=useState<History[]>([]);
 const [state,setState]=useState<"loading"|"ready"|"signin"|"forbidden"|"error">("loading");
 const [busy,setBusy]=useState(false),[notice,setNotice]=useState("");
 const [filter,setFilter]=useState("");
 const change=(key:keyof Form)=>(value:string)=>setForm(s=>({...s,[key]:value}));
 const current=screens.find(x=>x.id===screen)!;
 const refresh=useCallback(async()=>{
  const response=await api<Row[]>("GET","list",{query:{limit:50}});
  if(response.ok){
   setRows(response.data);
   setSelected(prev=>response.data.find(x=>x.id===prev?.id)||null);
   setState("ready");
  }else{
   setRows([]);setSelected(null);setHistory([]);setNotice(response.error);
   setState(response.status===401?"signin":response.status===403?"forbidden":"error");
  }
 },[]);
 useEffect(()=>{void refresh();},[refresh]);
 const select=async(row:Row)=>{
  setSelected(row);setHistory([]);
  const h=await api<History[]>("GET","history/"+row.id,{query:{limit:100}});
  if(h.ok)setHistory(h.data);else setNotice(t.denied+h.error);
 };
 const run=async(route:string,body:object)=>{
  if(busy)return;
  setBusy(true);setNotice("");
  try{
   const response=await api<{id:string;status:string;version:number}>("POST",route,{body});
   if(!response.ok){setNotice(t.denied+response.error.replaceAll("_"," "));return;}
   await refresh();setNotice(t.success+" ["+response.data.status+"]");
  }finally{setBusy(false);}
 };
 const create=()=>{
  if(!uuid.test(form.patientRef)||!uuid.test(form.recipientFacility)||!uuid.test(form.consentProofId)||
    !code.test(form.reasonCode)||!hex.test(form.summarySha256)){
   setNotice(t.invalid);return;
  }
  void run("create",{patientRef:form.patientRef,recipientFacility:form.recipientFacility,
   reasonCode:form.reasonCode,summarySha256:form.summarySha256,consentProofId:form.consentProofId});
 };
 const action=(name:string,external=false)=>{
  if(!selected){setNotice(t.noSelection);return;}
  if(external&&!uuid.test(form.transitionProofId)){setNotice(t.invalid);return;}
  void run("action/"+selected.id+"/"+name,{expectedVersion:selected.version,
   proofId:external?form.transitionProofId:null});
 };
 const options=rows.filter(x=>!filter||x.status===filter);
 return <section className={styles.root}>
  <div className={styles.head}><div><p className={styles.eyebrow}>SYNQORIA · M16 · {screen}</p>
   <h1>{current[lang]}</h1></div>
   <label className={styles.lang}>{t.language}<select value={lang}
    onChange={e=>setLang(e.target.value as Locale)}>
    <option value="en">English</option><option value="fr">Français</option>
   </select></label></div>
  <p className={styles.warning} role="note">{t.warning}</p>
  <nav className={styles.nav} aria-label="Referral workstations">{screens.map(s=>
   <Link key={s.id} href={"/workforce/clinical/"+s.id}
    aria-current={s.id===screen?"page":undefined}>{s.id} — {s[lang]}</Link>)}</nav>
  <div className={styles.head}><h2>{t.work}</h2>
   <button type="button" disabled={busy} onClick={()=>void refresh()}>{t.refresh}</button></div>
  {state==="loading"&&<p role="status">Loading / Chargement</p>}
  {state==="signin"&&<p role="alert">{t.signin}</p>}
  {state==="forbidden"&&<p role="alert">{t.forbidden}</p>}
  {state==="error"&&<p role="alert">{t.unavailable} ({notice})</p>}
  {state==="ready"&&<div className={styles.panel}>
   <label className={styles.field}>{t.status}<select value={filter}
    onChange={e=>setFilter(e.target.value)}><option value="">All / Tous</option>
    {[...new Set(rows.map(r=>r.status))].map(x=><option key={x}>{x}</option>)}</select></label>
   {!options.length?<p role="status">{t.empty}</p>:
    <div className={styles.tableWrap}><table className={styles.table}>
     <thead><tr><th>{t.select}</th><th>{t.status}</th><th>{t.subject}</th><th>{t.time}</th></tr></thead>
     <tbody>{options.map(row=><tr key={row.id}>
      <td><button type="button" aria-pressed={selected?.id===row.id}
       onClick={()=>void select(row)}>{row.id.slice(0,8)}</button></td>
      <td>{row.status}</td><td><code>{row.patient_ref.slice(0,12)}…</code></td>
      <td>{new Date(row.created_at).toLocaleString(lang)}</td></tr>)}</tbody>
    </table></div>}
   {selected&&<details className={styles.details} open={screen==="M16-S01"}>
    <summary>{t.detail}: {selected.id}</summary>
    <dl>{Object.entries(selected).map(([k,v])=><div key={k}>
     <dt>{k.replaceAll("_"," ")}</dt><dd>{String(v??"—")}</dd></div>)}</dl>
   </details>}
   {selected&&<details className={styles.details}>
    <summary>{t.history} ({history.length})</summary>
    <div className={styles.tableWrap}><table className={styles.table}>
     <thead><tr><th>{t.source}</th><th>{t.to}</th><th>{t.version}</th></tr></thead>
     <tbody>{history.map(h=><tr key={h.version}>
      <td>{h.from_status}</td><td>{h.to_status}</td><td>{h.version}</td></tr>)}</tbody>
    </table></div>
   </details>}
  </div>}
  {state==="ready"&&<div className={styles.panel}>
   {screen==="M16-S02"&&<>
    <h2>{t.create}</h2><p className={styles.muted}>{t.caution}</p>
    <form onSubmit={e=>{e.preventDefault();create();}}><div className={styles.grid}>
     <Field label={t.subject} value={form.patientRef} onChange={change("patientRef")}/>
     <Field label={t.recipient} value={form.recipientFacility} onChange={change("recipientFacility")}/>
     <Field label={t.reason} value={form.reasonCode} onChange={change("reasonCode")}/>
     <Field label={t.summary} value={form.summarySha256} onChange={change("summarySha256")}/>
     <Field label={t.consent} value={form.consentProofId} onChange={change("consentProofId")}/>
    </div><button disabled={busy} type="submit">{t.create}</button></form>
    <div className={styles.actions}>
     {selected?.status==="DRAFT"&&<button type="button" disabled={busy}
      onClick={()=>action("authorize")}>{t.authorize}</button>}
     {selected?.status==="AUTHORIZED"&&<button type="button" disabled={busy}
      onClick={()=>action("send")}>{t.send}</button>}
     {selected&&["DRAFT","AUTHORIZED"].includes(selected.status)&&
      <button type="button" disabled={busy} onClick={()=>action("cancel")}>{t.cancel}</button>}
    </div>
   </>}
   {screen==="M16-S03"&&<>
    <h2>{t.review}</h2>
    <Field label={t.proof} value={form.transitionProofId} onChange={change("transitionProofId")}/>
    {selected?.status==="SENT"&&<div className={styles.actions}>
     <button type="button" disabled={busy} onClick={()=>action("accept",true)}>{t.accept}</button>
     <button type="button" disabled={busy} onClick={()=>action("reject",true)}>{t.reject}</button>
    </div>}
   </>}
   {screen==="M16-S04"&&<>
    <h2>{t.transfer}</h2><p className={styles.muted}>{t.review}</p>
    <Field label={t.proof} value={form.transitionProofId} onChange={change("transitionProofId")}/>
    {selected?.status==="ACCEPTED"&&<button type="button" disabled={busy}
     onClick={()=>action("transfer",true)}>{t.transfer}</button>}
   </>}
   {screen==="M16-S05"&&<>
    <h2>{t.history}</h2>
    <Field label={t.proof} value={form.transitionProofId} onChange={change("transitionProofId")}/>
    <div className={styles.actions}>
     {selected?.status==="TRANSFERRED"&&<button type="button" disabled={busy}
      onClick={()=>action("attend",true)}>{t.attend}</button>}
     {selected?.status==="ATTENDED"&&<button type="button" disabled={busy}
      onClick={()=>action("close")}>{t.close}</button>}
    </div>
   </>}
   {screen==="M16-S01"&&<p className={styles.muted}>{t.work}. {t.notCertified}</p>}
  </div>}
  {notice&&state==="ready"&&<p className={styles.notice} role="status" aria-live="polite">{notice}</p>}
  <p className={styles.footer}>{t.notCertified}</p>
 </section>;
}
