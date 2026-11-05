"use client";
import {useCallback,useEffect,useRef,useState} from "react";
import Link from "next/link";
import "./clinical-read.css";
export type LiveScreenId="G-S03"|"G-S10"|"M01-S03"|"M03-S03";
type Status="idle"|"loading"|"ready"|"unauthenticated"|"not-disclosed"|"unavailable"|"invalid";
type Facility={facilityId:string;revision:number};
type Patient={patientId:string;identityStatus:"verified"|"provisional"|"merged";revision:number;facilityId:string};
type TriageCase={caseId:string;stage:string;revision:number;station:string|null;assignee:string|null;arrivedAt:string;paused:boolean;safetyHold:boolean;manualEscalation:boolean};
type Triage={case:TriageCase;facilityId:string};
type Health={process:"alive";readiness:"ready";kind:"service_health_only";clinicalCertification:false};
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[89ab0-9a-f]{12}$/i;
const definitions:Record<LiveScreenId,{en:string;fr:string;mode:"facility"|"health"|"patient"|"triage";limitsEn:string;limitsFr:string}>={
 "G-S03":{en:"Verified facility context",fr:"Contexte de l’établissement vérifié",mode:"facility",limitsEn:"This read verifies facility assignment; role/shift selection remains unimplemented until the OIDC/roster service is integrated.",limitsFr:"Cette lecture confirme l’établissement; le choix de rôle et d'équipe attend l’intégration du service de connexion et de planning."},
 "G-S10":{en:"Connectivity and dependency readiness",fr:"Connectivité et disponibilité des dépendances",mode:"health",limitsEn:"A green response indicates configured service reachability only, not clinical certification, backup assurance or safe offline operation.",limitsFr:"Une réponse positive indique seulement la disponibilité du service, non une certification clinique."},
 "M01-S03":{en:"Authorized patient identity record",fr:"Identité du patient autorisée",mode:"patient",limitsEn:"The backend currently discloses identity status and revision only. Demographics, contact data and corrections remain unavailable.",limitsFr:"Le service ne fournit actuellement que le statut de l'identité et sa révision; les données démographiques restent indisponibles."},
 "M03-S03":{en:"Authorized triage case status",fr:"Statut de triage autorisé",mode:"triage",limitsEn:"Current case and workflow status only. Vital signs, observation entry, priority decisions, clinical sign-off and handover are not enabled.",limitsFr:"Uniquement le statut du dossier et du parcours. Constantes, observations, priorité, signature et transfert ne sont pas activés."}
};
const messages={
 en:{title:"Real server-backed read",noInput:"Enter authorized identifiers; do not share identifiers in support reports.",patient:"Patient UUID",case:"Triage case UUID",refresh:"Read from hospital server",again:"Refresh authoritative record",clear:"Clear sensitive context",idle:"Not loaded",loading:"Checking verified authorization and source record",ready:"Verified source response",unauthenticated:"Verified staff login required",notDisclosed:"No authorized record can be disclosed",unavailable:"Clinical service or source integration unavailable",invalid:"Provide canonical UUID identifiers",facility:"Hospital facility UUID",status:"Identity status",revision:"Source revision",stage:"Workflow stage",arrived:"Arrival (UTC)",assigned:"Assigned clinician reference",station:"Station reference",paused:"Queue paused",hold:"Safety hold",escalation:"Manual escalation",yes:"Yes",no:"No",unknown:"Not assigned",gate:"Clinical mutation gate",locked:"Read only — authoritative clinical actions are not available here",context:"Real database/server response (when available)",source:"Source",notCertified:"Not clinically certified",back:"Back to clinical workspaces",error:"An access or service error occurred",health:"Configured cell readiness"},
 fr:{title:"Lecture réelle du serveur",noInput:"Saisir des identifiants autorisés; ne pas les inclure dans un rapport d’assistance.",patient:"UUID du patient",case:"UUID du dossier de triage",refresh:"Lire le serveur de l’hôpital",again:"Actualiser le dossier source",clear:"Effacer le contexte sensible",idle:"Non chargé",loading:"Vérification des autorisations et du dossier source",ready:"Réponse de source vérifiée",unauthenticated:"Connexion du personnel vérifiée nécessaire",notDisclosed:"Aucun dossier autorisé ne peut être communiqué",unavailable:"Service clinique ou intégration indisponible",invalid:"Saisir des UUID canoniques",facility:"UUID de l'établissement",status:"Statut d'identité",revision:"Révision source",stage:"Étape du parcours",arrived:"Arrivée (UTC)",assigned:"Référence du médecin affecté",station:"Référence du poste",paused:"File suspendue",hold:"Blocage sécurité",escalation:"Escalade manuelle",yes:"Oui",no:"Non",unknown:"Non affecté",gate:"Verrou clinique",locked:"Lecture seule — opérations cliniques interdites ici",context:"Réponse réelle de la base/du serveur (si disponible)",source:"Source",notCertified:"Non certifié cliniquement",back:"Retour à l'espace clinique",error:"Erreur d'accès ou de service",health:"Disponibilité de la cellule"}
} as const;

