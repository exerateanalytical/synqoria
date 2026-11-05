"use client";
import {useCallback,useEffect,useRef,useState} from "react";
import Link from "next/link";
import "./clinical-read.css";
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[89ab0-9a-f]{12}$/i;
const views=["FACILITY_QUEUE","STATION_QUEUE","PENDING_HANDOVERS","MANUAL_ESCALATIONS","PENDING_REASSESSMENTS","SAFETY_HOLDS","CLINICIAN_WORKLIST","ARRIVALS_SINCE"] as const;
type View=typeof views[number];
type Row={caseId:string;stage:string;revision:number;station:string|null;assignee:string|null;arrivedAt:string;paused:boolean;safetyHold:boolean;manualEscalation:boolean};
type Phase="idle"|"loading"|"ready"|"invalid"|"login"|"denied"|"unavailable";
const text={
 en:{title:"Triage worklist",subtitle:"Actual authorized worklist reads require an explicit set of case IDs and the current patient context. There is no unrestricted patient search.",patient:"Patient UUID",cases:"Permitted case UUIDs (one per line, maximum 50)",view:"Triage view",station:"Required station or clinician UUID",after:"UTC cutoff timestamp",action:"Read permitted triage cases",clear:"Clear sensitive context",stageFilter:"Stage filter",sort:"Sort arrival",newest:"Newest first",oldest:"Oldest first",none:"All stages",loading:"Loading verified worklist",idle:"No case data has been fetched",ready:"Current authorized case state",invalid:"Enter a valid patient UUID and 1–50 distinct case UUIDs with required view filters",login:"Verified staff session required",denied:"No authorized worklist can be disclosed",unavailable:"Triage service unavailable; no local fallback",noRows:"No authorized cases returned",case:"Case reference",stage:"Stage",revision:"Revision",arrived:"Arrival (UTC)",stationCol:"Station",assigned:"Assigned clinician",alerts:"Safety flags",noneAssigned:"Not assigned",paused:"Paused",hold:"Safety hold",escalated:"Escalation",back:"Workforce",notice:"No clinical assessment, queue command, handover, signing, score or patient search is executed from this screen. Server authorization is rechecked for each request."},
 fr:{title:"File de triage",subtitle:"Lecture autorisée d'une liste explicite de dossiers dans le contexte du patient. Aucune recherche libre de patients.",patient:"UUID du patient",cases:"UUID des dossiers autorisés (un par ligne, maximum 50)",view:"Vue de triage",station:"UUID du poste ou clinicien obligatoire",after:"Heure limite UTC",action:"Charger les dossiers autorisés",clear:"Effacer le contexte sensible",stageFilter:"Filtrer par étape",sort:"Trier par arrivée",newest:"Plus récents",oldest:"Plus anciens",none:"Toutes les étapes",loading:"Chargement de la file autorisée",idle:"Aucun dossier chargé",ready:"Statut actuel autorisé",invalid:"Saisir un UUID patient et 1 à 50 dossiers distincts et les filtres requis",login:"Session vérifiée nécessaire",denied:"Aucune liste autorisée ne peut être divulguée",unavailable:"Service de triage indisponible",noRows:"Aucun dossier autorisé",case:"Référence du dossier",stage:"Étape",revision:"Révision",arrived:"Arrivée UTC",stationCol:"Poste",assigned:"Clinicien affecté",alerts:"Alertes",noneAssigned:"Non attribué",paused:"Suspendu",hold:"Blocage sécurité",escalated:"Escalade",back:"Personnel",notice:"Aucune évaluation clinique, commande de file, transmission, signature, score ou recherche de patient n'est réalisée ici. Chaque demande est revérifiée par le serveur."}
};
export default function TriageQueueWorkspace(){
 const [locale,setLocale]=useState<"en"|"fr">("en");
 const [patientId,setPatientId]=useState(""),[caseText,setCaseText]=useState(""),[view,setView]=useState<View>("FACILITY_QUEUE");
 const [station,setStation]=useState(""),[after,setAfter]=useState("");
 const [rows,setRows]=useState<Row[]>([]),[phase,setPhase]=useState<Phase>("idle");
 const [stageFilter,setStageFilter]=useState("all"),[sort,setSort]=useState<"newest"|"oldest">("oldest");
 const abort=useRef<AbortController|null>(null),t=text[locale];
 const reset=useCallback(()=>{abort.current?.abort();abort.current=null;setRows([]);setPatientId("");setCaseText("");setStation("");setAfter("");setStageFilter("all");setPhase("idle");},[]);
 useEffect(()=>{const onHide=()=>{if(document.visibilityState==="hidden")reset();};document.addEventListener("visibilitychange",onHide);return()=>{abort.current?.abort();document.removeEventListener("visibilitychange",onHide);};},[reset]);
 const stationNeeded=view==="STATION_QUEUE"||view==="CLINICIAN_WORKLIST",timeNeeded=view==="ARRIVALS_SINCE";
 const caseIds=caseText.split(/[\s,;]+/).map(x=>x.trim().toLowerCase()).filter(Boolean);
 async function load(){
  abort.current?.abort();const controller=new AbortController();abort.current=controller;setRows([]);
  const distinct=new Set(caseIds);
  if(!uuid.test(patientId)||caseIds.length<1||caseIds.length>50||distinct.size!==caseIds.length||caseIds.some(x=>!uuid.test(x))||(stationNeeded&&!uuid.test(station))||(timeNeeded&&(!/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ$/.test(after)||!Number.isFinite(Date.parse(after))))){setPhase("invalid");return;}
  setPhase("loading");
  const payload={view,caseIds,...(stationNeeded?{stationOrClinician:station}:{}),...(timeNeeded?{after}:{})};
  try{
   const r=await fetch("/api/clinical/triage/views/"+encodeURIComponent(patientId),{method:"POST",headers:{"Content-Type":"application/json",Accept:"application/json"},body:JSON.stringify(payload),credentials:"same-origin",cache:"no-store",redirect:"error",signal:controller.signal});
   if(controller.signal.aborted)return;
   if(r.status===401){setPhase("login");return;}if(r.status===403||r.status===404){setPhase("denied");return;}
   if(r.status!==200||!r.headers.get("content-type")?.includes("application/json")){setPhase("unavailable");return;}
   const raw:unknown=await r.json();
   if(controller.signal.aborted)return;
   if(!raw||typeof raw!=="object"||!Array.isArray((raw as Record<string,unknown>).cases)){setPhase("unavailable");return;}
   const result=(raw as {cases:Row[]}).cases;
   if(result.length>caseIds.length||result.some(row=>!caseIds.includes(row.caseId)||!uuid.test(row.caseId)||!Number.isInteger(row.revision))){setPhase("unavailable");return;}
   setRows(result);setPhase("ready");
  }catch{if(!controller.signal.aborted)setPhase("unavailable");}
 }
 const visible=rows.filter(x=>stageFilter==="all"||x.stage===stageFilter).sort((a,b)=>sort==="newest"?b.arrivedAt.localeCompare(a.arrivedAt):a.arrivedAt.localeCompare(b.arrivedAt));
 const stages=[...new Set(rows.map(x=>x.stage))].sort();
 return <main className="syn-live-workspace" data-real-screen-id="M03-S01" lang={locale}>
  <nav className="syn-live-breadcrumb"><Link href="/workforce/clinical">{t.back}</Link><span>/</span><strong>M03-S01</strong></nav>
  <header className="syn-live-header"><p className="syn-live-kicker">M03-S01 · Authenticated restricted queue read</p><h1>{t.title}</h1><p>{t.subtitle}</p><div className="syn-live-languages"><button type="button" aria-pressed={locale==="en"} onClick={()=>setLocale("en")}>English</button><button type="button" aria-pressed={locale==="fr"} onClick={()=>setLocale("fr")}>Français</button></div></header>
  <section className="syn-live-panel"><h2>{t.title}</h2><form className="syn-live-form" onSubmit={e=>{e.preventDefault();void load();}} noValidate>
   <label>{t.patient}<input type="text" inputMode="text" maxLength={36} autoComplete="off" spellCheck={false} value={patientId} onChange={e=>setPatientId(e.target.value.trim().toLowerCase())}/></label>
   <label className="syn-live-wide">{t.cases}<textarea rows={3} maxLength={1900} autoComplete="off" spellCheck={false} value={caseText} onChange={e=>setCaseText(e.target.value)}/></label>
   <label>{t.view}<select value={view} onChange={e=>setView(e.target.value as View)}>{views.map(v=><option key={v} value={v}>{v.replaceAll("_"," ")}</option>)}</select></label>
   {stationNeeded&&<label>{t.station}<input type="text" maxLength={36} autoComplete="off" spellCheck={false} value={station} onChange={e=>setStation(e.target.value.trim().toLowerCase())}/></label>}
   {timeNeeded&&<label>{t.after}<input type="text" maxLength={30} autoComplete="off" value={after} onChange={e=>setAfter(e.target.value.trim())} placeholder="YYYY-MM-DDTHH:mm:ssZ"/></label>}
   <div className="syn-live-actions"><button type="submit" disabled={phase==="loading"}>{t.action}</button><button type="button" onClick={reset}>{t.clear}</button></div>
  </form>
  <p role="status" aria-live="polite" className="syn-live-status" data-triage-queue-status={phase}>{t[phase]}</p>
  {phase==="ready"&&<>
   <div className="syn-live-toolbar"><label>{t.stageFilter}<select value={stageFilter} onChange={e=>setStageFilter(e.target.value)}><option value="all">{t.none}</option>{stages.map(x=><option key={x} value={x}>{x}</option>)}</select></label><label>{t.sort}<select value={sort} onChange={e=>setSort(e.target.value as "newest"|"oldest")}><option value="oldest">{t.oldest}</option><option value="newest">{t.newest}</option></select></label></div>
   <div className="syn-live-table-scroll"><table><caption>{t.ready} · {visible.length} {t.case}</caption><thead><tr><th scope="col">{t.case}</th><th scope="col">{t.stage}</th><th scope="col">{t.revision}</th><th scope="col">{t.arrived}</th><th scope="col">{t.stationCol}</th><th scope="col">{t.assigned}</th><th scope="col">{t.alerts}</th></tr></thead><tbody>{visible.length?visible.map(x=><tr key={x.caseId}><td>{x.caseId}</td><td>{x.stage}</td><td>{x.revision}</td><td>{x.arrivedAt}</td><td>{x.station??t.noneAssigned}</td><td>{x.assignee??t.noneAssigned}</td><td>{[x.paused&&t.paused,x.safetyHold&&t.hold,x.manualEscalation&&t.escalated].filter(Boolean).join(", ")||"—"}</td></tr>):<tr><td colSpan={7}>{t.noRows}</td></tr>}</tbody></table></div>
  </>}
  <section className="syn-live-locked"><h3>Clinical actions locked</h3><p>{t.notice}</p></section>
  </section>
 </main>;
}
