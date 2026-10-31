package org.synqoria.registration;
import org.synqoria.security.TrustedAccessContext;
import java.util.*;
/** Reviewer-only, bounded and scoped search plan; no generic patient enumeration or SQL string interpolation. */
public final class DuplicateCandidateQuery {
 private DuplicateCandidateQuery(){}
 public record Plan(String sql,List<Object> parameters){}
 public static Optional<Plan> plan(TrustedAccessContext reviewer,UUID tenant,UUID facility,byte[] keyedLookupToken,int limit){
  if(reviewer==null||tenant==null||facility==null||keyedLookupToken==null||keyedLookupToken.length!=32||limit<1||limit>20
   ||!reviewer.tenantId().equals(tenant)||!reviewer.facilityId().equals(facility)
   ||!reviewer.capabilities().contains("patient:identity-review"))return Optional.empty();
  // This is a query contract only. No lookup index or live duplicate matching is asserted.
  String sql="SELECT patient_id, candidate_revision FROM patient_identity_lookup WHERE tenant_id = ? AND facility_id = ? AND lookup_hmac = ? ORDER BY candidate_revision DESC LIMIT ?";
  return Optional.of(new Plan(sql,List.of(tenant,facility,keyedLookupToken.clone(),limit)));
 }
}