export default function ClinicalReadWorkspace({screenId}:{screenId:LiveScreenId}){
 const info=definitions[screenId];
 const [lang,setLang]=useState<"en"|"fr">("en"),t=messages[lang];
 const [patientId,setPatientId]=useState(""),[caseId,setCaseId]=useState("");
 const [status,setStatus]=useState<Status>("idle");
 const [data,setData]=useState<Facility|Patient|Triage|Health|null>(null);
 const [checkedAt,setCheckedAt]=useState("");
 const current=useRef<AbortController|null>(null);
 const run=useCallback(async()=>{
  current.current?.abort();const abort=new AbortController();current.current=abort;
  setData(null);setCheckedAt("");
  if((info.mode==="patient"||info.mode==="triage")&&!uuid.test(patientId)||info.mode==="triage"&&!uuid.test(caseId)){setStatus("invalid");return;}
  setStatus("loading");
  const endpoint=info.mode==="health"?"/api/clinical/health":info.mode==="facility"?"/api/clinical/facility":info.mode==="patient"?"/api/clinical/patients/"+encodeURIComponent(patientId):"/api/clinical/triage/"+encodeURIComponent(patientId)+"/"+encodeURIComponent(caseId);
  try{
   const response=await fetch(endpoint,{method:"GET",credentials:"same-origin",cache:"no-store",redirect:"error",headers:{Accept:"application/json"},signal:abort.signal});
   if(abort.signal.aborted)return;
   if(response.status===401){setStatus("unauthenticated");return;}
   if(response.status===404){setStatus("not-disclosed");return;}
   if(response.status!==200||!response.headers.get("content-type")?.includes("application/json")){setStatus("unavailable");return;}
   const raw:unknown=await response.json();
   if(abort.signal.aborted)return;
   if(!raw||typeof raw!=="object"||Array.isArray(raw)){setStatus("unavailable");return;}
   const d=raw as Record<string,unknown>;
   const valid=info.mode==="facility"?typeof d.facilityId==="string"&&uuid.test(d.facilityId)&&Number.isSafeInteger(d.revision):
    info.mode==="health"?d.process==="alive"&&d.readiness==="ready"&&d.clinicalCertification===false:
    info.mode==="patient"?d.patientId===patientId&&typeof d.facilityId==="string"&&["verified","provisional","merged"].includes(String(d.identityStatus))&&Number.isSafeInteger(d.revision):
    !!d.case&&typeof d.case==="object"&&!!(d.case as Record<string,unknown>).revision&&(d.case as Record<string,unknown>).caseId===caseId&&typeof d.facilityId==="string";
   if(!valid){setStatus("unavailable");return;}
   setData(raw as Facility|Patient|Triage|Health);setCheckedAt(new Date().toLocaleTimeString(lang==="fr"?"fr-FR":"en-GB"));setStatus("ready");
  }catch{if(!abort.signal.aborted)setStatus("unavailable");}
 },[caseId,info.mode,lang,patientId]);
 const clear=useCallback(()=>{current.current?.abort();current.current=null;setPatientId("");setCaseId("");setData(null);setCheckedAt("");setStatus("idle");},[]);
 useEffect(()=>{
  const onHide=()=>{if(document.visibilityState==="hidden")clear();};
  document.addEventListener("visibilitychange",onHide);
  const blur=()=>{current.current?.abort();setData(null);setCheckedAt("");setStatus("idle");};
  window.addEventListener("pagehide",blur);
  return()=>{clear();document.removeEventListener("visibilitychange",onHide);window.removeEventListener("pagehide",blur);};
 },[clear]);
 const detailRows:[string,string|number][]=[];
 if(status==="ready"&&data){
  if(info.mode==="facility"){const d=data as Facility;detailRows.push([t.facility,d.facilityId],[t.revision,d.revision]);}
  else if(info.mode==="health"){detailRows.push([t.health,"Ready (backend verified)"],[t.notCertified,t.yes]);}
  else if(info.mode==="patient"){const d=data as Patient;detailRows.push([t.patient,d.patientId],[t.facility,d.facilityId],[t.status,d.identityStatus],[t.revision,d.revision]);}
  else {const d=(data as Triage).case;detailRows.push([t.case,d.caseId],[t.facility,(data as Triage).facilityId],[t.stage,d.stage],[t.revision,d.revision],[t.arrived,d.arrivedAt],[t.station,d.station??t.unknown],[t.assigned,d.assignee??t.unknown],[t.paused,d.paused?t.yes:t.no],[t.hold,d.safetyHold?t.yes:t.no],[t.escalation,d.manualEscalation?t.yes:t.no]);}
 }
 return <main className="syn-live-workspace" lang={lang} data-real-screen-id={screenId}>
  <a className="syn-live-skip" href="#syn-live-content">Skip to main content</a>
  <nav className="syn-live-breadcrumb" aria-label="Breadcrumb"><Link href="/workforce/clinical">{t.back}</Link><span>/</span><strong>{screenId}</strong></nav>
  <header className="syn-live-header"><p className="syn-live-kicker">{t.title} · {screenId}</p><h1>{lang==="fr"?info.fr:info.en}</h1><p>{lang==="fr"?info.limitsFr:info.limitsEn}</p><div className="syn-live-languages"><button type="button" aria-pressed={lang==="en"} onClick={()=>setLang("en")}>English</button><button type="button" aria-pressed={lang==="fr"} onClick={()=>setLang("fr")}>Français</button></div></header>
  <section id="syn-live-content" className="syn-live-panel"><h2>{t.source}</h2><p>{t.noInput}</p>
   {(info.mode==="patient"||info.mode==="triage")&&<form onSubmit={e=>{e.preventDefault();void run();}} className="syn-live-form">
    <label>{t.patient}<input type="text" required inputMode="text" autoComplete="off" spellCheck={false} maxLength={36} value={patientId} onChange={e=>setPatientId(e.target.value.trim().toLowerCase())} placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"/></label>
    {info.mode==="triage"&&<label>{t.case}<input type="text" required inputMode="text" autoComplete="off" spellCheck={false} maxLength={36} value={caseId} onChange={e=>setCaseId(e.target.value.trim().toLowerCase())} placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"/></label>}
    <div className="syn-live-actions"><button type="submit" disabled={status==="loading"}>{status==="ready"?t.again:t.refresh}</button><button type="button" onClick={clear}>{t.clear}</button></div>
   </form>}
   {(info.mode==="facility"||info.mode==="health")&&<div className="syn-live-actions"><button type="button" onClick={()=>void run()} disabled={status==="loading"}>{status==="ready"?t.again:t.refresh}</button><button type="button" onClick={clear}>{t.clear}</button></div>}
   <div className="syn-live-status" aria-live="polite" role="status" data-clinical-read-status={status}>{t[status]}</div>
   {status==="ready"&&<section className="syn-live-data" aria-label={t.context}><h3>{t.context}</h3><dl>{detailRows.map(([name,value])=><div key={name}><dt>{name}</dt><dd>{String(value)}</dd></div>)}</dl>{checkedAt&&<p>Checked at: {checkedAt}</p>}</section>}
   <section className="syn-live-locked"><h3>{t.gate}</h3><p>{t.locked}</p><p>{t.notCertified}</p></section>
  </section>
 </main>;
}
