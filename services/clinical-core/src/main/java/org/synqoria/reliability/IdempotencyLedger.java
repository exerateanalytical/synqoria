package org.synqoria.reliability;
import java.time.Instant;
import java.util.*;
/** In-memory protocol reference only. Production must use durable, atomic DB claims,
 * business-level deduplication after retention and transactional outcome persistence. */
public final class IdempotencyLedger {
    public enum Decision { CLAIMED, IN_PROGRESS, REPLAY, PAYLOAD_CONFLICT, EXPIRED_RECONCILIATION_REQUIRED }
    public record Claim(Decision decision, UUID ownerToken, String resultDigest) {}
    private record Key(UUID tenantId, UUID callerId, String operation, String idempotencyKey) {}
    private record Entry(String payloadHash, UUID ownerToken, Instant expiresAt, String resultDigest) {}
    private final Map<Key,Entry> entries = new HashMap<>();
    public synchronized Claim claim(UUID tenant, UUID caller, String operation, String key, String payloadHash, Instant now, Instant expiresAt) {
        if (tenant==null || caller==null || now==null || expiresAt==null || !expiresAt.isAfter(now)
            || !valid(operation,128) || !valid(key,128) || !sha(payloadHash)) throw new IllegalArgumentException("Invalid idempotency claim");
        var k=new Key(tenant,caller,operation,key);
        var current=entries.get(k);
        if (current!=null) {
            if (!current.payloadHash.equals(payloadHash)) return new Claim(Decision.PAYLOAD_CONFLICT,null,null);
            if (!now.isBefore(current.expiresAt)) return new Claim(Decision.EXPIRED_RECONCILIATION_REQUIRED,null,null);
            return new Claim(current.resultDigest==null ? Decision.IN_PROGRESS : Decision.REPLAY,null,current.resultDigest);
        }
        var token=UUID.randomUUID(); entries.put(k,new Entry(payloadHash,token,expiresAt,null));
        return new Claim(Decision.CLAIMED,token,null);
    }
    public synchronized boolean complete(UUID tenant, UUID caller, String operation, String key, UUID ownerToken, String resultDigest, Instant trustedNow) {
        if (tenant==null || caller==null || ownerToken==null || trustedNow==null || !valid(operation,128) || !valid(key,128) || !sha(resultDigest)) return false;
        var k=new Key(tenant,caller,operation,key); var entry=entries.get(k);
        if(entry==null || !entry.ownerToken.equals(ownerToken) || entry.resultDigest!=null || !trustedNow.isBefore(entry.expiresAt))return false;
        entries.put(k,new Entry(entry.payloadHash,entry.ownerToken,entry.expiresAt,resultDigest)); return true;
    }
    private static boolean valid(String text,int limit){return text!=null && !text.isBlank() && text.length()<=limit && !text.chars().anyMatch(Character::isISOControl);}
    private static boolean sha(String s){return s!=null && s.matches("[a-f0-9]{64}");}
}
