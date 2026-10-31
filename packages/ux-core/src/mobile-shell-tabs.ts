import {mobileTaskDecision,type MobileTask} from './mobile-session-gate';
import type {Lease} from './server-session-lease';
export type MobileKind='staff'|'patient';
export type MobileTabPresentation=Readonly<{id:MobileTask;label:string;enabled:boolean;reason:string}>;
const staff=[['work','Work','Travail'],['patients','Patients','Patients'],['tasks','Tasks','Tâches'],['more','More','Plus']] as const;
const patient=[['home','Home','Accueil'],['appointments','Appointments','Rendez-vous'],['records','Records','Dossiers'],['more','More','Plus']] as const;
/** Four accessible bottom destinations; eligibility is supplementary to backend authorization. */
export function mobileShellTabs(kind:MobileKind,locale:'en'|'fr',lease:Lease,online:boolean,now:number):readonly MobileTabPresentation[]{
 const source=kind==='staff'?staff:patient;
 return source.map(([id,en,fr])=>{
  const permission=mobileTaskDecision(id,lease,online,now);
  // Home/More remain public shell labels; no protected data is rendered without authorization.
  const enabled=permission==='allowed-shell'&& (id==='home'||id==='more');
  const reason=permission==='offline-locked'?(locale==='fr'?'Connexion requise':'Connection required'):
   permission==='not-authorized'?(locale==='fr'?'Accès refusé':'Access denied'):
   permission==='allowed-shell'?(locale==='fr'?'Espace public uniquement':'Public shell only'):
    (locale==='fr'?'Connexion vérifiée nécessaire':'Verified sign-in required');
  return {id,label:locale==='fr'?fr:en,enabled,reason};
 });
}
