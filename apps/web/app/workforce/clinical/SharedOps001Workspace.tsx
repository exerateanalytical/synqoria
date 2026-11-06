"use client";
import {useCallback,useEffect,useRef,useState,type FormEvent,type ReactNode} from "react";
import Link from "next/link";
import {ArrowRight,BriefcaseMedical,ClipboardList,KeyRound,LockKeyhole,
 LogOut,RefreshCcw,ShieldAlert,ShieldCheck,UserRoundPlus,UserRoundSearch} from "lucide-react";
import "./ops001-workspace.css";

export type Ops001Screen="G-S01"|"G-S02"|"G-S04"|"G-S05"|"G-S07"|"G-S08"|"G-S09"|"M01-S02";
type Locale="en"|"fr";
type Scope="idle"|"checking"|"verified"|"signin"|"denied"|"unavailable";
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[89ab0-9a-f]{12}$/i;
const screens:Record<Ops001Screen,{en:string;fr:string;summaryEn:string;summaryFr:string}>={
 "G-S01":{en:"Verified staff sign-in",fr:"Connexion du personnel vérifiée",
  summaryEn:"Begin hospital-managed OIDC authentication. Staff MFA is performed by the identity provider.",
  summaryFr:"L’authentification OIDC et la MFA sont assurées par le fournisseur d’identité de l’hôpital."},
 "G-S02":{en:"MFA and account recovery",fr:"MFA et récupération du compte",
  summaryEn:"Only an institution-approved identity provider may verify a second factor or recover staff credentials.",
  summaryFr:"Seul le fournisseur d’identité agréé peut vérifier le second facteur ou rétablir les identifiants."},
 "G-S04":{en:"Role home and task hub",fr:"Accueil et tâches du personnel",
  summaryEn:"Verify your current hospital assignment before accessing scoped work. Task discovery needs an approved backend.",
  summaryFr:"Vérifiez votre affectation avant l’accès aux tâches. Leur chargement exige un service agréé."},
 "G-S05":{en:"Authorized patient search",fr:"Recherche patient autorisée",
  summaryEn:"Exact canonical patient identifier lookup only. Demographic and cross-facility searches are not yet connected.",
  summaryFr:"Recherche uniquement par identifiant canonique exact. La recherche démographique et inter-sites reste indisponible."},
 "G-S07":{en:"Session lock and reauthentication",fr:"Verrouillage et réauthentification",
  summaryEn:"Keep clinical information concealed until the server confirms revocation and a new verified sign-in.",
  summaryFr:"Les données restent masquées jusqu’à la révocation côté serveur et une nouvelle connexion vérifiée."},
 "G-S08":{en:"Access denied and review",fr:"Accès refusé et réexamen",
  summaryEn:"An access denial does not confirm whether a protected patient record exists. Privilege changes require an approved review service.",
  summaryFr:"Le refus ne confirme pas l’existence d’un dossier. Toute modification de privilèges requiert une revue approuvée."},
 "G-S09":{en:"Emergency access request",fr:"Demande d’accès d’urgence",
  summaryEn:"Emergency access requires named actor, exact patient scope, purpose, bounded duration, audit and policy approval.",
  summaryFr:"L’accès d’urgence exige un acteur identifié, un périmètre patient, un motif, une durée, un audit et une approbation."},
 "M01-S02":{en:"New patient registration",fr:"Enregistrement d’un nouveau patient",
  summaryEn:"Registration requires duplicate screening, verified identifiers, consent, audit and committed patient identity.",
  summaryFr:"L’enregistrement exige un contrôle des doublons, des identifiants vérifiés, le consentement et un audit."}
};
const order=Object.keys(screens) as Ops001Screen[];
const glyphs={
 "G-S01":ShieldCheck,"G-S02":KeyRound,"G-S04":BriefcaseMedical,
 "G-S05":UserRoundSearch,"G-S07":LockKeyhole,"G-S08":ShieldAlert,
 "G-S09":ShieldAlert,"M01-S02":UserRoundPlus
} as const;
const t=(locale:Locale,en:string,fr:string)=>locale==="en"?en:fr;

