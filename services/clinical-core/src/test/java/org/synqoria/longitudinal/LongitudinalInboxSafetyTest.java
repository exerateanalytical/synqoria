package org.synqoria.longitudinal;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.synqoria.longitudinal.ClinicalCaseCore.Scope;
import static org.synqoria.longitudinal.LongitudinalInboxSafety.*;

/** Ten standalone Java 21 policy tests. Synthetic store, not live Kafka or PostgreSQL. */
public final class LongitudinalInboxSafetyTest {
  static int count=0;
  static final Instant NOW=Instant.parse("2026-11-01T08:00:00Z");
  static final Scope S=new Scope(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
  static final Scope OTHER=new Scope(S.tenant(),S.facility(),UUID.randomUUID(),S.encounter());
  static final UUID EVENT=UUID.randomUUID(), CMD=UUID.randomUUID();
  static Envelope event(long rev) {return new Envelope(S,EVENT,CMD,rev,"a".repeat(64),"note.sign",NOW);}
  static Permit permit(){return new Permit(UUID.randomUUID(),S,NOW.plusSeconds(90),Set.of("note.sign"));}
  static Lease lease(){return new Lease(S,EVENT,UUID.randomUUID(),NOW.plusSeconds(90));}
  static void ok(boolean condition){count++;if(!condition)throw new AssertionError("inbox check "+count);}
  static void deny(Runnable r){count++;try{r.run();throw new AssertionError("accepted bad input");}
    catch(SecurityException|IllegalArgumentException|NullPointerException expected) {}}
  static void envelope(){ok(event(1).revision()==1);deny(()->new Envelope(S,EVENT,CMD,0,"a".repeat(64),"note.sign",NOW));
    deny(()->new Envelope(S,EVENT,CMD,1,"ZZZZ","note.sign",NOW));
    deny(()->new Envelope(S,EVENT,CMD,1,"a".repeat(64),"note.sign\npatient",NOW));}
  static void consumerPermit(){Envelope e=event(1);permit().require(e,NOW);
    deny(()->permit().require(new Envelope(OTHER,EVENT,CMD,1,e.eventHash(),e.eventType(),NOW),NOW));
    deny(()->permit().require(e,NOW.plusSeconds(91)));
    deny(()->new Permit(UUID.randomUUID(),S,NOW,Set.of("bad type")));}
  static void dedupe(){Envelope e=event(1);ok(replayKey(e).matches("[0-9a-f]{64}"));ok(replayKey(e).equals(replayKey(e)));
    ok(!replayKey(new Envelope(OTHER,EVENT,CMD,1,e.eventHash(),e.eventType(),NOW)).equals(replayKey(e)));
    ok(!replayKey(event(2)).equals(replayKey(e)));}
  static void sequenceGate(){Envelope e=event(1);ok(sequence(0,null,e)==Sequence.APPLY);
    ok(sequence(1,e.eventHash(),e)==Sequence.REPLAY);deny(()->sequence(0,null,event(2)));
    deny(()->sequence(1,"b".repeat(64),e));deny(()->sequence(2,"a".repeat(64),e));}
  static void leaseFence(){Envelope e=event(1);Lease l=lease();l.require(e,l.token(),NOW);
    deny(()->l.require(e,UUID.randomUUID(),NOW));deny(()->l.require(e,l.token(),NOW.plusSeconds(90)));
    deny(()->l.require(new Envelope(OTHER,EVENT,CMD,1,e.eventHash(),e.eventType(),NOW),l.token(),NOW));}
  static void retries(){ok(retry(1).delay().equals(Duration.ofSeconds(1)));
    ok(retry(11).delay().compareTo(MAX_RETRY)<=0);
    ok(retry(12).action()==RetryAction.PARK&&retry(12).delay().isZero());
    deny(()->retry(0));deny(()->retry(13));}
  static void redrive(){Redrive good=new Redrive(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),S,EVENT,NOW.plusSeconds(120),"INC-2026-00001");
    good.require(event(1),NOW);
    deny(()->new Redrive(good.requester(),good.requester(),good.approverB(),S,EVENT,NOW.plusSeconds(120),"INC-2026-00001"));
    deny(()->good.require(event(1),NOW.plusSeconds(121)));
    deny(()->good.require(new Envelope(OTHER,EVENT,CMD,1,"a".repeat(64),"note.sign",NOW),NOW));}
  static void commit(){Envelope e=event(1);new CommitProof(S,EVENT,1,e.eventHash(),true,true,true,true,true).require(e);
    deny(()->new CommitProof(S,EVENT,1,e.eventHash(),true,true,false,true,true).require(e));
    deny(()->new CommitProof(S,EVENT,1,e.eventHash(),true,true,true,true,false).require(e));
    deny(()->new CommitProof(OTHER,EVENT,1,e.eventHash(),true,true,true,true,true).require(e));}
  static void processor(){Envelope e=event(1);Lease l=lease();AtomicInteger applied=new AtomicInteger();
    AtomicStore store=new AtomicStore(){public boolean hasCommittedReplay(Envelope v){return false;}
      public CommitProof persistAtomically(Envelope v,UUID token){applied.incrementAndGet();
        return new CommitProof(S,EVENT,1,e.eventHash(),true,true,true,true,true);}};
    ok(consume(store,permit(),l,e,l.token(),NOW)==ResultKind.ACK_COMMITTED);ok(applied.get()==1);
    AtomicStore replay=new AtomicStore(){public boolean hasCommittedReplay(Envelope v){return true;}
      public CommitProof persistAtomically(Envelope v,UUID t){throw new AssertionError("replay write");}};
    ok(consume(replay,permit(),l,e,l.token(),NOW)==ResultKind.ACK_REPLAY);
    AtomicStore fail=new AtomicStore(){public boolean hasCommittedReplay(Envelope v){return false;}
      public CommitProof persistAtomically(Envelope v,UUID t) throws Exception {throw new Exception("sensitive data");}};
    ok(consume(fail,permit(),l,e,l.token(),NOW)==ResultKind.RETRY_NO_ACK);
    deny(()->consume(store,permit(),l,e,UUID.randomUUID(),NOW));ok(applied.get()==1);}
  static void receiptPrivacy(){SafeReceipt r=receipt(event(1),ResultKind.ACK_COMMITTED);
    ok(r.code().equals("INBOX_APPLIED"));ok(r.toString().indexOf(S.patient().toString())<0);
    ok(r.toString().indexOf(EVENT.toString())<0);deny(()->new SafeReceipt("PATIENT_ID="+S.patient(),1,"note.sign"));}
  static final List<String> CASES=List.of("envelope","consumer-permit","dedupe","sequence-gate","lease-fence","retries","redrive","commit","processor","receipt-privacy");
  static void run(String x){switch(x){case "envelope"->envelope();case "consumer-permit"->consumerPermit();
    case "dedupe"->dedupe();case "sequence-gate"->sequenceGate();case "lease-fence"->leaseFence();
    case "retries"->retries();case "redrive"->redrive();case "commit"->commit();case "processor"->processor();
    case "receipt-privacy"->receiptPrivacy();default->throw new IllegalArgumentException("scenario");}}
  public static void main(String[] args){if(args.length!=1)throw new IllegalArgumentException("one scenario");
    if(args[0].equals("all")){for(String x:CASES)run(x);}else run(args[0]);
    System.out.println("PASS "+args[0]+" "+count+" assertions (synthetic)");}
}
