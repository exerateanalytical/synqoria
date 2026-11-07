package org.synqoria.longitudinal;
import java.util.*;
/** Bounded, token-based delivery reservations. A crash must cause external host cleanup. */
public final class LongitudinalInboxBackpressure {
  private LongitudinalInboxBackpressure(){}
  public static final class Window {
    private final int limit;private final Set<UUID> active=new HashSet<>();
    public Window(int max){if(max<1||max>256)throw new IllegalArgumentException("capacity");limit=max;}
    public synchronized Optional<UUID> acquire(){
      if(active.size()>=limit)return Optional.empty();UUID t=UUID.randomUUID();active.add(t);return Optional.of(t);
    }
    public synchronized void release(UUID ticket){if(ticket==null||!active.remove(ticket))
      throw new SecurityException("inbox reservation unavailable");}
    public synchronized int outstanding(){return active.size();}
    public synchronized void revokeAll(){active.clear();}
  }
}
