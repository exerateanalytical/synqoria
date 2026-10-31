/** P02-B0064. Ephemeral return ticket, never a URL or persisted PHI. */
export type NativeReturnTicket={shell:'staff'|'patient';scopeEpoch:number;issuedAt:number;expiresAt:number};
export function nativeReturnAllowed(ticket:NativeReturnTicket|null,shell:'staff'|'patient',scopeEpoch:number,now:number):boolean{
 return !!ticket&&ticket.shell===shell&&Number.isSafeInteger(scopeEpoch)&&scopeEpoch>0&&ticket.scopeEpoch===scopeEpoch
 &&Number.isFinite(now)&&Number.isFinite(ticket.issuedAt)&&Number.isFinite(ticket.expiresAt)
 &&now>=ticket.issuedAt&&now<ticket.expiresAt&&ticket.expiresAt-ticket.issuedAt<=60_000;
}
