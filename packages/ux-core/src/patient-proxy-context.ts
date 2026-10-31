/** P02-B0045. Patient/proxy presentation context; backend must independently verify each grant and record access. */
export type ProxyReceipt=Readonly<{tenant:string;subject:string;proxy:string;patient:string;grantRevision:number;expiresAt:number;serverVerified:boolean}>;
export type ProxyState=Readonly<{kind:'locked'}|{kind:'active';tenant:string;subject:string;proxy:string;patient:string;revision:number;expiresAt:number}>;
const opaque=(s:string)=>/^[A-Za-z0-9_-]{8,128}$/.test(s);
export class PatientProxyContext {
 private state:ProxyState={kind:'locked'};
 activate(receipt:ProxyReceipt,now:number):boolean{
  this.clear();
  if(!receipt||!receipt.serverVerified||!Number.isFinite(now)||!Number.isFinite(receipt.expiresAt)||receipt.expiresAt<=now||receipt.expiresAt>now+300000||!Number.isSafeInteger(receipt.grantRevision)||receipt.grantRevision<1)return false;
  if(![receipt.tenant,receipt.subject,receipt.proxy,receipt.patient].every(x=>typeof x==='string'&&opaque(x)))return false;
  if(receipt.proxy===receipt.patient)return false;
  this.state={kind:'active',tenant:receipt.tenant,subject:receipt.subject,proxy:receipt.proxy,patient:receipt.patient,revision:receipt.grantRevision,expiresAt:receipt.expiresAt};return true;
 }
 snapshot(now:number):ProxyState{
  if(this.state.kind==='active'&&(!Number.isFinite(now)||now>=this.state.expiresAt))this.clear();
  return this.state;
 }
 revoke():void{this.clear();}
 switchSubject():void{this.clear();}
 background():void{this.clear();}
 private clear():void{this.state={kind:'locked'};}
}
