package org.synqoria.operations;

import java.time.Instant;
import java.util.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.synqoria.operations.domain.OperationWorkflow;
import org.synqoria.operations.domain.WorkflowViolation;

/** JWT must be issuer/audience/signature validated by Spring Security before use. */
public final class OperationsActor {
 private OperationsActor(){}
 public static OperationWorkflow.Actor fromVerifiedJwt(Jwt jwt){
  if(jwt==null)throw new WorkflowViolation("identity_denied");
  try{
   UUID actor=UUID.fromString(jwt.getSubject());
   UUID cell=UUID.fromString(jwt.getClaimAsString("synqoria_cell"));
   UUID facility=UUID.fromString(jwt.getClaimAsString("synqoria_facility"));
   List<String> grants=jwt.getClaimAsStringList("synqoria_permissions");
   if(grants==null||grants.isEmpty()||grants.size()>128)throw new WorkflowViolation("grant_denied");
   Set<String> distinct=new HashSet<>(grants);
   if(distinct.size()!=grants.size()||distinct.stream().anyMatch(
      v->v==null||!v.matches("m(21|22|23|25|27|29|31|37):[a-z_]{1,40}")))
    throw new WorkflowViolation("grant_denied");
   Number expires=jwt.getClaim("synqoria_grant_expires");
   if(expires==null||expires.longValue()<=Instant.now().getEpochSecond()||
      expires.longValue()>Instant.now().getEpochSecond()+900)
    throw new WorkflowViolation("grant_expired");
   return new OperationWorkflow.Actor(actor,cell,facility,distinct);
  }catch(IllegalArgumentException e){throw new WorkflowViolation("identity_denied");}
 }
}
