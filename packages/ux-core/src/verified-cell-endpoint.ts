/** P02-B0023: endpoint must come from verified provisioning, never from QR/header/query context. */
export function verifiedCellEndpoint(value:string,verifiedProvisioning:boolean):string|null {
 if(verifiedProvisioning!==true||typeof value!=='string'||value.length>255)return null;
 try{
  const u=new URL(value);
  if(u.protocol!=='https:'||u.username||u.password||u.port||u.search||u.hash||u.pathname!=='/'||!u.hostname
    ||u.hostname==='localhost'||u.hostname.endsWith('.localhost')||/^\d+\.\d+\.\d+\.\d+$/.test(u.hostname)
    ||u.hostname.includes('..'))return null;
  return u.origin;
 }catch{return null;}
}
