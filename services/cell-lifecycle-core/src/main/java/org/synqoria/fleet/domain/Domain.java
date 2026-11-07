package org.synqoria.fleet.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Pure M39 domain: permissions, cell lifecycle, routing signatures, rollout gates and the migration machine. No I/O. */
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
  public static final String FLEET_READ="m39:fleet_read", TENANT_REQUEST="m39:tenant_request", TENANT_APPROVE="m39:tenant_approve", CELL_OPERATE="m39:cell_operate",
   QUALIFY_RECORD="m39:qualify_record", QUALIFY_COMPLETE="m39:qualify_complete", HEALTH_REPORT="m39:health_report", ROUTE_MANAGE="m39:route_manage", ROUTE_READ="m39:route_read",
   RELEASE_MANAGE="m39:release_manage", RELEASE_APPROVE="m39:release_approve", ROLLOUT_OPERATE="m39:rollout_operate", MIGRATION_PLAN="m39:migration_plan",
   MIGRATION_OPERATE="m39:migration_operate", MIGRATION_APPROVE="m39:migration_approve";
  public static final Set<String> ALL=Set.of(FLEET_READ,TENANT_REQUEST,TENANT_APPROVE,CELL_OPERATE,QUALIFY_RECORD,QUALIFY_COMPLETE,HEALTH_REPORT,ROUTE_MANAGE,ROUTE_READ,
   RELEASE_MANAGE,RELEASE_APPROVE,ROLLOUT_OPERATE,MIGRATION_PLAN,MIGRATION_OPERATE,MIGRATION_APPROVE);
 }
 /** tenants empty = platform-wide operator; otherwise the operator is confined to those tenants (the database enforces it too). */
 public record Actor(UUID id,UUID cell,UUID facility,Set<String> grants,Set<UUID> tenants){
  public Actor{grants=Set.copyOf(grants);tenants=Set.copyOf(tenants);}
  public void require(String p){if(!grants.contains(p))throw new Violation("permission_denied");}
  public boolean has(String p){return grants.contains(p);}
  public boolean platformWide(){return tenants.isEmpty();}
  public void requirePlatformWide(){if(!platformWide())throw new Violation("tenant_scope_denied");}
  public void requireTenant(UUID t){if(!platformWide()&&!tenants.contains(t))throw new Violation("tenant_scope_denied");}
 }

 private static final Pattern KEY=Pattern.compile("[a-z][a-z0-9-]{2,47}");
 public static boolean validKey(String k){return k!=null&&KEY.matcher(k).matches();}
 public static String sha256(String s){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}
  catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }

 // ================================================================== B05 lifecycle (mirrors the database trigger)
 public static final Set<String> CELL_STATES=Set.of("PROVISIONING","QUALIFIED","ACTIVE","DRAINING","FENCED","MIGRATING","RETIRED","FAILED");
 private static final Map<String,Set<String>> NEXT=Map.of(
  "PROVISIONING",Set.of("QUALIFIED","FAILED"),"QUALIFIED",Set.of("ACTIVE","FAILED","RETIRED"),"ACTIVE",Set.of("DRAINING","FENCED"),
  "DRAINING",Set.of("ACTIVE","FENCED","RETIRED"),"FENCED",Set.of("ACTIVE","MIGRATING","RETIRED"),"MIGRATING",Set.of("ACTIVE","RETIRED"),"FAILED",Set.of("RETIRED"),"RETIRED",Set.of());
 public static boolean canMove(String from,String to){return NEXT.getOrDefault(from,Set.of()).contains(to);}
 public static final List<String> QUALIFICATION_CHECKS=List.of("DATABASE","NETWORK","IDENTITY","STORAGE","CAPACITY","MONITORING","BACKUP","MIGRATION_COMPAT");

 // ================================================================== B06 routing: signed, fail-closed decisions
 public static final long HEALTH_MAX_AGE_SECONDS=300, DECISION_TTL_SECONDS=60;
 public record Decision(String routeKey,UUID cellId,long placementEpoch,long directoryVersion,Instant issuedAt,Instant expiresAt){
  public String canonical(){return "v1|"+routeKey+"|"+cellId+"|"+placementEpoch+"|"+directoryVersion+"|"+issuedAt.getEpochSecond()+"|"+expiresAt.getEpochSecond();}
 }
 public static String sign(String key,Decision d){
  if(key==null||key.length()<32)throw new IllegalStateException("routing_key_not_configured");
  try{
   Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
   return HexFormat.of().formatHex(mac.doFinal(d.canonical().getBytes(StandardCharsets.UTF_8)));
  }catch(java.security.GeneralSecurityException e){throw new IllegalStateException(e);}
 }
 /** Constant-time verification; also false once expired. */
 public static boolean verify(String key,Decision d,String signature,Instant now){
  if(signature==null||!d.expiresAt().isAfter(now))return false;
  return MessageDigest.isEqual(sign(key,d).getBytes(StandardCharsets.UTF_8),signature.getBytes(StandardCharsets.UTF_8));
 }
 /** Fail closed: only an ACTIVE cell with a recent HEALTHY or DEGRADED observation may receive traffic. Unknown is not healthy. */
 public static boolean routable(String cellState,String healthStatus,Instant observedAt,Instant now){
  if(!"ACTIVE".equals(cellState)||healthStatus==null||observedAt==null)return false;
  if(observedAt.plusSeconds(HEALTH_MAX_AGE_SECONDS).isBefore(now))return false;
  return "HEALTHY".equals(healthStatus)||"DEGRADED".equals(healthStatus);
 }

 // ================================================================== B07 releases
 public static boolean schemaCompatible(int cellSchema,int minSchema,int maxSchema){return cellSchema>=minSchema&&cellSchema<=maxSchema;}
 /** Stage k may start only when every cell of stages < k is DEPLOYED. A stage never skips ahead. */
 public static boolean stageComplete(Collection<String> statuses){return !statuses.isEmpty()&&statuses.stream().allMatch("DEPLOYED"::equals);}

 // ================================================================== B08 migration machine
 public static final List<String> STEPS=List.of("COPY","CATCH_UP","FREEZE","FENCE","VERIFY","SWITCH","OBSERVE","RETIRE");
 public static final long MAX_CATCHUP_LAG_MS=5000;
 /** state before -> state after for each step */
 private static final Map<String,String[]> STEP=Map.of(
  "COPY",new String[]{"PREPARED","COPYING"},"CATCH_UP",new String[]{"COPYING","CAUGHT_UP"},"FREEZE",new String[]{"CAUGHT_UP","FROZEN"},"FENCE",new String[]{"FROZEN","FENCED"},
  "VERIFY",new String[]{"FENCED","VERIFIED"},"SWITCH",new String[]{"VERIFIED","SWITCHED"},"OBSERVE",new String[]{"SWITCHED","OBSERVING"},"RETIRE",new String[]{"OBSERVING","RETIRED"});
 public static String stepFrom(String step){String[] s=STEP.get(step);if(s==null)throw new Violation("invalid_step");return s[0];}
 public static String stepTo(String step){String[] s=STEP.get(step);if(s==null)throw new Violation("invalid_step");return s[1];}
 /** Rollback is possible until the source is retired; after SWITCH it reverses the route (new epoch) and needs an independent approver. */
 public static boolean rollbackNeedsApproval(String state){return Set.of("SWITCHED","OBSERVING").contains(state);}
 public static boolean rollbackPossible(String state){return Set.of("PREPARED","COPYING","CAUGHT_UP","FROZEN","FENCED","VERIFIED","SWITCHED","OBSERVING").contains(state);}
}
