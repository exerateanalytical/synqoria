"use client";
import Link from "next/link";
import {createContext,useCallback,useContext,useEffect,useId,useRef,useState} from "react";
import type {ReactNode} from "react";
import styles from "./analytics.module.css";
import {detectLang,dictionaries,errorKey,safetyLabel,translate} from "../../lib/analytics/i18n";
import type {Key,Lang} from "../../lib/analytics/i18n";
import type {Failure,Result} from "../../lib/analytics/client";

type Ctx={lang:Lang;t:(k:Key,v?:Record<string,string|number>)=>string;online:boolean};
const AnContext=createContext<Ctx>({lang:"en",t:(k,v)=>translate("en",k,v),online:true});
export function useAn(){return useContext(AnContext);}

const NAV:{href:string;key:Key;id:string}[]=[
 {href:"/workforce/analytics/dashboard",key:"nav.dashboard",id:"M28-S01"},
 {href:"/workforce/analytics/clinical",key:"nav.clinical",id:"M28-S02"},
 {href:"/workforce/analytics/financial",key:"nav.financial",id:"M28-S03"},
 {href:"/workforce/analytics/reports",key:"nav.reports",id:"M28-S04"},
 {href:"/workforce/analytics/exports",key:"nav.exports",id:"M28-S05"}
];
export {NAV};

export function AnFrame({active,title,intro,children}:{active:string;title:Key;intro:Key;children:ReactNode}){
 const [lang,setLang]=useState<Lang>("en");
 const [online,setOnline]=useState(true);
 useEffect(()=>{
  let stored:string|null=null;
  try{stored=window.localStorage.getItem("synq_lang");}catch{/* storage may be blocked */}
  setLang(detectLang(stored,navigator.language));
  setOnline(navigator.onLine);
  const up=()=>setOnline(true),down=()=>setOnline(false);
  window.addEventListener("online",up);window.addEventListener("offline",down);
  return()=>{window.removeEventListener("online",up);window.removeEventListener("offline",down);};
 },[]);
 useEffect(()=>{document.documentElement.lang=lang;},[lang]);
 const t=useCallback((k:Key,v?:Record<string,string|number>)=>translate(lang,k,v),[lang]);
 const change=(value:string)=>{
  const next:Lang=value==="fr"?"fr":"en";setLang(next);
  try{window.localStorage.setItem("synq_lang",next);}catch{/* ignore */}
 };
 return <AnContext.Provider value={{lang,t,online}}>
  <div className={styles.root}>
   <a className={styles.skip} href="#content">{t("skip")}</a>
   <div className={styles.shell}>
    <header className={styles.header}>
     <div className={styles.headerText}>
      <p className={styles.eyebrow}>{t("app.subtitle")} · {NAV.find(n=>n.href.endsWith(active))?.id??""}</p>
      <h1 className={styles.title}>{t(title)}</h1>
      <p className={styles.intro}>{t(intro)}</p>
     </div>
     <div className={styles.langBox}>
      <label htmlFor="m26-lang">{t("lang.label")}</label>
      <select id="m26-lang" className={styles.input} value={lang} onChange={e=>change(e.target.value)}>
       <option value="en">English</option><option value="fr">Français</option>
      </select>
     </div>
    </header>
    <nav aria-label={t("nav.label")}>
     <ul className={styles.nav}>
      {NAV.map(n=><li key={n.href}><Link href={n.href} aria-current={n.href.endsWith(active)?"page":undefined}>
       <span className={styles.mono}>{n.id}</span> {t(n.key)}</Link></li>)}
     </ul>
    </nav>
    {!online&&<div className={`${styles.banner} ${styles.bannerWarn}`} role="status"><strong>{t("state.offline")}</strong></div>}
    <main id="content" tabIndex={-1}>{children}</main>
   </div>
  </div>
 </AnContext.Provider>;
}

export function Loading(){
 const {t}=useAn();
 return <div className={styles.state} role="status" aria-live="polite"><h3>{t("state.loading")}</h3></div>;
}
export function Empty({hint=true,message}:{hint?:boolean;message?:Key}){
 const {t}=useAn();
 return <div className={styles.state} role="status"><h3>{t(message??"state.empty")}</h3>{hint&&<p>{t("state.emptyHint")}</p>}</div>;
}
/** Full-panel failure state for a failed load. */
export function FailureView({failure,onRetry}:{failure:Failure;onRetry?:()=>void}){
 const {t}=useAn();
 const tone=failure.kind==="unauthorized"||failure.kind==="forbidden"?styles.bannerWarn:styles.bannerDanger;
 let title:Key="state.error",hint:Key="state.errorHint";
 if(failure.kind==="unauthorized"){title="state.unauthorized";hint="state.unauthorizedHint";}
 else if(failure.kind==="forbidden"){title="state.forbidden";hint="state.forbiddenHint";}
 else if(failure.kind==="notfound"){title="state.notFound";hint="state.emptyHint";}
 else if(failure.kind==="offline"){title="state.offline";hint="state.errorHint";}
 else if(failure.kind==="notconfigured"){title="state.notConfigured";hint="state.errorHint";}
 return <div className={`${styles.banner} ${tone}`} role="alert">
  <strong>{t(title)}</strong><p>{t(hint)}</p>
  <div className={styles.actions}>
   {failure.kind==="unauthorized"&&<a className={styles.btn} href="/api/operations/auth/start">{t("state.signIn")}</a>}
   {onRetry&&failure.kind!=="unauthorized"&&failure.kind!=="forbidden"&&
    <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={onRetry}>{t("common.retry")}</button>}
  </div>
 </div>;
}
/** Inline result of a failed action (409/422/403…). Safety codes are listed in plain words. */
export function ActionError({failure,onReload}:{failure:Failure|null;onReload?:()=>void}){
 const {t,lang}=useAn();
 if(!failure)return null;
 if(failure.kind==="unauthorized")return <FailureView failure={failure}/>;
 const key=errorKey(failure.code);
 const msg=key==="err.generic"?t("err.generic",{code:failure.code}):t(key);
 const stale=failure.code==="stale_version";
 return <div className={`${styles.banner} ${failure.kind==="conflict"||failure.kind==="invalid"?styles.bannerDanger:styles.bannerWarn}`} role="alert">
  <strong>{msg}</strong>
  {failure.details.length>0&&<p>{failure.details.map(d=>safetyLabel(lang,d)).join(" · ")}</p>}
  {stale&&onReload&&<div className={styles.actions}><button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={onReload}>{t("common.reload")}</button></div>}
 </div>;
}
export function Notice({children}:{children:ReactNode}){
 return <div className={`${styles.banner} ${styles.bannerOk}`} role="status">{children}</div>;
}

