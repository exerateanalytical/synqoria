"use client";
import Link from "next/link";
import {NAV,NutritionFrame,styles,useNutrition} from "./ui";
import type {Key} from "../../lib/nutrition/i18n";

const BLURB:Record<string,Key>={"M26-S01":"wl.intro","M26-S02":"or.intro","M26-S03":"pr.intro","M26-S04":"dl.intro"};
function Cards(){
 const {t}=useNutrition();
 return <div className={styles.hub}>{NAV.map(n=><section key={n.href} className={styles.panel} aria-labelledby={`h-${n.id}`}>
  <p className={styles.eyebrow}>{n.id}</p>
  <h2 id={`h-${n.id}`}>{t(n.key)}</h2>
  <p>{t(BLURB[n.id])}</p>
  <Link className={styles.btn} href={n.href}>{t("hub.open")}: {t(n.key)}</Link>
 </section>)}</div>;
}
export function Hub(){return <NutritionFrame active="" title="hub.title" intro="hub.intro"><Cards/></NutritionFrame>;}
