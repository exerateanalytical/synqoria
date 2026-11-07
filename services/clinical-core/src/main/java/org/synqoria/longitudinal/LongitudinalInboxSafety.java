package org.synqoria.longitudinal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import static org.synqoria.longitudinal.ClinicalCaseCore.Scope;

/**
 * Dependency-free policy boundary for a future authenticated Kafka consumer and transactional
 * PostgreSQL inbox. No listener, privileged role, production key, or clinical write is provided.
 * Caller MUST enforce permits via a trusted identity service, and all Store writes in one DB TX.
 * Metadata itself is confidential: never log Scope, Envelope, or exception messages.
 */
public final class LongitudinalInboxSafety {
  private LongitudinalInboxSafety() {}
  public static final int MAX_ATTEMPTS = 12;
  public static final Duration MAX_RETRY = Duration.ofMinutes(5);
  private static boolean hex64(String v) {return v!=null && v.matches("[0-9a-f]{64}");}
  private static void require(boolean ok) {if(!ok) throw new SecurityException("clinical inbox unavailable");}

  /** P07-B0105: single immutable, bounded metadata-only contract; no note text or values. */
  public record Envelope(Scope scope,UUID eventId,UUID commandId,long revision,
                         String eventHash,String eventType,Instant occurredAt) {
    public Envelope {
      Objects.requireNonNull(scope);Objects.requireNonNull(eventId);Objects.requireNonNull(commandId);
      Objects.requireNonNull(occurredAt);
      if(revision<1||revision>20000||!hex64(eventHash)||eventType==null||
         !eventType.matches("[a-z][a-z0-9_.-]{0,95}"))
        throw new IllegalArgumentException("invalid inbox metadata");
    }
  }
  /** P07-B0106: permit MUST come from a trusted host identity and grant verifier. */
  public record Permit(UUID consumerId,Scope scope,Instant expiresAt,Set<String> allowedTypes) {
    public Permit {
      Objects.requireNonNull(consumerId);Objects.requireNonNull(scope);
      Objects.requireNonNull(expiresAt);allowedTypes=Set.copyOf(allowedTypes);
      if(allowedTypes.isEmpty()||allowedTypes.size()>32||allowedTypes.stream().anyMatch(t->
          t==null||!t.matches("[a-z][a-z0-9_.-]{0,95}")))
        throw new IllegalArgumentException("invalid inbox grant");
    }
    public void require(Envelope event,Instant now) {
      Objects.requireNonNull(event);Objects.requireNonNull(now);
      LongitudinalInboxSafety.require(scope.equals(event.scope())&&now.isBefore(expiresAt)&&
         allowedTypes.contains(event.eventType()));
    }
  }
  /** P07-B0107: collision-resistant inbox deduplication identity across all hospital scopes. */
  public static String replayKey(Envelope event) {
    Objects.requireNonNull(event);
    String data=String.join("\u001f",event.scope().tenant().toString(),
      event.scope().facility().toString(),event.scope().patient().toString(),
      event.scope().encounter().toString(),event.eventId().toString(),
      event.commandId().toString(),Long.toString(event.revision()),event.eventHash());
    try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
      .digest(data.getBytes(StandardCharsets.UTF_8)));}
    catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
  }
  /** P07-B0108: reject missing revisions and conflicting replays; no out-of-order apply. */
  public enum Sequence {APPLY, REPLAY}
  public static Sequence sequence(long committedRevision,String committedHash,Envelope incoming) {
    Objects.requireNonNull(incoming);
    if(committedRevision<0||committedRevision>20000)throw new IllegalArgumentException("revision");
    if(committedRevision==0 && committedHash!=null)throw new IllegalArgumentException("genesis hash");
    if(committedRevision>0&&!hex64(committedHash))throw new IllegalArgumentException("history hash");
    if(incoming.revision()==committedRevision+1)return Sequence.APPLY;
    if(incoming.revision()==committedRevision&&
       Objects.equals(incoming.eventHash(),committedHash))return Sequence.REPLAY;
    throw new SecurityException("clinical inbox revision unavailable");
  }
  /** P07-B0109: claim lease token + event + scope + time must all match before mutation. */
  public record Lease(Scope scope,UUID eventId,UUID token,Instant expiresAt) {
    public Lease {Objects.requireNonNull(scope);Objects.requireNonNull(eventId);
      Objects.requireNonNull(token);Objects.requireNonNull(expiresAt);}
    public void require(Envelope event,UUID presentedToken,Instant now) {
      LongitudinalInboxSafety.require(scope.equals(event.scope())&&eventId.equals(event.eventId())&&
         token.equals(presentedToken)&&now.isBefore(expiresAt));
    }
  }
  /** P07-B0110: bounded exponential retry and definitive parking (never silent drop). */
  public enum RetryAction {RETRY, PARK}
  public record Retry(RetryAction action,Duration delay) {}
  public static Retry retry(int attempt) {
    if(attempt<1||attempt>MAX_ATTEMPTS)throw new IllegalArgumentException("attempt");
    if(attempt==MAX_ATTEMPTS)return new Retry(RetryAction.PARK,Duration.ZERO);
    return new Retry(RetryAction.RETRY,Duration.ofSeconds(Math.min(300,1L<<Math.min(attempt-1,8))));
  }
  /** P07-B0111: four-eyes time-boxed redrive; host verifies reviewer roles independently. */
  public record Redrive(UUID requester,UUID approverA,UUID approverB,Scope scope,UUID eventId,
                        Instant expiresAt,String incidentRef) {
    public Redrive {
      Objects.requireNonNull(requester);Objects.requireNonNull(approverA);Objects.requireNonNull(approverB);
      Objects.requireNonNull(scope);Objects.requireNonNull(eventId);Objects.requireNonNull(expiresAt);
      if(requester.equals(approverA)||requester.equals(approverB)||approverA.equals(approverB)||
         incidentRef==null||!incidentRef.matches("[A-Z0-9-]{8,64}"))
        throw new IllegalArgumentException("invalid independent redrive evidence");
    }
    public void require(Envelope event,Instant now) {
      LongitudinalInboxSafety.require(scope.equals(event.scope())&&eventId.equals(event.eventId())&&
        now.isBefore(expiresAt)&&expiresAt.isBefore(now.plus(Duration.ofMinutes(16))));
    }
  }
  /** P07-B0112: no broker ACK until every correlated DB action is durably committed. */
  public record CommitProof(Scope scope,UUID eventId,long revision,String eventHash,
                            boolean inbox,boolean projection,boolean audit,boolean outbox,boolean committed) {
    public CommitProof {Objects.requireNonNull(scope);Objects.requireNonNull(eventId);
      if(revision<1||!hex64(eventHash))throw new IllegalArgumentException("invalid commit proof");}
    public void require(Envelope incoming) {
      LongitudinalInboxSafety.require(scope.equals(incoming.scope())&&eventId.equals(incoming.eventId())&&
        revision==incoming.revision()&&eventHash.equals(incoming.eventHash())&&
        inbox&&projection&&audit&&outbox&&committed);
    }
  }
  /** P07-B0113: results distinguish committed apply, safe replay and retryable failure. */
  public enum ResultKind {ACK_COMMITTED, ACK_REPLAY, RETRY_NO_ACK}
  public interface AtomicStore {
    /** Must check DB RLS scope + dedupe + sequence atomically and return proof after commit. */
    CommitProof persistAtomically(Envelope event,UUID leaseToken) throws Exception;
    /** Return true only when the complete existing receipt matches event and its digest. */
    boolean hasCommittedReplay(Envelope event) throws Exception;
  }
  public static ResultKind consume(AtomicStore store,Permit permit,Lease lease,
                                   Envelope event,UUID token,Instant now) {
    Objects.requireNonNull(store);Objects.requireNonNull(permit);Objects.requireNonNull(lease);
    permit.require(event,now);lease.require(event,token,now);
    try {
      if(store.hasCommittedReplay(event))return ResultKind.ACK_REPLAY;
      CommitProof proof=store.persistAtomically(event,token);
      if(proof==null)return ResultKind.RETRY_NO_ACK;
      proof.require(event);return ResultKind.ACK_COMMITTED;
    } catch(SecurityException e) {throw e;}
      catch(Exception e) {return ResultKind.RETRY_NO_ACK;}
  }
  /** P07-B0114: no identifiers, note text, symptoms, or free-text exception messages in log receipt. */
  public record SafeReceipt(String code,long revision,String eventType) {
    public SafeReceipt {
      if(code==null||!code.matches("INBOX_(APPLIED|REPLAY|RETRY|PARKED)"))
        throw new IllegalArgumentException("receipt code");
      if(revision<1||revision>20000||eventType==null||!eventType.matches("[a-z][a-z0-9_.-]{0,95}"))
        throw new IllegalArgumentException("receipt metadata");
    }
  }
  public static SafeReceipt receipt(Envelope event,ResultKind outcome) {
    Objects.requireNonNull(event);Objects.requireNonNull(outcome);
    String code=switch(outcome) {case ACK_COMMITTED->"INBOX_APPLIED";
      case ACK_REPLAY->"INBOX_REPLAY";case RETRY_NO_ACK->"INBOX_RETRY";};
    return new SafeReceipt(code,event.revision(),event.eventType());
  }
}
