import type {Lease} from './server-session-lease';
/** P02-B0019: online and server-verified lease are required for navigation to clinical workspaces.
 * Offline cached lease never authorizes new patient context. This is UI gating only.
 */
export type MobileTask='home'|'work'|'patients'|'appointments'|'records'|'tasks'|'more';
export type MobileDecision='allowed-shell'|'locked-sign-in'|'offline-locked'|'not-authorized';
const tasks:Record<MobileTask,string|null>={home:null,more:null,work:'queue:view',patients:'patient:read',appointments:'appointments:view',records:'patient:read',tasks:'task:view'};
export function mobileTaskDecision(task:MobileTask,lease:Lease,online:boolean,now:number):MobileDecision{
 if(task==='home'||task==='more')return 'allowed-shell';
 if(!online)return 'offline-locked';
 if(lease.kind!=='verified'||!Number.isFinite(now)||now>=lease.expiresAt)return 'locked-sign-in';
 return lease.capabilities.includes(tasks[task]!)?'allowed-shell':'not-authorized';
}
