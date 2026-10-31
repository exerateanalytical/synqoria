from pathlib import Path
r=Path(__file__).resolve().parents[1]
def put(path,s):
 p=r/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(s)
# P05-B0022: remove direct JDBC write seam from exported package API, add a deliberately locked public production boundary
p=r/'services/clinical-core/src/main/java/org/synqoria/registration/RegistrationJdbc.java'
s=p.read_text().replace('public final class RegistrationJdbc {','final class RegistrationJdbc {')
p.write_text(s)
put('services/clinical-core/src/main/java/org/synqoria/registration/RegistrationProductionBoundary.java', '''package org.synqoria.registration;
/** P05-B0022. Public API cannot invoke the package-private JDBC test seam. No write release exists. */
public final class RegistrationProductionBoundary {
 private RegistrationProductionBoundary(){}
 public enum State { LOCKED_PENDING_TRUSTED_IAM, LOCKED_PENDING_DATABASE_AND_CLINICAL_APPROVAL }
 public static State admission(boolean identityVerified,boolean rlsTested,boolean clinicalApproved){
  if(!identityVerified)return State.LOCKED_PENDING_TRUSTED_IAM;
  return State.LOCKED_PENDING_DATABASE_AND_CLINICAL_APPROVAL;
 }
}
''')
# P05-B0023
put('services/clinical-core/src/main/java/org/synqoria/registration/ReviewEventTransition.java', '''package org.synqoria.registration;
/** P05-B0023. State machine for append-only reviewer decisions; never accepts resurrection or gaps. */
public final class ReviewEventTransition {
 public enum State { NONE, APPROVED, REVOKED }
 public enum Kind { REVIEW_APPROVED, REVIEW_REVOKED }
 public record Snapshot(State state,long version){public Snapshot{if(state==null||version<0||(state==State.NONE)!=(version==0))throw new IllegalArgumentException("Invalid snapshot");}}
 private ReviewEventTransition(){}
 public static Snapshot apply(Snapshot current,long nextVersion,Kind event){
  if(current==null||event==null||nextVersion!=current.version()+1||nextVersion<1)throw new IllegalArgumentException("Nonsequential review event");
  if(current.state()==State.NONE&&event==Kind.REVIEW_APPROVED)return new Snapshot(State.APPROVED,nextVersion);
  if(current.state()==State.APPROVED&&event==Kind.REVIEW_REVOKED)return new Snapshot(State.REVOKED,nextVersion);
  throw new IllegalArgumentException("Invalid review state transition");
 }
}
''')
# P05-B0024
put('services/clinical-core/src/main/java/org/synqoria/registration/ReviewAuditChain.java', '''package org.synqoria.registration;
import java.security.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** P05-B0024. Domain-separated tamper-evident digest chain, NOT durable immutable audit custody. */
public final class ReviewAuditChain {
 private ReviewAuditChain(){}
 public static String append(String previousDigest,String bindingDigest,long eventVersion,String kind){
  if(eventVersion<1||bindingDigest==null||!bindingDigest.matches("[0-9a-f]{64}")
   ||previousDigest==null||!previousDigest.matches("[0-9a-f]{64}")
   ||!("REVIEW_APPROVED".equals(kind)||"REVIEW_REVOKED".equals(kind)))throw new IllegalArgumentException("Invalid chain event");
  try{
   String payload=String.join("\\n","SYNQORIA-REVIEW-EVENT-CHAIN-v1",previousDigest,Long.toString(eventVersion),kind,bindingDigest);
   return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
  }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 public static String genesis(){return "0".repeat(64);}
}
''')
# P05-B0025
put('services/clinical-core/src/main/java/org/synqoria/registration/ReviewerGrantEpoch.java', '''package org.synqoria.registration;
import java.time.Instant;import java.util.UUID;
/** P05-B0025. Independent grant epoch check; must be loaded from authoritative IAM, never a client header. */
public record ReviewerGrantEpoch(UUID sessionId,UUID reviewerId,long currentPolicyRevision,Instant lastVerifiedAt,boolean revoked) {
 public ReviewerGrantEpoch {if(sessionId==null||reviewerId==null||currentPolicyRevision<0||lastVerifiedAt==null)throw new IllegalArgumentException("Invalid grant epoch");}
 public boolean matches(VerifiedReviewerSession session,Instant now){
  return session!=null&&now!=null&&!revoked&&session.sessionId().equals(sessionId)&&session.reviewerId().equals(reviewerId)
   &&session.policyRevision()==currentPolicyRevision&&!now.isBefore(lastVerifiedAt)
   &&!lastVerifiedAt.isBefore(now.minusSeconds(15))&&now.isBefore(session.expiresAt());
 }
}
''')
# P05-B0026
put('services/clinical-core/src/main/java/org/synqoria/registration/ReviewReadinessReport.java', '''package org.synqoria.registration;
import java.time.Instant;import java.util.UUID;
/** P05-B0026. Reports evidence state; NEVER yields an approval to mutate a patient. */
public final class ReviewReadinessReport {
 public enum Status { IAM_NOT_VERIFIED, REVIEW_REVOKED_OR_STALE, DATABASE_NOT_VERIFIED, REVIEWED_NOT_RELEASED }
 private ReviewReadinessReport(){}
 public static Status evaluate(VerifiedReviewerSession session,ReviewerGrantEpoch grant,ReviewRevocationFence revocation,
 UUID tenant,UUID facility,UUID registrar,UUID reviewId,long eventVersion,long candidateRevision,long requiredPolicyRevision,
 boolean committedReviewRow,boolean rlsIsolationTested,Instant now){
  if(session==null||grant==null||!grant.matches(session,now)||!session.authorizes(tenant,facility,registrar,requiredPolicyRevision,now))return Status.IAM_NOT_VERIFIED;
  if(revocation==null||!revocation.freshAndUnrevoked(reviewId,eventVersion,candidateRevision,now))return Status.REVIEW_REVOKED_OR_STALE;
  if(!committedReviewRow||!rlsIsolationTested)return Status.DATABASE_NOT_VERIFIED;
  return Status.REVIEWED_NOT_RELEASED;
 }
}
''')
# tests per Java batch
put('services/clinical-core/src/test/java/org/synqoria/registration/V160ReviewSafetyTest.java', '''package org.synqoria.registration;
import java.lang.reflect.Modifier;import java.time.*;import java.util.*;
/** Five separately executed Java batches; synthetic identities; no real IAM/PG. */
public final class V160ReviewSafetyTest {
 static int assertions=0;static void ok(boolean b){assertions++;if(!b)throw new AssertionError("Assertion "+assertions);}
 static void bad(Runnable fn){try{fn.run();throw new AssertionError("Expected refusal");}catch(IllegalArgumentException expected){assertions++;}}
 static final UUID T=UUID.randomUUID(),F=UUID.randomUUID(),R=UUID.randomUUID(),A=UUID.randomUUID(),S=UUID.randomUUID(),REV=UUID.randomUUID();
 static final Instant NOW=Instant.parse("2026-10-30T03:00:00Z");
 static VerifiedReviewerSession session(){return new VerifiedReviewerSession(T,F,R,S,Set.of("patient:identity-review:approve"),8,NOW.minusSeconds(30),NOW.plusSeconds(60),false);}
 static void b22(){
  ok(!Modifier.isPublic(RegistrationJdbc.class.getModifiers()));
  for(boolean identity:new boolean[]{false,true})for(boolean db:new boolean[]{false,true})for(boolean clinical:new boolean[]{false,true})
   ok(RegistrationProductionBoundary.admission(identity,db,clinical)!=null);
  ok(RegistrationProductionBoundary.admission(true,true,true)!=RegistrationProductionBoundary.State.LOCKED_PENDING_TRUSTED_IAM);
 }
 static void b23(){
  var none=new ReviewEventTransition.Snapshot(ReviewEventTransition.State.NONE,0);
  var approved=ReviewEventTransition.apply(none,1,ReviewEventTransition.Kind.REVIEW_APPROVED);
  ok(approved.state()==ReviewEventTransition.State.APPROVED&&approved.version()==1);
  var revoked=ReviewEventTransition.apply(approved,2,ReviewEventTransition.Kind.REVIEW_REVOKED);
  ok(revoked.state()==ReviewEventTransition.State.REVOKED);
  bad(()->ReviewEventTransition.apply(none,2,ReviewEventTransition.Kind.REVIEW_APPROVED));
  bad(()->ReviewEventTransition.apply(approved,2,ReviewEventTransition.Kind.REVIEW_APPROVED));
  bad(()->ReviewEventTransition.apply(revoked,3,ReviewEventTransition.Kind.REVIEW_APPROVED));
  bad(()->ReviewEventTransition.apply(none,1,ReviewEventTransition.Kind.REVIEW_REVOKED));
 }
 static void b24(){
  var binding=ReviewAuditBinding.digest(T,F,REV,R,S,A,1,"REVIEW_APPROVED");
  var first=ReviewAuditChain.append(ReviewAuditChain.genesis(),binding,1,"REVIEW_APPROVED");
  ok(first.matches("[0-9a-f]{64}"));ok(!first.equals(ReviewAuditChain.genesis()));
  ok(first.equals(ReviewAuditChain.append(ReviewAuditChain.genesis(),binding,1,"REVIEW_APPROVED")));
  ok(!first.equals(ReviewAuditChain.append(ReviewAuditChain.genesis(),binding,2,"REVIEW_APPROVED")));
  ok(!first.equals(ReviewAuditChain.append(ReviewAuditChain.genesis(),binding,1,"REVIEW_REVOKED")));
  bad(()->ReviewAuditChain.append("bad",binding,1,"REVIEW_APPROVED"));
  bad(()->ReviewAuditChain.append(ReviewAuditChain.genesis(),binding,0,"REVIEW_APPROVED"));
 }
 static void b25(){
  var grant=new ReviewerGrantEpoch(S,R,8,NOW.minusSeconds(1),false);
  ok(grant.matches(session(),NOW));
  ok(!new ReviewerGrantEpoch(S,R,7,NOW.minusSeconds(1),false).matches(session(),NOW));
  ok(!new ReviewerGrantEpoch(S,R,8,NOW.minusSeconds(1),true).matches(session(),NOW));
  ok(!new ReviewerGrantEpoch(S,R,8,NOW.minusSeconds(16),false).matches(session(),NOW));
  ok(!new ReviewerGrantEpoch(UUID.randomUUID(),R,8,NOW.minusSeconds(1),false).matches(session(),NOW));
  ok(!grant.matches(session(),NOW.plusSeconds(61)));
 }
 static void b26(){
  var grant=new ReviewerGrantEpoch(S,R,8,NOW.minusSeconds(1),false);
  var decision=new ReviewRevocationFence(REV,2,3,false,NOW.plusSeconds(30),NOW.minusSeconds(1));
  ok(ReviewReadinessReport.evaluate(session(),grant,decision,T,F,A,REV,2,3,8,true,true,NOW)==ReviewReadinessReport.Status.REVIEWED_NOT_RELEASED);
  ok(ReviewReadinessReport.evaluate(session(),grant,decision,T,F,A,REV,2,3,8,false,true,NOW)==ReviewReadinessReport.Status.DATABASE_NOT_VERIFIED);
  ok(ReviewReadinessReport.evaluate(session(),grant,decision,T,F,A,REV,2,3,8,true,false,NOW)==ReviewReadinessReport.Status.DATABASE_NOT_VERIFIED);
  ok(ReviewReadinessReport.evaluate(session(),grant,decision,T,F,A,REV,2,4,8,true,true,NOW)==ReviewReadinessReport.Status.REVIEW_REVOKED_OR_STALE);
  ok(ReviewReadinessReport.evaluate(session(),grant,decision,T,F,R,REV,2,3,8,true,true,NOW)==ReviewReadinessReport.Status.IAM_NOT_VERIFIED);
  ok(ReviewReadinessReport.evaluate(session(),grant,decision,T,F,A,REV,2,3,9,true,true,NOW)==ReviewReadinessReport.Status.IAM_NOT_VERIFIED);
 }
 public static void main(String[]args){if(args.length!=1)throw new IllegalArgumentException("Batch required");switch(args[0]){
  case "P05-B0022"->b22();case "P05-B0023"->b23();case "P05-B0024"->b24();case "P05-B0025"->b25();case "P05-B0026"->b26();
  default->throw new IllegalArgumentException("Unknown batch");}
  System.out.println("PASS "+args[0]+" "+assertions+" assertions (Java unit; production write LOCKED)");
 }
}
''')
# TS P02-B0040
put('packages/ux-core/src/registration-route-guard.ts', '''/** P02-B0040. Client-side intake navigation defense-in-depth; server must independently enforce access. */
export type IntakeRouteResult={allowed:boolean;screen:'M01-S02'|null;reason:'OK'|'DENIED'};
export function guardedIntakeRoute(raw:string,shell:'workforce'|'portal'|'platform',authenticated:boolean,grants:ReadonlySet<string>):IntakeRouteResult{
 const deny: IntakeRouteResult={allowed:false,screen:null,reason:'DENIED'};
 if(typeof raw!=='string'||raw.length>128||raw.includes('?')||raw.includes('#')||raw.includes('\\\\')||raw.includes('%')||raw.includes('//'))return deny;
 if(raw!=='/workforce/patients/new'||shell!=='workforce'||!authenticated||!grants.has('patient:register'))return deny;
 return {allowed:true,screen:'M01-S02',reason:'OK'};
}
''')
# TS P02-B0041
put('packages/ux-core/src/registration-a11y-summary.ts', '''/** P02-B0041. Non-identifying bilingual error summary for accessible locked intake. */
export type IntakeIssue='ACCESS'|'FACILITY'|'REVIEW'|'OFFLINE';
const copy={en:{ACCESS:'Staff access not verified',FACILITY:'Facility not selected',REVIEW:'Independent review required',OFFLINE:'Hospital connection unavailable'},
 fr:{ACCESS:'Accès du personnel non vérifié',FACILITY:'Établissement non sélectionné',REVIEW:'Vérification indépendante requise',OFFLINE:'Connexion hospitalière indisponible'}} as const;
const order:readonly IntakeIssue[]=['ACCESS','FACILITY','REVIEW','OFFLINE'];
export function intakeA11ySummary(locale:'en'|'fr',issues:readonly IntakeIssue[]){
 const distinct=new Set(issues.filter(i=>order.includes(i)));
 return {role:'alert' as const,screenId:'M01-S02' as const,items:order.filter(i=>distinct.has(i)).map(i=>({code:i,message:copy[locale][i]})),
  canSubmit:false as const,focusTarget:'intake-status' as const};
}
''')
# TS P02-B0042
put('packages/ux-core/src/registration-mobile-lifecycle.ts', '''/** P02-B0042. Native lifecycle gate: background/offline/scope changes invalidate intake. */
import {VolatileRegistrationSession,type IntakeScope} from './registration-draft-session';
import {RegistrationContextFence} from './registration-context-fence';
export class RegistrationMobileLifecycle {
 private readonly fence:RegistrationContextFence;
 private visible=false;
 constructor(private readonly draft:VolatileRegistrationSession){this.fence=new RegistrationContextFence(draft);}
 resume(scope:IntakeScope|null,online:boolean,locale:'en'|'fr'):{editable:false;viewOnly:true}{
  if(!online||!scope){this.suspend();return {editable:false,viewOnly:true};}
  this.visible=this.fence.enter(scope,locale);
  return {editable:false,viewOnly:true}; // no production reviewer endpoint / clinical release
 }
 suspend():void{this.visible=false;this.fence.end();}
 disconnect():void{this.suspend();}
 get active():boolean{return this.visible;}
}
''')
# TS P02-B0043 native source actual integration, add status summary and a11y
p=r/'apps/staff-mobile/RegistrationPreview.tsx';s=p.read_text()
s=s.replace('duplicateStatus,registrationActionPresentation','duplicateStatus,registrationActionPresentation,intakeA11ySummary')
s=s.replace(" const action=registrationActionPresentation(locale,false,false);", " const action=registrationActionPresentation(locale,false,false);\n const summary=intakeA11ySummary(locale,['ACCESS','FACILITY','REVIEW','OFFLINE']);")
s=s.replace('  <Text accessibilityRole="text" style={s.warning}>{review.status}: {review.instruction}</Text>', '''  <Text nativeID="intake-status" accessibilityRole="alert" style={s.warning}>{review.status}: {review.instruction}</Text>
  {summary.items.map(issue=><Text key={issue.code} style={s.text} accessibilityRole="text">{issue.message}</Text>)}''')
