package org.synqoria.longitudinal;

import java.util.Objects;
import static org.synqoria.longitudinal.LongitudinalInboxSafety.Envelope;
import static org.synqoria.longitudinal.LongitudinalOutboxDelivery.EventRef;

/** Bounded reconciliation of private outbox metadata against consumer inbox metadata.
 * An event ID from the consumer is not proof of a trusted publisher. Transport authenticity
 * and restricted database RLS remain mandatory external gates. */
public final class LongitudinalInboxOutboxParity {
  private LongitudinalInboxOutboxParity() {}
  public static void require(Envelope inbox,EventRef outbox) {
    Objects.requireNonNull(inbox);Objects.requireNonNull(outbox);
    if(!inbox.scope().equals(outbox.scope())||
       !inbox.commandId().equals(outbox.commandId())||
       inbox.revision()!=outbox.revision()||
       !inbox.eventHash().equals(outbox.eventHash())||
       !inbox.eventType().equals(outbox.eventType()))
      throw new SecurityException("clinical inbox/outbox parity unavailable");
  }
  /** A strict metadata-only reconciliation proof; no clinical content in returned state. */
  public static String correlatedKey(Envelope inbox,EventRef outbox) {
    require(inbox,outbox);
    return LongitudinalInboxSafety.replayKey(inbox);
  }
}
