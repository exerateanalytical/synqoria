package org.synqoria.longitudinal;
import java.time.Instant;
import java.util.*;
import static org.synqoria.longitudinal.ClinicalCaseCore.Scope;
import static org.synqoria.longitudinal.LongitudinalInboxSafety.Envelope;
import static org.synqoria.longitudinal.LongitudinalOutboxDelivery.EventRef;
public final class LongitudinalInboxOutboxParityTest {
  static int count;
  static void yes(boolean x){count++;if(!x)throw new AssertionError("parity #"+count);}
  static void deny(Runnable r){count++;try{r.run();throw new AssertionError("unsafe parity");}
     catch(SecurityException expected){}}
  static final Scope S=new Scope(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
  static final UUID CMD=UUID.randomUUID(),EV=UUID.randomUUID();
  static final Envelope E=new Envelope(S,EV,CMD,4,"a".repeat(64),"note.sign",Instant.parse("2026-11-01T12:00:00Z"));
  static EventRef out(Scope scope,UUID command,long rev,String hash,String type){return new EventRef(scope,command,rev,hash,type);}
  public static void main(String[] args){
    EventRef matching=out(S,CMD,4,E.eventHash(),E.eventType());
    yes(LongitudinalInboxOutboxParity.correlatedKey(E,matching).equals(LongitudinalInboxSafety.replayKey(E)));
    deny(()->LongitudinalInboxOutboxParity.require(E,out(new Scope(S.tenant(),S.facility(),UUID.randomUUID(),S.encounter()),CMD,4,E.eventHash(),E.eventType())));
    deny(()->LongitudinalInboxOutboxParity.require(E,out(S,UUID.randomUUID(),4,E.eventHash(),E.eventType())));
    deny(()->LongitudinalInboxOutboxParity.require(E,out(S,CMD,5,E.eventHash(),E.eventType())));
    deny(()->LongitudinalInboxOutboxParity.require(E,out(S,CMD,4,"b".repeat(64),E.eventType())));
    deny(()->LongitudinalInboxOutboxParity.require(E,out(S,CMD,4,E.eventHash(),"note.amend")));
    yes(!LongitudinalInboxOutboxParity.correlatedKey(E,matching).contains(S.patient().toString()));
    System.out.println("PASS P07-B0119 outbox/inbox parity "+count+" assertions");
  }
}
