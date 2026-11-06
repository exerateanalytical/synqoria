package org.synqoria.operations;

import org.springframework.security.oauth2.jwt.Jwt;
import org.synqoria.operations.domain.WorkflowViolation;
import org.springframework.stereotype.Component;

/**
 * Clinical/operations staff grant revocation cannot be inferred from JWT alone.
 * This bean is deliberately DENY unless replaced by a reviewed authoritative
 * facility/shift roster lookup, with tests and signed integration evidence.
 * Its presence makes the API startable but not clinically writable.
 */
@Component
public class WorkforceAdmission {
 public void requireAuthoritativeGrant(Jwt jwt){
  throw new WorkflowViolation("workforce_grant_source_not_integrated");
 }
}
