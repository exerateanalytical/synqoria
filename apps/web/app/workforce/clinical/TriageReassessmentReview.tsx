"use client";
import {useCallback,useEffect,useRef,useState} from "react";
import Link from "next/link";
import {ArrowLeft,CalendarClock,ChevronRight,History,LockKeyhole,RefreshCcw,ShieldAlert} from "lucide-react";
import "./triage-review.css";

const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[89ab0-9a-f]{12}$/i;
type RecordEntry={revision:number;operation:string;occurredAt:string;digest:string};
type Page={patientId:string;caseId:string;facilityId:string;currentCaseRevision:number;history:RecordEntry[];nextAfterRevision:number|null};
type Phase="idle"|"invalid"|"loading"|"ready"|"login"|"denied"|"unavailable";
const strings={
 en:{title:"Reassessment audit review",subtitle:"Inspect a real, patient-scoped triage change history. Clinical reassessment capture is unavailable until the authorized command and signing services are integrated.",patient:"Authorized patient UUID",triage:"Triage case UUID",load:"Read source audit history",more:"Load next verified page",clear:"Clear identifiers and records",intro:"Audit events report recorded changes, not medical conclusions. No patient narrative or vital signs are disclosed in this read-only view.",idle:"No case history requested",invalid:"Enter valid patient and case UUIDs",loading:"Loading authorized source history",ready:"Authorized audit metadata received",login:"Verified staff session required",denied:"No case history is available for disclosure",unavailable:"Clinical audit service unavailable; no cached history used",history:"Recorded events",revision:"Revision",operation:"Recorded operation",occurred:"Recorded UTC time",digest:"Event digest",empty:"No events returned for this requested revision window",total:"Events on this page",current:"Current source case revision",next:"Next revision cursor",complete:"No further pages currently indicated",locked:"Clinical reassessment commands remain locked",explain:"Do not interpret these events as an approved change of clinical priority. Authorized reassessment requires validated clinical observations, clinician attestation, revision fences and audit evidence.",back:"Clinical workspaces",facility:"Verified facility",changed:"A newer revision may exist; refresh for current source history",source:"Authoritative source",unknown:"No recorded value"},
 fr:{title:"Revue d’audit du triage",subtitle:"Consultez l’historique réel du triage dans le contexte du patient. Les réévaluations cliniques sont indisponibles sans service de commande et de signature autorisé.",patient:"UUID du patient autorisé",triage:"UUID du dossier de triage",load:"Lire l'historique d'audit",more:"Charger la page vérifiée suivante",clear:"Effacer les identifiants et dossiers",intro:"Les événements d’audit sont des changements enregistrés, pas des conclusions médicales. Aucun récit ni signe vital n'est divulgué.",idle:"Aucun historique demandé",invalid:"Saisissez des UUID patient et dossier valides",loading:"Chargement de l'historique autorisé",ready:"Métadonnées d’audit autorisées reçues",login:"Connexion du personnel vérifiée nécessaire",denied:"Aucun historique autorisé à divulguer",unavailable:"Service d’audit indisponible; aucun historique local utilisé",history:"Événements enregistrés",revision:"Révision",operation:"Opération enregistrée",occurred:"Heure UTC",digest:"Empreinte de l'événement",empty:"Aucun événement pour cette fenêtre de révisions",total:"Événements sur cette page",current:"Révision courante du dossier",next:"Curseur de la révision suivante",complete:"Aucune autre page indiquée",locked:"Commandes de réévaluation clinique verrouillées",explain:"Les événements ne prouvent pas la validation d'une priorité médicale. La réévaluation exige des observations vérifiées, une attestation clinique, le suivi des versions et des preuves d’audit.",back:"Espaces cliniques",facility:"Établissement vérifié",changed:"Une version plus récente peut exister; actualisez l’historique source",source:"Source faisant autorité",unknown:"Valeur non enregistrée"}
};
function validPage(v:unknown,patientId:string,caseId:string,previous:number):v is Page{
 if(!v||typeof v!=="object"||Array.isArray(v))return false;
 const x=v as Record<string,unknown>;
 if(x.patientId!==patientId||x.caseId!==caseId||typeof x.facilityId!=="string"||!uuid.test(x.facilityId)||
  !Number.isSafeInteger(x.currentCaseRevision)||Number(x.currentCaseRevision)<1||
  !Array.isArray(x.history)||x.history.length>100)return false;
 let last=previous;
 for(const item of x.history){
  if(!item||typeof item!=="object"||Array.isArray(item))return false;
  const e=item as Record<string,unknown>;
  if(!Number.isSafeInteger(e.revision)||Number(e.revision)<=last||typeof e.operation!=="string"||
     !/^[A-Z_]{2,80}$/.test(e.operation)||typeof e.occurredAt!=="string"||
     !Number.isFinite(Date.parse(e.occurredAt))||typeof e.digest!=="string"||!/^[0-9a-f]{64}$/.test(e.digest))return false;
  last=Number(e.revision);
 }
 if(x.nextAfterRevision!==null&&(!Number.isSafeInteger(x.nextAfterRevision)||x.nextAfterRevision!==last||x.history.length!==100))return false;
 return true;
}
export default function TriageReassessmentReview(){
 const [locale,setLocale]=useState<"en"|"fr">("en");
 const [patientId,setPatientId]=useState(""),[caseId,setCaseId]=useState("");
 const [records,setRecords]=useState<RecordEntry[]>([]),[phase,setPhase]=useState<Phase>("idle");
 const [currentRevision,setCurrentRevision]=useState<number|null>(null),[facilityId,setFacilityId]=useState("");
 const [next,setNext]=useState<number|null>(null);
 const abort=useRef<AbortController|null>(null),t=strings[locale];
 const clear=useCallback(()=>{abort.current?.abort();abort.current=null;setPatientId("");setCaseId("");setPhase("idle");setRecords([]);setNext(null);setCurrentRevision(null);setFacilityId("");},[]);
 useEffect(()=>{const onHide=()=>{if(document.visibilityState==="hidden")clear();};document.addEventListener("visibilitychange",onHide);return()=>{abort.current?.abort();document.removeEventListener("visibilitychange",onHide);};},[clear]);
 const read=useCallback(async(cursor=0)=>{
  abort.current?.abort();const controller=new AbortController();abort.current=controller;
  if(!uuid.test(patientId)||!uuid.test(caseId)||!Number.isInteger(cursor)||cursor<0){setPhase("invalid");return;}
  setPhase("loading");
  try{
   const path="/api/clinical/triage/"+encodeURIComponent(patientId)+"/"+encodeURIComponent(caseId)+"/history?afterRevision="+cursor;
   const response=await fetch(path,{method:"GET",headers:{Accept:"application/json"},cache:"no-store",credentials:"same-origin",redirect:"error",signal:controller.signal});
   if(controller.signal.aborted)return;
   if(response.status===401){setRecords([]);setPhase("login");return;}
   if(response.status===404||response.status===403){setRecords([]);setPhase("denied");return;}
   if(response.status!==200||!response.headers.get("content-type")?.includes("application/json")){setRecords([]);setPhase("unavailable");return;}
   const result:unknown=await response.json();
   if(controller.signal.aborted)return;
   if(!validPage(result,patientId,caseId,cursor)){setRecords([]);setPhase("unavailable");return;}
   const page=result as Page;
   setFacilityId(page.facilityId);setCurrentRevision(page.currentCaseRevision);
   setRecords(previous=>cursor===0?page.history:[...previous,...page.history]);
   setNext(page.nextAfterRevision);setPhase("ready");
  }catch{if(!controller.signal.aborted){setRecords([]);setPhase("unavailable");}}
 },[patientId,caseId]);
 return <main className="syn-audit" data-real-screen-id="M03-S04" lang={locale}>
  <a className="syn-audit-skip" href="#audit-content">Skip to history</a>
  <nav className="syn-audit-crumb"><Link href="/workforce/clinical"><ArrowLeft size={16} aria-hidden="true"/>{t.back}</Link><span>/ M03-S04</span></nav>
  <header className="syn-audit-header"><div><p className="syn-audit-kicker">M03-S04 · <History size={16} aria-hidden="true"/> {t.source}</p><h1>{t.title}</h1><p>{t.subtitle}</p></div>
   <div role="group" aria-label="Language" className="syn-audit-language"><button type="button" aria-pressed={locale==="en"} onClick={()=>setLocale("en")}>EN</button><button type="button" aria-pressed={locale==="fr"} onClick={()=>setLocale("fr")}>FR</button></div>
  </header>
  <section id="audit-content" className="syn-audit-panel">
   <h2>{t.history}</h2><p>{t.intro}</p>
   <form noValidate onSubmit={e=>{e.preventDefault();setRecords([]);setNext(null);void read(0);}} className="syn-audit-form">
    <label>{t.patient}<input type="text" inputMode="text" maxLength={36} spellCheck={false} autoComplete="off" value={patientId} onChange={e=>setPatientId(e.target.value.trim().toLowerCase())} placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"/></label>
    <label>{t.triage}<input type="text" maxLength={36} spellCheck={false} autoComplete="off" value={caseId} onChange={e=>setCaseId(e.target.value.trim().toLowerCase())} placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"/></label>
    <div className="syn-audit-actions"><button type="submit" disabled={phase==="loading"}><RefreshCcw size={16} aria-hidden="true"/>{t.load}</button><button type="button" onClick={clear}>{t.clear}</button></div>
   </form>
   <div role="status" aria-live="polite" className="syn-audit-status" data-audit-status={phase}>{t[phase]}</div>
   {phase==="ready"&&<><div className="syn-audit-summary"><div><span>{t.facility}</span><strong>{facilityId}</strong></div><div><span>{t.current}</span><strong>{currentRevision??t.unknown}</strong></div><div><span>{t.total}</span><strong>{records.length}</strong></div></div>
    {records.length?<div className="syn-audit-scroll"><table><caption>{t.history} · M03-S04</caption><thead><tr><th scope="col">{t.revision}</th><th scope="col">{t.operation}</th><th scope="col">{t.occurred}</th><th scope="col">{t.digest}</th></tr></thead><tbody>{records.map(e=><tr key={e.revision}><td>{e.revision}</td><td>{e.operation.replaceAll("_"," ")}</td><td>{e.occurredAt}</td><td className="syn-audit-digest"><code>{e.digest}</code></td></tr>)}</tbody></table></div>:<p>{t.empty}</p>}
    {next!==null?<button type="button" className="syn-audit-next" onClick={()=>void read(next)}><ChevronRight size={16} aria-hidden="true"/>{t.more}</button>:<p className="syn-audit-meta">{t.complete}</p>}
   </>}
   <section className="syn-audit-locked" aria-label={t.locked}><ShieldAlert size={20} aria-hidden="true"/><div><h3>{t.locked}</h3><p>{t.explain}</p></div><LockKeyhole size={19} aria-hidden="true"/></section>
  </section>
 </main>;
}
