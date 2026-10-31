'use client';
import {useState} from 'react';
import Link from 'next/link';
import {registrationFieldStructure,registrationSubmissionLocked,intakeNavigation,mobileRegistrationAccessibility,validateRegistrationDraft,blankRegistrationDraft,registrationIssueSummary} from '@synqoria/ux-core';
/** M01-S02 — read-only semantic engineering specimen. No patient collection or API mutation. */
export default function RegistrationIntakePage(){
 const [locale,setLocale]=useState<'en'|'fr'>('en');
 const fields=registrationFieldStructure(locale);
 const admission=intakeNavigation('workforce',new Set(),false);
 const lock=registrationSubmissionLocked({authenticated:false,online:false,facilityScoped:false,reviewCurrent:false,serverReleaseVerified:false,clinicalSignoff:false});
 const accessibility=mobileRegistrationAccessibility(locale);
 const issues=registrationIssueSummary(validateRegistrationDraft(blankRegistrationDraft(locale),'2026-10-30'),locale);
 return <main className="intake-page" lang={locale} id="main">
  <a className="skip-link" href="#intake-content">{locale==='fr'?'Aller au contenu':'Skip to content'}</a>
  <nav aria-label={locale==='fr'?'Fil d’Ariane':'Breadcrumb'} className="intake-crumbs"><Link href="/workforce">SYNQORIA</Link><span aria-hidden="true"> / </span><span>{locale==='fr'?'Patients':'Patients'}</span><span aria-hidden="true"> / </span><span aria-current="page">{accessibility.title}</span></nav>
  <header id="intake-content" className="intake-heading"><h1>{accessibility.title}</h1><div role="group" aria-label="Language"><button type="button" aria-pressed={locale==='en'} onClick={()=>setLocale('en')}>English</button><button type="button" aria-pressed={locale==='fr'} onClick={()=>setLocale('fr')}>Français</button></div></header>
  <section className="intake-locked" role="status" aria-live="polite"><h2>{locale==='fr'?'Accès non vérifié':'Access not verified'}</h2><p>{accessibility.alert}</p><p aria-live="polite">{issues.heading}</p><p>{locale==='fr'?'Aucune donnée de patient n’est demandée ni enregistrée.':'No patient information is requested or stored.'}</p></section>
  <section aria-labelledby="field-outline"><h2 id="field-outline">{locale==='fr'?'Structure du formulaire (aperçu)':'Form structure (preview)'}</h2><dl className="intake-fields">{fields.map(field=><div key={field.key}><dt>{field.label}</dt><dd>{field.hint}</dd></div>)}</dl></section>
  <button type="button" disabled aria-describedby="intake-unavailable">{accessibility.actionLabel}</button><p id="intake-unavailable">{locale==='fr'?'Autorisation clinique et connexion vérifiée nécessaires.':'Verified connection and clinical release required.'}</p>
  <p className="visually-hidden">{admission.canEnter?'':'Unauthorized'} {lock.reason}</p>
 </main>;
}
