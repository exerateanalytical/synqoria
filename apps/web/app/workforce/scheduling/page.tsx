import Link from "next/link";
import styles from "./scheduling.module.css";
const screens=[
 ["M02-S01","Appointment calendar","Calendrier"],
 ["M02-S02","Appointment booking","Réservation"],
 ["M02-S03","Appointment detail","Détail"],
 ["M02-S04","Waitlist and rescheduling","Liste d'attente et reprogrammation"],
 ["M02-S05","Resource availability","Disponibilité des ressources"]
];
export default function SchedulingHub(){
 return <main className={styles.root}>
  <header><p className={styles.eyebrow}>SYNQORIA · M02</p>
   <h1>Appointments and resource scheduling</h1>
   <p lang="fr">Rendez-vous et planification des ressources</p>
   <p className={styles.warning}>Independent verified patient context and authorized
    hospital roles are required before booking.</p></header>
  <nav className={styles.nav} aria-label="Scheduling workstations">{screens.map(([id,en,fr])=>
   <Link key={id} href={"/workforce/scheduling/"+id}>{id} — {en} / {fr}</Link>)}</nav>
 </main>;
}
