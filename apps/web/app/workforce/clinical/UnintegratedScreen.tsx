"use client";
import {useCallback,useEffect,useMemo,useRef,useState} from "react";
import Link from "next/link";
import {
 Activity,ArrowLeft,ArrowRight,BookOpen,ClipboardCheck,FileText,LayoutDashboard,
 ListFilter,LockKeyhole,PanelTop,RefreshCcw,ShieldAlert,Workflow
} from "lucide-react";
import "./unintegrated-screen.css";

export type CanonicalRouteSpec={
 id:string;module:string;name:string;layout:"F"|"L"|"D"|"X"|"B"|"T"|"H";
 data_input_groups:string;primary_action:string;
};
export type ExtendedSpec={
 title:string;moduleName:string;eligibilityNote:string;authorityWorkflow:string;
 tabs:readonly string[];fields:readonly {id:string;label:string;widget:string;requiredness:string;permission:string}[];
 filters:readonly string[];
};
export type FlowPath={id:string;title:string;path:readonly string[];exceptionPath:string};
export type DocumentLink={id:string;title:string;href:string};
type SafeStaffState="not-checked"|"checking"|"authenticated"|"login-required"|"not-disclosed"|"offline";
const layouts={F:{en:"Form workspace",fr:"Espace formulaire"},L:{en:"Record list",fr:"Liste des dossiers"},D:{en:"Record details",fr:"Détails du dossier"},
 X:{en:"Review and approval",fr:"Examen et approbation"},B:{en:"Operational board",fr:"Tableau opérationnel"},T:{en:"Timeline and analytics",fr:"Chronologie et analyse"},H:{en:"Role dashboard",fr:"Tableau de bord"}} as const;
