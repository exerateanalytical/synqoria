/** P02-B0075. Drop stale async UI results after scope/background changes. */
export class NativeResponseEpoch {
 private version=0;
 private activeScope:string|null=null;
 private pending=new Set<AbortController>();
 start(scope:string):{version:number;signal:AbortSignal} {
  if(!/^[a-zA-Z0-9:_-]{3,128}$/.test(scope))throw new Error('Invalid scope');
  if(this.activeScope!==scope)this.invalidate();
  this.activeScope=scope;
  const controller=new AbortController();this.pending.add(controller);
  return {version:this.version,signal:controller.signal};
 }
 accept(version:number,scope:string,serverAuthorized:boolean):boolean {
  return serverAuthorized===true&&this.activeScope===scope&&Number.isSafeInteger(version)&&version===this.version;
 }
 finish(signal:AbortSignal){for(const c of this.pending)if(c.signal===signal){this.pending.delete(c);break;}}
 invalidate(){for(const c of this.pending)c.abort();this.pending.clear();this.activeScope=null;this.version++;}
 get activeRequests(){return this.pending.size;}
}
