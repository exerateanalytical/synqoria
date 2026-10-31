/** P02-B0066. Backgrounding invalidates prior foreground proof even after clock rollback. */
export class NativeForegroundEpoch {
 private generation=1;private foreground=false;
 background(){this.generation++;this.foreground=false;return this.generation;}
 resume(){this.foreground=true;return this.generation;}
 accepts(generation:number,serverReauthorized:boolean){return this.foreground&&serverReauthorized===true&&generation===this.generation;}
 get current(){return this.generation;}
}
