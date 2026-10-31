/** P02-B0042. Native lifecycle gate: background/offline/scope changes invalidate intake. */
import {VolatileRegistrationSession,type IntakeScope} from './registration-draft-session';
import {RegistrationContextFence} from './registration-context-fence';
export class RegistrationMobileLifecycle {
 private readonly fence:RegistrationContextFence;
 private visible=false;
 constructor(private readonly draft:VolatileRegistrationSession){this.fence=new RegistrationContextFence(draft);}
 resume(scope:IntakeScope|null,online:boolean,locale:'en'|'fr'):{editable:false;viewOnly:true}{
  if(!online||!scope){this.suspend();return {editable:false,viewOnly:true};}
  this.visible=this.fence.enter(scope,locale);
  return {editable:false,viewOnly:true}; // no production reviewer endpoint / clinical release
 }
 suspend():void{this.visible=false;this.fence.end();}
 disconnect():void{this.suspend();}
 get active():boolean{return this.visible;}
}
