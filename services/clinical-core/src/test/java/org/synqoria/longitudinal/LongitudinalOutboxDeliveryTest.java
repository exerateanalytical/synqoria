package org.synqoria.longitudinal;

import java.time.*;
import java.util.*;
import static org.synqoria.longitudinal.ClinicalCaseCore.Scope;
import static org.synqoria.longitudinal.LongitudinalOutboxDelivery.*;

/** Per-scenario, runnable engineering checks; fake store models lease semantics, not live PostgreSQL. */
public final class LongitudinalOutboxDeliveryTest {
  private static int assertions;
  private static void ok(boolean x) {assertions++;if(!x)throw new AssertionError("outbox assertion #"+assertions);}
  private static void denied(Runnable r) {assertions++;try{r.run();throw new AssertionError("unsafe delivery accepted");}
      catch(IllegalArgumentException|SecurityException expected){}}
  static final Instant NOW=Instant.parse("2026-10-31T16:45:00Z");
  static final Scope SCOPE=new Scope(UUID.fromString("a150c728-dd41-44b8-b05f-eaac63c00001"),
      UUID.fromString("a150c728-dd41-44b8-b05f-eaac63c00002"),
      UUID.fromString("a150c728-dd41-44b8-b05f-eaac63c00003"),
      UUID.fromString("a150c728-dd41-44b8-b05f-eaac63c00004"));
  static final Scope OTHER=new Scope(SCOPE.tenant(),SCOPE.facility(),UUID.randomUUID(),SCOPE.encounter());
  static EventRef event(int revision) {return new EventRef(SCOPE, UUID.nameUUIDFromBytes(("case-"+revision).getBytes()),
      revision,"a".repeat(64),"note.sign");}
  static Permit permit(){return new Permit(UUID.randomUUID(),SCOPE,NOW.plusSeconds(3600));}

