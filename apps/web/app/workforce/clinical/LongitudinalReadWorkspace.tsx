"use client";
import {useCallback,useEffect,useRef,useState} from "react";
import Link from "next/link";
import {ArrowLeft,ClipboardList,LockKeyhole,RefreshCcw,ShieldAlert,Search,FileCheck2,Activity} from "lucide-react";
import "./longitudinal-read.css";

export type LongitudinalScreenId="M04-S02"|"M05-S01"|"M05-S04"|"M05-S05"|"M06-S02"|"M06-S03"|"M06-S04"|"M06-S05";
type Resource="summary"|"audit"|"note-index"|"observations"|"note"|"lineage";
type Phase="idle"|"invalid"|"loading"|"ready"|"login"|"denied"|"unavailable";
type Summary={encounterId:string;revision:number;stage:string;onLeave:boolean;notes:number;observations:number;auditHead:string};
type Note={id:string;status:string;author:string;title:string;body:string;confidential:boolean};
type NoteMeta={id:string;status:string;title:string;confidential:boolean};
type Observation={id:string;code:string;value:string;unit:string;measuredAt:string;status:string;replaces?:string|null};
type AuditEvent={revision:number;operation:string;at:string;digest:string};
type ResponseData={resource:Resource;patientId:string;encounterId:string;facilityId:string;data:Summary|Note|{notes:NoteMeta[];nextId:string|null}|{observations:Observation[]}|{lineage:Observation[]}|{history:AuditEvent[];nextRevision:number|null}};
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[89ab0-9a-f]{12}$/i;
const code=/^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$/;
const config:Record<LongitudinalScreenId,{en:string;fr:string;resource:Resource;explainEn:string;explainFr:string}>={
 "M04-S02":{en:"Encounter overview",fr:"Vue générale de la consultation",resource:"summary",explainEn:"Authoritative summary for one patient and encounter. A facility-wide encounter search, admission or bed assignment is not connected.",explainFr:"Résumé d'une consultation vérifiée. La recherche globale, l’admission et l’attribution des lits ne sont pas connectées."},
 "M05-S01":{en:"Clinical workspace — encounter summary",fr:"Espace clinique — résumé de consultation",resource:"summary",explainEn:"Read authorized encounter stage, note count and observation count. Diagnoses, allergies, orders and clinical commands remain unavailable.",explainFr:"Consultez l'état de la consultation et les nombres de notes/observations. Diagnostics, allergies, prescriptions et actes restent indisponibles."},
 "M05-S04":{en:"Note review — signing locked",fr:"Révision d’une note — signature verrouillée",resource:"note",explainEn:"Read a verified clinical note by exact ID. No signature, approval or note changes are permitted.",explainFr:"Consultez une note par identifiant exact. Aucune signature, approbation ou modification autorisée."},
 "M05-S05":{en:"Signed note and amendment inspection",fr:"Note signée et amendements — consultation",resource:"note",explainEn:"Inspect the source note status and content. A returned draft must not be represented as signed, and amendments are not created here.",explainFr:"Consultez le statut réel et le contenu d’une note. Une ébauche n'est jamais considérée comme signée."},
 "M06-S02":{en:"Observation review — code-scoped only",fr:"Observations — filtrage par code",resource:"observations",explainEn:"Read measurements by known code for one encounter. This is not a hospital-wide verification queue.",explainFr:"Consultez les mesures d’une consultation par code. Ce n'est pas une file de vérification hospitalière."},
 "M06-S03":{en:"Observation verification review",fr:"Vérification d’observation — revue",resource:"lineage",explainEn:"Read authoritative correction lineage for one known observation. No value is verified or signed here.",explainFr:"Consultez les corrections d'une observation. Aucune valeur n'est vérifiée ni signée ici."},
 "M06-S04":{en:"Longitudinal measurements",fr:"Mesures longitudinales",resource:"observations",explainEn:"Read a bounded, code-filtered observation timeline from one encounter. No fabricated chart values or reference ranges.",explainFr:"Consultez les mesures réelles d’une consultation par code. Aucune valeur ou référence inventée."},
 "M06-S05":{en:"Observation correction lineage",fr:"Historique des corrections",resource:"lineage",explainEn:"Read complete available lineage for a known observation. Corrections and invalidations remain disabled.",explainFr:"Consultez la lignée d'une observation. Les corrections et invalidations restent désactivées."}
};
const copy={
 en:{back:"All clinical screens",origin:"Verified clinical source",protected:"Source-backed read-only subset",patient:"Patient UUID",encounter:"Encounter UUID",member:"Clinical note / observation UUID",code:"Measurement code",load:"Load authorized record",clear:"Clear patient context",filter:"Local filter",status:"Status",facility:"Facility UUID",revision:"Revision",stage:"Encounter stage",leave:"On leave",countNotes:"Notes",countObservations:"Observations",auditHead:"Audit head digest",title:"Title",author:"Author",body:"Clinical note text",confidential:"Confidential",yes:"Yes",no:"No",event:"Operation",at:"UTC time",digest:"Audit digest",observations:"Source measurements",unit:"Unit",value:"Value",measured:"Measured UTC",replaces:"Replaces observation",idle:"No server record loaded",invalid:"Enter valid patient, encounter and resource identifiers",loading:"Fetching authorized record from hospital server",ready:"Verified backend response available",login:"Verified staff authentication required",denied:"Record not found or disclosure denied",unavailable:"Clinical source unavailable or returned an invalid record",noData:"No authorized source records matched",notApproved:"Clinical writes disabled",notice:"The production identity issuer and hospital-cell integration are not qualified. No save, correction, signature, discharge, prescribing or result verification occurs from this screen.",sensitive:"Sensitive clinical data. Do not share screenshots or identifiers through unverified channels.",summary:"Current encounter",codeHint:"e.g. 8310-5",noteStatus:"Note status",empty:"No values in this source response",caution:"Source metadata does not prove clinical certification"},
 fr:{back:"Écrans cliniques",origin:"Source clinique vérifiée",protected:"Lecture vérifiée uniquement",patient:"UUID du patient",encounter:"UUID de la consultation",member:"UUID de la note ou observation",code:"Code de mesure",load:"Charger le dossier autorisé",clear:"Effacer le contexte patient",filter:"Filtre local",status:"Statut",facility:"UUID de l’établissement",revision:"Révision",stage:"Étape de consultation",leave:"En congé",countNotes:"Notes",countObservations:"Observations",auditHead:"Empreinte d’audit",title:"Titre",author:"Auteur",body:"Contenu de la note",confidential:"Confidentiel",yes:"Oui",no:"Non",event:"Opération",at:"Heure UTC",digest:"Empreinte d’audit",observations:"Mesures sources",unit:"Unité",value:"Valeur",measured:"Mesuré UTC",replaces:"Remplace l’observation",idle:"Aucun dossier chargé",invalid:"Saisir des identifiants patient, consultation et ressource valides",loading:"Chargement du dossier autorisé",ready:"Réponse vérifiée du serveur",login:"Authentification du personnel nécessaire",denied:"Dossier absent ou accès refusé",unavailable:"Source clinique indisponible ou dossier non valide",noData:"Aucun dossier source autorisé",notApproved:"Écriture clinique désactivée",notice:"L'identité et la cellule hospitalière ne sont pas qualifiées. Aucun enregistrement, correction, signature, sortie, prescription ou validation de résultat ici.",sensitive:"Données cliniques sensibles. Ne partagez pas les captures d'écran sans autorisation.",summary:"Consultation actuelle",codeHint:"p. ex. 8310-5",noteStatus:"Statut de la note",empty:"Aucune valeur retournée",caution:"Le contenu source ne vaut pas certification clinique"}
};
function Field({name,value}:{name:string;value:string|number|boolean}){
 return <div className="syn-long-field"><dt>{name}</dt><dd>{typeof value==="boolean"?(value?"Yes":"No"):String(value)}</dd></div>;
}
export default function LongitudinalReadWorkspace({screenId}:{screenId:LongitudinalScreenId}){
 const setting=config[screenId];
 const [locale,setLocale]=useState<"en"|"fr">("en"),t=copy[locale];
 const [patientId,setPatientId]=useState(""),[encounterId,setEncounterId]=useState(""),[memberId,setMemberId]=useState(""),[codeValue,setCodeValue]=useState("");
 const [phase,setPhase]=useState<Phase>("idle"),[result,setResult]=useState<ResponseData|null>(null);
 const [search,setSearch]=useState("");
 const controller=useRef<AbortController|null>(null);
 const clear=useCallback(()=>{controller.current?.abort();controller.current=null;setPatientId("");setEncounterId("");setMemberId("");setCodeValue("");setSearch("");setPhase("idle");setResult(null);},[]);
 useEffect(()=>{const onHide=()=>{if(document.visibilityState==="hidden")clear();};document.addEventListener("visibilitychange",onHide);return()=>{controller.current?.abort();document.removeEventListener("visibilitychange",onHide);};},[clear]);
 const requireMember=["note","lineage"].includes(setting.resource),requireCode=setting.resource==="observations";
 const load=useCallback(async()=>{
  controller.current?.abort();const abort=new AbortController();controller.current=abort;setResult(null);
  if(!uuid.test(patientId)||!uuid.test(encounterId)||requireMember&&!uuid.test(memberId)||requireCode&&!code.test(codeValue)){setPhase("invalid");return;}
  setPhase("loading");
  const base="/api/clinical/longitudinal/"+patientId+"/"+encounterId+"/";
  const endpoint=setting.resource==="note"?base+"notes/"+memberId:setting.resource==="lineage"?base+"observations/"+memberId+"/lineage":base+setting.resource+(requireCode?"?code="+encodeURIComponent(codeValue)+"&limit=50":"");
  try{
   const response=await fetch(endpoint,{method:"GET",credentials:"same-origin",cache:"no-store",redirect:"error",headers:{Accept:"application/json"},signal:abort.signal});
   if(abort.signal.aborted)return;
   if(response.status===401){setPhase("login");return;}
   if(response.status===403||response.status===404){setPhase("denied");return;}
   if(response.status!==200||!response.headers.get("content-type")?.toLowerCase().includes("application/json")){setPhase("unavailable");return;}
   const value:unknown=await response.json();
   if(abort.signal.aborted)return;
   if(!value||typeof value!=="object"||Array.isArray(value)){setPhase("unavailable");return;}
   const obj=value as Record<string,unknown>;
   if(obj.patientId!==patientId||obj.encounterId!==encounterId||obj.resource!==setting.resource||
    typeof obj.facilityId!=="string"||!uuid.test(obj.facilityId)||!obj.data||typeof obj.data!=="object"){setPhase("unavailable");return;}
   setResult(value as ResponseData);setPhase("ready");
  }catch{if(!abort.signal.aborted)setPhase("unavailable");}
 },[patientId,encounterId,memberId,codeValue,requireMember,requireCode,setting.resource]);
 function visibleRows(rows:Observation[]):Observation[]{
  const term=search.trim().toLowerCase();
  return rows.filter(x=>!term||[x.code,x.status,x.value,x.unit,x.measuredAt].join(" ").toLowerCase().includes(term));
 }
 const data=result?.data;
 const summary=setting.resource==="summary"&&phase==="ready"?data as Summary:null;
 const note=setting.resource==="note"&&phase==="ready"?data as Note:null;
 const lines=setting.resource==="lineage"&&phase==="ready"?(data as {lineage:Observation[]}).lineage:null;
 const observations=setting.resource==="observations"&&phase==="ready"?(data as {observations:Observation[]}).observations:null;
 const measures=visibleRows(lines??observations??[]);
 return <main className="syn-long" data-real-screen-id={screenId} lang={locale}>
  <nav className="syn-long-crumb"><Link href="/workforce/clinical"><ArrowLeft size={17} aria-hidden="true"/>{t.back}</Link><span>/</span><strong>{screenId}</strong></nav>
  <header className="syn-long-head"><div><p className="syn-long-kicker"><Activity size={16}/>{screenId} · {t.origin}</p><h1>{locale==="fr"?setting.fr:setting.en}</h1><p>{locale==="fr"?setting.explainFr:setting.explainEn}</p></div><div className="syn-long-language" role="group" aria-label="Language"><button type="button" onClick={()=>setLocale("en")} aria-pressed={locale==="en"}>EN</button><button type="button" onClick={()=>setLocale("fr")} aria-pressed={locale==="fr"}>FR</button></div></header>
  <div className="syn-long-warning" role="note"><ShieldAlert size={22} aria-hidden="true"/><p>{t.sensitive} {t.caution}</p></div>
  <section className="syn-long-panel"><h2>{t.protected}</h2>
   <form noValidate onSubmit={e=>{e.preventDefault();void load();}} className="syn-long-form">
    <label>{t.patient}<input type="text" value={patientId} onChange={e=>setPatientId(e.target.value.trim().toLowerCase())} maxLength={36} autoComplete="off" spellCheck={false} aria-label={t.patient}/></label>
    <label>{t.encounter}<input type="text" value={encounterId} onChange={e=>setEncounterId(e.target.value.trim().toLowerCase())} maxLength={36} autoComplete="off" spellCheck={false} aria-label={t.encounter}/></label>
    {requireMember&&<label>{t.member}<input type="text" value={memberId} onChange={e=>setMemberId(e.target.value.trim().toLowerCase())} maxLength={36} autoComplete="off" spellCheck={false} aria-label={t.member}/></label>}
    {requireCode&&<label>{t.code}<input type="text" value={codeValue} onChange={e=>setCodeValue(e.target.value.trim())} maxLength={128} placeholder={t.codeHint} autoComplete="off" spellCheck={false} aria-label={t.code}/></label>}
    <div className="syn-long-actions"><button type="submit" disabled={phase==="loading"}><RefreshCcw size={16}/>{t.load}</button><button type="button" onClick={clear}>{t.clear}</button></div>
   </form>
   <div className="syn-long-status" data-clinical-status={phase} role="status" aria-live="polite">{t[phase]}</div>
   {phase==="ready"&&result&&<>
    <p className="syn-long-facility">{t.facility}: {result.facilityId}</p>
    {summary&&<dl className="syn-long-grid"><Field name={t.encounter} value={summary.encounterId}/><Field name={t.revision} value={summary.revision}/><Field name={t.stage} value={summary.stage}/><Field name={t.leave} value={summary.onLeave?t.yes:t.no}/><Field name={t.countNotes} value={summary.notes}/><Field name={t.countObservations} value={summary.observations}/><Field name={t.auditHead} value={summary.auditHead}/></dl>}
    {note&&<section className="syn-long-note"><h3>{t.title}: {note.title}</h3><dl className="syn-long-grid"><Field name={t.member} value={note.id}/><Field name={t.noteStatus} value={note.status}/><Field name={t.author} value={note.author}/><Field name={t.confidential} value={note.confidential?t.yes:t.no}/></dl><h4>{t.body}</h4><div className="syn-long-note-body">{note.body}</div></section>}
    {(lines||observations)&&<><label className="syn-long-search"><Search size={16}/>{t.filter}<input type="search" maxLength={80} autoComplete="off" value={search} onChange={e=>setSearch(e.target.value)}/></label><div className="syn-long-table-wrap"><table><caption>{t.observations}: {measures.length}</caption><thead><tr><th scope="col">{t.member}</th><th scope="col">{t.code}</th><th scope="col">{t.value}</th><th scope="col">{t.unit}</th><th scope="col">{t.measured}</th><th scope="col">{t.status}</th>{lines&&<th scope="col">{t.replaces}</th>}</tr></thead><tbody>{measures.map(m=><tr key={m.id}><td>{m.id}</td><td>{m.code}</td><td>{m.value}</td><td>{m.unit}</td><td>{m.measuredAt}</td><td>{m.status}</td>{lines&&<td>{m.replaces??"—"}</td>}</tr>)}{measures.length===0&&<tr><td colSpan={lines?7:6}>{t.noData}</td></tr>}</tbody></table></div></>}
   </>}
   <section className="syn-long-locked"><LockKeyhole size={19} aria-hidden="true"/><div><h3>{t.notApproved}</h3><p>{t.notice}</p></div></section>
  </section>
 </main>;
}
