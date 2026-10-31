/** P02-B0054. Context switches require explicit sensitive-state disposal before a new view. */
export class FacilitySwitchBarrier {
 private generation=0;private ready=false;private cleared=false;
 get currentGeneration(){return this.generation;}
 get mayDisplayClinicalData(){return this.ready;}
 beginSwitch(clear:()=>void):number{
  this.ready=false;this.cleared=false;this.generation++;
  try{clear();this.cleared=true;}catch{this.ready=false;this.cleared=false;return this.generation;}
  return this.generation;
 }
 confirmNewScope(generation:number,serverVerified:boolean,online:boolean):boolean{
  this.ready=this.cleared&&Number.isSafeInteger(generation)&&generation===this.generation&&serverVerified===true&&online===true;
  return this.ready;
 }
 revoke():void{this.generation++;this.ready=false;this.cleared=false;}
}
