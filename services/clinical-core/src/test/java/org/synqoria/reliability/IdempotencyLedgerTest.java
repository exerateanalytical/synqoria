package org.synqoria.reliability;
import java.time.*;import java.util.*;
public final class IdempotencyLedgerTest {
  static int n=0;static void ok(boolean x){if(!x)throw new AssertionError("Idempotency "+n);n++;}
  public static void main(String[] args){
    var ledger=new IdempotencyLedger();var t=UUID.randomUUID();var c=UUID.randomUUID();var now=Instant.parse("2026-10-30T00:00:00Z");
    var a="a".repeat(64);var b="b".repeat(64);
    var initial=ledger.claim(t,c,"patient:create","key-01",a,now,now.plusSeconds(300));
    ok(initial.decision()==IdempotencyLedger.Decision.CLAIMED && initial.ownerToken()!=null);
    ok(ledger.claim(t,c,"patient:create","key-01",a,now,now.plusSeconds(300)).decision()==IdempotencyLedger.Decision.IN_PROGRESS);
    ok(ledger.claim(t,c,"patient:create","key-01",b,now,now.plusSeconds(300)).decision()==IdempotencyLedger.Decision.PAYLOAD_CONFLICT);
    ok(!ledger.complete(t,c,"patient:create","key-01",UUID.randomUUID(),b,now));
    ok(ledger.complete(t,c,"patient:create","key-01",initial.ownerToken(),b,now));
    ok(!ledger.complete(t,c,"patient:create","key-01",initial.ownerToken(),b,now));
    ok(ledger.claim(t,c,"patient:create","key-01",a,now,now.plusSeconds(300)).decision()==IdempotencyLedger.Decision.REPLAY);
    ok(ledger.claim(t,c,"patient:create","key-01",a,now,now.plusSeconds(300)).resultDigest().equals(b));
    ok(ledger.claim(t,c,"patient:create","key-01",a,now.plusSeconds(300),now.plusSeconds(600)).decision()==IdempotencyLedger.Decision.EXPIRED_RECONCILIATION_REQUIRED);
    ok(!ledger.complete(t,c,"patient:create","key-01",initial.ownerToken(),b,now.plusSeconds(350)));
    ok(ledger.claim(UUID.randomUUID(),c,"patient:create","key-01",a,now,now.plusSeconds(300)).decision()==IdempotencyLedger.Decision.CLAIMED);
    ok(ledger.claim(t,UUID.randomUUID(),"patient:create","key-01",a,now,now.plusSeconds(300)).decision()==IdempotencyLedger.Decision.CLAIMED);
    ok(ledger.claim(t,c,"patient:update","key-01",a,now,now.plusSeconds(300)).decision()==IdempotencyLedger.Decision.CLAIMED);
    boolean failed=false;try{ledger.claim(t,c,"patient:create","key-02","free text",now,now.plusSeconds(3));}catch(IllegalArgumentException x){failed=true;}ok(failed);
    failed=false;try{ledger.claim(t,c,"patient:create","key-02",a,now,now);}catch(IllegalArgumentException x){failed=true;}ok(failed);
    System.out.println("PASS: "+n+" scoped idempotency contract assertions (in-memory only)");
  }
}
