import Link from "next/link";
import styles from "./privacy.module.css";

export default function PrivacyHub(){
 const screens=[
 ["M32-S01","Consent overview","Vue d'ensemble des consentements"],
 ["M32-S02","Capture directive","Saisir une directive"],
 ["M32-S03","Verify and revoke","Vérifier et révoquer"],
 ["M32-S04","Disclosure review","Examiner la divulgation"],
 ["M32-S05","Privacy cases","Dossiers de confidentialité"]
 ];
 return <main className={styles.root}><a href="#workstations" className={styles.skip}>Skip / Passer</a>
  <header><p className={styles.eyebrow}>SYNQORIA · M32</p>
   <h1>Consent, privacy and disclosure governance</h1>
   <p lang="fr">Gouvernance du consentement, de la confidentialité et des divulgations</p>
   <p className={styles.safety}>Independent consent evidence is required. No clinical record
    is exported by M32. Institutional legal acceptance remains outstanding.</p>
  </header>
  <nav className={styles.nav} id="workstations" aria-label="Privacy workstations">
   {screens.map(([id,en,fr])=><Link href={"/workforce/privacy/"+id} key={id}>
    <strong>{id}</strong> — {en} / {fr}</Link>)}
  </nav>
 </main>;
}
