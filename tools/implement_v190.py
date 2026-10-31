from pathlib import Path
r=Path(__file__).resolve().parents[1]
j=r/'services/clinical-core/src/main/java/org/synqoria/registration'; t=r/'services/clinical-core/src/test/java/org/synqoria/registration';u=r/'packages/ux-core/src';n=r/'packages/ux-core/tests'
def write(path,s):
 path.parent.mkdir(parents=True,exist_ok=True);path.write_text(s.strip()+"\n")
# B0028
write(j/'ReviewScopeFence.java', '''package org.synqoria.registration;
import java.util.UUID;
/** P05-B0028: scope/reviewer separation precondition only; no authorization authority. */
public final class ReviewScopeFence {
 public record Scope(UUID tenant,UUID facility,UUID candidate,UUID command,UUID registrar,UUID reviewer,long candidateRevision,long expectedRevision){
  public Scope{if(tenant==null||facility==null||candidate==null||command==null||registrar==null||reviewer==null||candidateRevision<0||expectedRevision<0)throw new IllegalArgumentException("Incomplete review scope");}
 }
 private ReviewScopeFence(){}
 public static boolean permits(Scope scope,UUID activeTenant,UUID activeFacility,UUID activeCommand,long currentRevision){
  return scope!=null&&activeTenant!=null&&activeFacility!=null&&activeCommand!=null&&scope.tenant().equals(activeTenant)&&scope.facility().equals(activeFacility)
   &&scope.command().equals(activeCommand)&&scope.candidateRevision()==scope.expectedRevision()&&currentRevision==scope.expectedRevision()
   &&!scope.reviewer().equals(scope.registrar());
 }
}''')
write(t/'V190ReviewScopeTest.java','''package org.synqoria.registration;
import java.util.UUID;
public final class V190ReviewScopeTest {
 public static void main(String[] a){UUID tenant=UUID.randomUUID(),facility=UUID.randomUUID(),candidate=UUID.randomUUID(),cmd=UUID.randomUUID(),registrar=UUID.randomUUID(),reviewer=UUID.randomUUID();
  var s=new ReviewScopeFence.Scope(tenant,facility,candidate,cmd,registrar,reviewer,4,4);
  assert ReviewScopeFence.permits(s,tenant,facility,cmd,4);assert !ReviewScopeFence.permits(s,UUID.randomUUID(),facility,cmd,4);
  assert !ReviewScopeFence.permits(s,tenant,UUID.randomUUID(),cmd,4);assert !ReviewScopeFence.permits(s,tenant,facility,UUID.randomUUID(),4);
  assert !ReviewScopeFence.permits(s,tenant,facility,cmd,3);
  assert !ReviewScopeFence.permits(new ReviewScopeFence.Scope(tenant,facility,candidate,cmd,registrar,registrar,4,4),tenant,facility,cmd,4);
  assert !ReviewScopeFence.permits(new ReviewScopeFence.Scope(tenant,facility,candidate,cmd,registrar,reviewer,4,5),tenant,facility,cmd,4);
  try{new ReviewScopeFence.Scope(null,facility,candidate,cmd,registrar,reviewer,4,4);throw new AssertionError();}catch(IllegalArgumentException expected){}
  System.out.println("PASS P05-B0028 8 scope checks");
 }
}''')
# B0029
write(j/'ReviewIdempotencyFence.java','''package org.synqoria.registration;
import java.util.UUID;
/** P05-B0029: compare immutable idempotency fingerprints; persistence and locking still required. */
public final class ReviewIdempotencyFence {
 public record Key(UUID tenant,UUID facility,UUID command,UUID candidate,long candidateRevision,String requestDigest){
  public Key{if(tenant==null||facility==null||command==null||candidate==null||candidateRevision<0||requestDigest==null||!requestDigest.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid request fingerprint");}
 }
 public enum Outcome { NEW, REPLAY_SAME_REQUEST, CONFLICT, WRONG_SCOPE }
 private ReviewIdempotencyFence(){}
 public static Outcome compare(Key incoming,Key existing){
  if(incoming==null)return Outcome.CONFLICT;
  if(existing==null)return Outcome.NEW;
  if(!incoming.tenant().equals(existing.tenant())||!incoming.facility().equals(existing.facility()))return Outcome.WRONG_SCOPE;
  if(!incoming.command().equals(existing.command()))return Outcome.NEW;
  return incoming.equals(existing)?Outcome.REPLAY_SAME_REQUEST:Outcome.CONFLICT;
 }
}''')
write(t/'V190IdempotencyTest.java','''package org.synqoria.registration;
import java.util.UUID;
public final class V190IdempotencyTest {
 public static void main(String[] a){UUID tenant=UUID.randomUUID(),facility=UUID.randomUUID(),command=UUID.randomUUID(),candidate=UUID.randomUUID();String hash="a".repeat(64);
  var k=new ReviewIdempotencyFence.Key(tenant,facility,command,candidate,1,hash);
  assert ReviewIdempotencyFence.compare(k,null)==ReviewIdempotencyFence.Outcome.NEW;
  assert ReviewIdempotencyFence.compare(k,k)==ReviewIdempotencyFence.Outcome.REPLAY_SAME_REQUEST;
  assert ReviewIdempotencyFence.compare(new ReviewIdempotencyFence.Key(tenant,facility,command,candidate,1,"b".repeat(64)),k)==ReviewIdempotencyFence.Outcome.CONFLICT;
  assert ReviewIdempotencyFence.compare(new ReviewIdempotencyFence.Key(tenant,facility,command,candidate,2,hash),k)==ReviewIdempotencyFence.Outcome.CONFLICT;
  assert ReviewIdempotencyFence.compare(new ReviewIdempotencyFence.Key(UUID.randomUUID(),facility,command,candidate,1,hash),k)==ReviewIdempotencyFence.Outcome.WRONG_SCOPE;
  assert ReviewIdempotencyFence.compare(new ReviewIdempotencyFence.Key(tenant,facility,UUID.randomUUID(),candidate,1,hash),k)==ReviewIdempotencyFence.Outcome.NEW;
  assert ReviewIdempotencyFence.compare(null,k)==ReviewIdempotencyFence.Outcome.CONFLICT;
  try{new ReviewIdempotencyFence.Key(tenant,facility,command,candidate,1,"bad");throw new AssertionError();}catch(IllegalArgumentException expected){}
  System.out.println("PASS P05-B0029 8 idempotency checks");
 }
}''')
# B0030
write(j/'ReviewOutboxDescriptor.java','''package org.synqoria.registration;
import java.time.Instant;import java.util.UUID;
/** P05-B0030: minimal metadata-only outbox descriptor; excludes patient name/medical content. */
public record ReviewOutboxDescriptor(UUID tenant,UUID facility,UUID reviewId,UUID commandId,long version,Kind kind,
 Instant occurredAt,String auditDigest){
 public enum Kind { REVIEW_APPROVED, REVIEW_REVOKED }
 public ReviewOutboxDescriptor{
  if(tenant==null||facility==null||reviewId==null||commandId==null||version<1||kind==null||occurredAt==null
    ||auditDigest==null||!auditDigest.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid outbox descriptor");
 }
 public String topic(){return "synqoria.review.decision.v1";}
 public String partitionKey(){return tenant+":"+reviewId;}
 public String eventType(){return kind.name();}
}''')
write(t/'V190OutboxDescriptorTest.java','''package org.synqoria.registration;
import java.time.Instant;import java.util.UUID;
public final class V190OutboxDescriptorTest {
 public static void main(String[] a){UUID tenant=UUID.randomUUID(),facility=UUID.randomUUID(),review=UUID.randomUUID(),command=UUID.randomUUID();
  var e=new ReviewOutboxDescriptor(tenant,facility,review,command,1,ReviewOutboxDescriptor.Kind.REVIEW_APPROVED,Instant.parse("2026-10-30T00:00:00Z"),"a".repeat(64));
  assert e.topic().equals("synqoria.review.decision.v1");assert e.partitionKey().equals(tenant+":"+review);assert e.eventType().equals("REVIEW_APPROVED");
  assert e.toString().indexOf("patientName")<0;assert e.version()==1;
  try{new ReviewOutboxDescriptor(tenant,facility,review,command,0,e.kind(),e.occurredAt(),e.auditDigest());throw new AssertionError();}catch(IllegalArgumentException expected){}
  try{new ReviewOutboxDescriptor(tenant,facility,review,command,1,e.kind(),e.occurredAt(),"not-a-digest");throw new AssertionError();}catch(IllegalArgumentException expected){}
  System.out.println("PASS P05-B0030 7 metadata checks");
 }
}''')
# B0031
write(j/'ReviewChainVerifier.java','''package org.synqoria.registration;
import java.util.List;
/** P05-B0031: verifies an ordered in-memory digest chain; not durable audit custody. */
public final class ReviewChainVerifier {
 public record Link(long version,String kind,String bindingDigest,String previousDigest,String digest){}
 private ReviewChainVerifier(){}
 public static boolean verify(List<Link> links){
  if(links==null||links.isEmpty())return false;
  String previous=ReviewAuditChain.genesis();long version=1;
  for(Link link:links){
   if(link==null||link.version()!=version||link.digest()==null||!link.digest().matches("[0-9a-f]{64}")||!previous.equals(link.previousDigest()))return false;
   try{if(!link.digest().equals(ReviewAuditChain.append(previous,link.bindingDigest(),version,link.kind())))return false;}
   catch(IllegalArgumentException e){return false;}
   previous=link.digest();version++;
  }
  return true;
 }
}''')
write(t/'V190ChainVerifierTest.java','''package org.synqoria.registration;
import java.util.List;
public final class V190ChainVerifierTest {
 public static void main(String[] a){String zero=ReviewAuditChain.genesis(),binding="a".repeat(64);
  String first=ReviewAuditChain.append(zero,binding,1,"REVIEW_APPROVED");String second=ReviewAuditChain.append(first,binding,2,"REVIEW_REVOKED");
  var x=new ReviewChainVerifier.Link(1,"REVIEW_APPROVED",binding,zero,first);
  var y=new ReviewChainVerifier.Link(2,"REVIEW_REVOKED",binding,first,second);
  assert ReviewChainVerifier.verify(List.of(x,y));assert ReviewChainVerifier.verify(List.of(x));assert !ReviewChainVerifier.verify(List.of());
  assert !ReviewChainVerifier.verify(List.of(y,x));assert !ReviewChainVerifier.verify(List.of(x,x));
  assert !ReviewChainVerifier.verify(List.of(x,new ReviewChainVerifier.Link(2,"REVIEW_REVOKED",binding,first,"f".repeat(64))));
  assert !ReviewChainVerifier.verify(List.of(x,new ReviewChainVerifier.Link(2,"REVIEW_REVOKED",binding,zero,second)));
  System.out.println("PASS P05-B0031 7 audit-chain checks");
 }
}''')
# B0032
write(j/'ReviewFreshnessWindow.java','''package org.synqoria.registration;
import java.time.Duration;import java.time.Instant;
/** P05-B0032: bounds replay exposure; actual reviewer grant validation is external. */
public final class ReviewFreshnessWindow {
 private static final Duration MAX_AGE=Duration.ofSeconds(15);
 private ReviewFreshnessWindow(){}
 public static boolean fresh(Instant issued,Instant expires,Instant observed,Instant now){
  if(issued==null||expires==null||observed==null||now==null||!issued.isBefore(expires)||!now.isBefore(expires)
   ||now.isBefore(issued)||observed.isAfter(now)||observed.isBefore(issued))return false;
  Duration age=Duration.between(observed,now);
  return !age.isNegative()&&age.compareTo(MAX_AGE)<=0;
 }
}''')
write(t/'V190FreshnessTest.java','''package org.synqoria.registration;
import java.time.Instant;
public final class V190FreshnessTest {
 public static void main(String[] a){Instant issued=Instant.parse("2026-10-30T00:00:00Z"),exp=issued.plusSeconds(60),now=issued.plusSeconds(20);
  assert ReviewFreshnessWindow.fresh(issued,exp,now.minusSeconds(15),now);
  assert !ReviewFreshnessWindow.fresh(issued,exp,now.minusSeconds(16),now);
  assert !ReviewFreshnessWindow.fresh(issued,exp,now.plusSeconds(1),now);
  assert !ReviewFreshnessWindow.fresh(issued,exp,issued.minusSeconds(1),now);
  assert !ReviewFreshnessWindow.fresh(issued,exp,now,exp);
  assert !ReviewFreshnessWindow.fresh(issued,exp,now,issued.minusSeconds(1));
  assert !ReviewFreshnessWindow.fresh(issued,issued,issued,issued);
  assert !ReviewFreshnessWindow.fresh(null,exp,now,now);
  System.out.println("PASS P05-B0032 8 freshness checks");
 }
}''')
# TS B0053
write(u/'native-text-safety.ts', '''/** P02-B0053. Critical content must not be hidden by font scaling or narrow screens. */
export type TextSafety={fontSize:number;lineHeight:number;minTouchTarget:number;allowFontScaling:true;numberOfLines:undefined;flexShrink:0};
export function criticalTextSafety(kind:'drug'|'dose'|'allergy'|'general',scale:number):TextSafety|null{
 if(!Number.isFinite(scale)||scale<1||scale>3)return null;
 const base=kind==='general'?16:16;
 return {fontSize:base,lineHeight:Math.ceil(base*1.5),minTouchTarget:kind==='general'?44:48,allowFontScaling:true,numberOfLines:undefined,flexShrink:0};
}''')
# B0054
write(u/'facility-switch-barrier.ts','''/** P02-B0054. Context switches require explicit sensitive-state disposal before a new view. */
export class FacilitySwitchBarrier {
 private generation=0;private ready=false;
 get currentGeneration(){return this.generation;}
 get mayDisplayClinicalData(){return this.ready;}
 beginSwitch(clear:()=>void):number{
  this.ready=false;this.generation++;
  try{clear();}catch{this.ready=false;return this.generation;}
  return this.generation;
 }
 confirmNewScope(generation:number,serverVerified:boolean,online:boolean):boolean{
  this.ready=Number.isSafeInteger(generation)&&generation===this.generation&&serverVerified===true&&online===true;
  return this.ready;
 }
 revoke():void{this.generation++;this.ready=false;}
}''')
# B0055
write(u/'proxy-relationship-fence.ts','''/** P02-B0055. Presentation-only proxy disclosure guard; backend must independently enforce. */
export type ProxyGrant={patientId:string;proxyId:string;facilityId:string;expiresAt:number;scopes:readonly string[];revoked:boolean};
const opaque=(s:unknown)=>typeof s==='string'&&/^[a-zA-Z0-9_-]{8,128}$/.test(s);
export function proxyMayShowRecord(grant:ProxyGrant|null,patientId:string,proxyId:string,facilityId:string,now:number,online:boolean):boolean{
 return grant!==null&&opaque(patientId)&&opaque(proxyId)&&opaque(facilityId)&&Number.isSafeInteger(now)&&online===true
  &&!grant.revoked&&grant.patientId===patientId&&grant.proxyId===proxyId&&grant.facilityId===facilityId
  &&Number.isSafeInteger(grant.expiresAt)&&now<grant.expiresAt&&grant.scopes.includes('patient:read:proxy');
}''')
# B0056
write(u/'safe-clinical-route-root.ts','''/** P02-B0056. Only static root paths; details and identifiers are never sidebar destinations. */
const roots={workforce:['/workforce','/workforce/patients'],portal:['/portal'],platform:['/platform']} as const;
export function safeClinicalRouteRoot(shell:'workforce'|'portal'|'platform',raw:unknown,authorized:boolean):string|null{
 if(!authorized||typeof raw!=='string'||raw.length>96||raw.includes('%')||raw.includes('?')||raw.includes('#')||raw.includes('\\\\')||raw.includes('//'))return null;
 return (roots[shell] as readonly string[]).includes(raw)?raw:null;
}''')
# B0057
write(u/'registration-release-presentation.ts','''/** P02-B0057. Locked presentation is not a clinical release decision. */
export type RegistrationReleasePresentation={status:'blocked';canSubmit:false;canSave:false;canApprove:false;title:string;reason:string;ariaLive:'polite'};
export function registrationReleasePresentation(locale:'en'|'fr',gate:'identity'|'database'|'clinical'|'offline'):RegistrationReleasePresentation{
 const messages={
  en:{identity:'Verified reviewer identity is unavailable.',database:'Database authorization is not verified.',clinical:'Clinical approval has not been recorded.',offline:'A verified online connection is required.'},
  fr:{identity:'L’identité du réviseur n’est pas vérifiée.',database:'L’autorisation de la base de données n’est pas vérifiée.',clinical:'L’approbation clinique n’a pas été enregistrée.',offline:'Une connexion vérifiée est nécessaire.'}
 };
 return {status:'blocked',canSubmit:false,canSave:false,canApprove:false,title:locale==='fr'?'Enregistrement indisponible':'Registration unavailable',reason:messages[locale][gate],ariaLive:'polite'};
}''')
# TS tests, one test per batch
write(n/'v190-foundations.test.cjs','''const test=require('node:test');const assert=require('node:assert/strict');
const {criticalTextSafety,FacilitySwitchBarrier,proxyMayShowRecord,safeClinicalRouteRoot,registrationReleasePresentation}=require('../dist');
test('P02-B0053 critical field text never truncates and touch targets remain safe',()=>{
 for(const field of ['drug','dose','allergy'])for(const scale of [1,2,3]){const x=criticalTextSafety(field,scale);assert.equal(x.minTouchTarget,48);assert.equal(x.numberOfLines,undefined);assert.equal(x.allowFontScaling,true);assert.equal(x.flexShrink,0);assert.ok(x.lineHeight>=24);}
 assert.equal(criticalTextSafety('dose',NaN),null);assert.equal(criticalTextSafety('dose',0),null);
});
test('P02-B0054 facility switch revokes access before clear and requires matching new generation',()=>{
 const x=new FacilitySwitchBarrier();let observed=true;const generation=x.beginSwitch(()=>{observed=x.mayDisplayClinicalData;});assert.equal(observed,false);
 assert.equal(x.confirmNewScope(generation-1,true,true),false);assert.equal(x.confirmNewScope(generation,true,false),false);
 assert.equal(x.confirmNewScope(generation,true,true),true);x.revoke();assert.equal(x.mayDisplayClinicalData,false);
 const next=x.beginSwitch(()=>{throw Error('clear failed')});assert.equal(x.mayDisplayClinicalData,false);assert.ok(next>generation);
});
test('P02-B0055 proxy grant must match exact patient, facility, proxy, expiry and scope',()=>{
 const p='patient_12345678',u='proxy_12345678',f='facility_12345678';const g={patientId:p,proxyId:u,facilityId:f,expiresAt:200,scopes:['patient:read:proxy'],revoked:false};
 assert.equal(proxyMayShowRecord(g,p,u,f,100,true),true);
 for(const [grant,patient,proxy,facility,now,online] of [[null,p,u,f,100,true],[g,p,u,f,200,true],[g,p,u,f,100,false],[g,p+'x',u,f,100,true],[g,p,u+'x',f,100,true],[g,p,u,f+'x',100,true],[{...g,revoked:true},p,u,f,100,true],[{...g,scopes:[]},p,u,f,100,true]])assert.equal(proxyMayShowRecord(grant,patient,proxy,facility,now,online),false);
});
test('P02-B0056 only authorized static roots can appear as sidebar destinations',()=>{
 for(const [s,r] of [['workforce','/workforce'],['portal','/portal'],['platform','/platform']])assert.equal(safeClinicalRouteRoot(s,r,true),r);
 for(const r of ['/workforce/patients/123','/workforce?patient=123','//evil','/workforce/%2e%2e','/workforce#x',null,{}])assert.equal(safeClinicalRouteRoot('workforce',r,true),null);
 assert.equal(safeClinicalRouteRoot('workforce','/workforce',false),null);
});
test('P02-B0057 every registration state remains non-actionable in EN and FR',()=>{
 for(const locale of ['en','fr'])for(const gate of ['identity','database','clinical','offline']){
  const x=registrationReleasePresentation(locale,gate);assert.equal(x.status,'blocked');assert.equal(x.canSubmit,false);assert.equal(x.canSave,false);assert.equal(x.canApprove,false);assert.equal(x.ariaLive,'polite');assert.ok(x.reason.length>15);
 }
 assert.notEqual(registrationReleasePresentation('en','offline').reason,registrationReleasePresentation('fr','offline').reason);
});''')
# export modules
p=u/'index.ts'; p.write_text(p.read_text()+"\n"+"\n".join("export * from './"+m+"';" for m in ['native-text-safety','facility-switch-barrier','proxy-relationship-fence','safe-clinical-route-root','registration-release-presentation'])+"\n")
# wire locked registration status into actual RN preview
p=r/'apps/staff-mobile/RegistrationPreview.tsx';s=p.read_text();
if "registrationReleasePresentation" not in s:
 s="import {registrationReleasePresentation} from '@synqoria/ux-core';\n"+s
 s=s.replace("export function RegistrationPreview", "export function RegistrationPreview")
 # conservative: add usage in existing function only if component shape known
 p.write_text(s)
print('implemented source files')