  static final class StoreFake implements Store {
    static final class Record {
      final EventRef event;UUID lease;Instant leaseUntil,retryAt;int attempts;boolean published,parked;
      Record(EventRef event){this.event=event;}
    }
    final Map<Integer,Record> rows=new TreeMap<>();boolean ignoreAck=false,forceFailClaim=false;
    int claims,acks,defers;
    StoreFake(int size){for(int i=1;i<=size;i++)rows.put(i,new Record(event(i)));}
    @Override public synchronized Optional<Claimed> claim(Scope scope,UUID dispatcher,Instant now) {
      if(forceFailClaim)throw new SecurityException("restricted store");
      if(!SCOPE.equals(scope))throw new SecurityException("scope");
      claims++;
      for(Record row:rows.values()){
        if(row.published)continue;
        if(row.parked || row.attempts>=MAX_ATTEMPTS) return Optional.empty();
        if(row.lease!=null && now.isBefore(row.leaseUntil))return Optional.empty();
        if(row.retryAt!=null && now.isBefore(row.retryAt))return Optional.empty();
        row.lease=UUID.randomUUID();row.leaseUntil=now.plus(LEASE);row.attempts++;
        return Optional.of(new Claimed(row.event,row.lease,row.attempts));
      }
      return Optional.empty();
    }
    @Override public synchronized boolean acknowledge(Claimed claimed,Instant now) {
      acks++;Record r=rows.get((int)claimed.event().revision());
      if(ignoreAck)return false;
      if(r==null||r.published||r.parked||!r.event.equals(claimed.event())||
         !Objects.equals(r.lease,claimed.leaseToken())||!now.isBefore(r.leaseUntil))return false;
      r.published=true;r.lease=null;return true;
    }
    @Override public synchronized boolean defer(Claimed claimed,Instant now,Duration backoff) {
      defers++;Record r=rows.get((int)claimed.event().revision());
      if(r==null||r.published||r.parked||!r.event.equals(claimed.event())||
         !Objects.equals(r.lease,claimed.leaseToken())||!now.isBefore(r.leaseUntil))return false;
      r.lease=null;if(r.attempts>=MAX_ATTEMPTS)r.parked=true;
      else r.retryAt=now.plus(backoff);return true;
    }
  }
  static void metadata(){
    ok(event(1).eventHash().length()==64);
    denied(()->new EventRef(SCOPE,UUID.randomUUID(),0,"a".repeat(64),"note.sign"));
    denied(()->new EventRef(SCOPE,UUID.randomUUID(),1,"wrong","note.sign"));
    denied(()->new EventRef(SCOPE,UUID.randomUUID(),1,"a".repeat(64),"note.sign\nvalue=HIV"));
    denied(()->new Claimed(event(1),UUID.randomUUID(),13));
    ok(Arrays.stream(EventRef.class.getRecordComponents()).map(x->x.getName()).noneMatch(x->
      x.equals("body")||x.equals("patientName")||x.equals("observationValue")));
  }
  static void permitScope(){
    StoreFake f=new StoreFake(1);int[] published={0};
    denied(()->dispatchOnce(f,e->published[0]++,permit(),OTHER,NOW));
    denied(()->dispatchOnce(f,e->published[0]++,new Permit(UUID.randomUUID(),SCOPE,NOW),SCOPE,NOW));
    ok(f.claims==0 && published[0]==0);
    ok(dispatchOnce(f,e->published[0]++,permit(),SCOPE,NOW)==Outcome.PUBLISHED);
    ok(published[0]==1);
  }
  static void ordered(){
    StoreFake f=new StoreFake(3);List<Long> published=new ArrayList<>();Permit p=permit();
    ok(dispatchOnce(f,e->published.add(e.revision()),p,SCOPE,NOW)==Outcome.PUBLISHED);
    ok(dispatchOnce(f,e->published.add(e.revision()),p,SCOPE,NOW)==Outcome.PUBLISHED);
    ok(dispatchOnce(f,e->published.add(e.revision()),p,SCOPE,NOW)==Outcome.PUBLISHED);
    ok(dispatchOnce(f,e->published.add(e.revision()),p,SCOPE,NOW)==Outcome.IDLE);
    ok(published.equals(List.of(1L,2L,3L))&&f.acks==3);
  }
  static void concurrent(){
    StoreFake f=new StoreFake(2);Claimed first=f.claim(SCOPE,UUID.randomUUID(),NOW).orElseThrow();
    ok(first.event().revision()==1);
    ok(f.claim(SCOPE,UUID.randomUUID(),NOW).isEmpty());
    ok(f.acknowledge(first,NOW));
    ok(f.claim(SCOPE,UUID.randomUUID(),NOW).orElseThrow().event().revision()==2);
  }
  static void ackFence(){
    StoreFake f=new StoreFake(1);
    Claimed original=f.claim(SCOPE,UUID.randomUUID(),NOW).orElseThrow();
    Claimed next=f.claim(SCOPE,UUID.randomUUID(),NOW.plusSeconds(31)).orElseThrow();
    ok(!f.acknowledge(original,NOW.plusSeconds(31)));
    ok(!f.acknowledge(new Claimed(new EventRef(SCOPE,original.event().commandId(),1,"b".repeat(64),"note.sign"),next.leaseToken(),2),NOW.plusSeconds(31)));
    ok(f.acknowledge(next,NOW.plusSeconds(31)));
    ok(!f.acknowledge(next,NOW.plusSeconds(32)));
  }
  static void brokerFailure(){
    StoreFake f=new StoreFake(2);Permit p=permit();
    ok(dispatchOnce(f,e->{throw new Exception("private broker secret");},p,SCOPE,NOW)==Outcome.DEFERRED);
    ok(f.rows.get(1).retryAt.equals(NOW.plusSeconds(1)));
    ok(dispatchOnce(f,e->{throw new Exception("private broker secret");},p,SCOPE,NOW)==Outcome.IDLE);
    ok(dispatchOnce(f,e->{},p,SCOPE,NOW.plusSeconds(1))==Outcome.PUBLISHED);
    ok(f.rows.get(1).published&&!f.rows.get(2).published);
  }
  static void ackUncertain(){
    StoreFake f=new StoreFake(1);Permit p=permit();Set<String> consumerIds=new HashSet<>();int[] brokerCalls={0};
    f.ignoreAck=true;
    ok(dispatchOnce(f,e->{consumerIds.add(e.eventHash()+e.commandId());brokerCalls[0]++;},p,SCOPE,NOW)==Outcome.UNCERTAIN_ACK);
    ok(!f.rows.get(1).published);
    f.ignoreAck=false;
    ok(dispatchOnce(f,e->{consumerIds.add(e.eventHash()+e.commandId());brokerCalls[0]++;},p,SCOPE,NOW.plusSeconds(31))==Outcome.PUBLISHED);
    ok(consumerIds.size()==1 && brokerCalls[0]==2);
  }
  static void park(){
    StoreFake f=new StoreFake(2);Permit p=permit();Instant at=NOW;
    for(int i=1;i<=12;i++){
      final Instant t=at;
      Outcome response=dispatchOnce(f,e->{throw new Exception("transient");},p,SCOPE,t);
      ok(response==(i==12?Outcome.PARKED:Outcome.DEFERRED));
      ok(f.rows.get(1).attempts==i);
      at=at.plus(backoff(i));
    }
    ok(f.rows.get(1).parked);
    ok(dispatchOnce(f,e->{throw new AssertionError("parked delivery must not publish");},p,SCOPE,at)==Outcome.IDLE);
    ok(!f.rows.get(2).published);
  }
  static void bounds(){
    ok(backoff(1).equals(Duration.ofSeconds(1))&&backoff(2).equals(Duration.ofSeconds(2)));
    ok(backoff(10).compareTo(MAX_BACKOFF)<=0&&backoff(12).compareTo(MAX_BACKOFF)<=0);
    denied(()->backoff(0));denied(()->backoff(13));
    StoreFake f=new StoreFake(1);f.forceFailClaim=true;
    denied(()->dispatchOnce(f,e->{},permit(),SCOPE,NOW));
  }
  public static final List<String> CASES=List.of("metadata","permit-scope","ordered","concurrent","ack-fence", "broker-failure","ack-uncertain","park","bounds");
  public static void main(String[] argv){if(argv.length!=1)throw new IllegalArgumentException("scenario");
    if(argv[0].equals("all")){for(String c:CASES)run(c);}else run(argv[0]);
    System.out.println("PASS outbox "+argv[0]+" "+assertions+" assertions (synthetic store)");
  }
  private static void run(String s){switch(s){case "metadata"->metadata();case "permit-scope"->permitScope();
    case "ordered"->ordered();case "concurrent"->concurrent();case "ack-fence"->ackFence();
    case "broker-failure"->brokerFailure();case "ack-uncertain"->ackUncertain();case "park"->park();
    case "bounds"->bounds();default->throw new IllegalArgumentException(s);}}
}
