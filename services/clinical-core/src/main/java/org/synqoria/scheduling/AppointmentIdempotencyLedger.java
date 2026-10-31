package org.synqoria.scheduling;
import java.util.*;
/** In-memory reference only: production must use a unique database constraint. */
public final class AppointmentIdempotencyLedger {
 public enum Outcome { FIRST_SEEN, REPLAY_SAME_COMMAND, CONFLICT_DIFFERENT_COMMAND }
 private record Key(UUID tenant,UUID facility,UUID actor,String key){}
 private final Map<Key,String> fingerprints=new HashMap<>();
 public synchronized Outcome checkAndRecord(UUID tenant,UUID facility,UUID actor,String key,String digest){
  if(tenant==null||facility==null||actor==null||key==null||!key.matches("[A-Za-z0-9_-]{8,128}")||digest==null||!digest.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("invalid idempotency envelope");
  Key k=new Key(tenant,facility,actor,key);String old=fingerprints.putIfAbsent(k,digest);
  return old==null?Outcome.FIRST_SEEN:old.equals(digest)?Outcome.REPLAY_SAME_COMMAND:Outcome.CONFLICT_DIFFERENT_COMMAND;
 }
 public synchronized int size(){return fingerprints.size();}
}
