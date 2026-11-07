import Link from "next/link";
import styles from "./integrations.module.css";
const screens=[
 ["M35-S01","Partner directory","Répertoire partenaires"],
 ["M35-S02","API catalogue","Catalogue API"],
 ["M35-S03","Developer applications","Applications développeur"],
 ["M35-S04","Message monitor","Suivi messages"],
 ["M35-S05","Mapping review","Révision correspondances"],
 ["M35-S06","Quarantine and replay","Quarantaine et reprise"]
];
export default function IntegrationHub(){
 return <main className={styles.root}>
  <header><p className={styles.eyebrow}>SYNQORIA · M35</p>
   <h1>Interoperability and API governance</h1>
   <p lang="fr">Interopérabilité et gouvernance des API</p>
   <p className={styles.warning}>No production credentials issued, source messages
    processed or network replays dispatched by this management module.</p></header>
  <nav className={styles.nav} aria-label="Integration workstations">{screens.map(([id,en,fr])=>
   <Link key={id} href={"/workforce/integrations/"+id}>{id} — {en} / {fr}</Link>)}</nav>
 </main>;
}
