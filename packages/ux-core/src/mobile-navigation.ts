/** Mobile navigation presentation guard; no patient payload is represented in a tab. */
export type MobileShell='staff'|'patient';
export type TabGate={id:string; enabled:boolean; reason:'ready'|'sign-in-required'|'no-permission'};
const labels={staff:['work','patients','tasks','more'],patient:['home','appointments','records','more']} as const;
const required:Readonly<Record<MobileShell,Readonly<Record<string,string>>>>={
 staff:{work:'staff:work',patients:'patient:list',tasks:'staff:tasks',more:'ui:more'},
 patient:{home:'portal:home',appointments:'portal:appointments',records:'portal:records',more:'ui:more'},
};
export function mobileTabGates(shell:MobileShell, authenticated:boolean, capabilities:ReadonlySet<string>):readonly TabGate[]{
 return labels[shell].map(id=>({id,enabled:authenticated && capabilities.has(required[shell][id]!),
   reason:!authenticated?'sign-in-required':capabilities.has(required[shell][id]!)?'ready':'no-permission'}));
}
export function requestMobileTab(shell:MobileShell, target:string,authenticated:boolean,
  capabilities:ReadonlySet<string>):{kind:'navigate';target:string}|{kind:'denied'} {
 const tab=mobileTabGates(shell,authenticated,capabilities).find(x=>x.id===target);
 return tab?.enabled===true?{kind:'navigate',target}:{kind:'denied'};
}
