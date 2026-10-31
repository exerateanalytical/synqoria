/** Per-facility UI lease can be renewed only through a server session endpoint. No offline self-renewal. */
export type Lease={kind:'locked'}|{kind:'verified';expiresAt:number;tenantId:string;facilityId:string;capabilities:readonly string[]};
export type ServerSessionResult={confirmed:boolean;expiresAt:number;tenantId:string;facilityId:string;capabilities:readonly string[]};
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[89ab0-9a-f]{12}$/i;
export class SessionLeaseController {
 private lease:Lease={kind:'locked'};
 private generation=0;
 snapshot(now:number):Lease {
  if(this.lease.kind==='verified'&&now>=this.lease.expiresAt)this.invalidate();
  return this.lease;
 }
 invalidate(){this.generation++;this.lease={kind:'locked'};}
 async renew(revalidate:()=>Promise<ServerSessionResult>,now:number):Promise<Lease>{
  this.invalidate();const version=this.generation;
  try{
   const result=await revalidate();
   if(version!==this.generation)return this.lease;
   if(result.confirmed===true&&Number.isFinite(result.expiresAt)&&result.expiresAt>now
       &&result.expiresAt<=now+3600_000&&uuid.test(result.tenantId)&&uuid.test(result.facilityId)
       &&Array.isArray(result.capabilities)&&result.capabilities.every(x=>typeof x==='string'&&x.length>0&&x.length<=64)){
    this.lease={kind:'verified',tenantId:result.tenantId,facilityId:result.facilityId,expiresAt:result.expiresAt,capabilities:Object.freeze([...new Set(result.capabilities)])};
   }
  }catch{/* no session from errors */}
  return this.lease;
 }
}
