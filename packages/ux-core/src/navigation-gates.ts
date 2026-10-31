import { allowedWorkspaces, workspaces, type NavigationContext, type Shell, type Workspace } from './navigation';
/** Classify a path for navigation presentation only; never use this as a backend auth check. */
export type WorkspaceRouteResult =
  | {kind:'allowed'; workspace:Workspace}
  | {kind:'requires-auth-or-license'}
  | {kind:'not-workspace-root'};
/** Exact roots only: no permissive startsWith(), no detail-route leakage. */
export function resolveWorkspaceRoot(pathname:string,context:NavigationContext):WorkspaceRouteResult {
  if(typeof pathname!=='string' || !pathname.startsWith('/') || pathname.includes('?') || pathname.includes('#')) {
    return {kind:'not-workspace-root'};
  }
  const candidate=workspaces.find(w=>w.href===pathname && w.shell===context.shell);
  if(!candidate) return {kind:'not-workspace-root'};
  return allowedWorkspaces(context).some(w=>w.id===candidate.id)
    ? {kind:'allowed',workspace:candidate}
    : {kind:'requires-auth-or-license'};
}
export function allowedMobileTabs(shell:'staff'|'patient', locale:'en'|'fr') {
  // Only labels/IDs are returned. Their associated data routes remain inaccessible
  // until a separately authenticated, backend-authorized workflow is implemented.
  const tabs=shell==='staff' ? [
    {id:'work',en:'Work',fr:'Travail'}, {id:'patients',en:'Patients',fr:'Patients'},
    {id:'tasks',en:'Tasks',fr:'Tâches'}, {id:'more',en:'More',fr:'Plus'},
  ] : [
    {id:'home',en:'Home',fr:'Accueil'}, {id:'appointments',en:'Appointments',fr:'Rendez-vous'},
    {id:'records',en:'Records',fr:'Dossiers'}, {id:'more',en:'More',fr:'Plus'},
  ];
  return tabs.map(t=>({id:t.id,label:locale==='fr'?t.fr:t.en}));
}
/** Preventing accidental cross-shell links does not replace backend route guards. */
export function shellForPath(path:string):Shell|null {
  if(path==='/workforce'||path.startsWith('/workforce/'))return 'workforce';
  if(path==='/portal'||path.startsWith('/portal/'))return 'portal';
  if(path==='/platform'||path.startsWith('/platform/'))return 'platform';
  return null;
}
