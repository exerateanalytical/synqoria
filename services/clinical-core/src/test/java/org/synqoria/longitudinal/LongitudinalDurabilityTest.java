package org.synqoria.longitudinal;

import static org.synqoria.longitudinal.ClinicalCaseCore.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;

/** Independently addressable synthetic durability and security regression assertions. */
public final class LongitudinalDurabilityTest {
 static final SecretKeySpec KEY=new SecretKeySpec(new byte[32],"AES");
 static final SecretKeySpec WRONG=new SecretKeySpec(new byte[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32},"AES");
 static int checked;
 static void ok(boolean valid){checked++;if(!valid)throw new AssertionError("durability assertion "+checked);}
 static void denied(Runnable r){checked++;try{r.run();throw new AssertionError("corrupt or foreign state accepted");}
  catch(SecurityException|IllegalArgumentException expected){/* fail closed */}}
 static final ClinicalVerticalBatchTest.F F=new ClinicalVerticalBatchTest.F();
 static State rich(){var f=new ClinicalVerticalBatchTest.F();f.active();f.assign();f.draft();f.body();f.verified();return f.state();}
 static LongitudinalEnvelopeCrypto.Context context(State s){return new LongitudinalEnvelopeCrypto.Context(s.scope,s.revision,"kms-alpha");}
 static State decode(State s){return LongitudinalSnapshotCodec.decode(LongitudinalSnapshotCodec.encode(s));}
 static void roundTrips(){
  var fresh=new ClinicalVerticalBatchTest.F();State empty=decode(fresh.state());ok(empty.revision==0&&empty.stage==Stage.PLANNED);
  State original=rich(),restored=decode(original);ok(original.scope.equals(restored.scope));
  ok(restored.revision==original.revision&&restored.stage==Stage.ACTIVE&&restored.responsible.equals(ClinicalVerticalBatchTest.A));
  ok(restored.notes.size()==1&&restored.notes.get(ClinicalVerticalBatchTest.N).body.contains("Synthetic"));
  ok(restored.observations.size()==1&&restored.observations.get(ClinicalVerticalBatchTest.O).status==Observation.Status.VERIFIED);
  ok(restored.events.equals(original.events)&&restored.outbox.equals(original.outbox));
  ok(restored.receipts.equals(original.receipts)&&auditValid(restored));
  ok(Arrays.equals(LongitudinalSnapshotCodec.encode(original),LongitudinalSnapshotCodec.encode(restored)));
 }
 static void signatures(){var f=new ClinicalVerticalBatchTest.F();f.active();f.signed();State restored=decode(f.state());
  ok(restored.notes.get(ClinicalVerticalBatchTest.N).isImmutable());
  ok(restored.notes.get(ClinicalVerticalBatchTest.N).signedAt!=null);
  ok(auditValid(restored));
 }
 static void corrections(){var f=new ClinicalVerticalBatchTest.F();f.active();f.corrected();State restored=decode(f.state());
  ok(restored.observations.get(ClinicalVerticalBatchTest.OC).replaces.equals(ClinicalVerticalBatchTest.O));
  ok(restored.observations.get(ClinicalVerticalBatchTest.O).status==Observation.Status.CORRECTED);
 }
 static void evidence(){State s=rich(),d=decode(s);ok(d.events.size()==s.revision);ok(d.outbox.size()==s.revision);ok(d.receipts.size()==s.revision);
  ok(d.events.get(d.events.size()-1).hash().equals(d.receipts.values().stream().filter(x->x.revision()==d.revision).findFirst().orElseThrow().eventHash()));
 }
 static void corruption(){byte[] plain=LongitudinalSnapshotCodec.encode(rich());
  denied(()->LongitudinalSnapshotCodec.decode(null));denied(()->LongitudinalSnapshotCodec.decode(new byte[1]));
  byte[] bad=plain.clone();bad[0]^=1;final byte[] magic=bad;denied(()->LongitudinalSnapshotCodec.decode(magic));
  bad=plain.clone();bad[7]^=1;final byte[] version=bad;denied(()->LongitudinalSnapshotCodec.decode(version));
  bad=Arrays.copyOf(plain,plain.length-3);final byte[] truncate=bad;denied(()->LongitudinalSnapshotCodec.decode(truncate));
  bad=Arrays.copyOf(plain,plain.length+1);final byte[] trailer=bad;denied(()->LongitudinalSnapshotCodec.decode(trailer));
  bad=plain.clone();bad[bad.length-1]^=1;final byte[] altered=bad;denied(()->LongitudinalSnapshotCodec.decode(altered));
  bad=new byte[4_194_305];final byte[] huge=bad;denied(()->LongitudinalSnapshotCodec.decode(huge));
  State malformed=new State(rich().scope);malformed.admissionReason="\ud800";
  denied(()->LongitudinalSnapshotCodec.encode(malformed));
 }
 static void encryption(){State s=rich();byte[] plain=LongitudinalSnapshotCodec.encode(s);var c=context(s);
  var one=LongitudinalEnvelopeCrypto.seal(c,plain,KEY);var two=LongitudinalEnvelopeCrypto.seal(c,plain,KEY);
  ok(!Arrays.equals(one.value(),two.value()));ok(Arrays.equals(plain,LongitudinalEnvelopeCrypto.open(c,one,KEY)));
  denied(()->LongitudinalEnvelopeCrypto.open(c,one,WRONG));
  var diffFacility=new Scope(s.scope.tenant(),UUID.randomUUID(),s.scope.patient(),s.scope.encounter());
  denied(()->LongitudinalEnvelopeCrypto.open(new LongitudinalEnvelopeCrypto.Context(diffFacility,s.revision,c.keyId()),one,KEY));
  var diffPatient=new Scope(s.scope.tenant(),s.scope.facility(),UUID.randomUUID(),s.scope.encounter());
  denied(()->LongitudinalEnvelopeCrypto.open(new LongitudinalEnvelopeCrypto.Context(diffPatient,s.revision,c.keyId()),one,KEY));
  denied(()->LongitudinalEnvelopeCrypto.open(new LongitudinalEnvelopeCrypto.Context(s.scope,s.revision+1,c.keyId()),one,KEY));
  denied(()->LongitudinalEnvelopeCrypto.open(new LongitudinalEnvelopeCrypto.Context(s.scope,s.revision,"other-kms"),one,KEY));
  byte[] tampered=one.value();tampered[tampered.length-1]^=1;
  denied(()->LongitudinalEnvelopeCrypto.open(c,new LongitudinalEnvelopeCrypto.Envelope(c.keyId(),tampered),KEY));
  byte[] nonce=one.value();nonce[0]^=1;
  denied(()->LongitudinalEnvelopeCrypto.open(c,new LongitudinalEnvelopeCrypto.Envelope(c.keyId(),nonce),KEY));
  denied(()->LongitudinalEnvelopeCrypto.seal(c,plain,new SecretKeySpec(new byte[16],"AES")));
  denied(()->LongitudinalEnvelopeCrypto.seal(c,new byte[0],KEY));
  ok(Arrays.equals(one.value(),new LongitudinalEnvelopeCrypto.Envelope(one.keyId(),one.value()).value()));
  byte[] callerCopy=one.value();callerCopy[0]^=1;ok(!Arrays.equals(callerCopy,one.value()));
 }
 static void crossScope(){State s=rich();byte[] bytes=LongitudinalSnapshotCodec.encode(s);State recovered=LongitudinalSnapshotCodec.decode(bytes);
  ok(recovered.scope.equals(s.scope));var foreign=new Scope(s.scope.tenant(),s.scope.facility(),UUID.randomUUID(),s.scope.encounter());
  denied(()->LongitudinalEnvelopeCrypto.open(new LongitudinalEnvelopeCrypto.Context(foreign,s.revision,"kms-alpha"),
    LongitudinalEnvelopeCrypto.seal(context(s),bytes,KEY),KEY));
 }
 static void run(String name){switch(name){
  case "snapshot" -> roundTrips();case "signed-note" -> signatures();case "observation-corrections" -> corrections();
  case "audit-receipts" -> evidence();case "snapshot-tampering" -> corruption();case "envelope-aad" -> encryption();
  case "scope-integrity" -> crossScope();default -> throw new IllegalArgumentException(name);
 }}
 public static void main(String[] args){if(args.length!=1)throw new IllegalArgumentException("specify case or all");
  if(args[0].equals("all")){for(String name:List.of("snapshot","signed-note","observation-corrections","audit-receipts","snapshot-tampering","envelope-aad","scope-integrity"))run(name);}
  else run(args[0]);System.out.println("PASS "+args[0]+" "+checked+" assertions");
 }
}
