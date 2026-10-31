/** A canonical screen must never be made routable by inference from its screen ID.
 * The registered path is inert unless its implementation and independent tests are verified.
 * Presentation checks here do NOT replace backend authorization. */
export type RegisteredScreenRoute = Readonly<{
  screenId:string; module:string; route:string|null;
  shell:'workforce'|'portal'|'platform';
  implementationStatus:'not_implemented'|'implemented'|'tested';
  capability:string;
}>;
export type RouteDecision = 'available-in-ui' | 'not-delivered' | 'not-authorized';
export function routeDecision(path:unknown, entries:readonly RegisteredScreenRoute[],
  context:{shell:'workforce'|'portal'|'platform'; capabilities:ReadonlySet<string>; licensedModules:ReadonlySet<string>}):RouteDecision {
  if(typeof path!=='string' || !/^\/[a-zA-Z0-9/_-]+$/.test(path) || path.includes('//'))return 'not-delivered';
  const matches=entries.filter(r=>r.route===path);
  if(matches.length!==1)return 'not-delivered';
  const r=matches[0]!;
  if(r.implementationStatus!=='tested'||r.shell!==context.shell || !path.startsWith('/'+r.shell+'/'))return 'not-delivered';
  if(!/^([A-Z0-9-]{4,40})$/.test(r.screenId)|| !/^M[0-9]{2}$/.test(r.module)||!r.capability)return 'not-delivered';
  return context.capabilities.has(r.capability)&&context.licensedModules.has(r.module) ? 'available-in-ui' : 'not-authorized';
}
export function validateRouteRegistry(entries:readonly RegisteredScreenRoute[]):boolean {
  const ids=new Set<string>(),paths=new Set<string>();
  for(const r of entries){
    if(!r.screenId||ids.has(r.screenId))return false;
    ids.add(r.screenId);
    if(r.route!==null){
      if(!/^\/[a-zA-Z0-9/_-]+$/.test(r.route)||r.route.includes('//')||paths.has(r.route))return false;
      paths.add(r.route);
    }
    if(r.implementationStatus==='not_implemented' && r.route!==null)return false;
  }
  return true;
}
