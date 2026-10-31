/** P02-B0040. Client-side intake navigation defense-in-depth; server must independently enforce access. */
export type IntakeRouteResult={allowed:boolean;screen:'M01-S02'|null;reason:'OK'|'DENIED'};
export function guardedIntakeRoute(raw:string,shell:'workforce'|'portal'|'platform',authenticated:boolean,grants:ReadonlySet<string>):IntakeRouteResult{
 const deny: IntakeRouteResult={allowed:false,screen:null,reason:'DENIED'};
 if(typeof raw!=='string'||raw.length>128||raw.includes('?')||raw.includes('#')||raw.includes('\\')||raw.includes('%')||raw.includes('//'))return deny;
 if(raw!=='/workforce/patients/new'||shell!=='workforce'||!authenticated||!grants.has('patient:register'))return deny;
 return {allowed:true,screen:'M01-S02',reason:'OK'};
}
