"use client";

import {useCallback,useEffect,useState} from "react";
import Link from "next/link";
import {call,code,uuid,type RecordRow} from "../../lib/privacy/client";
import styles from "../../app/workforce/privacy/privacy.module.css";

export type Screen="M32-S01"|"M32-S02"|"M32-S03"|"M32-S04"|"M32-S05";
type Locale="en"|"fr";
const screens:{id:Screen;en:string;fr:string}[]=[
 {id:"M32-S01",en:"Consent and privacy overview",fr:"Vue d'ensemble des consentements et de la confidentialité"},
 {id:"M32-S02",en:"Consent directive capture",fr:"Saisie des directives de consentement"},
 {id:"M32-S03",en:"Consent verification and revocation",fr:"Vérification et révocation des consentements"},
 {id:"M32-S04",en:"Disclosure request review",fr:"Examen des demandes de divulgation"},
 {id:"M32-S05",en:"Privacy requests and incidents",fr:"Demandes de confidentialité et incidents"}
];
const dict={
 en:{
  refresh:"Refresh",newRecord:"New record",subject:"Verified subject UUID",purpose:"Purpose code",
  recipient:"Recipient code",operation:"Operation code",from:"Effective from",until:"Expires at",
  source:"External proof UUID",submit:"Submit",select:"Select",verify:"Verify independent proof",
  activate:"Activate independently",revoke:"Revoke consent",disclosure:"Request scoped disclosure",
  review:"Approve authorization",deny:"Deny request",recordClass:"Record class code",
  directive:"Directive UUID",reason:"Reason code",category:"Request category",close:"Close case",
  status:"Status",version:"Version",empty:"No authorized records were returned.",
  offline:"Offline. State-changing actions are unavailable.",
  loading:"Loading scoped records",notConfigured:"The privacy service is unavailable or not configured.",
  forbidden:"You are not permitted to view or update these records.",
  signIn:"Sign in using your hospital account",success:"The server accepted the action.",
  warning:"Consent must be verified by an independent authority. This module does not release clinical records.",
  selected:"Selected record",noSelection:"Select a record before applying a decision.",
  data:"Current hospital and facility records",date:"Created",authorization:"Consent authorization only",
  noClinical:"An approved disclosure request does not itself export patient records.",
  logout:"Sign in",language:"Language",busy:"Processing",invalid:"Check the UUIDs, dates and codes.",
  view:"View details",all:"All statuses",patient:"Subject reference",open:"Open privacy case",
  closed:"Mark reviewed case closed",details:"Record details",retry:"Retry",unknown:"No active selection",
  action:"Action",failure:"The server rejected this action: "
 },
 fr:{
  refresh:"Actualiser",newRecord:"Nouvel enregistrement",subject:"UUID du sujet vérifié",purpose:"Code de finalité",
  recipient:"Code du destinataire",operation:"Code de l'opération",from:"Début de validité",until:"Expiration",
  source:"UUID de la preuve externe",submit:"Soumettre",select:"Sélectionner",verify:"Vérifier la preuve indépendante",
  activate:"Activer indépendamment",revoke:"Révoquer le consentement",disclosure:"Demander la divulgation ciblée",
  review:"Approuver l'autorisation",deny:"Refuser la demande",recordClass:"Code de catégorie des dossiers",
  directive:"UUID de la directive",reason:"Code du motif",category:"Catégorie de la demande",close:"Clore le dossier",
  status:"État",version:"Version",empty:"Aucun enregistrement autorisé retourné.",
  offline:"Hors connexion. Les modifications sont indisponibles.",
  loading:"Chargement des enregistrements autorisés",notConfigured:"Le service de confidentialité est indisponible ou non configuré.",
  forbidden:"Vous n'êtes pas autorisé à consulter ou modifier ces dossiers.",
  signIn:"Connexion avec le compte hospitalier",success:"L'action a été acceptée par le serveur.",
  warning:"Tout consentement doit être vérifié par une autorité indépendante. Ce module ne divulgue pas de données cliniques.",
  selected:"Enregistrement sélectionné",noSelection:"Sélectionnez un enregistrement avant toute décision.",
  data:"Enregistrements actuels de cet hôpital et établissement",date:"Créé le",authorization:"Autorisation de consentement uniquement",
  noClinical:"Une divulgation approuvée ne transfère aucune donnée patient.",
  logout:"Se connecter",language:"Langue",busy:"Traitement",invalid:"Vérifiez les UUID, les dates et les codes.",
  view:"Voir les détails",all:"Tous les états",patient:"Référence du sujet",open:"Ouvrir un dossier de confidentialité",
  closed:"Marquer le dossier examiné comme clos",details:"Détails de l'enregistrement",retry:"Réessayer",unknown:"Aucune sélection",
  action:"Action",failure:"Action refusée par le serveur : "
 }
} as const;

