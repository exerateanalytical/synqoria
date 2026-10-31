import {allowedWorkspaces,type NavigationContext} from './navigation';
/** P02-B0011: sidebar only root workspaces, child breadcrumbs are not sidebar entries. */
export type SidebarEntry=Readonly<{id:string;href:string;label:string}>;
export type SidebarHierarchy=Readonly<{entries:readonly SidebarEntry[];activeRoot:string|null;breadcrumbs:readonly {label:string;href:string|null}[]}>;
export function sidebarHierarchy(context:NavigationContext,pathname:string,locale:'en'|'fr'):SidebarHierarchy{
 const roots=allowedWorkspaces(context);
 const entries=roots.map(w=>({id:w.id,href:w.href,label:locale==='fr'?w.labelFr:w.labelEn}));
 // Require exact known root or a bounded, opaque feature action under it; never show child as sidebar.
 if(typeof pathname!=='string'||pathname.length>256||pathname.includes('//')||pathname.includes('?')||pathname.includes('#'))return {entries,activeRoot:null,breadcrumbs:[]};
 const active=roots.find(w=>w.href===pathname || /^\/(detail|edit|review)\/[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(pathname.slice(w.href.length)) && pathname.startsWith(w.href+'/'));
 if(!active)return {entries,activeRoot:null,breadcrumbs:[]};
 const label=locale==='fr'?active.labelFr:active.labelEn;
 const base={label,href:active.href};
 if(pathname===active.href)return {entries,activeRoot:active.id,breadcrumbs:[base]};
 // The browser must not leak a patient name or ID in the title or breadcrumb label.
 const action=pathname.slice(active.href.length+1).split('/')[0];
 const detailLabel=locale==='fr'?{detail:'Détails',edit:'Modifier',review:'Réviser'}:{detail:'Details',edit:'Edit',review:'Review'};
 return {entries,activeRoot:active.id,breadcrumbs:[base,{label:detailLabel[action as keyof typeof detailLabel]!,href:null}]};
}
