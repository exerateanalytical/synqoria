package org.synqoria.reliability;
import java.util.*;
/** In-memory projection guard prototype. Not durable and not an exactly-once messaging claim. */
public final class InboxSequenceGuard {
 public enum Decision { ACCEPT_NEXT, DUPLICATE_OR_STALE, GAP_REQUIRES_REPLAY }
 private record Stream(UUID tenant,UUID aggregate,String consumer) {}
 private final Map<Stream,Long> lastVersion = new HashMap<>();
 public synchronized Decision accept(UUID tenant,UUID aggregate,String consumer,long version){
  if(tenant==null||aggregate==null||consumer==null||!consumer.matches("[a-zA-Z0-9_.-]{1,96}")||version<1)throw new IllegalArgumentException("Invalid inbox stream");
  var key=new Stream(tenant,aggregate,consumer);long last=lastVersion.getOrDefault(key,0L);
  if(version<=last)return Decision.DUPLICATE_OR_STALE;
  if(version!=last+1)return Decision.GAP_REQUIRES_REPLAY;
  lastVersion.put(key,version);return Decision.ACCEPT_NEXT;
 }
 public synchronized long checkpoint(UUID tenant,UUID aggregate,String consumer){return lastVersion.getOrDefault(new Stream(tenant,aggregate,consumer),0L);}
}
