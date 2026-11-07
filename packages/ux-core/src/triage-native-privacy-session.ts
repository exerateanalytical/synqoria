import {TriageAuthorizedTasks,type TriagePermissionLease} from './triage-authorized-tasks';
import {TriageStaffWorkflow} from './triage-staff-workflow';
/** React Native host integration: no clinical data in background, after app lock, or before verified reauthentication. */
export class TriageNativePrivacySession {
 private foreground=false;private verified=false;private epoch=0;
 constructor(private readonly workflow:TriageStaffWorkflow,private readonly tasks:TriageAuthorizedTasks){}
 onBackground(){this.epoch++;this.foreground=false;this.verified=false;this.tasks.clear();}
 onForeground(){this.epoch++;this.foreground=true;this.verified=false;this.tasks.clear();}
 /** The host must separately authenticate and attest the lease; foreground alone never unlocks clinical UI. */
 unlock(lease:TriagePermissionLease){if(!this.foreground)throw new Error('foreground verification required');
  this.tasks.bind(lease);this.verified=true;this.epoch++;}
 snapshot(){return Object.freeze({visible:this.foreground&&this.verified&&this.workflow.snapshot().mode!=='locked',
  epoch:this.epoch,hasClinicalData:this.foreground&&this.verified&&this.workflow.snapshot().selectedCase!==null});}
 onShiftOrFacilityChange(){this.onBackground();}
 onLogout(){this.onBackground();}
 dispose(){this.onBackground();}
}
