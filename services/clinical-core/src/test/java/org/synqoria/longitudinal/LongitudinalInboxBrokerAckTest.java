package org.synqoria.longitudinal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.synqoria.longitudinal.ClinicalCaseCore.Scope;
import static org.synqoria.longitudinal.LongitudinalInboxSafety.*;
import static org.synqoria.longitudinal.LongitudinalInboxBrokerAck.*;
public final class LongitudinalInboxBrokerAckTest {
  static int cases;
  static void yes(boolean value){cases++;if(!value)throw new AssertionError("ack #"+cases);}
  static void deny(Runnable runnable){cases++;try{runnable.run();throw new AssertionError("unsafe ack admitted");}
    catch(SecurityException expected){}}
  static final Instant NOW=Instant.parse("2026-11-01T12:00:00Z");
  static final Scope S=new Scope(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
  static final UUID EVENT=UUID.randomUUID(),CMD=UUID.randomUUID();
  static final Envelope E=new Envelope(S,EVENT,CMD,1,"b".repeat(64),"note.sign",NOW);
  static final Permit P=new Permit(UUID.randomUUID(),S,NOW.plusSeconds(30),Set.of("note.sign"));
  static final Lease L=new Lease(S,EVENT,UUID.randomUUID(),NOW.plusSeconds(30));
  static AtomicStore store(boolean replay,boolean fail){return new AtomicStore(){
    public boolean hasCommittedReplay(Envelope event){return replay;}
    public CommitProof persistAtomically(Envelope event,UUID token)throws Exception {
      if(fail)throw new Exception("sensitive broker error");
      return new CommitProof(S,EVENT,1,E.eventHash(),true,true,true,true,true);
    }
  };}
  public static void main(String[] args){
    AtomicInteger ack=new AtomicInteger();
    BrokerAck ok=(event,hash,revision)->{yes(event.equals(EVENT));yes(hash.equals(E.eventHash()));
      yes(revision==1);ack.incrementAndGet();};
    yes(receive(store(false,false),P,L,E,L.token(),NOW,ok)==Disposition.ACK_APPLIED);
    yes(ack.get()==1);
    yes(receive(store(true,false),P,L,E,L.token(),NOW,ok)==Disposition.ACK_REPLAY);
    yes(ack.get()==2);
    yes(receive(store(false,true),P,L,E,L.token(),NOW,ok)==Disposition.RETRY_NO_ACK);
    yes(ack.get()==2);
    yes(receive(store(false,false),P,L,E,L.token(),NOW,(a,b,c)->{throw new Exception("private");})
      ==Disposition.COMMITTED_ACK_UNCERTAIN);
    deny(()->receive(store(false,false),P,L,E,UUID.randomUUID(),NOW,ok));
    deny(()->receive(store(false,false),P,L,E,L.token(),NOW.plusSeconds(31),ok));
    yes(ack.get()==2);
    AtomicStore incomplete=new AtomicStore(){public boolean hasCommittedReplay(Envelope event){return false;}
      public CommitProof persistAtomically(Envelope event,UUID token){
       return new CommitProof(S,EVENT,1,E.eventHash(),true,true,true,false,true);}};
    deny(()->receive(incomplete,P,L,E,L.token(),NOW,ok));
    yes(ack.get()==2);
    System.out.println("PASS P07-B0118 broker ACK barrier "+cases+" assertions (fake broker)");
  }
}
