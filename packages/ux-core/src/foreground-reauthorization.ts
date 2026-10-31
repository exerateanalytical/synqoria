/** P02-B0049: foreground entry is locked until a NEW server verification matches a new generation. */
export class ForegroundReauthorization {
 private epoch=0;private active=false;private lastVerified=-1;
 get generation(){return this.epoch;}
 get canDisplayProtectedData(){return this.active&&this.lastVerified===this.epoch;}
 background(){this.epoch++;this.active=false;this.lastVerified=-1;}
 revoke(){this.background();}
 resume(verification:{generation:number;online:boolean;serverVerified:boolean;grantFresh:boolean}):boolean{
  if(!verification||verification.generation!==this.epoch||!verification.online||!verification.serverVerified||!verification.grantFresh){this.active=false;return false;}
  this.active=true;this.lastVerified=this.epoch;return true;
 }
}
