/** P02-B0044. Safe return navigation: only canonical, nonidentifying destinations, never patient ids or redirects. */
export type ReturnShell='workforce'|'portal'|'platform';
const paths:Readonly<Record<ReturnShell,readonly string[]>>={
 workforce:['/workforce','/workforce/patients'],portal:['/portal'],platform:['/platform']
};
export function privateReturnRoute(raw:unknown,shell:ReturnShell):string|null{
 if(typeof raw!=='string'||raw.length>100||!Object.hasOwn(paths,shell))return null;
 if(raw.includes('%')||raw.includes('?')||raw.includes('#')||raw.includes('\\')||raw.includes('//')||raw.includes('..'))return null;
 return paths[shell].includes(raw)?raw:null;
}
