package org.synqoria.longitudinal;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import static org.synqoria.longitudinal.LongitudinalInboxSafety.*;

/** A message-specific broker ACK barrier; never ACK until trusted DB work has committed.
 * An ACK transport error must be retried and deduped by a database receipt on redelivery.
 * Host must verify broker authentication, delivery identity, and secure callback binding. */
public final class LongitudinalInboxBrokerAck {
  private LongitudinalInboxBrokerAck() {}
  public enum Disposition {ACK_APPLIED, ACK_REPLAY, RETRY_NO_ACK, COMMITTED_ACK_UNCERTAIN}
  public interface BrokerAck {
    /** Broker callback must be bound to this delivery, not an unrelated received message. */
    void acknowledge(UUID eventId,String eventHash,long revision) throws Exception;
  }
  public static Disposition receive(AtomicStore store,Permit permit,Lease lease,Envelope event,
                                    UUID token,Instant now,BrokerAck broker) {
    Objects.requireNonNull(store);Objects.requireNonNull(permit);Objects.requireNonNull(lease);
    Objects.requireNonNull(event);Objects.requireNonNull(token);Objects.requireNonNull(now);
    Objects.requireNonNull(broker);
    ResultKind applied=LongitudinalInboxSafety.consume(store,permit,lease,event,token,now);
    if(applied==ResultKind.RETRY_NO_ACK)return Disposition.RETRY_NO_ACK;
    try {broker.acknowledge(event.eventId(),event.eventHash(),event.revision());}
    catch(Exception e){return Disposition.COMMITTED_ACK_UNCERTAIN;}
    return applied==ResultKind.ACK_REPLAY?Disposition.ACK_REPLAY:Disposition.ACK_APPLIED;
  }
}