type Tone="info"|"ok"|"warn"|"danger"|"neutral";
export function Badge({tone,children}:{tone:Tone;children:ReactNode}){
 const cls={info:styles.bInfo,ok:styles.bOk,warn:styles.bWarn,danger:styles.bDanger,neutral:styles.bNeutral}[tone];
 return <span className={`${styles.badge} ${cls}`}>{children}</span>;
}
export function toneFor(status:string|null|undefined):Tone{
 switch(status){
  case "OK":case "SUCCEEDED":case "READY":case "APPROVED":case "VERIFIED":return "ok";
  case "QUEUED":case "REQUESTED":case "PENDING":case "RUNNING":case "DRAFT":return "info";
  case "INCOMPLETE":case "STALE":return "warn";
  case "FAILED":case "REJECTED":case "EXPIRED":case "SUSPENDED":return "danger";
  default:return "neutral";
 }
}

/** Loads a resource, discarding stale async results when inputs change or the screen unmounts. */
export function useLoad<T>(loader:(signal:AbortSignal)=>Promise<Result<T>>,deps:unknown[]){
 const [state,setState]=useState<{phase:"loading"|"ready"|"failed";data?:T;failure?:Failure}>({phase:"loading"});
 const [tick,setTick]=useState(0);
 const latest=useRef(0);
 useEffect(()=>{
  const id=++latest.current;const ctl=new AbortController();
  setState(s=>({phase:"loading",data:s.data}));
  loader(ctl.signal).then(r=>{
   if(id!==latest.current)return;
   setState(r.ok?{phase:"ready",data:r.data}:{phase:"failed",failure:r});
  });
  return()=>ctl.abort();
  // eslint-disable-next-line react-hooks/exhaustive-deps
 },[...deps,tick]);
 const reload=useCallback(()=>setTick(x=>x+1),[]);
 return {...state,reload};
}

export function Field({label,hint,children,wide,id}:{label:string;hint?:string;children:ReactNode;wide?:boolean;id:string}){
 return <div className={`${styles.field} ${wide?styles.wide:""}`}>
  <label htmlFor={id}>{label}</label>{children}{hint&&<span className={styles.hint} id={id+"-hint"}>{hint}</span>}
 </div>;
}
export function Pager({page,size,total,onPage}:{page:number;size:number;total:number;onPage:(p:number)=>void}){
 const {t}=useAn();
 const pages=Math.max(1,Math.ceil(total/size));
 return <div className={styles.pager}>
  <span role="status">{t("common.total",{total})} · {t("common.page",{page:page+1,pages})}</span>
  <span className={styles.actions} style={{marginTop:0}}>
   <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} disabled={page<=0} onClick={()=>onPage(page-1)}>{t("common.previous")}</button>
   <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} disabled={pages<=page+1} onClick={()=>onPage(page+1)}>{t("common.next")}</button>
  </span>
 </div>;
}
/** A button that is either wired to an action or disabled WITH a visible reason. */
export function ActionButton({label,onClick,reason,tone="primary",busy}:{label:string;onClick:()=>void;reason?:string|null;tone?:"primary"|"secondary"|"danger";busy?:boolean}){
 const {online,t}=useAn();
 const why=reason??(!online?t("disabled.offline"):null);
 const cls=tone==="danger"?styles.btnDanger:tone==="secondary"?styles.btnSecondary:"";
 const id=useId();
 return <span>
  <button type="button" className={`${styles.btn} ${cls}`} disabled={!!why||busy} aria-describedby={why?id:undefined} onClick={onClick}>{label}</button>
  {why&&<span id={id} className={styles.actionNote} style={{display:"block"}}>{why}</span>}
 </span>;
}
export function fmtTime(lang:Lang,iso:string|null|undefined):string{
 if(!iso)return "—";
 const d=new Date(iso);
 return Number.isNaN(d.getTime())?"—":d.toLocaleString(lang==="fr"?"fr-FR":"en-GB",{dateStyle:"medium",timeStyle:"short"});
}
export {styles,dictionaries};
