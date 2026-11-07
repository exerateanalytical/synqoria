"use client";
import Link from "next/link";
import {NAV,ContentFrame,styles,useContent} from "./ui";
import type {Key} from "../../lib/content/i18n";

const BLURB:Record<string,Key>={"M30-S01":"tm.intro","M30-S02":"fm.intro","M30-S03":"pk.intro","M30-S04":"ap.intro","M30-S05":"ac.intro"};
function Cards(){
 const {t}=useContent();
 return <div className={styles.hub}>{NAV.map(n=><section key={n.href} className={styles.panel} aria-labelledby={`h-${n.id}`}>
  <p className={styles.eyebrow}>{n.id}</p>
  <h2 id={`h-${n.id}`}>{t(n.key)}</h2>
  <p>{t(BLURB[n.id])}</p>
  <Link className={styles.btn} href={n.href}>{t("hub.open")}: {t(n.key)}</Link>
 </section>)}</div>;
}
export function Hub(){return <ContentFrame active="" title="hub.title" intro="hub.intro"><Cards/></ContentFrame>;}