const icons={F:ClipboardCheck,L:ListFilter,D:PanelTop,X:ShieldAlert,B:Workflow,T:Activity,H:LayoutDashboard} as const;
const copy={
 en:{home:"All hospital modules",ready:"Route mounted · domain backend not integrated",notCertified:"Clinical release denied",warning:"This is a real application route with a deliberately locked clinical operation. It is not a functional patient/financial workflow. Do not enter or upload real patient data here.", module:"Module",type:"Screen type",scope:"Staff session",notChecked:"Not checked",checking:"Checking server session",authenticated:"Verified hospital-cell session; this screen's domain API is still unavailable",login:"Verified staff login required",denied:"Not authorized for the current hospital",offline:"Session or clinical service unavailable",check:"Check current server authorization",refresh:"Retry session check",structure:"Required domain data",workflow:"Workflow and exceptions",documents:"Related document samples",nav:"Screens in this module",search:"Find module screen",source:"Source fields awaiting a reviewed dictionary",noData:"No server-authorized clinical dataset is connected to this screen",noPermission:"No clinical action, export, order or signature can be executed",action:"Primary clinical action",disabled:"Unavailable — clinical domain service not integrated",back:"Previous",next:"Next",jump:"Open screen",noRelated:"No related approved document samples have been registered",readOnly:"Read-only engineering interface",documentsNotice:"Sample-only printable layouts; no issued clinical documents",exceptions:"Exception handling",required:"Source-required fields",configured:"Service dependency",clinical:"Clinical functionality",unavailable:"Not integrated",noClaim:"No data or transaction has been created, modified, saved or signed",all:"All",filter:"Filter by name or code",skip:"Skip to main content",tab:"Screen content",records:"Server records",noRecords:"No live records loaded",review:"Clinical reviewer",reviewNote:"Qualified reviewer, version, effective scope and audit are required before any approval",summary:"Overview",data:"Domain data model",controls:"Configured actions",zero:"Not available",locked:"Locked",perm:"Authorization",field:"Field",value:"Value",evidence:"Source and evidence",none:"Not connected"},
 fr:{home:"Tous les modules hospitaliers",ready:"Page accessible · service métier non intégré",notCertified:"Mise en service clinique interdite",warning:"Ceci est une vraie page applicative, mais les opérations cliniques sont verrouillées. Ce n’est pas un processus patient/financier fonctionnel. Ne saisissez aucune donnée patient réelle.",module:"Module",type:"Type d’écran",scope:"Session du personnel",notChecked:"Non vérifiée",checking:"Vérification de la session",authenticated:"Session hospitalière vérifiée; l’API métier de cet écran reste indisponible",login:"Connexion du personnel vérifiée requise",denied:"Non autorisé pour cet établissement",offline:"Session ou service clinique indisponible",check:"Vérifier les autorisations du serveur",refresh:"Réessayer la vérification",structure:"Données métier requises",workflow:"Parcours et exceptions",documents:"Modèles documentaires associés",nav:"Écrans du même module",search:"Rechercher un écran du module",source:"Champs en attente d’un dictionnaire validé",noData:"Aucune donnée clinique autorisée n’est reliée à cet écran",noPermission:"Aucun acte clinique, export, ordonnance ou signature ne peut être exécuté",action:"Action clinique principale",disabled:"Indisponible — service métier non intégré",back:"Précédent",next:"Suivant",jump:"Ouvrir la page",noRelated:"Aucun modèle documentaire approuvé associé",readOnly:"Interface technique en lecture seule",documentsNotice:"Modèles imprimables d’exemple uniquement",exceptions:"Gestion des exceptions",required:"Champs nécessaires",configured:"Dépendance au service",clinical:"Fonction clinique",unavailable:"Non intégrée",noClaim:"Aucune donnée ni transaction n’a été créée, modifiée, enregistrée ou signée",all:"Tous",filter:"Filtrer par nom ou code",skip:"Aller au contenu principal",tab:"Contenu de l’écran",records:"Dossiers serveur",noRecords:"Aucun dossier réel chargé",review:"Réviseur clinique",reviewNote:"Une revue qualifiée, des versions, des permissions et un audit sont obligatoires avant toute validation",summary:"Résumé",data:"Modèle de données",controls:"Actions configurées",zero:"Indisponible",locked:"Verrouillé",perm:"Autorisation",field:"Champ",value:"Valeur",evidence:"Source et preuve",none:"Non connecté"}
} as const;
function words(raw:string):string[]{return raw.split(",").map(x=>x.trim()).filter(Boolean).slice(0,24);}
function groups(screen:CanonicalRouteSpec,detail?:ExtendedSpec):{key:string;label:string;permission:string}[]{
 if(detail?.fields.length)return detail.fields.slice(0,36).map(x=>({key:x.id,label:x.label,permission:x.permission}));
 return words(screen.data_input_groups).map((s,i)=>({key:screen.id+"-"+i,label:s,permission:"server-authorized"}));
}
function LocaleToggle({locale,onChange}:{locale:"en"|"fr";onChange:(v:"en"|"fr")=>void}){
 return <div className="syn-route-lang" role="group" aria-label="Language"><button type="button" aria-pressed={locale==="en"} onClick={()=>onChange("en")}>English</button><button type="button" aria-pressed={locale==="fr"} onClick={()=>onChange("fr")}>Français</button></div>;
}
function EmptySource({message,detail}:{message:string;detail:string}){
 return <div className="syn-route-empty" role="note"><LockKeyhole size={21} aria-hidden="true"/><div><strong>{message}</strong><p>{detail}</p></div></div>;
}
function ScreenBody({screen,fields,locale}:{screen:CanonicalRouteSpec;fields:ReturnType<typeof groups>;locale:"en"|"fr"}){
 const t=copy[locale];
 if(screen.layout==="F")return <section aria-label={t.structure}><div className="syn-route-cols">{fields.map(f=><label className="syn-route-field" key={f.key}>{f.label}<input type="text" disabled value="" aria-label={f.label} placeholder={t.unavailable}/><small>{f.permission} · {t.locked}</small></label>)}</div><EmptySource message={t.noData} detail={t.noPermission}/></section>;
 if(screen.layout==="L")return <section><div className="syn-route-table-wrap"><table><caption>{screen.name} · {t.records}</caption><thead><tr>{fields.slice(0,5).map(f=><th key={f.key} scope="col">{f.label}</th>)}</tr></thead><tbody><tr><td colSpan={Math.max(1,Math.min(fields.length,5))}>{t.noRecords}</td></tr></tbody></table></div><EmptySource message={t.noData} detail={t.noPermission}/></section>;
 if(screen.layout==="D")return <section aria-label={t.summary}><dl className="syn-route-descriptors">{fields.map(f=><div key={f.key}><dt>{f.label}</dt><dd>{t.none}</dd></div>)}</dl><EmptySource message={t.noData} detail={t.noPermission}/></section>;
 if(screen.layout==="X")return <section><div className="syn-route-cols"><div className="syn-route-check"><h3>{t.data}</h3>{fields.map(f=><p key={f.key}>{f.label}: <strong>{t.none}</strong></p>)}</div><div className="syn-route-check"><h3>{t.evidence}</h3><p>{t.reviewNote}</p><p>{t.noClaim}</p></div></div><EmptySource message={t.noPermission} detail={t.reviewNote}/></section>;
 if(screen.layout==="B")return <section className="syn-route-cols"><div className="syn-route-check"><h3>{t.configured}</h3><EmptySource message={t.noRecords} detail={t.noData}/></div><div className="syn-route-check"><h3>{t.clinical}</h3><EmptySource message={t.locked} detail={t.noPermission}/></div></section>;
 if(screen.layout==="T")return <section><div className="syn-route-table-wrap"><table><caption>{screen.name} · {t.data}</caption><thead><tr>{fields.slice(0,5).map(f=><th key={f.key} scope="col">{f.label}</th>)}</tr></thead><tbody><tr><td colSpan={Math.max(1,Math.min(fields.length,5))}>{t.noRecords}</td></tr></tbody></table></div><EmptySource message={t.noData} detail={t.noPermission}/></section>;
 return <section className="syn-route-cols"><div className="syn-route-check"><h3>{t.summary}</h3><dl className="syn-route-descriptors">{fields.map(f=><div key={f.key}><dt>{f.label}</dt><dd>{t.none}</dd></div>)}</dl></div><div className="syn-route-check"><h3>{t.clinical}</h3><EmptySource message={t.noRecords} detail={t.noPermission}/></div></section>;
}
export default function UnintegratedScreen({screen,moduleName,detail,peers,flowPaths,documents}:{screen:CanonicalRouteSpec;moduleName:string;detail?:ExtendedSpec;peers:readonly {id:string;name:string}[];flowPaths:readonly FlowPath[];documents:readonly DocumentLink[]}){
 const [locale,setLocale]=useState<"en"|"fr">("en");
 const [tab,setTab]=useState<"screen"|"workflows"|"documents">("screen");
 const [query,setQuery]=useState("");
 const [staff,setStaff]=useState<SafeStaffState>("not-checked");
 const current=useRef<AbortController|null>(null);
 const t=copy[locale];
 const definition=layouts[screen.layout];
 const Glyph=icons[screen.layout];
 const inputFields=useMemo(()=>groups(screen,detail),[screen,detail]);
 const peerList=useMemo(()=>peers.filter(x=>(x.id+" "+x.name).toLowerCase().includes(query.trim().toLowerCase())),[peers,query]);
 const index=peers.findIndex(x=>x.id===screen.id);
 const checkStaff=useCallback(async()=>{
  current.current?.abort();
  const controller=new AbortController();current.current=controller;setStaff("checking");
  try{
   const response=await fetch("/api/clinical/facility",{method:"GET",credentials:"same-origin",cache:"no-store",redirect:"error",headers:{"Accept":"application/json"},signal:controller.signal});
   if(controller.signal.aborted)return;
   if(response.status===200){
    const payload:unknown=await response.json();
    if(payload&&typeof payload==="object"&&!Array.isArray(payload)&&typeof (payload as {facilityId?:unknown}).facilityId==="string")setStaff("authenticated");
    else setStaff("offline");
   } else if(response.status===401)setStaff("login-required");
   else if(response.status===404)setStaff("not-disclosed");
   else setStaff("offline");
  }catch{if(!controller.signal.aborted)setStaff("offline");}
 },[]);
 useEffect(()=>{
  const onHide=()=>{if(document.visibilityState==="hidden"){current.current?.abort();setStaff("not-checked");}};
  document.addEventListener("visibilitychange",onHide);
  return()=>{current.current?.abort();document.removeEventListener("visibilitychange",onHide);};
 },[]);
 const scopeMsg=staff==="not-checked"?t.notChecked:staff==="checking"?t.checking:staff==="authenticated"?t.authenticated:staff==="login-required"?t.login:staff==="not-disclosed"?t.denied:t.offline;
 return <main id="main" className="syn-route" lang={locale} data-canonical-route={screen.id} data-domain-state="unintegrated" data-layout={screen.layout}>
  <a href="#route-main" className="syn-route-skip">{t.skip}</a>
  <div className="syn-route-shell">
   <aside className="syn-route-aside" aria-label={t.nav}>
    <Link className="syn-route-brand" href="/workforce/clinical">SYNQORIA <span>{t.home}</span></Link>
    <div className="syn-route-module-head"><span>{screen.module}</span><strong>{moduleName}</strong></div>
    <label className="syn-route-search">{t.search}<input type="search" maxLength={80} autoComplete="off" value={query} onChange={e=>setQuery(e.target.value)}/></label>
    <nav aria-label={t.nav} className="syn-route-peers">{peerList.map(p=><Link key={p.id} href={"/workforce/clinical/"+p.id} aria-current={p.id===screen.id?"page":undefined}><small>{p.id}</small><span>{p.name}</span></Link>)}</nav>
   </aside>
   <div className="syn-route-main" id="route-main">
    <header className="syn-route-header">
     <nav className="syn-route-breadcrumb"><Link href="/workforce/clinical">{t.home}</Link><span>/</span><span>{screen.module}</span><span>/</span><strong>{screen.id}</strong></nav>
     <div className="syn-route-title"><div><p>{screen.id} · {screen.module} · {locale==="fr"?definition.fr:definition.en}</p><h1>{screen.name}</h1><p>{detail?.authorityWorkflow||t.ready}</p></div><Glyph size={31} strokeWidth={1.7} aria-hidden="true"/></div>
     <LocaleToggle locale={locale} onChange={setLocale}/>
    </header>
    <section className="syn-route-alert" aria-label={t.notCertified} role="status"><ShieldAlert size={22} aria-hidden="true"/><div><strong>{t.notCertified}</strong><p>{t.warning}</p></div></section>
    <section className="syn-route-status" aria-label={t.scope}><div><h2>{t.scope}</h2><p aria-live="polite" data-staff-status={staff}>{scopeMsg}</p></div><button type="button" disabled={staff==="checking"} onClick={()=>void checkStaff()}><RefreshCcw size={17} aria-hidden="true"/>{staff==="not-checked"?t.check:t.refresh}</button></section>
    <div className="syn-route-tabs" role="group" aria-label={t.tab}>
     <button type="button" aria-pressed={tab==="screen"} onClick={()=>setTab("screen")}><PanelTop size={17}/>{t.structure}</button>
     <button type="button" aria-pressed={tab==="workflows"} onClick={()=>setTab("workflows")}><Workflow size={17}/>{t.workflow}</button>
     <button type="button" aria-pressed={tab==="documents"} onClick={()=>setTab("documents")}><FileText size={17}/>{t.documents}</button>
    </div>
    <section className="syn-route-panel" data-active-tab={tab}>
     {tab==="screen"&&<><div className="syn-route-panel-head"><h2>{screen.name}</h2><span><LockKeyhole size={15}/>{t.readOnly}</span></div><ScreenBody screen={screen} fields={inputFields} locale={locale}/><div className="syn-route-action"><div><strong>{t.action}</strong><p>{screen.primary_action}</p></div><button type="button" disabled title={t.disabled}>{screen.primary_action} · {t.locked}</button></div></>}
     {tab==="workflows"&&<><h2>{t.workflow}</h2>{flowPaths.length?flowPaths.map(f=><article key={f.id} className="syn-route-flow"><h3>{f.id} · {f.title}</h3><p><strong>{t.exceptions}:</strong> {f.exceptionPath}</p><nav>{f.path.map(x=><Link key={x} href={"/workforce/clinical/"+x} aria-current={x===screen.id?"page":undefined}>{x}</Link>)}</nav></article>):<EmptySource message={t.workflow} detail={detail?.authorityWorkflow||t.noData}/>}<p className="syn-route-small">{t.noPermission}</p></>}
     {tab==="documents"&&<><h2>{t.documents}</h2><p>{t.documentsNotice}</p>{documents.length?<nav className="syn-route-documents">{documents.map(d=><Link key={d.id+"-"+d.href} href={d.href}><FileText size={18} aria-hidden="true"/><span>{d.id} · {d.title}</span></Link>)}</nav>:<EmptySource message={t.noRelated} detail={t.documentsNotice}/>}<Link href="/hospital-documents">{t.documents}</Link></>}
    </section>
    <nav className="syn-route-pager" aria-label={t.nav}><span>{index>0&&<Link href={"/workforce/clinical/"+peers[index-1].id}><ArrowLeft size={18}/>{t.back}: {peers[index-1].name}</Link>}</span><span>{index>=0&&index<peers.length-1&&<Link href={"/workforce/clinical/"+peers[index+1].id}>{t.next}: {peers[index+1].name}<ArrowRight size={18}/></Link>}</span></nav>
    <footer className="syn-route-foot"><BookOpen size={17} aria-hidden="true"/><p>{t.noClaim}. {t.notCertified}.</p><Link href={"/ui/catalog/"+screen.id}>UI specification</Link></footer>
   </div>
  </div>
 </main>;
}