function Info({children,warning=false}:{children:ReactNode;warning?:boolean}){
 return <div className={warning?"ops001-notice ops001-notice--warning":"ops001-notice"} role="status">{children}</div>;
}
function AuthHealth({locale}:{locale:Locale}){
 const [status,setStatus]=useState<"loading"|"ready"|"unavailable">("loading");
 const check=useCallback(async(signal?:AbortSignal)=>{
  setStatus("loading");
  try{
   const r=await fetch("/api/auth/status",{method:"GET",credentials:"same-origin",cache:"no-store",
    redirect:"error",headers:{Accept:"application/json"},signal});
   const dto:unknown=r.status===200?await r.json():null;
   if(signal?.aborted)return;
   setStatus(dto!==null&&typeof dto==="object"&&!Array.isArray(dto)&&
    (dto as {ready?:unknown}).ready===true?"ready":"unavailable");
  }catch{if(!signal?.aborted)setStatus("unavailable");}
 },[]);
 useEffect(()=>{const controller=new AbortController();void check(controller.signal);
  return ()=>controller.abort();},[check]);
 return <section className="ops001-card" aria-live="polite">
  <h2>{t(locale,"Identity provider","Fournisseur d’identité")}</h2>
  <p>{status==="loading"?t(locale,"Checking approved service…","Vérification du service…"):
   status==="ready"?t(locale,"Verified identity service installed","Service d’identité vérifié disponible"):
   t(locale,"No approved identity service deployed for this hospital cell.","Aucun service d’identité agréé déployé pour cet établissement.")}</p>
  <div className="ops001-actions">
   {status==="ready"?<a className="ops001-primary" href="/api/auth/oidc/start"><ShieldCheck size={18} aria-hidden="true"/>
    {t(locale,"Continue to secure staff sign-in","Poursuivre la connexion sécurisée")}</a>:
    <button type="button" disabled>{t(locale,"Secure sign-in unavailable","Connexion sécurisée indisponible")}</button>}
   <button type="button" onClick={()=>void check()} disabled={status==="loading"}><RefreshCcw size={17} aria-hidden="true"/>
    {t(locale,"Retry availability","Vérifier de nouveau")}</button>
  </div>
 </section>;
}
function useFacility(){
 const [scope,setScope]=useState<Scope>("idle");
 const [facility,setFacility]=useState<{facilityId:string;revision:number}|null>(null);
 const current=useRef<AbortController|null>(null);
 const verify=useCallback(async()=>{
  current.current?.abort();
  const ctl=new AbortController();current.current=ctl;setScope("checking");setFacility(null);
  try{
   const r=await fetch("/api/ops001/facility",{method:"GET",credentials:"same-origin",
    cache:"no-store",redirect:"error",headers:{Accept:"application/json"},signal:ctl.signal});
   if(ctl.signal.aborted)return;
   if(r.status!==200){setScope(r.status===401?"signin":r.status===404?"denied":"unavailable");return;}
   const dto:unknown=await r.json();
   if(ctl.signal.aborted)return;
   if(dto&&typeof dto==="object"&&!Array.isArray(dto)&&
     uuid.test((dto as {facilityId?:string}).facilityId||"")&&
     Number.isSafeInteger((dto as {revision?:number}).revision)&&
     (dto as {revision:number}).revision>0){
    setFacility(dto as {facilityId:string;revision:number});setScope("verified");
   }else setScope("unavailable");
  }catch{if(!ctl.signal.aborted)setScope("unavailable");}
 },[]);
 useEffect(()=>{const hide=()=>{if(document.visibilityState==="hidden"){
    current.current?.abort();setScope("idle");setFacility(null);
   }};
  document.addEventListener("visibilitychange",hide);
  return ()=>{current.current?.abort();document.removeEventListener("visibilitychange",hide);};
 },[]);
 return {scope,facility,verify};
}
function FacilityGate({locale,scope,verify}:{locale:Locale;scope:Scope;verify:()=>Promise<void>}){
 const message:Record<Scope,[string,string]>={
  idle:["No verified hospital session checked","Session hospitalière non vérifiée"],
  checking:["Checking live staff authorization…","Vérification des autorisations…"],
  verified:["Assigned facility verified","Affectation à l’établissement vérifiée"],
  signin:["Verified sign-in required","Connexion vérifiée nécessaire"],
  denied:["Not authorized for this facility","Non autorisé pour cet établissement"],
  unavailable:["Identity or clinical service unavailable","Service d’identité ou clinique indisponible"]
 };
 return <div className="ops001-facility-status" role="status" aria-live="polite">
  <span>{t(locale,...message[scope])}</span>
  <button type="button" onClick={()=>void verify()} disabled={scope==="checking"}>
   <RefreshCcw size={16} aria-hidden="true"/>{t(locale,"Verify session","Vérifier la session")}
  </button>
 </div>;
}
function RoleHub({locale}:{locale:Locale}){
 const {scope,facility,verify}=useFacility();
 return <div className="ops001-stack">
  <FacilityGate locale={locale} scope={scope} verify={verify}/>
  {scope==="verified"&&facility?<section className="ops001-card">
   <h2>{t(locale,"Current hospital cell","Établissement actuel")}</h2>
   <dl className="ops001-summary"><div><dt>{t(locale,"Facility reference","Référence établissement")}</dt><dd>{facility.facilityId}</dd></div>
    <div><dt>{t(locale,"Context revision","Révision du contexte")}</dt><dd>{facility.revision}</dd></div></dl>
  </section>:null}
  <section className="ops001-card"><h2>{t(locale,"Assigned clinical work","Travail clinique attribué")}</h2>
   <Info warning>{t(locale,"The authoritative task and urgent-exception service is not integrated. No task count or assignment is inferred.",
    "Le service officiel des tâches et urgences n’est pas intégré. Aucun nombre de tâches n’est inventé.")}</Info>
  </section>
  <section className="ops001-card"><h2>{t(locale,"Quick actions","Actions rapides")}</h2>
   <div className="ops001-links">
    <Link href="/workforce/clinical/G-S05">{t(locale,"Patient lookup","Recherche patient")}<ArrowRight size={16}/></Link>
    <Link href="/workforce/clinical/M01-S01">{t(locale,"Exact patient identity","Identité du patient")}<ArrowRight size={16}/></Link>
    <Link href="/workforce/clinical/M03-S01">{t(locale,"Triage worklist","Liste de triage")}<ArrowRight size={16}/></Link>
   </div><p className="ops001-note">{t(locale,"Destination services independently verify authorization.",
    "Chaque destination vérifie séparément les autorisations.")}</p>
  </section>
 </div>;
}
type Patient={patientId:string;identityStatus:"verified"|"provisional"|"merged";revision:number;facilityId:string};
function PatientLookup({locale}:{locale:Locale}){
 const {scope,verify}=useFacility();
 const [id,setId]=useState("");const [patient,setPatient]=useState<Patient|null>(null);
 const [status,setStatus]=useState<"idle"|"loading"|"invalid"|"denied"|"unavailable"|"ready">("idle");
 const ctrl=useRef<AbortController|null>(null);
 const erase=useCallback(()=>{ctrl.current?.abort();setPatient(null);setId("");setStatus("idle");},[]);
 useEffect(()=>{const hide=()=>{if(document.visibilityState==="hidden")erase();};
  document.addEventListener("visibilitychange",hide);
  return ()=>{ctrl.current?.abort();document.removeEventListener("visibilitychange",hide);};
 },[erase]);
 async function search(e:FormEvent<HTMLFormElement>){
  e.preventDefault();ctrl.current?.abort();setPatient(null);
  if(scope!=="verified"||!uuid.test(id)){setStatus("invalid");return;}
  const requested=id;const ctl=new AbortController();ctrl.current=ctl;setStatus("loading");
  try{
   const r=await fetch("/api/clinical/patient-lookup",{method:"POST",credentials:"same-origin",cache:"no-store",
    redirect:"error",headers:{"Content-Type":"application/json",Accept:"application/json"},
    body:JSON.stringify({patientId:requested}),signal:ctl.signal});
   if(ctl.signal.aborted)return;
   if(r.status!==200){setStatus(r.status===401||r.status===404?"denied":"unavailable");return;}
   const dto:unknown=await r.json();
   if(ctl.signal.aborted)return;
   const value=dto as Partial<Patient>|null;
   if(!value||typeof value!=="object"||value.patientId!==requested||
      !uuid.test(value.facilityId||"")||!Number.isSafeInteger(value.revision)||
      !value.revision||!["verified","provisional","merged"].includes(value.identityStatus||"")){
    setStatus("unavailable");return;
   }
   setPatient(value as Patient);setStatus("ready");
  }catch{if(!ctl.signal.aborted)setStatus("unavailable");}
 }
 return <div className="ops001-stack">
  <FacilityGate locale={locale} scope={scope} verify={verify}/>
  <section className="ops001-card">
   <h2>{t(locale,"Exact identifier search","Recherche par identifiant exact")}</h2>
   <p className="ops001-note">{t(locale,"Enter only an approved canonical UUID. Never enter names, phone numbers or clinical notes.",
    "Saisissez uniquement un UUID canonique autorisé. N’entrez ni nom, ni téléphone, ni notes médicales.")}</p>
   <form onSubmit={e=>void search(e)} autoComplete="off" className="ops001-form">
    <label htmlFor="ops001-patient">{t(locale,"Canonical patient UUID","UUID canonique du patient")}</label>
    <input id="ops001-patient" value={id} onChange={e=>{setId(e.target.value.trim());setPatient(null);setStatus("idle");}}
     type="text" maxLength={36} inputMode="text" autoCapitalize="off" spellCheck={false}
     disabled={scope!=="verified"} placeholder="xxxxxxxx-xxxx-4xxx-8xxx-xxxxxxxxxxxx" aria-describedby="ops001-search-hint"/>
    <p className="ops001-note" id="ops001-search-hint">{t(locale,"No patient identifier is saved in browser storage or added to the page URL.",
     "Aucun identifiant patient n’est conservé en stockage local ou ajouté à l’URL.")}</p>
    <div className="ops001-actions"><button className="ops001-primary" type="submit" disabled={scope!=="verified"||status==="loading"||!uuid.test(id)}>
     <UserRoundSearch size={17}/>{t(locale,"Search authorized record","Rechercher le dossier autorisé")}</button>
     <button type="button" onClick={erase}>{t(locale,"Clear sensitive context","Effacer le contexte sensible")}</button></div>
   </form>
   <p aria-live="polite" className="ops001-status">{status==="loading"?t(locale,"Checking patient scope…","Vérification des autorisations patient…"):
    status==="invalid"?t(locale,"Verify your staff session and identifier format.","Vérifiez la session et le format de l’identifiant."):
    status==="denied"?t(locale,"No authorized patient record can be disclosed.","Aucun dossier patient autorisé ne peut être divulgué."):
    status==="unavailable"?t(locale,"Patient source unavailable; no information was loaded.","Service patient indisponible; aucune donnée chargée."):
    status==="ready"?t(locale,"Authorized identity metadata received.","Métadonnées d’identité autorisées reçues."):""}</p>
   {status==="ready"&&patient?<dl className="ops001-summary">
    <div><dt>{t(locale,"Patient reference","Référence patient")}</dt><dd>{patient.patientId}</dd></div>
    <div><dt>{t(locale,"Identity status","Statut d’identité")}</dt><dd>{patient.identityStatus}</dd></div>
    <div><dt>{t(locale,"Source revision","Révision source")}</dt><dd>{patient.revision}</dd></div>
    <div><dt>{t(locale,"Facility","Établissement")}</dt><dd>{patient.facilityId}</dd></div>
   </dl>:null}
  </section>
  <Info warning>{t(locale,"Full demographic search, duplicate matching and patient cross-facility discovery are not connected.",
   "La recherche démographique, les doublons et la recherche inter-sites ne sont pas intégrés.")}</Info>
 </div>;
}
function SessionLock({locale}:{locale:Locale}){
 const [state,setState]=useState<"locked"|"signingout"|"revoked"|"uncertain">("locked");
 async function signout(){
  setState("signingout");
  try{
   const r=await fetch("/api/auth/logout",{method:"POST",credentials:"same-origin",cache:"no-store",
    redirect:"error",headers:{Accept:"application/json"}});
   setState(r.status===204?"revoked":"uncertain");
  }catch{setState("uncertain");}
 }
 return <section className="ops001-card ops001-card--locked">
  <LockKeyhole size={38} aria-hidden="true"/><h2>{t(locale,"This workspace is concealed","Cet espace est masqué")}</h2>
  <p>{state==="revoked"?t(locale,"Server revocation confirmed. Sign in again to resume.","Révocation serveur confirmée. Reconnectez-vous."):
   state==="uncertain"?t(locale,"Server revocation could not be confirmed. Keep this workstation locked and contact your administrator.",
    "Révocation serveur non confirmée. Gardez le poste verrouillé et contactez l’administrateur."):
   t(locale,"No protected clinical content is shown on this page. Reauthentication is managed by the hospital IdP.",
    "Aucune donnée clinique n’est affichée. La réauthentification est gérée par l’identité hospitalière.")}</p>
  <div className="ops001-actions"><button type="button" className="ops001-primary" onClick={()=>void signout()} disabled={state==="signingout"||state==="revoked"}>
   <LogOut size={18}/>{t(locale,"Revoke staff session","Révoquer la session")}</button>
   <Link href="/workforce/clinical/G-S01">{t(locale,"Return to verified sign-in","Retour à la connexion vérifiée")}<ArrowRight size={16}/></Link>
  </div>
 </section>;
}
function ReviewAccess({locale}:{locale:Locale}){
 return <div className="ops001-stack"><Info warning>{t(locale,
  "A denial intentionally does not reveal whether the underlying patient, task or resource exists.",
  "Le refus ne révèle pas si le patient, la tâche ou la ressource protégée existe.")}</Info>
  <section className="ops001-card"><h2>{t(locale,"Authorized review","Réexamen autorisé")}</h2>
   <dl className="ops001-summary"><div><dt>{t(locale,"Safe reason","Motif non sensible")}</dt><dd>{t(locale,"Insufficient or unverified privileges","Privilèges insuffisants ou non vérifiés")}</dd></div>
   <div><dt>{t(locale,"Access request reference","Référence de demande")}</dt><dd>{t(locale,"Not issued","Non émise")}</dd></div></dl>
   <Info>{t(locale,"An accountable reviewer, approved ticket service and audit are required before a request can be submitted.",
    "Un réviseur désigné, un système de tickets approuvé et un audit sont nécessaires pour soumettre la demande.")}</Info>
   <div className="ops001-actions"><button disabled type="button">{t(locale,"Request authorized review — unavailable","Demander un réexamen — indisponible")}</button>
    <Link href="/workforce/clinical/G-S11">{t(locale,"View safe help","Consulter l’aide")}<ArrowRight size={16}/></Link></div>
  </section></div>;
}
function EmergencyAccess({locale}:{locale:Locale}){
 return <section className="ops001-card">
  <h2>{t(locale,"Break-glass policy gate","Contrôle d’accès d’urgence")}</h2>
  <Info warning>{t(locale,"Emergency grants cannot be issued, simulated, or bypassed without a live authorizing policy, clinical audit, scope and short expiry.",
    "Aucun accès d’urgence ne peut être accordé ou simulé sans politique active, audit clinique, périmètre et expiration courte.")}</Info>
  <ul className="ops001-checklist"><li>{t(locale,"Named, reauthenticated staff member","Personnel nommé et réauthentifié")}</li>
   <li>{t(locale,"Exact patient and facility scope","Patient et établissement définis")}</li>
   <li>{t(locale,"Permitted purpose, short duration and approval","Motif autorisé, courte durée et approbation")}</li>
   <li>{t(locale,"Durable clinical audit and post-event review","Audit durable et revue après l’événement")}</li></ul>
  <button type="button" disabled>{t(locale,"Activate policy-approved access — unavailable","Activer l’accès autorisé — indisponible")}</button>
 </section>;
}
function RegistrationGate({locale}:{locale:Locale}){
 const labels=locale==="en"?["Legal names and preferred name","Verified identifiers and issuer",
  "Date of birth or estimated-age basis","Demographics and language",
  "Contact channels and address","Duplicate check, consent and provenance"]:
 ["Noms légaux et nom usuel","Identifiants vérifiés et émetteur",
  "Date de naissance ou estimation","Démographie et langue",
  "Contacts et adresse","Contrôle des doublons, consentement et provenance"];
 return <div className="ops001-stack">
  <Info warning>{t(locale,"Registration commands and authoritative patient persistence are not connected. Do not enter actual patient information.",
   "Les commandes et la persistance patient ne sont pas intégrées. N’entrez aucune information patient réelle.")}</Info>
  <section className="ops001-card"><h2>{t(locale,"Required registration domains","Domaines obligatoires")}</h2>
   <div className="ops001-check-grid">{labels.map((name,i)=><div key={name}><span>{String(i+1).padStart(2,"0")}</span><strong>{name}</strong></div>)}</div>
   <div className="ops001-actions"><button type="button" disabled>{t(locale,"Register patient — unavailable","Enregistrer le patient — indisponible")}</button>
    <Link href="/workforce/patients/new">{t(locale,"View intake field specification","Voir la structure du formulaire")}<ArrowRight size={16}/></Link></div>
  </section></div>;
}
export default function SharedOps001Workspace({screenId}:{screenId:Ops001Screen}){
 const [locale,setLocale]=useState<Locale>("en");
 const def=screens[screenId],Glyph=glyphs[screenId];
 return <main className="ops001-page" data-screen-id={screenId} data-acceptance="partial-not-certified" lang={locale}>
  <a href="#ops001-main" className="ops001-skip">{t(locale,"Skip to content","Aller au contenu")}</a>
  <aside className="ops001-sidebar" aria-label={t(locale,"Shared clinical navigation","Navigation clinique partagée")}>
   <Link href="/workforce/clinical" className="ops001-brand">SYNQORIA<span>{t(locale,"Hospital workforce","Personnel hospitalier")}</span></Link>
   <p className="ops001-nav-label">{t(locale,"Shared workspaces","Espaces partagés")}</p>
   <nav>{order.map(id=><Link key={id} href={"/workforce/clinical/"+id}
    aria-current={id===screenId?"page":undefined}><small>{id}</small>{t(locale,screens[id].en,screens[id].fr)}</Link>)}</nav>
  </aside>
  <div className="ops001-main" id="ops001-main">
   <header className="ops001-head">
    <div><nav aria-label={t(locale,"Breadcrumb","Fil d’Ariane")}>
     <Link href="/workforce/clinical">{t(locale,"Clinical workspaces","Espaces cliniques")}</Link><span aria-hidden="true">/</span>
     <span>{screenId}</span></nav>
     <p className="ops001-eyebrow">{screenId} · {t(locale,"Shared clinical operations","Opérations cliniques partagées")}</p>
     <h1><Glyph size={28} strokeWidth={1.8} aria-hidden="true"/>{t(locale,def.en,def.fr)}</h1>
     <p>{t(locale,def.summaryEn,def.summaryFr)}</p>
    </div>
    <fieldset className="ops001-locales"><legend>{t(locale,"Language","Langue")}</legend>
     <button type="button" aria-pressed={locale==="en"} onClick={()=>setLocale("en")}>EN</button>
     <button type="button" aria-pressed={locale==="fr"} onClick={()=>setLocale("fr")}>FR</button>
    </fieldset>
   </header>
   <Info warning><strong>{t(locale,"Clinical release not approved.","Mise en service clinique non approuvée.")}</strong>
    {" "}{t(locale,"This screen enables only specifically verified workflows; all other functions remain blocked.",
     "Seules les opérations spécifiquement vérifiées sont disponibles; les autres restent bloquées.")}</Info>
   <div className="ops001-body">
    {screenId==="G-S01"?<AuthHealth locale={locale}/>:null}
    {screenId==="G-S02"?<><Info>{t(locale,"MFA and recovery stay entirely within the verified IdP. This page never collects one-time codes or passwords.",
     "La MFA et la récupération restent gérées par le fournisseur vérifié. Cette page ne demande ni code ni mot de passe.")}</Info><AuthHealth locale={locale}/></>:null}
    {screenId==="G-S04"?<RoleHub locale={locale}/>:null}
    {screenId==="G-S05"?<PatientLookup locale={locale}/>:null}
    {screenId==="G-S07"?<SessionLock locale={locale}/>:null}
    {screenId==="G-S08"?<ReviewAccess locale={locale}/>:null}
    {screenId==="G-S09"?<EmergencyAccess locale={locale}/>:null}
    {screenId==="M01-S02"?<RegistrationGate locale={locale}/>:null}
   </div>
   <footer className="ops001-footer"><ClipboardList size={16} aria-hidden="true"/>
    {t(locale,"Source-verified subsets only · No clinical acceptance or signed transaction",
     "Fonctions partielles vérifiées · Aucune validation clinique ni transaction signée")}</footer>
  </div>
 </main>;
}
