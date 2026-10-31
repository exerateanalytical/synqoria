/** P02-B0052: accessible four-destination mobile navigation for signed-out state; labels never imply access. */
export type LockedApp='staff'|'patient';
const labels={
 staff:{en:['Work','Patients','Tasks','More'],fr:['Travail','Patients','Tâches','Plus']},
 patient:{en:['Home','Appointments','Records','More'],fr:['Accueil','Rendez-vous','Dossiers','Plus']}
} as const;
export function lockedNavigationAccessibility(app:LockedApp,locale:'en'|'fr'){
 return labels[app][locale].map((label,index)=>({id:index,accessibilityLabel:label,accessibilityHint:locale==='fr'?'Connexion et autorisation requises':'Verified sign-in and authorization required',selected:false,disabled:true,minTouchTarget:48}));
}
