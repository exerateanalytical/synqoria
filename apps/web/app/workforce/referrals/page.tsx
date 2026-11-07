import Link from "next/link";
import styles from "./referrals.module.css";
const screens=[["M16-S01","Referral worklist","Liste des références"],
 ["M16-S02","Referral preparation","Préparation d'une référence"],
 ["M16-S03","Receiving decision","Décision du receveur"],
 ["M16-S04","Transfer handover","Transfert du patient"],
 ["M16-S05","Follow-up and closure","Suivi et clôture"]];
export default function ReferralsHub(){
 return <main className={styles.root}><header><p className={styles.eyebrow}>SYNQORIA · M16</p>
  <h1>Referrals and transitions of care</h1>
  <p lang="fr">Références et transitions de soins</p>
  <p className={styles.warning}>External consent, receiving decision and handover proofs are
    required. No clinical documents are released by M16.</p></header>
  <nav className={styles.nav} aria-label="Referral workstations">{screens.map(([id,en,fr])=>
   <Link key={id} href={"/workforce/referrals/"+id}>{id} — {en} / {fr}</Link>)}</nav>
 </main>;
}
