/** P02-B0068. Native privacy curtain state, not an authentication mechanism. */
export class NativePrivacyCurtain {
 private hidden=true;private generation=0;private foreground=false;
 background(){this.generation++;this.hidden=true;this.foreground=false;return this.generation;}
 resume(){this.foreground=true;this.hidden=true;return this.generation;}
 /** No PHI is released until a future server adapter reauthorizes the current generation. */
 confirm(generation:number,verifiedServerLease:boolean){
  if(!this.foreground||!verifiedServerLease||generation!==this.generation)return false;
  this.hidden=false;return true;
 }
 get isCovered(){return this.hidden;}
 get epoch(){return this.generation;}
}
