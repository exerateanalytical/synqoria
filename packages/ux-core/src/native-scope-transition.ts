/** P02-B0074. Clears volatile presentation state when identity, facility, role or shift changes.
 * Callers must clear their own persisted stores too; this object cannot enforce OS storage. */
export class NativeScopeTransition {
 private epochValue=0;
 private context:string|null=null;
 private drafts=new Map<string,string>();
 get epoch(){return this.epochValue;}
 private key(actor:string,facility:string,role:string,shift:string):string {
  if([actor,facility,role,shift].some(x=>!x||x.length>128||/[\s<>]/.test(x)))throw new Error('Invalid context');
  return [actor,facility,role,shift].join(':');
 }
 switchTo(actor:string,facility:string,role:string,shift:string):number {
  const next=this.key(actor,facility,role,shift);
  if(this.context!==next){this.drafts.clear();this.context=next;this.epochValue++;}
  return this.epochValue;
 }
 suspend(){this.drafts.clear();this.context=null;this.epochValue++;}
 stage(key:string,value:string){if(!this.context||!/^draft-[a-z0-9-]{1,32}$/.test(key)||value.length>4096)throw new Error('No safe draft scope');this.drafts.set(key,value);}
 read(key:string,epoch:number):string|null{return this.context&&epoch===this.epochValue?this.drafts.get(key)??null:null;}
 get draftCount(){return this.drafts.size;}
}
