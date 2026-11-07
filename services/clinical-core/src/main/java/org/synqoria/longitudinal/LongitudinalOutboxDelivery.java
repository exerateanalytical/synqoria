package org.synqoria.longitudinal;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import static org.synqoria.longitudinal.ClinicalCaseCore.Scope;

/**
 * Bounded, per-encounter ordered, at-least-once clinical event delivery.
 * No note text, observation value, or free-text clinical content leaves the cell.
 * Dispatchers MUST be authenticated by the host service before creating a Permit.
 * Real PostgreSQL RLS and broker trust are mandatory deployment dependencies.
 */
public final class LongitudinalOutboxDelivery {
    private LongitudinalOutboxDelivery() {}
    public static final int MAX_ATTEMPTS = 12;
    public static final Duration LEASE = Duration.ofSeconds(30);
    public static final Duration MAX_BACKOFF = Duration.ofMinutes(5);

    public record Permit(UUID dispatcherId, Scope scope, Instant expiresAt) {
        public Permit { Objects.requireNonNull(dispatcherId); Objects.requireNonNull(scope); Objects.requireNonNull(expiresAt); }
        void check(Scope expected, Instant now) {
            if (!scope.equals(expected) || !now.isBefore(expiresAt))
                throw new SecurityException("clinical dispatch unavailable");
        }
    }
    /** No patient text or clinical values. Still confidential metadata: use an authorized private broker. */
    public record EventRef(Scope scope, UUID commandId, long revision, String eventHash, String eventType) {
        public EventRef {
            Objects.requireNonNull(scope); Objects.requireNonNull(commandId);
            if (revision < 1 || revision > 20000 || !hex64(eventHash) || eventType == null ||
                eventType.length() < 1 || eventType.length() > 96 ||
                !eventType.matches("[a-z][a-z0-9_.-]*"))
                throw new IllegalArgumentException("invalid outbox event metadata");
        }
    }
    public record Claimed(EventRef event, UUID leaseToken, int attempt) {
        public Claimed {
            Objects.requireNonNull(event); Objects.requireNonNull(leaseToken);
            if (attempt < 1 || attempt > MAX_ATTEMPTS) throw new IllegalArgumentException("invalid attempt");
        }
    }
    public interface Store {
        /** Must claim only earliest unpublished event for scope under a scope-locked DB transaction. */
        Optional<Claimed> claim(Scope scope, UUID dispatcherId, Instant now);
        /** Must CAS match scope, lease token, revision and digest, with unexpired lease. */
        boolean acknowledge(Claimed claimed, Instant now);
        /** Transient failure: release lease with bounded backoff or park after MAX_ATTEMPTS. */
        boolean defer(Claimed claimed, Instant now, Duration retryDelay);
    }
    public interface Publisher {
        /** Send to a private authenticated broker using eventHash as consumer de-duplication ID. */
        void publish(EventRef event) throws Exception;
    }
    public enum Outcome { IDLE, PUBLISHED, UNCERTAIN_ACK, DEFERRED, PARKED }
    private static boolean hex64(String h) { return h != null && h.matches("[0-9a-f]{64}"); }
    public static Duration backoff(int attempt) {
        if (attempt < 1 || attempt > MAX_ATTEMPTS) throw new IllegalArgumentException("attempt");
        long seconds = Math.min(MAX_BACKOFF.toSeconds(), 1L << Math.min(8, attempt - 1));
        return Duration.ofSeconds(seconds);
    }
    /**
     * Does not hold a DB transaction while publishing. A broker success followed by a lost ACK
     * remains UNCERTAIN: the same event may be re-sent; consumers MUST dedupe by eventHash.
     * The host must treat broker transport exceptions as transient and must not log clinical PHI.
     */
    public static Outcome dispatchOnce(Store store, Publisher publisher, Permit permit,
                                       Scope scope, Instant now) {
        Objects.requireNonNull(store); Objects.requireNonNull(publisher); Objects.requireNonNull(permit);
        Objects.requireNonNull(scope); Objects.requireNonNull(now);
        permit.check(scope, now);
        Optional<Claimed> found = store.claim(scope, permit.dispatcherId(), now);
        if (found.isEmpty()) return Outcome.IDLE;
        Claimed claim = found.get();
        if (!scope.equals(claim.event().scope())) throw new SecurityException("dispatch scope mismatch");
        try {
            publisher.publish(claim.event());
        } catch (Exception deliveryFailure) {
            // Never leak the exception message (could contain patient details or broker credentials).
            boolean deferred = store.defer(claim, now, backoff(claim.attempt()));
            return deferred ? (claim.attempt() == MAX_ATTEMPTS ? Outcome.PARKED : Outcome.DEFERRED)
                            : Outcome.UNCERTAIN_ACK;
        }
        return store.acknowledge(claim, now) ? Outcome.PUBLISHED : Outcome.UNCERTAIN_ACK;
    }
}
