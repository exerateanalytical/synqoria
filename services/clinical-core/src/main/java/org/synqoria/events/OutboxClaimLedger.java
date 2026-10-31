package org.synqoria.events;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
/** P01-B0007 in-memory dispatcher claim/fencing reference; NOT durable or DB tested. */
public final class OutboxClaimLedger {
 public record Key(UUID tenant,UUID facility,UUID event) {
  public Key{Objects.requireNonNull(tenant);Objects.requireNonNull(facility);Objects.requireNonNull(event);}
 }
 public record Claim(long fence,Instant leaseUntil,UUID worker) {}
 public enum Outcome { CLAIMED, BUSY, ALREADY_DELIVERED, REJECTED }
 public record Result(Outcome outcome,Claim claim) {}
 private record State(long fence,Instant leaseUntil,UUID worker,boolean delivered) {}
 private final ConcurrentHashMap<Key,State> states=new ConcurrentHashMap<>();
 public Result claim(Key key,UUID worker,Instant now,Instant until) {
  Objects.requireNonNull(key);
  if(worker==null||now==null||until==null||!until.isAfter(now)||until.isAfter(now.plusSeconds(300)))return new Result(Outcome.REJECTED,null);
  final Outcome[] outcome={Outcome.CLAIMED};
  State s=states.compute(key,(k,old)->{
   if(old!=null && old.delivered){outcome[0]=Outcome.ALREADY_DELIVERED;return old;}
   if(old!=null && now.isBefore(old.leaseUntil)){outcome[0]=Outcome.BUSY;return old;}
   return new State(old==null?1:Math.addExact(old.fence,1),until,worker,false);
  });
  return new Result(outcome[0],outcome[0]==Outcome.CLAIMED?new Claim(s.fence,s.leaseUntil,s.worker):null);
 }
 /** Late workers cannot acknowledge after lease expiration or after another worker takes over. */
 public boolean delivered(Key key,UUID worker,long fence,Instant now) {
  if(key==null||worker==null||now==null||fence<=0)return false;
  final boolean[] done={false};
  states.computeIfPresent(key,(k,s)->{
   if(!s.delivered && s.worker.equals(worker)&&s.fence==fence&&now.isBefore(s.leaseUntil)){
    done[0]=true;return new State(s.fence,s.leaseUntil,s.worker,true);
   }return s;
  });return done[0];
 }
}
