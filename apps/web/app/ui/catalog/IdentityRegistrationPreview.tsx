"use client";

import {useEffect,useId,useRef,useState} from "react";
import type {KeyboardEvent,ReactNode} from "react";
import Link from "next/link";
import {
 blankRegistrationDraft, duplicateStatus, registrationSubmissionLocked,
 validateRegistrationDraft
} from "@synqoria/ux-core";
import {IDENTITY_SCREEN_SPECS} from "./identity-contracts";
import type {IdentityField,IdentityScreenId} from "./identity-contracts";
import "./identity-preview.css";

type Locale="en"|"fr";
const sectionByTab: Record<IdentityScreenId,readonly string[]>={
 "G-S01":["account","service","help"],
 "G-S02":["challenge","recovery","help"],
 "G-S03":["scope","scope","review"],
 "M01-S01":["query","filters","results"],
 "M01-S02":["identity","contact","review"],
 "M01-S03":["summary","verification","history"],
 "M01-S04":["compare","reason","review"],
 "M01-S05":["candidates","impact","proposal"],
 "M01-S06":["lineage","approval","reversal"]
};
const statusCopy={
 en:{banner:"DESIGN PREVIEW — not a clinical system",description:"Use synthetic demonstration entries only. No login, search, registration, disclosure or merge is possible.",server:"Clinical service not connected",fieldLocked:"This value requires a protected, authenticated server.",clear:"Clear demonstration entries",next:"Next section",previous:"Previous section",review:"Review demo draft",submit:"Unavailable — server authorization required",duplicate:"Independent duplicate review",validation:"Draft review findings",noErrors:"No local format issue detected. Registration still blocked.",empty:"No authorized records loaded.",help:"Help and safe operation",workflow:"Expected workflow",status:"Current permission status",noData:"No connected hospital identity, patient or session context.",notSaved:"Nothing is saved or transmitted.",language:"Interface language",source:"Schema proposal: pending verified field and role contract.",pending:"Requires protected backend",locked:"Locked",filters:"Filter status",clearFilters:"Reset filters",searchStatus:"Patient search requires verified access; no lookup was sent.",print:"Print/export locked"},
 fr:{banner:"APERÇU DE CONCEPTION — pas un système clinique",description:"Utilisez uniquement des exemples fictifs. Aucune connexion, recherche, inscription, divulgation ou fusion n'est possible.",server:"Service clinique non connecté",fieldLocked:"Cette valeur nécessite un serveur protégé et authentifié.",clear:"Effacer les exemples",next:"Section suivante",previous:"Section précédente",review:"Vérifier le brouillon fictif",submit:"Indisponible — autorisation du serveur requise",duplicate:"Révision indépendante des doublons",validation:"Résultats de la vérification locale",noErrors:"Aucun problème de format local détecté. L'inscription reste bloquée.",empty:"Aucun dossier autorisé chargé.",help:"Aide et sécurité",workflow:"Flux de travail attendu",status:"État des autorisations",noData:"Aucun contexte hospitalier, patient ou session connecté.",notSaved:"Rien n'est enregistré ni transmis.",language:"Langue de l'interface",source:"Proposition de schéma : validation des champs et rôles requise.",pending:"Serveur protégé requis",locked:"Verrouillé",filters:"État des filtres",clearFilters:"Réinitialiser les filtres",searchStatus:"La recherche nécessite un accès vérifié ; aucune requête envoyée.",print:"Impression/export bloqués"}
};
const issueEn:Record<string,string>={
 name_too_long:"Reported name exceeds 200 characters or includes control characters.",
 invalid_date:"The known birth date is invalid.",
 future_birth:"The known birth date is in the future.",
 invalid_estimated_age:"Estimated age must be a whole number from 0 to 130.",
 conflicting_birth_fields:"Use a known birth date OR an estimated age, never both.",
 review_required:"Independent duplicate review has not been verified."
};
const issueFr:Record<string,string>={
 name_too_long:"Nom trop long ou contenant des caractères interdits.",
 invalid_date:"Date de naissance connue non valide.",
 future_birth:"La date de naissance est dans le futur.",
 invalid_estimated_age:"L'âge estimé doit être un nombre entier de 0 à 130.",
 conflicting_birth_fields:"Indiquez une date connue OU un âge estimé, jamais les deux.",
 review_required:"La révision indépendante des doublons n'est pas vérifiée."
};
const screenIntro=(id:IdentityScreenId,locale:Locale)=>{
 const en:Record<IdentityScreenId,string>={
 "G-S01":"Staff identity must be verified through an approved identity provider.",
 "G-S02":"Never enter a genuine MFA code or recovery secret in this preview.",
 "G-S03":"Facility, role and shift are server-issued. Demo entries cannot grant privileges.",
 "M01-S01":"Search before registering. Patient existence cannot be disclosed without authorization.",
 "M01-S02":"Review identity, contact and possible duplicates before any registration.",
 "M01-S03":"Patient profile requires scoped, patient-specific read permissions.",
 "M01-S04":"Corrections require source evidence, version checks and reason.",
 "M01-S05":"Duplicate reviews require an independent human and preserve both records.",
 "M01-S06":"A merge needs independent approval, impact review and lineage retention."};
 const fr:Record<IdentityScreenId,string>={
 "G-S01":"L'identité du personnel doit être vérifiée par un fournisseur agréé.",
 "G-S02":"Ne saisissez aucun code MFA réel ni secret de récupération.",
 "G-S03":"L'établissement, le rôle et le service proviennent du serveur.",
 "M01-S01":"Recherchez avant d'enregistrer. La présence du patient reste protégée.",
 "M01-S02":"Examinez l'identité, les contacts et les doublons avant l'inscription.",
 "M01-S03":"Le profil exige une lecture limitée au patient autorisé.",
 "M01-S04":"Les corrections exigent une preuve, une version et un motif.",
 "M01-S05":"Les doublons exigent une révision indépendante et la conservation des historiques.",
 "M01-S06":"La fusion exige une approbation indépendante et la traçabilité."};
 return (locale==="fr"?fr:en)[id];
};
const screenIds=new Set(Object.keys(IDENTITY_SCREEN_SPECS));
export function isIdentityPreview(id:string):id is IdentityScreenId{return screenIds.has(id);}

