"use client";
import {useEffect,useState} from "react";
import Link from "next/link";
import {defaultPreferences,decodePreferences,encodePreferences,formatTime,PREFERENCE_KEY} from "../../../lib/workforce-preferences.mjs";
import type {UiPreferences} from "../../../lib/workforce-preferences.mjs";
import "./settings.css";
function applyNonclinicalPreferences(p:UiPreferences){
 document.documentElement.lang=p.language;
 document.documentElement.dataset.synqoriaDensity=p.density;
 document.documentElement.dataset.synqoriaContrast=p.contrast;
 document.documentElement.dataset.synqoriaMotion=p.motion;
}
const copy={
 en:{title:"Personal settings & accessibility",intro:"Your saved presentation preferences apply to this browser. No medical data or staff credentials are stored.",language:"Language",time:"Time display",density:"Layout density",contrast:"Contrast",motion:"Animation and motion",comfortable:"Comfortable",compact:"Compact",standard:"Standard",high:"Higher contrast",system:"Follow device preference",reduced:"Reduce motion",save:"Save preferences",reset:"Reset defaults",saved:"Preferences saved in this browser",error:"Browser storage unavailable; changes remain in memory",loaded:"Preferences loaded",sample:"Time display preview",back:"Workforce operations",privacy:"Stored values are limited to language, time format, density, contrast and motion. Never store patient data or authentication tokens here."},
 fr:{title:"Paramètres et accessibilité",intro:"Les préférences de présentation sont conservées dans ce navigateur sans données médicales ou identifiants de connexion.",language:"Langue",time:"Format de l’heure",density:"Densité de l’interface",contrast:"Contraste",motion:"Animation et mouvement",comfortable:"Confortable",compact:"Compact",standard:"Standard",high:"Contraste élevé",system:"Selon l'appareil",reduced:"Réduire les animations",save:"Enregistrer les préférences",reset:"Réinitialiser",saved:"Préférences enregistrées dans le navigateur",error:"Stockage indisponible; changements conservés en mémoire",loaded:"Préférences chargées",sample:"Aperçu de l’heure",back:"Retour aux opérations",privacy:"Seuls langue, format horaire, densité, contraste et mouvements sont stockés. Aucune donnée patient ou jeton d’authentification."}
};
export default function StaffPreferences(){
 const [p,setP]=useState<UiPreferences>(()=>defaultPreferences());
 const [message,setMessage]=useState(""),[mounted,setMounted]=useState(false);
 useEffect(()=>{
  try{const value=decodePreferences(window.localStorage.getItem(PREFERENCE_KEY));setP(value);applyNonclinicalPreferences(value);}
  catch{/* Browser may disallow storage */}finally{setMounted(true);}
  const onStorage=(e:StorageEvent)=>{if(e.key===PREFERENCE_KEY){const next=decodePreferences(e.newValue);setP(next);applyNonclinicalPreferences(next);}};
  window.addEventListener("storage",onStorage);return()=>window.removeEventListener("storage",onStorage);
 },[]);
 const t=copy[p.language];
 function update<K extends keyof UiPreferences>(name:K,value:UiPreferences[K]){setP(prev=>({...prev,[name]:value}));setMessage("");}
 function persist(next:UiPreferences){
  try{window.localStorage.setItem(PREFERENCE_KEY,encodePreferences(next));applyNonclinicalPreferences(next);setMessage(copy[next.language].saved);}
  catch{applyNonclinicalPreferences(next);setMessage(copy[next.language].error);}
 }
 function reset(){const defaults=defaultPreferences();setP(defaults);persist(defaults);}
 return <main className="syn-settings" data-real-screen-id="G-S06" lang={p.language}>
  <nav className="syn-settings-breadcrumb"><Link href="/workforce/clinical">{t.back}</Link><span>/</span><span>G-S06</span></nav>
  <header><p>G-S06 · SYNQORIA</p><h1>{t.title}</h1><p>{t.intro}</p></header>
  <form onSubmit={e=>{e.preventDefault();persist(p);}} className="syn-settings-form">
   <label>{t.language}<select value={p.language} onChange={e=>update("language",e.target.value as UiPreferences["language"])}><option value="en">English</option><option value="fr">Français</option></select></label>
   <label>{t.time}<select value={p.timeFormat} onChange={e=>update("timeFormat",e.target.value as UiPreferences["timeFormat"])}><option value="24h">24-hour</option><option value="12h">12-hour</option></select></label>
   <label>{t.density}<select value={p.density} onChange={e=>update("density",e.target.value as UiPreferences["density"])}><option value="comfortable">{t.comfortable}</option><option value="compact">{t.compact}</option></select></label>
   <label>{t.contrast}<select value={p.contrast} onChange={e=>update("contrast",e.target.value as UiPreferences["contrast"])}><option value="standard">{t.standard}</option><option value="high">{t.high}</option></select></label>
   <label>{t.motion}<select value={p.motion} onChange={e=>update("motion",e.target.value as UiPreferences["motion"])}><option value="system">{t.system}</option><option value="reduced">{t.reduced}</option></select></label>
   <div className="syn-settings-actions"><button type="submit">{t.save}</button><button type="button" onClick={reset}>{t.reset}</button></div>
  </form>
  <section className="syn-settings-note"><h2>{t.sample}</h2><p>{formatTime("2026-11-05T14:15:00.000Z",p.language,p.timeFormat)}</p><p>{t.privacy}</p></section>
  {mounted&&<div role="status" aria-live="polite" className="syn-settings-status">{message||t.loaded}</div>}
 </main>;
}
