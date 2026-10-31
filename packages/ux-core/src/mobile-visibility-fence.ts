/** P02-B0046. Background/foreground fence; never promotes protected data without fresh backend admission. */
export class MobileVisibilityFence {
 private epoch=0;private protectedVisible=false;
 hide():number{this.protectedVisible=false;return ++this.epoch;}
 showAfterVerifiedForeground(receipt:{verified:boolean;epoch:number;online:boolean;serverSessionCurrent:boolean}):boolean{
  this.protectedVisible=Boolean(receipt?.verified&&receipt.online&&receipt.serverSessionCurrent&&receipt.epoch===this.epoch);
  return this.protectedVisible;
 }
 get visible():boolean{return this.protectedVisible;}
 get generation():number{return this.epoch;}
}
