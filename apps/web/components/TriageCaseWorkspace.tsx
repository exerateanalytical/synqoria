'use client';
import {useEffect,useMemo,useState} from 'react';
import {TriageCaseSession,TriageAuditPager,triageAction,type CaseSessionView,type TriageAuditRecord} from '@synqoria/ux-core';

/** Opt-in M03 staff case workspace. Parent must supply an already-verified volatile session and scoped patient/case IDs.
 * No session source is invented here; the app's unverified shell remains locked.
 */
type Locale='en'|'fr';
type Action='QUEUE_CALL'|'QUEUE_RECALL'|'QUEUE_PAUSE'|'QUEUE_RESUME'|'REASSESSMENT_REQUESTED'|'HANDOVER_REQUEST';
const actions:ReadonlyArray<Action>=['QUEUE_CALL','QUEUE_RECALL','QUEUE_PAUSE','QUEUE_RESUME','REASSESSMENT_REQUESTED','HANDOVER_REQUEST'];
const labels:{[K in Action]:{en:string;fr:string}}={
 QUEUE_CALL:{en:'Call patient',fr:'Appeler le patient'},QUEUE_RECALL:{en:'Recall',fr:'Rappeler'},
 QUEUE_PAUSE:{en:'Pause queue',fr:'Suspendre la file'},QUEUE_RESUME:{en:'Resume queue',fr:'Reprendre la file'},
 REASSESSMENT_REQUESTED:{en:'Request reassessment',fr:'Demander une réévaluation'},HANDOVER_REQUEST:{en:'Request handover',fr:'Demander un transfert de responsabilité'}
};
const copy={en:{title:'Triage case',status:'Workflow status',arrived:'Arrival (UTC)',revision:'Revision',station:'Station',assigned:'Assigned clinician',
 required:'Verified clinical session required',loading:'Loading authorized case',refresh:'Refresh from server',history:'Audit history',loadHistory:'Load audit history',
 noHistory:'No audit events loaded',actionError:'Operation not completed. Review access and refresh before retrying.',unavailable:'Case unavailable or not disclosed',
 context:'Current patient context',none:'Not assigned',priority:'Clinical priority is entered only by an authorized clinician',
 quick:'Authorized quick actions',noActions:'No actions have been granted in the current clinical session',more:'Load more history'},
 fr:{title:'Dossier de triage',status:'État du parcours',arrived:'Arrivée (UTC)',revision:'Révision',station:'Poste',assigned:'Clinicien responsable',
 required:'Session clinique vérifiée obligatoire',loading:'Chargement du dossier autorisé',refresh:'Actualiser depuis le serveur',history:'Historique d’audit',loadHistory:'Charger l’historique',
 noHistory:'Aucun événement d’audit chargé',actionError:'Opération non exécutée. Vérifier l’accès et actualiser avant de réessayer.',unavailable:'Dossier indisponible ou non divulgué',
 context:'Contexte patient actif',none:'Non attribué',priority:'La priorité clinique est saisie uniquement par un clinicien autorisé',
 quick:'Actions rapides autorisées',noActions:'Aucune action accordée dans la session clinique courante',more:'Charger la suite'}
} as const;
export function TriageCaseWorkspace({session,patientId,caseId,locale='en',grantedActions=[]}:{
 session:TriageCaseSession;patientId:string;caseId:string;locale?:Locale;grantedActions:readonly string[];
}){
 const t=copy[locale];
 const [state,setState]=useState<CaseSessionView>(()=>session.snapshot());
 const [busy,setBusy]=useState(false);const [error,setError]=useState(false);
 const [events,setEvents]=useState<readonly TriageAuditRecord[]>([]);
 const [hasMore,setHasMore]=useState(true);
 const pager=useMemo(()=>new TriageAuditPager(session),[session]);
 useEffect(()=>{
  let mounted=true;const unsub=session.subscribe(s=>{if(mounted)setState(s);});
  setEvents([]);setHasMore(true);setError(false);
  void session.open(patientId,caseId).catch(()=>{if(mounted)setError(true);});
  return ()=>{mounted=false;unsub();pager.dispose();session.close();};
 },[session,patientId,caseId,pager]);
 const permitted=actions.filter(action=>grantedActions.includes(action) && triageAction(action).capability!=='triage:sign');
 async function command(action:Action){
  if(busy||state.status!=='ready')return;
  setBusy(true);setError(false);
  try{
   const commandId=crypto.randomUUID();
   await session.command(commandId,action);
   await session.refresh();
   pager.clear();setEvents([]);setHasMore(true);
  }catch{setError(true);}finally{setBusy(false);}
 }
 async function loadHistory(){
  if(busy||state.status!=='ready'||!hasMore)return;
  setBusy(true);setError(false);
  try{setEvents(await pager.loadNext());setHasMore(pager.snapshot().nextAfterRevision!==null);}
  catch{setError(true);}finally{setBusy(false);}
 }
 const current=state.case;
 return <section className="triage-workspace" aria-label={t.title} lang={locale}>
  <header className="triage-workspace__header"><div><p className="eyebrow">M03 · {t.context}</p><h2>{t.title}</h2></div>
   <p className="triage-workspace__status" aria-live="polite">{t.status}: {state.status}</p></header>
  {error&&<p role="alert" className="triage-workspace__notice">{t.actionError}</p>}
  {!current?<div className="triage-workspace__empty" role="status">
   <p>{state.status==='loading'?t.loading:state.status==='refresh-required'?t.refresh:state.status==='closed'?t.required:t.unavailable}</p>
   {state.status==='refresh-required'&&<button type="button" onClick={()=>{setError(false);void session.refresh().catch(()=>setError(true));}}>{t.refresh}</button>}
  </div>:<>
   <dl className="triage-workspace__details">
    <div><dt>{t.status}</dt><dd>{current.stage}</dd></div>
    <div><dt>{t.revision}</dt><dd>{current.revision}</dd></div>
    <div><dt>{t.arrived}</dt><dd>{current.arrivedAt}</dd></div>
    <div><dt>{t.station}</dt><dd>{current.station??t.none}</dd></div>
    <div><dt>{t.assigned}</dt><dd>{current.assignee??t.none}</dd></div>
   </dl>
   <section aria-labelledby="triage-quick-actions" className="triage-workspace__panel">
    <h3 id="triage-quick-actions">{t.quick}</h3><p className="triage-workspace__hint">{t.priority}</p>
    {permitted.length===0?<p>{t.noActions}</p>:<div className="triage-workspace__actions">
     {permitted.map(action=><button type="button" key={action} disabled={busy||state.status!=='ready'} onClick={()=>void command(action)}>{labels[action][locale]}</button>)}
    </div>}
   </section>
   <section aria-labelledby="triage-audit-heading" className="triage-workspace__panel">
    <h3 id="triage-audit-heading">{t.history}</h3>
    {events.length===0?<p>{t.noHistory}</p>:<ol className="triage-workspace__history">{events.map(record=><li key={record.revision}>
      <span>{record.operation}</span><time dateTime={record.occurredAt}>{record.occurredAt}</time><span>#{record.revision}</span>
    </li>)}</ol>}
    <button type="button" disabled={busy||!hasMore} onClick={()=>void loadHistory()}>{events.length?t.more:t.loadHistory}</button>
   </section>
  </>}
 </section>;
}
