'use client';
import { useEffect, useRef, useState } from 'react';
import { attachMobileMoreDisclosure } from '../lib/mobile-more-controller';
import Link from 'next/link';
import { allowedWorkspaces, translate, selectLocale, workspaceLabel, type Locale, type Shell } from '@synqoria/ux-core';

/** Presentation only. Empty grants intentionally prevent data-bearing workspace navigation. */
export function ShellFrame({shell}:{shell:Shell}) {
  const [locale,setLocale]=useState<Locale>('en');
  const [moreOpen,setMoreOpen]=useState(false);
  const moreButton=useRef<HTMLButtonElement>(null);
  const morePanel=useRef<HTMLElement>(null);
  useEffect(()=>{const requested=new URLSearchParams(window.location.search).get('lang');if(requested==='fr'||requested==='en')setLocale(selectLocale(requested));},[]);
  useEffect(()=>{document.documentElement.lang=locale;},[locale]);
  const localized=(path:string)=>`${path}?lang=${locale}`;
  useEffect(()=>{
    if (!moreOpen || !moreButton.current || !morePanel.current) return;
    return attachMobileMoreDisclosure(moreButton.current,morePanel.current,(restoreFocus)=>{
      setMoreOpen(false);
      if (restoreFocus) moreButton.current?.focus();
    });
  },[moreOpen]);
  const entries=allowedWorkspaces({shell,grantedCapabilities:new Set(),licensedModules:new Set()});
  const t=(key:Parameters<typeof translate>[1])=>translate(locale,key);
  const shellTitle=t(shell==='workforce'?'workforce':shell==='portal'?'portal':'platform');
  return <div className="shell" lang={locale}>
    <a className="skip-link" href="#main">{t('skipToContent')}</a>
    <header className="topbar">
      <Link href={localized("/")} className="brand" aria-label="SYNQORIA">SYNQORIA</Link>
      <span className="topbar-context">{shellTitle} · {t('noFacility')} · {t('noConnection')}</span>
      <nav className="shell-switcher" aria-label={locale==='fr'?'Espaces':'Spaces'}>
        <Link href={localized("/workforce")} aria-current={shell==='workforce'?'page':undefined}>{locale==='fr'?'Personnel':'Workforce'}</Link>
        <Link href={localized("/portal")} aria-current={shell==='portal'?'page':undefined}>{locale==='fr'?'Patients':'Patient'}</Link>
        <Link href={localized("/platform")} aria-current={shell==='platform'?'page':undefined}>{locale==='fr'?'Plateforme':'Platform'}</Link>
      </nav>
      <fieldset className="locale-switch" aria-label={t('language')}>
        <legend className="visually-hidden">{t('language')}</legend>
        <button type="button" lang="en" aria-pressed={locale==='en'} onClick={()=>setLocale(selectLocale('en'))}>EN</button>
        <button type="button" lang="fr" aria-pressed={locale==='fr'} onClick={()=>setLocale(selectLocale('fr'))}>FR</button>
      </fieldset>
    </header>
    <aside className="sidebar" aria-label={t('navigation')}>
      <p className="sidebar-label">{shellTitle}</p>
      {entries.length ? <nav>{entries.map(e=><Link key={e.id} href={e.href}>{workspaceLabel(locale,e)}</Link>)}</nav> :
        <p className="sidebar-empty">{t('accessReason')}</p>}
      <Link href={localized("/")} className="back-link">{t('backToEntry')}</Link>
    </aside>
    <main className="main" id="main" aria-label={t('mainContent')}>
      <nav className="breadcrumb" aria-label={locale==='fr'?'Fil d’Ariane':'Breadcrumb'}><Link href={localized("/")}>SYNQORIA</Link> / <span aria-current="page">{shellTitle}</span></nav>
      <h1>{shellTitle}</h1>
      <p className="lead">{t('accessBlocked')}</p>
      <section className="task-panel" aria-labelledby="status-heading">
        <div>
          <p className="eyebrow">{t('serviceStatus')}</p>
          <h2 id="status-heading">{t('signInNeeded')}</h2>
          <p>{t('noClinicalData')}</p>
        </div>
        <button type="button" disabled aria-describedby="disabled-reason" aria-disabled="true">{t('tasksUnavailable')}</button>
        <p id="disabled-reason" className="hint">{t('accessReason')}</p>
      </section>
      <section className="secondary-panel" aria-label="Engineering status">
        <h2>{t('pendingVerification')}</h2><p>{t('engineeringOnly')}</p>
      </section>
    </main>
    <nav className="mobile-bottom" aria-label={t('navigation')}>
      <Link href={localized("/")}>{t('home')}</Link>
      <span aria-current="page">{shellTitle}</span>
      <span aria-disabled="true">{t('tasks')}</span>
      <button ref={moreButton} type="button" aria-expanded={moreOpen} aria-controls="mobile-more-panel" aria-haspopup="true" onClick={()=>setMoreOpen(value=>!value)}>{t('more')}</button>
    </nav>
    <section ref={morePanel} className="mobile-more-panel" id="mobile-more-panel" tabIndex={-1} hidden={!moreOpen} aria-label={locale==='fr'?'Plus d’options':'More options'}>
      <h2>{locale==='fr'?'Options accessibles':'Available options'}</h2>
      <p>{t('accessReason')}</p>
      <fieldset className="locale-switch" aria-label={t('language')}>
        <legend className="visually-hidden">{t('language')}</legend>
        <button type="button" aria-pressed={locale==='en'} onClick={()=>setLocale('en')}>EN</button>
        <button type="button" aria-pressed={locale==='fr'} onClick={()=>setLocale('fr')}>FR</button>
      </fieldset>
    </section>
  </div>;
}
