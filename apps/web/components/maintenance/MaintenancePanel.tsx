"use client";
import {useCallback,useEffect,useState} from "react";
import styles from "../../app/workforce/operations/operations.module.css";

/**
 * Read-only preventive-maintenance views mounted on the EXISTING M23 canonical screens (no new route or screen):
 * M23-S01 servicing calendar, M23-S03 planner health + calendar + policy, M23-S05 independent inspection queue.
 * Every call is a same-origin GET through the allow-listed operations proxy. Planning, linking, calibration and approvals stay
 * API-only; this panel never creates, transitions or approves anything, and shows nothing the server did not return for the
 * caller's verified cell and facility.
 */
type Row=Record<string,string|number|boolean|null>;
type Phase="loading"|"ready"|"denied"|"unavailable";
const L={
 calendar:"Servicing calendar · Calendrier d'entretien",health:"Planner health · État du planificateur",
 policy:"Maintenance policy · Politique de maintenance",queue:"Awaiting independent verification · En attente de vérification indépendante",
 none:"Nothing scheduled in this window · Rien de planifié sur cette période",noQueue:"No work awaits your independent verification · Aucun travail à vérifier par vous",
 denied:"You do not have permission to view this · Vous n'avez pas l'autorisation de voir ceci",
 unavailable:"Maintenance data is temporarily unavailable · Données de maintenance indisponibles",
 loading:"Loading… · Chargement…",refresh:"Refresh · Actualiser",
 unknown:"UNKNOWN is never shown as healthy · INCONNU n'est jamais affiché comme sain"
};
const PROJECTION:Record<string,string>={SCHEDULED:"Scheduled · Planifié",DUE:"Due · À faire",OVERDUE:"Overdue · En retard",BLOCKED:"Blocked · Bloqué",
 IN_SERVICE:"In service · En intervention",DONE:"Done · Terminé",CANCELLED:"Cancelled · Annulé"};
