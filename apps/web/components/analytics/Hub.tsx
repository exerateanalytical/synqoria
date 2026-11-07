"use client";
import Link from "next/link";
import {AnFrame,NAV,styles,useAn} from "./ui";

function Body(){
 const {t}=useAn();
 return <ul className={styles.hub}>
  {NAV.map(n=><li key={n.href} className={styles.card}>
   <p className={styles.eyebrow}><span className={styles.mono}>{n.id}</span></p>
   <h2>{t(n.key)}</h2>
   <Link className={styles.btn} href={n.href}>{t("hub.open")}</Link>
  </li>)}
 </ul>;
}
export default function Hub(){
 return <AnFrame active="analytics" title="hub.title" intro="hub.intro"><Body/></AnFrame>;
}
