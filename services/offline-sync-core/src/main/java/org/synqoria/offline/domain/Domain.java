package org.synqoria.offline.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/** Pure M38 domain: permissions and the offline command verdict. No I/O, no clock, no database. */
public final class Domain {
 private Domain(){}

 public static class Violation extends RuntimeException {
  private final List<String> details;
  public Violation(String code){this(code,List.of());}
  public Violation(String code,List<String> details){super(code,null,false,false);this.details=List.copyOf(details);}
  public String code(){return getMessage();}
  public List<String> details(){return details;}
 }

 public static final class Perm {
  private Perm(){}
  public static final String DEVICE_MANAGE="m38:device_manage", SYNC_SUBMIT="m38:sync_submit", SYNC_READ="m38:sync_read", CONTRACT_MANAGE="m38:contract_manage",
   CONTRACT_APPROVE="m38:contract_approve", HEAD_PUBLISH="m38:head_publish", CONFLICT_RESOLVE="m38:conflict_resolve", TRANSFER_PROPOSE="m38:transfer_propose",
   TRANSFER_OPERATE="m38:transfer_operate", TRANSFER_APPROVE="m38:transfer_approve", HISTORY_READ="m38:history_read";
  public static final Set<String> ALL=Set.of(DEVICE_MANAGE,SYNC_SUBMIT,SYNC_READ,CONTRACT_MANAGE,CONTRACT_APPROVE,HEAD_PUBLISH,CONFLICT_RESOLVE,TRANSFER_PROPOSE,TRANSFER_OPERATE,TRANSFER_APPROVE,HISTORY_READ);
 }
 public record Actor(UUID id,UUID cell,UUID facility,Set<String> grants){
  public Actor{grants=Set.copyOf(grants);}
  public void require(String p){if(!grants.contains(p))throw new Violation("permission_denied");}
  public boolean has(String p){return grants.contains(p);}
 }

 private static final Pattern KEY=Pattern.compile("[a-z][a-z0-9_.-]{1,47}");
 private static final Pattern OP=Pattern.compile("[a-z][a-z0-9_.-]{2,63}");
 public static boolean validKey(String k){return k!=null&&KEY.matcher(k).matches();}
 public static boolean validOp(String k){return k!=null&&OP.matcher(k).matches();}
 public static String sha256(String s){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}
  catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }

 // ================================================================== B05: the verdict on one queued command
 public record Cmd(String operationType,long baseVersion,long baseEpoch,Set<String> fields,Instant createdLocal,Instant expiresAt){}
 public record Contract(boolean approved,boolean offlineAllowed,boolean requiresSafetyGate,boolean mergeable,int maxQueueAgeMinutes){}
 public record Head(long epoch,long version,boolean frozen){}
 public enum Kind {APPLY,MERGE,CONFLICT,REJECT,RETRY,EXPIRED}
 public record Verdict(Kind kind,String code,String conflictKind,List<String> differing){
  static Verdict of(Kind k,String code){return new Verdict(k,code,null,List.of());}
 }
 public static final Duration CLOCK_SKEW=Duration.ofMinutes(5);

 /**
  * Decides what may happen to a queued command. Order matters: expiry, legality offline, record existence, ownership fencing,
  * freezes, then version comparison. A stale or overlapping change is NEVER applied silently: it becomes a conflict.
  * changedSinceBase: fields changed by revisions after the command's base version.
  */
 public static Verdict evaluate(Cmd c,Contract k,Head h,Set<String> changedSinceBase,Instant now){
  if(!c.expiresAt().isAfter(now))return Verdict.of(Kind.EXPIRED,"command_expired");
  if(c.createdLocal().isAfter(now.plus(CLOCK_SKEW)))return Verdict.of(Kind.REJECT,"clock_skew");
  if(k==null||!k.approved())return Verdict.of(Kind.REJECT,"no_offline_contract");
  if(c.createdLocal().plus(Duration.ofMinutes(k.maxQueueAgeMinutes())).isBefore(now))return Verdict.of(Kind.EXPIRED,"queue_age_exceeded");
  if(k.requiresSafetyGate())return Verdict.of(Kind.REJECT,"safety_gate_required");   // a required clinical gate cannot be satisfied offline
  if(!k.offlineAllowed())return Verdict.of(Kind.REJECT,"operation_not_offline");
  if(h==null)return Verdict.of(Kind.REJECT,"unknown_record");
  if(c.baseEpoch()<h.epoch())return new Verdict(Kind.CONFLICT,"stale_writer_fenced","OWNER_FENCED",List.of());
  if(c.baseEpoch()>h.epoch())return Verdict.of(Kind.REJECT,"invalid_epoch");
  if(h.frozen())return Verdict.of(Kind.RETRY,"record_frozen");
  if(c.baseVersion()>h.version())return Verdict.of(Kind.REJECT,"invalid_base");
  if(c.baseVersion()==h.version())return Verdict.of(Kind.APPLY,"applied");
  Set<String> overlap=new TreeSet<>(changedSinceBase);overlap.retainAll(c.fields());
  if(k.mergeable()&&!c.fields().isEmpty()&&overlap.isEmpty())return Verdict.of(Kind.MERGE,"applied_merged");
  if(k.mergeable()&&!overlap.isEmpty())return new Verdict(Kind.CONFLICT,"incompatible_changes","INCOMPATIBLE",List.copyOf(overlap));
  return new Verdict(Kind.CONFLICT,"stale_base","STALE_BASE",List.copyOf(new TreeSet<>(changedSinceBase)));
 }
 public static String nextHash(String headHash,String payloadHash){return sha256(headHash+":"+payloadHash);}
}