s=s.replace('padding:20,borderRadius:12','padding:20,flexShrink:1,borderRadius:12')
p.write_text(s)
p=r/'tests/test_tsx_syntax.cjs';s=p.read_text().replace("'apps/staff-mobile/App.tsx'","'apps/staff-mobile/App.tsx','apps/staff-mobile/RegistrationPreview.tsx'");p.write_text(s)
p=r/'packages/ux-core/src/index.ts';s=p.read_text()+"\nexport * from './registration-route-guard';\nexport * from './registration-a11y-summary';\nexport * from './registration-mobile-lifecycle';\n";p.write_text(s)
put('packages/ux-core/tests/v160-intake-safety.test.cjs', '''const test=require('node:test');const assert=require('node:assert/strict');
const {guardedIntakeRoute,intakeA11ySummary,VolatileRegistrationSession,RegistrationMobileLifecycle}=require('../dist/index.js');
test('P02-B0040 canonical route only; no query/deep-link escalation',()=>{
 const g=new Set(['patient:register']);
 assert.equal(guardedIntakeRoute('/workforce/patients/new','workforce',true,g).allowed,true);
 for(const path of ['/workforce/patients/new?patient=1','/workforce/patients/new#edit','/workforce/patients/new/1','/workforce/patients//new','/workforce/patients/%6eew','/workforce/patients/new\\\\other','/workforce/patients/new?redirect=/admin'])
  assert.equal(guardedIntakeRoute(path,'workforce',true,g).allowed,false,path);
 for(const shell of ['portal','platform'])assert.equal(guardedIntakeRoute('/workforce/patients/new',shell,true,g).allowed,false);
 assert.equal(guardedIntakeRoute('/workforce/patients/new','workforce',false,g).allowed,false);
 assert.equal(guardedIntakeRoute('/workforce/patients/new','workforce',true,new Set()).allowed,false);
});
test('P02-B0041 bilingual accessible summary has stable nonidentifying messages',()=>{
 for(const locale of ['en','fr']){
  const s=intakeA11ySummary(locale,['REVIEW','ACCESS','REVIEW','OFFLINE']);
  assert.deepEqual(s.items.map(x=>x.code),['ACCESS','REVIEW','OFFLINE']);
  assert.equal(s.canSubmit,false);assert.equal(s.role,'alert');assert.equal(s.focusTarget,'intake-status');
  assert.ok(!JSON.stringify(s).includes('patientName'));
 }
});
test('P02-B0042 background and offline destroy synthetic volatile intake draft',()=>{
 const store=new VolatileRegistrationSession(),life=new RegistrationMobileLifecycle(store);
 const scope={tenant:'synthetic-tenant',facility:'synthetic-facility',actor:'synthetic-actor',sessionEpoch:1};
 assert.equal(life.resume(scope,true,'en').editable,false);assert.equal(life.active,true);
 assert.equal(store.update(scope,{name:'Synthetic Person'}),true);
 life.suspend();assert.equal(life.active,false);assert.equal(store.snapshot(scope),null);
 life.resume(scope,true,'fr');assert.equal(store.snapshot(scope).name,'');
 store.update(scope,{name:'Synthetic Person 2'});life.disconnect();assert.equal(store.snapshot(scope),null);
 assert.equal(life.resume(scope,false,'en').viewOnly,true);assert.equal(life.active,false);
});
''')
put('tests/test_v160_native_integration.py', '''from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=(r/'apps/staff-mobile/RegistrationPreview.tsx').read_text()
assert 'intakeA11ySummary' in s and "['ACCESS','FACILITY','REVIEW','OFFLINE']" in s
assert 'accessibilityRole="alert"' in s and 'nativeID="intake-status"' in s
assert 'summary.items.map' in s and 'registrationActionPresentation(locale,false,false)' in s
assert 'disabledAction' in s and 'editable' not in s
assert 'flexShrink:1' in s
print('PASS P02-B0043 native source integration; source-only, not Expo build')
''')
# P01-B0032: an actual fail-closed release lock executable (no shell bypass)
put('tools/verify_release_gate.py', '''#!/usr/bin/env python3
"""P01-B0032. Release gate must refuse any request without external evidence and native builds."""
import argparse,json,sys
from pathlib import Path
r=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--check-locked',action='store_true');p.add_argument('--request-release',action='store_true');args=p.parse_args()
ledger=json.loads((r/'registers/batch-ledger.json').read_text())
screens=json.loads((r/'registers/screen-status-matrix.json').read_text())['items']
modules=json.loads((r/'registers/module-status-matrix.json').read_text())['items']
blockers=json.loads((r/'registers/blockers.json').read_text())['blockers']
assert len(screens)==261 and len(modules)==40
assert ledger['release_status']=='NOT_CERTIFIED'
assert any(not x['accepted'] for x in screens)
assert any(b.get('status')=='open' for b in blockers)
if args.request_release:
 print('DENIED: clinical signoff, native builds, PostgreSQL RLS and 261 screen acceptance incomplete',file=sys.stderr)
 sys.exit(3)
print('PASS: fail-closed release lock verified against 40 modules / 261 screens; NO certification')
''')
put('tests/test_v160_release_gate.py', '''from pathlib import Path
import subprocess,sys
r=Path(__file__).resolve().parents[1]
cmd=[sys.executable,str(r/'tools/verify_release_gate.py')]
allowed=subprocess.run(cmd+['--check-locked'],capture_output=True,text=True)
assert allowed.returncode==0,allowed.stderr
assert 'NO certification' in allowed.stdout
refused=subprocess.run(cmd+['--request-release'],capture_output=True,text=True)
assert refused.returncode==3,refused.stdout+refused.stderr
assert 'DENIED:' in refused.stderr
print('PASS P01-B0032 release gate denies promotion and validates canonical scope')
''')
# ensure regression invokes Java additions
p=r/'tools/check_java_security.sh';p.write_text(p.read_text()+'''\n# v0.16.0: five independent reviewer/registration safety batches, local Java only.
for batch in P05-B0022 P05-B0023 P05-B0024 P05-B0025 P05-B0026; do
 java -ea -cp /tmp/synqoria-security-classes org.synqoria.registration.V160ReviewSafetyTest "$batch"
done
''')
print('Created 10 implementation increments (5 Java, 4 TS/native, 1 release gate)')
