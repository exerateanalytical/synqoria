import {allowedWorkspaces,workspaces,type NavigationContext} from './navigation';
/** P02-B0005. Presentation route classification, NOT HTTP authorization. */
export type RouteClassification =
 | {kind:'root'; workspaceId:string}
 | {kind:'feature-child-unimplemented'; workspaceId:string; action:'detail'|'edit'|'review'; opaqueId:string}
 | {kind:'denied-or-unavailable'};
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
/** No query strings, percent-encoding, path traversal, unicode or fragments in clinical navigation. */
export function classifySafeRoute(path:string,context:NavigationContext):RouteClassification {
 if(typeof path!=='string'||path.length>256||!/^\/[a-z0-9\-/]*$/.test(path)||path.includes('//')||path.includes('/../')||path.endsWith('/..'))
  return {kind:'denied-or-unavailable'};
 const root=workspaces.find(w=>w.href===path&&w.shell===context.shell);
 const eligible=allowedWorkspaces(context);
 if(root)return eligible.some(w=>w.id===root.id)?{kind:'root',workspaceId:root.id}:{kind:'denied-or-unavailable'};
 const match=path.match(/^(.*)\/(detail|edit|review)\/([0-9a-f-]{36})$/);
 if(!match)return {kind:'denied-or-unavailable'};
 const [,prefix,action,id]=match;
 if(!prefix||!id||!uuid.test(id))return {kind:'denied-or-unavailable'};
 const parent=eligible.find(w=>w.href===prefix);
 if(!parent)return {kind:'denied-or-unavailable'};
 // Explicitly NOT an implemented route; never put child in sidebar or fetch patient data.
 return {kind:'feature-child-unimplemented',workspaceId:parent.id,action:action as 'detail'|'edit'|'review',opaqueId:id};
}
export function sidebarRoots(context:NavigationContext):readonly {id:string;href:string}[] {
 return allowedWorkspaces(context).map(w=>({id:w.id,href:w.href}));
}
