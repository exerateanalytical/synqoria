/** P02-B0060. Native lifecycle privacy: app resumption never restores an old clinical context automatically. */
export class MobilePrivacyLatch {
 private epoch=0;private locked=true;private pendingForeground=false;
 hide(){this.epoch++;this.locked=true;this.pendingForeground=true;return this.epoch;}
 foreground(){this.pendingForeground=false;return this.epoch;}
 /** Only a future verified server adapter may provide a fresh grant; this method is NOT an authentication service. */
 confirm(epoch:number,serverVerified:boolean,scopeVerified:boolean){
  if(this.pendingForeground||epoch!==this.epoch||!serverVerified||!scopeVerified)return false;
  this.locked=false;return true;
 }
 revoke(){this.hide();}
 get mayDisplayPrivateData(){return !this.locked&&!this.pendingForeground;}
 get generation(){return this.epoch;}
}