const blank={subjectRef:"",purpose:"CARE",recipient:"HOSPITAL",operation:"READ",
 effectiveAt:"",expiresAt:"",evidenceId:"",directiveId:"",recordClass:"CLINICAL_SUMMARY",
 category:"ACCESS",reason:"PRIVACY_REVIEW"};
type Form=typeof blank;
const uuidValid=(v:string)=>uuid.test(v.trim());
function Table({rows,selected,onSelect,lang}:{rows:RecordRow[];selected:RecordRow|null;
 onSelect:(r:RecordRow)=>void;lang:Locale}){
 const t=dict[lang];
 if(!rows.length)return <p className={styles.muted} role="status">{t.empty}</p>;
 return <div className={styles.tableWrap}><table className={styles.table}>
  <thead><tr><th scope="col">{t.select}</th><th scope="col">{t.status}</th>
   <th scope="col">{t.patient}</th><th scope="col">{t.version}</th><th scope="col">{t.date}</th></tr></thead>
  <tbody>{rows.map(row=><tr key={row.id}>
   <td><button className={styles.select} type="button"
     aria-pressed={selected?.id===row.id}
     onClick={()=>onSelect(row)}>{row.id.substring(0,8)}</button></td>
   <td><strong>{row.status}</strong></td><td><code>{row.subject_ref?.substring(0,16)}…</code></td>
   <td>{row.version}</td><td>{row.created_at?new Date(row.created_at).toLocaleString(lang):"—"}</td>
  </tr>)}</tbody>
 </table></div>;
}
function Field({label,value,onChange,type="text",required=true}:{label:string;value:string;
 onChange:(v:string)=>void;type?:string;required?:boolean}){
 return <label className={styles.field}><span>{label}</span><input value={value}
   type={type} required={required} onChange={e=>onChange(e.target.value)}
   spellCheck={false} autoComplete="off"/></label>;
}
export function PrivacyWorkbench({screen}:{screen:Screen}){
 const [lang,setLang]=useState<Locale>("en");
 const t=dict[lang];
 const [form,setForm]=useState<Form>(blank);
 const [rows,setRows]=useState<RecordRow[]>([]);
 const [selected,setSelected]=useState<RecordRow|null>(null);
 const [status,setStatus]=useState<"loading"|"ready"|"error"|"signin"|"forbidden">("loading");
 const [message,setMessage]=useState("");
 const [busy,setBusy]=useState(false);
 const [filter,setFilter]=useState("");
 const collection=screen==="M32-S04"?"disclosures":screen==="M32-S05"?"cases":"directives";
 const change=(key:keyof Form)=>(value:string)=>setForm(f=>({...f,[key]:value}));
 const refresh=useCallback(async()=>{
  const result=await call<RecordRow[]>("GET",collection,{query:{limit:50}});
  if(result.ok){
   setRows(result.data);setSelected(previous=>result.data.find(r=>r.id===previous?.id)||null);
   setStatus("ready");setMessage("");
  }else{
   setRows([]);setSelected(null);
   setStatus(result.status===401?"signin":result.status===403?"forbidden":"error");
   setMessage(result.code);
  }
 },[collection]);
 useEffect(()=>{void refresh();},[refresh]);
 const setError=(error:string)=>setMessage(t.failure+error.replaceAll("_"," "));
 const run=async(route:string,body:object)=>{
  if(busy)return;
  setBusy(true);setMessage("");
  try{
   const result=await call<{id:string;status:string;version:number}>("POST",route,{body});
   if(!result.ok){setError(result.code);return;}
   await refresh();
   setMessage(t.success+" ["+result.data.status+"]");
  }finally{setBusy(false);}
 };
 const valid=(keys:(keyof Form)[])=>keys.every(k=>k.endsWith("Id")||k==="subjectRef"||k==="evidenceId"
  ?uuidValid(form[k]):code.test(form[k]));
 const directives=()=>{
  if(!valid(["subjectRef","purpose","recipient","operation"])||
   !form.effectiveAt||!form.expiresAt||
   !Number.isFinite(Date.parse(form.effectiveAt))||
   !Number.isFinite(Date.parse(form.expiresAt))||
   Date.parse(form.expiresAt)<=Date.parse(form.effectiveAt)){setError(t.invalid);return;}
  void run("directives",{subjectRef:form.subjectRef.trim(),purpose:form.purpose,
   recipient:form.recipient,operation:form.operation,
   effectiveAt:new Date(form.effectiveAt).toISOString(),
   expiresAt:new Date(form.expiresAt).toISOString()});
 };
 const stateAction=(action:"verify"|"activate"|"revoke"|"review"|"close",approve=true)=>{
  if(!selected){setError(t.noSelection);return;}
  const body=action==="verify"
   ?{expectedVersion:selected.version,evidenceId:form.evidenceId.trim()}
   :action==="review"||action==="close"
    ?{expectedVersion:selected.version,approve,reason:approve?"AUTHORIZED":form.reason}
    :{expectedVersion:selected.version};
  if(action==="verify"&&!uuidValid(form.evidenceId)){setError(t.invalid);return;}
  if((action==="review"||action==="close")&&!approve&&!code.test(form.reason)){
   setError(t.invalid);return;
  }
  if(action==="close")body.reason=form.reason;
  void run(collection+"/"+selected.id+"/"+action,body);
 };
 const disclosure=()=>{
  if(!valid(["subjectRef","directiveId","purpose","recipient","operation","recordClass"])){
   setError(t.invalid);return;
  }
  void run("disclosures",{subjectRef:form.subjectRef, directiveId:form.directiveId,
   purpose:form.purpose,recipient:form.recipient,
   operation:form.operation,recordClass:form.recordClass});
 };
 const openCase=()=>{
  if(!valid(["subjectRef","reason"])||!["ACCESS","CORRECTION","COMPLAINT","INCIDENT"].includes(form.category)){
   setError(t.invalid);return;
  }
  void run("cases",{subjectRef:form.subjectRef,category:form.category,reason:form.reason});
 };
 const filtered=rows.filter(x=>filter===""||x.status===filter);
 const label=screens.find(x=>x.id===screen)!;
 return <section className={styles.workbench} aria-label={label[lang]}>
  <div className={styles.header}>
   <div><p className={styles.eyebrow}>SYNQORIA / M32 / {screen}</p>
    <h1>{label[lang]}</h1></div>
   <label className={styles.lang}>{t.language}
    <select value={lang} onChange={e=>setLang(e.target.value as Locale)}>
     <option value="en">English</option><option value="fr">Français</option>
    </select>
   </label>
  </div>
  <p className={styles.safety} role="note">{t.warning}</p>
  <nav className={styles.nav} aria-label="M32 privacy screens">{screens.map(s=>
   <Link key={s.id} aria-current={screen===s.id?"page":undefined}
    href={"/workforce/privacy/"+s.id}>{s.id} — {s[lang]}</Link>)}</nav>
  <div className={styles.bar}><h2>{t.data}</h2>
   <button type="button" onClick={()=>void refresh()} disabled={busy}>{t.refresh}</button></div>
  {status==="loading"&&<p role="status">{t.loading}</p>}
  {status==="signin"&&<p role="alert">{t.signIn} — <Link href="/api/operations/auth/login">{t.logout}</Link></p>}
  {status==="forbidden"&&<p role="alert">{t.forbidden}</p>}
  {status==="error"&&<p role="alert">{t.notConfigured} ({message})</p>}
  {status==="ready"&&<>
   <label className={styles.filter}>{t.status}
    <select value={filter} onChange={e=>setFilter(e.target.value)}>
     <option value="">{t.all}</option>
     {[...new Set(rows.map(x=>x.status))].map(st=><option key={st} value={st}>{st}</option>)}
    </select>
   </label>
   <Table rows={filtered} selected={selected} onSelect={setSelected} lang={lang}/>
  </>}
  {selected&&status==="ready"&&<details className={styles.details}>
   <summary>{t.details}: {selected.id}</summary>
   <dl>{Object.entries(selected).filter(([k])=>!["cell_id","facility_id"].includes(k))
    .map(([k,v])=><div key={k}><dt>{k.replaceAll("_"," ")}</dt>
     <dd>{v==null?"—":String(v)}</dd></div>)}</dl>
  </details>}
  {status==="ready"&&<div className={styles.panel}>
   {screen==="M32-S01"&&<p className={styles.muted}>
    {t.authorization}. {t.warning} {t.noClinical}
   </p>}
   {screen==="M32-S02"&&<form onSubmit={e=>{e.preventDefault();directives();}}>
    <h2>{t.newRecord}</h2><div className={styles.fields}>
     <Field label={t.subject} value={form.subjectRef} onChange={change("subjectRef")}/>
     <Field label={t.purpose} value={form.purpose} onChange={change("purpose")}/>
     <Field label={t.recipient} value={form.recipient} onChange={change("recipient")}/>
     <Field label={t.operation} value={form.operation} onChange={change("operation")}/>
     <Field label={t.from} type="datetime-local" value={form.effectiveAt} onChange={change("effectiveAt")}/>
     <Field label={t.until} type="datetime-local" value={form.expiresAt} onChange={change("expiresAt")}/>
    </div><button disabled={busy} type="submit">{busy?t.busy:t.submit}</button></form>}
   {screen==="M32-S03"&&<div>
    <h2>{t.selected}</h2><p className={styles.muted}>{selected?.id||t.noSelection}</p>
    {selected?.status==="DRAFT"&&<div className={styles.fields}>
     <Field label={t.source} value={form.evidenceId} onChange={change("evidenceId")}/></div>}
    <div className={styles.actions}>
     {selected?.status==="DRAFT"&&<button type="button" disabled={busy}
      onClick={()=>stateAction("verify")}>{t.verify}</button>}
     {selected?.status==="VERIFIED"&&<button type="button" disabled={busy}
      onClick={()=>stateAction("activate")}>{t.activate}</button>}
     {selected?.status==="ACTIVE"&&<button type="button" disabled={busy}
      onClick={()=>stateAction("revoke")}>{t.revoke}</button>}
    </div></div>}
   {screen==="M32-S04"&&<>
    <h2>{t.disclosure}</h2><p className={styles.muted}>{t.noClinical}</p>
    <form onSubmit={e=>{e.preventDefault();disclosure();}}>
     <div className={styles.fields}>
      <Field label={t.subject} value={form.subjectRef} onChange={change("subjectRef")}/>
      <Field label={t.directive} value={form.directiveId} onChange={change("directiveId")}/>
      <Field label={t.purpose} value={form.purpose} onChange={change("purpose")}/>
      <Field label={t.recipient} value={form.recipient} onChange={change("recipient")}/>
      <Field label={t.operation} value={form.operation} onChange={change("operation")}/>
      <Field label={t.recordClass} value={form.recordClass} onChange={change("recordClass")}/>
     </div><button type="submit" disabled={busy}>{t.submit}</button>
    </form>
    {selected?.status==="PENDING"&&<div className={styles.decision}>
     <Field label={t.reason} value={form.reason} onChange={change("reason")}/>
     <div className={styles.actions}>
      <button type="button" disabled={busy} onClick={()=>stateAction("review",true)}>{t.review}</button>
      <button type="button" disabled={busy} onClick={()=>stateAction("review",false)}>{t.deny}</button>
     </div>
    </div>}
   </>}
   {screen==="M32-S05"&&<>
    <h2>{t.open}</h2>
    <form onSubmit={e=>{e.preventDefault();openCase();}}>
     <div className={styles.fields}>
      <Field label={t.subject} value={form.subjectRef} onChange={change("subjectRef")}/>
      <label className={styles.field}>{t.category}
       <select value={form.category} onChange={e=>change("category")(e.target.value)}>
        {["ACCESS","CORRECTION","COMPLAINT","INCIDENT"].map(v=><option key={v}>{v}</option>)}
       </select>
      </label>
      <Field label={t.reason} value={form.reason} onChange={change("reason")}/>
     </div><button type="submit" disabled={busy}>{t.submit}</button>
    </form>
    {selected?.status==="OPEN"&&<div className={styles.decision}>
     <Field label={t.reason} value={form.reason} onChange={change("reason")}/>
     <button type="button" disabled={busy} onClick={()=>stateAction("close",true)}>{t.closed}</button>
    </div>}
   </>}
  </div>}
  {message&&status==="ready"&&<p className={styles.notice} role="status" aria-live="polite">{message}</p>}
  <p className={styles.footer}>{t.noClinical}</p>
 </section>;
}
