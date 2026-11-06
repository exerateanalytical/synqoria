package org.synqoria.operations;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.synqoria.operations.domain.OperationWorkflow;
import org.synqoria.operations.domain.WorkflowViolation;

/**
 * Independent, revocable and shift-bounded workforce grant authority.
 * The JWT has ALREADY been signature/issuer/audience checked by SecurityFilterChain.
 * It is insufficient alone: current hospital roster approval and revision must match.
 * The runtime DB login can SELECT scoped grants, never INSERT/UPDATE/DELETE them.
 */
@Component
public final class WorkforceAdmission {
 private final JdbcTemplate jdbc;
 private final TransactionTemplate tx;
 public WorkforceAdmission(JdbcTemplate jdbc,PlatformTransactionManager manager){
  this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);
 }
 public OperationWorkflow.Actor requireAuthoritativeGrant(Jwt verifiedJwt) {
  OperationWorkflow.Actor candidate=OperationsActor.fromVerifiedJwt(verifiedJwt);
  Number revision=verifiedJwt.getClaim("synqoria_grant_revision");
  if(revision==null||revision.longValue()<1)
   throw new WorkflowViolation("workforce_revision_required");
  return Objects.requireNonNull(tx.execute(status->{
   String current=jdbc.queryForObject("SELECT current_user",String.class);
   if(!"synqoria_operations_runtime".equals(current))
    throw new WorkflowViolation("database_role_denied");
   jdbc.queryForObject("SELECT set_config('synqoria.cell',?,true)",String.class,
     candidate.cell().toString());
   jdbc.queryForObject("SELECT set_config('synqoria.facility',?,true)",String.class,
     candidate.facility().toString());
   List<Grant> currentGrants=jdbc.query(
    "SELECT grant_revision,permissions,active,shift_from,shift_until,valid_until " +
    "FROM synqoria_operations.workforce_grants WHERE cell_id=? AND facility_id=? AND actor_id=?",
    (rs,i)->new Grant(rs.getLong("grant_revision"),
      rs.getArray("permissions")==null?Set.of():Set.of((String[])rs.getArray("permissions").getArray()),
      rs.getBoolean("active"),
      rs.getTimestamp("shift_from").toInstant(),rs.getTimestamp("shift_until").toInstant(),
      rs.getTimestamp("valid_until").toInstant()),
    candidate.cell(),candidate.facility(),candidate.actor());
   if(currentGrants.size()!=1)throw new WorkflowViolation("workforce_grant_not_current");
   Grant grant=currentGrants.getFirst();
   if(!grant.active||grant.revision!=revision.longValue() ||
      !grant.permissions.containsAll(candidate.permissions()))
    throw new WorkflowViolation("workforce_grant_not_current");
   Instant now=Instant.now();
   if(now.isBefore(grant.shiftFrom)||!now.isBefore(grant.shiftUntil)||
      !now.isBefore(grant.validUntil))
    throw new WorkflowViolation("workforce_shift_inactive");
   // JWT may grant a SUBSET of the current workforce permissions, not a superset.
   return candidate;
  }));
 }
 record Grant(long revision,Set<String>permissions,boolean active,Instant shiftFrom,Instant shiftUntil,Instant validUntil){}
}
