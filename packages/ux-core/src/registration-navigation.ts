/** P02-B0034: stable registration route is subordinate to patients, not an extra sidebar module. */
export type IntakeNavigation={url:string;parent:string;breadcrumbs:readonly string[];activeSidebar:'patients';canonicalScreen:'M01-S02';canEnter:boolean};
export function intakeNavigation(shell:'workforce'|'portal'|'platform',grants:ReadonlySet<string>,authenticated:boolean):IntakeNavigation{
 return {url:'/workforce/patients/new',parent:'/workforce',breadcrumbs:['SYNQORIA','Patients','New registration'],activeSidebar:'patients',canonicalScreen:'M01-S02',
 canEnter:shell==='workforce'&&authenticated&&grants.has('patient:register')};
}
