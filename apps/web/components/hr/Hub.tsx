"use client";
import Link from "next/link";
import {NAV,HrFrame,styles,useHr} from "./ui";
import type {Key} from "../../lib/hr/i18n";

const BLURB:Record<string,Key>={"M24-S01":"dir.intro","M24-S02":"emp.intro","M24-S03":"ro.intro","M24-S04":"at.intro","M24-S05":"pp.intro","M24-S06":"pa.intro"};
function Cards(){
 const {t}=useHr();
 return <div className={styles.hub}>{NAV.map(n=><section key={n.href} className={styles.panel} aria-labelledby={`h-${n.id}`}>
  <p className={styles.eyebrow}>{n.id}</p>
  <h2 id={`h-${n.id}`}>{t(n.key)}</h2>
  <p>{t(BLURB[n.id])}</p>
  <Link className={styles.btn} href={n.href}>{t("hub.open")}: {t(n.key)}</Link>
 </section>)}</div>;
}
export function Hub(){return <HrFrame active="" title="hub.title" intro="hub.intro"><Cards/></HrFrame>;}
