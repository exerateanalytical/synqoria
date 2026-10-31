/** Navigation metadata is not authorization. Backend must enforce every resource access. */
export type Shell = 'workforce' | 'portal' | 'platform';
export type NavigationContext = {
  shell: Shell;
  grantedCapabilities: ReadonlySet<string>;
  licensedModules: ReadonlySet<string>;
};
export type Workspace = Readonly<{
  id: string;
  labelEn: string;
  labelFr: string;
  href: string;
  shell: Shell;
  capability: string;
  modules: readonly string[];
}>;

/** Only stable workspace roots belong in the sidebar, never create/edit/details/dialog routes. */
export const workspaces: readonly Workspace[] = [
  {id:'clinical-work', labelEn:'Clinical work', labelFr:'Soins cliniques', href:'/workforce/clinical', shell:'workforce', capability:'workspace:clinical', modules:['M03','M04','M05','M06','M07','M08','M09','M10']},
  {id:'patients', labelEn:'Patients', labelFr:'Patients', href:'/workforce/patients', shell:'workforce', capability:'workspace:patients', modules:['M01']},
  {id:'scheduling', labelEn:'Appointments', labelFr:'Rendez-vous', href:'/workforce/appointments', shell:'workforce', capability:'workspace:scheduling', modules:['M02']},
  {id:'diagnostics', labelEn:'Diagnostics', labelFr:'Diagnostics', href:'/workforce/diagnostics', shell:'workforce', capability:'workspace:diagnostics', modules:['M11','M12','M15','M36']},
  {id:'pharmacy', labelEn:'Pharmacy', labelFr:'Pharmacie', href:'/workforce/pharmacy', shell:'workforce', capability:'workspace:pharmacy', modules:['M13']},
  {id:'specialties', labelEn:'Specialty care', labelFr:'Spécialités', href:'/workforce/specialties', shell:'workforce', capability:'workspace:specialties', modules:['M14','M16','M17']},
  {id:'revenue', labelEn:'Revenue', labelFr:'Facturation', href:'/workforce/revenue', shell:'workforce', capability:'workspace:revenue', modules:['M19','M20']},
  {id:'operations', labelEn:'Hospital operations', labelFr:'Exploitation hospitalière', href:'/workforce/operations', shell:'workforce', capability:'workspace:operations', modules:['M21','M22','M23','M24','M25','M26','M27','M28','M29']},
  {id:'my-record', labelEn:'My record', labelFr:'Mon dossier', href:'/portal/records', shell:'portal', capability:'portal:records', modules:['M18']},
  {id:'my-appointments', labelEn:'Appointments', labelFr:'Rendez-vous', href:'/portal/appointments', shell:'portal', capability:'portal:appointments', modules:['M18']},
  {id:'cells', labelEn:'Hospital cells', labelFr:'Cellules hospitalières', href:'/platform/cells', shell:'platform', capability:'platform:cells', modules:['M39']},
  {id:'security', labelEn:'Identity & audit', labelFr:'Identité et audit', href:'/platform/security', shell:'platform', capability:'platform:security', modules:['M33','M34']},
  {id:'configuration', labelEn:'Configuration', labelFr:'Configuration', href:'/platform/configuration', shell:'platform', capability:'platform:configuration', modules:['M30','M31','M32']},
  {id:'integrations', labelEn:'Integrations', labelFr:'Interopérabilité', href:'/platform/integrations', shell:'platform', capability:'platform:integrations', modules:['M35','M37','M38']},
  {id:'reliability', labelEn:'Reliability', labelFr:'Fiabilité', href:'/platform/reliability', shell:'platform', capability:'platform:reliability', modules:['M40']},
];
export function allowedWorkspaces(context: NavigationContext): Workspace[] {
  return workspaces.filter(item =>
    item.shell === context.shell &&
    context.grantedCapabilities.has(item.capability) &&
    item.modules.some(id => context.licensedModules.has(id))
  );
}
export const staffMobileTabs = Object.freeze([
  {id:'work', labelEn:'Work', labelFr:'Travail'},
  {id:'patients', labelEn:'Patients', labelFr:'Patients'},
  {id:'tasks', labelEn:'Tasks', labelFr:'Tâches'},
  {id:'more', labelEn:'More', labelFr:'Plus'},
]);
export const patientMobileTabs = Object.freeze([
  {id:'home', labelEn:'Home', labelFr:'Accueil'},
  {id:'appointments', labelEn:'Appointments', labelFr:'Rendez-vous'},
  {id:'records', labelEn:'Records', labelFr:'Dossiers'},
  {id:'more', labelEn:'More', labelFr:'Plus'},
]);
