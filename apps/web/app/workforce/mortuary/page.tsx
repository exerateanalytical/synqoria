import Link from "next/link";
import mortuary from "../../../lib/mortuary-screen-catalog.json";
import styles from "../operations/operations.module.css";

export default function MortuaryDirectory(){
 return <main className={styles.root} id="main">
  <a href="#mortuary-screens" className={styles.skip}>Skip to screens / Aller aux écrans</a>
  <header className={styles.header}>
   <p className={styles.eyebrow}>SYNQORIA · M27</p>
   <h1>{mortuary.name}</h1>
   <p lang="fr">{mortuary.nameFr}</p>
   <p>Five hospital-cell scoped workstations for verified intake, identity,
    storage, custody, independent release review and final handover.</p>
   <p className={styles.warning} role="status">Actual identity, legal authority and release evidence must
    come from independently authorized hospital registries. No self-certified or undocumented release.</p>
  </header>
  <section id="mortuary-screens" className={styles.modules} aria-label="Five mortuary screens">
   {mortuary.screens.map(s=><article key={s.id} className={styles.module}>
    <div className={styles.moduleHead}><span className={styles.moduleId}>{s.id}</span></div>
    <h2>{s.name}</h2><p lang="fr">{s.nameFr}</p>
    <nav className={styles.screenList}>
     <Link href={`/workforce/operations/M27/${s.id}`}>{s.primaryAction}</Link>
    </nav>
   </article>)}
  </section>
  <p className={styles.footer}>M27 source implementation is not an authorization for live mortuary
   operations. All clinical and legal release checks remain institution-controlled.</p>
 </main>;
}