export function IdentityRegistrationPreview({screenId,primaryAction}:{screenId:IdentityScreenId;primaryAction:string}){
 const spec=IDENTITY_SCREEN_SPECS[screenId];
 const [locale,setLocale]=useState<Locale>("en");
 const [tab,setTab]=useState(0);
 const [draft,setDraft]=useState<Record<string,string>>({});
 const [reviewed,setReviewed]=useState(false);
 const [issues,setIssues]=useState<string[]>([]);
 const [filtersVisible,setFiltersVisible]=useState(false);
 const [helpVisible,setHelpVisible]=useState(false);
 const tabRefs=useRef<Array<HTMLButtonElement|null>>([]);
 const base=useId().replace(/:/g,"");
 const t=statusCopy[locale];
 const section=sectionByTab[screenId][tab]??"";
 const registration=screenId==="M01-S02";
 const search=screenId==="M01-S01";
 const readonlyServer=spec.readonlyServer;
 const showHelp=section==="help";
 const summary=section==="review"||section==="results"||section==="proposal"||section==="reversal"||section==="history";
 const locked=registrationSubmissionLocked({authenticated:false,online:false,facilityScoped:false,reviewCurrent:false,serverReleaseVerified:false,clinicalSignoff:false});
 const duplicate=duplicateStatus("NOT_CHECKED",locale);

 useEffect(()=>{
  const clearOnHide=()=>{if(document.visibilityState==="hidden"){setDraft({});setReviewed(false);setIssues([]);}};
  document.addEventListener("visibilitychange",clearOnHide);
  return ()=>document.removeEventListener("visibilitychange",clearOnHide);
 },[]);

 function setField(key:string,value:string){
  setDraft(prev=>{
   const next={...prev,[key]:value};
   if(key==="birthInformation"){
    next.dateOfBirth="";next.estimatedAge="";
   }
   return next;
  });
  setReviewed(false);setIssues([]);
 }
 function clearDraft(){setDraft({});setReviewed(false);setIssues([]);}
 function onTabKey(e:KeyboardEvent<HTMLButtonElement>,i:number){
  let n=i;
  if(e.key==="ArrowRight")n=(i+1)%spec.tabs.length;
  else if(e.key==="ArrowLeft")n=(i+spec.tabs.length-1)%spec.tabs.length;
  else if(e.key==="Home")n=0;
  else if(e.key==="End")n=spec.tabs.length-1;
  else return;
  e.preventDefault();setTab(n);tabRefs.current[n]?.focus();
 }
 function reviewDraft(){
  if(registration){
   const birth=(draft.birthInformation??"unknown") as "known"|"estimated"|"unknown";
   const registrationDraft={...blankRegistrationDraft(locale),name:draft.name??"",birthInformation:birth,dateOfBirth:draft.dateOfBirth??"",estimatedAge:draft.estimatedAge??"",language:((draft.language==="fr"?"fr":"en") as Locale),duplicateReviewCleared:false};
   const localIssues=validateRegistrationDraft(registrationDraft,new Date().toISOString().slice(0,10));
   setIssues(localIssues);
  }else setIssues([]);
  setReviewed(true);setTab(spec.tabs.length-1);
 }
 function activeFields():IdentityField[]{
  if(showHelp||summary) return [];
  if(screenId==="G-S03"&&tab===0)return spec.fields.filter(f=>["facility","role"].includes(f.key));
  if(screenId==="G-S03"&&tab===1)return spec.fields.filter(f=>["shift","activeScope"].includes(f.key));
  return spec.fields.filter(f=>f.section===section);
 }
 function renderField(f:IdentityField):ReactNode{
  const fieldId=base+"-"+f.key;
  const disabled=f.locked===true;
  const birth=draft.birthInformation??"unknown";
  const condition=registration&&(f.key==="dateOfBirth"&&birth!=="known"||f.key==="estimatedAge"&&birth!=="estimated");
  const label=locale==="fr"?f.fr:f.en;
  const opts=f.options??[];
  const value=draft[f.key]??(f.key==="birthInformation"?"unknown":f.key==="language"?locale:"");
  const attrs={id:fieldId,name:f.key,disabled:disabled||condition,"aria-describedby":fieldId+"-hint"} as const;
  return <label key={f.key} htmlFor={fieldId} className="syn-identity-field">
   <span>{label}{f.required&&<span className="syn-required"> ({locale==="fr"?"requis":"required"})</span>}</span>
   {f.type==="select"?<select {...attrs} value={value} onChange={e=>setField(f.key,e.target.value)}>
     {!opts.length&&<option value="">{t.pending}</option>}
     {opts.map(o=><option key={o.value} value={o.value}>{locale==="fr"?o.fr:o.en}</option>)}
   </select>:
   f.type==="textarea"?<textarea {...attrs} value={value} onChange={e=>setField(f.key,e.target.value)} maxLength={400} rows={4} placeholder={t.source}/>:
   <input {...attrs} type={f.type} value={value} onChange={e=>setField(f.key,e.target.value)}
     autoComplete="off" spellCheck={false} maxLength={f.type==="number"?3:160} min={f.type==="number"?0:undefined} max={f.type==="number"?130:undefined}
     placeholder={disabled?t.pending:locale==="fr"?"Exemple fictif uniquement":"Synthetic example only"}/>}
   <small id={fieldId+"-hint"}>{disabled?t.fieldLocked:condition?(locale==="fr"?"Choisissez d'abord le type de naissance":"Choose birth information first"):t.notSaved}</small>
  </label>;
 }
 function fieldPreview(){
  const fields=activeFields();
  if(!fields.length) return null;
  return <div className="syn-identity-fields">{fields.map(renderField)}</div>;
 }
 function safeSummary(){
  if(screenId==="M01-S02"){
   return <div className="syn-identity-review" aria-label="Registration review">
    <div className="syn-identity-summary">{spec.fields.filter(f=>!f.locked).map(f=><div key={f.key}><span>{locale==="fr"?f.fr:f.en}</span><strong>{draft[f.key]?.trim()?"● "+(locale==="fr"?"Saisi":"Supplied"):(locale==="fr"?"Non saisi":"Not supplied")}</strong></div>)}</div>
    <div className="syn-identity-warning"><h4>{t.duplicate}</h4><p>{duplicate.status}: {duplicate.instruction}</p><p>{t.submit} ({locked.reason}).</p></div>
    {reviewed?<div role="alert" className="syn-identity-feedback"><h4>{t.validation}</h4>{issues.length?<ul>{issues.map(code=><li key={code}>{(locale==="fr"?issueFr:issueEn)[code]??code}</li>)}</ul>:<p>{t.noErrors}</p>}</div>:<p>{locale==="fr"?"Utilisez Vérifier le brouillon fictif pour l'analyse locale.":"Use Review demo draft for local-only format checks."}</p>}
   </div>;
  }
  return <div className="syn-identity-empty" role="status"><strong>{t.empty}</strong><p>{t.noData}</p><p>{t.source}</p></div>;
 }
 function searchResults(){
  return <div className="syn-identity-table" role="region" aria-label="Authorized matching results">
   <table><caption>{t.empty}</caption><thead><tr><th scope="col">{locale==="fr"?"Identifiant":"Identifier"}</th><th scope="col">{locale==="fr"?"Correspondance":"Match confidence"}</th><th scope="col">{locale==="fr"?"État":"Verification"}</th><th scope="col">{locale==="fr"?"Action":"Action"}</th></tr></thead><tbody><tr><td colSpan={4}>{t.searchStatus}</td></tr></tbody></table>
   <button type="button" disabled>{primaryAction}</button>
  </div>;
 }
 function workflow(){
  return <ol className="syn-identity-workflow">{spec.workflow.map((item,i)=><li key={item}><span className="syn-identity-step">{i+1}</span><p>{item}</p></li>)}</ol>;
 }
 function area():ReactNode{
  if(showHelp)return <div><h3>{t.help}</h3><p>{screenIntro(screenId,locale)}</p><p>{t.description}</p><Link href="/ui/catalog"> {locale==="fr"?"Retour aux écrans":"Return to all screens"}</Link>{workflow()}</div>;
  if(screenId==="M01-S01"){
   if(section==="results")return <>{searchResults()}<Link href="/ui/catalog/M01-S02">{locale==="fr"?"Aperçu nouvelle inscription":"New registration preview"}</Link></>;
   return <>{fieldPreview()}{section==="query"&&<div className="syn-identity-actions"><button type="button" className="syn-preview-secondary" onClick={()=>setFiltersVisible(!filtersVisible)} aria-expanded={filtersVisible}>{filtersVisible?(locale==="fr"?"Masquer les filtres":"Hide advanced filters"):(locale==="fr"?"Afficher les filtres":"Show advanced filters")}</button><button type="button" className="syn-preview-secondary" onClick={clearDraft}>{t.clearFilters}</button><button type="button" disabled>{primaryAction}</button></div>}{filtersVisible&&section==="query"&&<div className="syn-identity-filter-panel"><h4>{t.filters}</h4>{spec.fields.filter(f=>f.section==="filters").map(renderField)}</div>}<p className="syn-identity-status" role="status">{t.searchStatus}</p></>;
  }
  if(registration&&section==="review")return safeSummary();
  if(["M01-S03","M01-S05","M01-S06"].includes(screenId))return <>{fieldPreview()}{safeSummary()}{section==="proposal"||section==="reversal"?workflow():null}</>;
  if(section==="history"||section==="results"||section==="proposal"||section==="reversal")return safeSummary();
  if(screenId==="G-S03"&&section==="review")return <>{safeSummary()}{workflow()}</>;
  if(screenId==="M01-S04"&&section==="review")return <>{safeSummary()}{workflow()}</>;
  return <>{fieldPreview()}{screenId==="G-S02"&&section==="recovery"&&<p>{locale==="fr"?"La récupération n'est possible qu'avec l'autorisation de l'organisation.":"Recovery is permitted only through approved organizational policies."}</p>}</>;
 }
 return <div className="syn-ui-preview syn-identity-preview" data-screen-id={screenId}>
   <div className="syn-identity-toolbar"><span className="syn-chip">{screenId} · {locale==="fr"?"Aperçu sécurisé":"Secure preview"}</span><fieldset className="syn-lang-choice"><legend>{t.language}</legend><button type="button" aria-pressed={locale==="en"} onClick={()=>setLocale("en")}>English</button><button type="button" aria-pressed={locale==="fr"} onClick={()=>setLocale("fr")}>Français</button></fieldset></div>
   <div className="syn-identity-alert" role="status"><strong>{t.banner}</strong><p>{t.description}</p><p>{screenIntro(screenId,locale)}</p></div>
   <nav aria-label={screenId+" workflow links"} className="syn-identity-journey">
    {(screenId.startsWith("G-")?["G-S01","G-S02","G-S03"]:["M01-S01","M01-S02","M01-S03","M01-S04","M01-S05","M01-S06"]).map(id=><Link key={id} href={"/ui/catalog/"+id} aria-current={screenId===id?"page":undefined}>{id==="G-S01"?"Sign in":id==="G-S02"?"MFA":id==="G-S03"?"Facility":id==="M01-S01"?"Search":id==="M01-S02"?"Registration":id==="M01-S03"?"Profile":id==="M01-S04"?"Correction":id==="M01-S05"?"Duplicate review":"Merge approval"}</Link>)}
   </nav>
   <div className="syn-real-tabs" role="tablist" aria-label={screenId+" screen sections"}>{spec.tabs.map((name,i)=><button type="button" role="tab" aria-selected={tab===i} aria-controls={base+"-panel"} id={base+"-tab-"+i} tabIndex={tab===i?0:-1} ref={node=>{tabRefs.current[i]=node;}} key={name} onClick={()=>setTab(i)} onKeyDown={e=>onTabKey(e,i)}>{name}</button>)}</div>
   <section className="syn-identity-main" role="tabpanel" id={base+"-panel"} aria-labelledby={base+"-tab-"+tab} tabIndex={0}>
     <h3>{spec.tabs[tab]}</h3><p className="syn-identity-intro">{spec.description}</p>
     {area()}
   </section>
   <div className="syn-identity-actions">
    <button type="button" className="syn-preview-secondary" disabled={tab===0} onClick={()=>setTab(Math.max(0,tab-1))}>{t.previous}</button>
    <button type="button" className="syn-preview-secondary" disabled={tab===spec.tabs.length-1} onClick={()=>setTab(Math.min(spec.tabs.length-1,tab+1))}>{t.next}</button>
    {registration&&<button type="button" className="syn-preview-primary" onClick={reviewDraft}>{t.review}</button>}
    {(registration||search||screenId==="M01-S04")&&<button type="button" className="syn-preview-secondary" onClick={clearDraft}>{t.clear}</button>}
    <button type="button" className="syn-locked" disabled title={t.submit}>{primaryAction}<span className="syn-lock-reason">{t.locked}: {t.pending}</span></button>
   </div>
   <div className="syn-identity-footer"><button type="button" className="syn-preview-secondary" onClick={()=>setHelpVisible(v=>!v)} aria-expanded={helpVisible}>{helpVisible?(locale==="fr"?"Masquer le flux":"Hide workflow"):(locale==="fr"?"Voir le flux":"View workflow")}</button>{helpVisible&&<section><h4>{t.workflow}</h4>{workflow()}</section>}<p>{t.notSaved} {t.server}.</p><p><strong>{t.status}:</strong> {t.locked} · {spec.controls.length} {locale==="fr"?"contrôles prévus":"planned controls"}</p><p><Link href="/ui/catalog/components">{locale==="fr"?"Bibliothèque des composants":"Component gallery"}</Link></p></div>
  </div>;
}