const base="/api/operations/proxy/M23/maintenance/";
async function get<T>(path:string):Promise<T>{
 const res=await fetch(base+path,{credentials:"same-origin",cache:"no-store",headers:{Accept:"application/json"}});
 if(res.status===401||res.status===403)throw Object.assign(new Error("denied"),{denied:true});
 if(!res.ok)throw new Error("unavailable");
 return await res.json() as T;
}
function text(value:unknown){return value===null||value===undefined?"—":String(value);}
function when(value:unknown){
 if(typeof value!=="string")return "—";
 const date=new Date(value);
 return Number.isNaN(date.getTime())?"—":date.toISOString().replace("T"," ").slice(0,16)+" UTC";
}
function useRead<T>(path:string){
 const [phase,setPhase]=useState<Phase>("loading");
 const [data,setData]=useState<T|null>(null);
 const [tick,setTick]=useState(0);
 useEffect(()=>{
  let current=true;setPhase("loading");
  get<T>(path).then(v=>{if(current){setData(v);setPhase("ready");}})
   .catch(e=>{if(current){setData(null);setPhase((e as {denied?:boolean}).denied?"denied":"unavailable");}});
  return ()=>{current=false;};
 },[path,tick]);
 return {phase,data,reload:useCallback(()=>setTick(n=>n+1),[])};
}
function Gate({phase}:{phase:Phase}){
 if(phase==="loading")return <p role="status">{L.loading}</p>;
 if(phase==="denied")return <p role="alert">{L.denied}</p>;
 if(phase==="unavailable")return <p role="alert">{L.unavailable}</p>;
 return null;
}
function Calendar(){
 // The window is fixed by the client clock only to choose a range; the server bounds it (366 days, 200 rows) and scopes it.
 const [range]=useState(()=>{const now=Date.now();return {from:new Date(now-30*86400000).toISOString(),to:new Date(now+90*86400000).toISOString()};});
 const {phase,data,reload}=useRead<Row[]>("calendar?from="+encodeURIComponent(range.from)+"&to="+encodeURIComponent(range.to)+"&limit=100");
 return <section className={styles.opsPanel} aria-labelledby="m23-calendar-h">
  <div className={styles.opsHeader}><h3 id="m23-calendar-h">{L.calendar}</h3><button type="button" onClick={reload}>{L.refresh}</button></div>
  <Gate phase={phase}/>
  {phase==="ready"&&data&&(data.length===0?<p>{L.none}</p>:
   <div className={styles.opsTableWrap} role="region" aria-label={L.calendar}>
    <table className={styles.opsTable}>
     <thead><tr><th scope="col">Due · Échéance</th><th scope="col">Asset · Équipement</th><th scope="col">Kind · Type</th><th scope="col">State · État</th><th scope="col">Escalation · Escalade</th></tr></thead>
     <tbody>{data.map(r=><tr key={text(r.occurrenceId)}>
      <td>{when(r.dueAt)}</td><td>{text(r.assetTitle)}<br/><small>{text(r.locationRef)} · {text(r.safetyClass)}</small></td>
      <td>{text(r.kind).replaceAll("_"," ")}</td>
      <td>{PROJECTION[text(r.projection)]||text(r.projection)}</td>
      <td>{Number(r.escalationLevel)>0?"L"+text(r.escalationLevel):"—"}</td>
     </tr>)}</tbody>
    </table>
   </div>)}
 </section>;
}
function Health(){
 const {phase,data,reload}=useRead<Row>("planner/health");
 return <section className={styles.opsPanel} aria-labelledby="m23-health-h">
  <div className={styles.opsHeader}><h3 id="m23-health-h">{L.health}</h3><button type="button" onClick={reload}>{L.refresh}</button></div>
  <Gate phase={phase}/>
  {phase==="ready"&&data&&<dl className={styles.opsDetails}>
   <div><dt>Status · Statut</dt><dd>{text(data.status)}</dd></div>
   <div><dt>Last success · Dernier succès</dt><dd>{when(data.lastSuccessAt)}</dd></div>
   <div><dt>Due, not yet linked · Échu, non lié</dt><dd>{text(data.unprocessedDue)}</dd></div>
   <div><dt>Overdue · En retard</dt><dd>{text(data.overdue)}</dd></div>
   <div><dt>Overdue, not escalated · En retard, non escaladé</dt><dd>{text(data.overdueUnescalated)}</dd></div>
  </dl>}
  <p className={styles.caption}>{L.unknown}</p>
 </section>;
}
function Policy(){
 const {phase,data}=useRead<Row[]>("policies");
 return <section className={styles.opsPanel} aria-labelledby="m23-policy-h">
  <h3 id="m23-policy-h">{L.policy}</h3>
  <Gate phase={phase}/>
  {phase==="ready"&&data&&<div className={styles.opsTableWrap} role="region" aria-label={L.policy}><table className={styles.opsTable}>
   <thead><tr><th scope="col">Class · Classe</th><th scope="col">Interval (days) · Intervalle</th><th scope="col">Calibration (days) · Étalonnage</th><th scope="col">Grace (h) · Délai</th><th scope="col">Source</th></tr></thead>
   <tbody>{data.map(r=><tr key={text(r.safetyClass)}><td>{text(r.safetyClass)}</td><td>{text(r.intervalDays)}</td><td>{text(r.calibrationIntervalDays)}</td><td>{text(r.graceHours)}</td><td>{text(r.source).replaceAll("_"," ")}</td></tr>)}</tbody>
  </table></div>}
 </section>;
}
function Queue(){
 const {phase,data,reload}=useRead<Row[]>("inspections/pending?limit=50");
 return <section className={styles.opsPanel} aria-labelledby="m23-queue-h">
  <div className={styles.opsHeader}><h3 id="m23-queue-h">{L.queue}</h3><button type="button" onClick={reload}>{L.refresh}</button></div>
  <Gate phase={phase}/>
  {phase==="ready"&&data&&(data.length===0?<p>{L.noQueue}</p>:
   <div className={styles.opsTableWrap} role="region" aria-label={L.queue}><table className={styles.opsTable}>
    <thead><tr><th scope="col">Work order · Ordre de travail</th><th scope="col">Due · Échéance</th><th scope="col">Evidence · Preuve</th></tr></thead>
    <tbody>{data.map(r=><tr key={text(r.workOrderId)}><td>{text(r.workOrderId)}</td><td>{when(r.dueAt)}</td>
     <td>{r.evidenceReady===true?"Ready · Prête":"Not ready — cannot be verified · Non prête — vérification impossible"}</td></tr>)}</tbody>
   </table></div>)}
 </section>;
}
export function MaintenancePanel({screenId}:{screenId:string}){
 if(screenId==="M23-S01")return <Calendar/>;
 if(screenId==="M23-S03")return <><Health/><Calendar/><Policy/></>;
 if(screenId==="M23-S05")return <Queue/>;
 return null;
}
export default MaintenancePanel;
