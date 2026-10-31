package org.synqoria.security;
import java.util.UUID;
/** Allowlisted client-safe API failure mapping; never copy raw exception or PHI. */
public record SafeApiProblem(int httpStatus, String code, String title, UUID correlationId) {
 public enum Failure { NOT_DISCLOSED, STALE_VERSION, BUSINESS_CONFLICT, INVALID_SEMANTICS, UNAVAILABLE, RATE_LIMIT }
 public SafeApiProblem {
  if (correlationId==null || httpStatus<400 || httpStatus>599 || code==null || title==null)throw new IllegalArgumentException("Invalid problem");
 }
 public static SafeApiProblem of(Failure failure, UUID correlationId) {
  if(failure==null || correlationId==null)throw new IllegalArgumentException("Invalid failure");
  return switch(failure) {
   case NOT_DISCLOSED -> new SafeApiProblem(404,"RESOURCE_NOT_AVAILABLE","Requested resource not available",correlationId);
   case STALE_VERSION -> new SafeApiProblem(412,"VERSION_PRECONDITION_FAILED","Resource version is no longer current",correlationId);
   case BUSINESS_CONFLICT -> new SafeApiProblem(409,"STATE_CONFLICT","Request conflicts with current state",correlationId);
   case INVALID_SEMANTICS -> new SafeApiProblem(422,"INVALID_REQUEST","Request cannot be processed",correlationId);
   case UNAVAILABLE -> new SafeApiProblem(503,"SERVICE_UNAVAILABLE","Service temporarily unavailable",correlationId);
   case RATE_LIMIT -> new SafeApiProblem(429,"RATE_LIMITED","Request rate limit reached",correlationId);
  };
 }
}
