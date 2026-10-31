/** P02-B0048: deny private IDs, query and redirects in staff navigation intents. This is NOT backend authorization. */
export type StaffIntent = 'work'|'patients'|'tasks'|'more';
const roots:Readonly<Record<StaffIntent,string>>=Object.freeze({work:'/workforce',patients:'/workforce/patients',tasks:'/workforce/tasks',more:'/workforce/more'});
export function safeStaffRouteIntent(intent:unknown,locked:boolean):{kind:'locked'|'navigate';href?:string}{
 if(locked||typeof intent!=='string'||!Object.prototype.hasOwnProperty.call(roots,intent))return {kind:'locked'};
 return {kind:'navigate',href:roots[intent as StaffIntent]};
}
export function safeStaffReturnPath(raw:unknown):string|null{
 if(typeof raw!=='string'||raw.length>80)return null;
 return (Object.values(roots) as string[]).includes(raw)?raw:null;
}
