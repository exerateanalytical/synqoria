/** P02-B0056. Only static root paths; details and identifiers are never sidebar destinations. */
const roots={workforce:['/workforce','/workforce/patients'],portal:['/portal'],platform:['/platform']} as const;
export function safeClinicalRouteRoot(shell:'workforce'|'portal'|'platform',raw:unknown,authorized:boolean):string|null{
 if(!Object.hasOwn(roots,shell)||!authorized||typeof raw!=='string'||raw.length>96||raw.includes('%')||raw.includes('?')||raw.includes('#')||raw.includes('\\')||raw.includes('//'))return null;
 return (roots[shell] as readonly string[]).includes(raw)?raw:null;
}
