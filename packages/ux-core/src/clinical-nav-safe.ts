import {workspaces} from './navigation';
/** P02-B0020: tasks and detail routes are not sidebar destinations.
 * Patient UUIDs and clinical facts must never appear in URLs or browser history.
 */
export type MenuKind='root'|'detail'|'edit'|'dialog'|'review'|'task';
export type MenuDestination=Readonly<{id:string;href:string;kind:MenuKind;enabled:boolean;entitled:boolean}>;
export function clinicalSidebar(items:readonly MenuDestination[]):readonly MenuDestination[]{
 const dedupe=new Set<string>();const allowed:MenuDestination[]=[];
 for(const item of items){
  if(item.kind!=='root'||!item.enabled||!item.entitled)continue;
  if(!workspaces.some(w=>w.id===item.id&&w.href===item.href)||dedupe.has(item.href))continue;
  dedupe.add(item.href);allowed.push(item);
 }
 return Object.freeze(allowed);
}
export function safeClinicalDeepLink(path:unknown):false{
 // No canonical clinical detail screens are implemented at this checkpoint.
 // A future, audited route registry must replace this fail-closed operation.
 void path;return false;
}
