package org.synqoria.events;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
/**
 * P01-B0006 in-process semantic reference ONLY. It is NOT a durable idempotency
 * implementation: restart, multi-instance operation, retention and transaction
 * coupling require a reviewed PostgreSQL-backed implementation.
 * Never place PHI or raw request bodies in the key or stored outcome.
 */
public final class IdempotencyGuard {
    public record Scope(UUID tenant, UUID facility, UUID actor, String operation, UUID requestId) {
        public Scope {
            Objects.requireNonNull(tenant);Objects.requireNonNull(facility);Objects.requireNonNull(actor);
            Objects.requireNonNull(requestId);
            if (operation == null || !operation.matches("[a-z][a-z0-9:_-]{2,99}"))
                throw new IllegalArgumentException("Invalid operation");
        }
    }
    public enum Status { NEW, IN_PROGRESS, REPLAY, CONFLICT, UNKNOWN }
    private record Entry(String digest, boolean completed, String receipt) {}
    public record Decision(Status status, String receipt) {}
    private final ConcurrentHashMap<Scope,Entry> entries = new ConcurrentHashMap<>();
    public Decision begin(Scope scope,String sha256Hex) {
        Objects.requireNonNull(scope);
        if (sha256Hex==null || !sha256Hex.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Expected server-computed SHA-256");
        final Status[] status = {Status.NEW};
        Entry entry = entries.compute(scope,(key,existing)->{
            if(existing==null)return new Entry(sha256Hex,false,null);
            if(!existing.digest.equals(sha256Hex)){status[0]=Status.CONFLICT;return existing;}
            status[0]=existing.completed?Status.REPLAY:Status.IN_PROGRESS;
            return existing;
        });
        return new Decision(status[0],status[0]==Status.REPLAY?entry.receipt:null);
    }
    public boolean complete(Scope scope,String sha256Hex,String opaqueReceipt) {
        Objects.requireNonNull(scope);
        if (opaqueReceipt==null || !opaqueReceipt.matches("[a-zA-Z0-9_-]{8,128}")) throw new IllegalArgumentException("Invalid opaque receipt");
        final boolean[] changed={false};
        entries.computeIfPresent(scope,(key,entry)->{
            if(entry.digest.equals(sha256Hex) && !entry.completed){changed[0]=true;return new Entry(entry.digest,true,opaqueReceipt);}
            return entry;
        });
        return changed[0];
    }
    /** In-progress after crash MUST NOT be retried automatically without authoritative reconciliation. */
    public Status unknownOutcome(Scope scope) { var e=entries.get(scope);return e==null?Status.NEW:(e.completed?Status.REPLAY:Status.UNKNOWN); }
}
