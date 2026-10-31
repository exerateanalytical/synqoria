/** P02-B0032: staff-only native intake navigation and submission admission; never grants server authority. */
import type {RegistrationDraft} from './registration-draft';
import {gateRegistration,type IntakeAccess} from './registration-intake-gate';
export type StaffIntakeDestination='work'|'patients'|'tasks'|'more';
export const staffIntakeTabs:readonly StaffIntakeDestination[]=['work','patients','tasks','more'];
export function staffIntakeModel(locale:'en'|'fr',draft:RegistrationDraft,context:IntakeAccess,today:string){
 const result=gateRegistration(draft,context,today);
 // Even if a local context is accidentally marked ready, native submission stays locked
 // until the real server endpoint and clinical release are verified.
 return {screen:'M01-S02' as const,labels:locale==='fr'?['Travail','Patients','Tâches','Plus']:['Work','Patients','Tasks','More'],
  tabs:staffIntakeTabs,submitEnabled:false,readOnly:true,gate:result,requiresServerAuthority:true};
}
