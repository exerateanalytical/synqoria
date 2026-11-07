import Link from "next/link";
import styles from "./audit.module.css";
const links=[
 ["M34-S01","Audit search","Recherche dans l'audit"],
 ["M34-S02","Event detail","Détail de l'événement"],
 ["M34-S03","Provenance timeline","Chronologie de provenance"],
 ["M34-S04","Evidence export review","Examen de l'exportation des preuves"],
 ["M34-S05","Retention and legal holds","Conservation et mesures légales"]
];
export default function AuditHub(){
 return <main className={styles.root}>
  <a className={styles.skip} href="#workstations">Skip / Passer au contenu</a>
  <header><p className={styles.eyebrow}>SYNQORIA · M34</p>
   <h1>Audit, provenance and evidence governance</h1>
   <p lang="fr">Gouvernance de l'audit, de la provenance et des preuves</p>
   <p className={styles.notice}>Only metadata and independently attested event hashes.
    Clinical documents and lawful disclosure remain with their issuing authorities.</p>
  </header>
  <nav className={styles.nav} id="workstations" aria-label="Audit workstations">
   {links.map(([id,en,fr])=><Link href={"/workforce/audit/"+id} key={id}>
    {id} — {en} / {fr}</Link>)}
  </nav>
 </main>;
}
