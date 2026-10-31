package org.synqoria.security;
import org.synqoria.http.AuditedPatientAccess;
import java.util.*;
/** Double-check response against trusted actor scope after DB commit and before serialization. */
public final class PatientDisclosureFence {
 private PatientDisclosureFence(){}
 public static Optional<PatientRecordEnvelope> release(TrustedAccessContext actor,UUID requested,AuditedPatientAccess.Result committed){
  if(actor==null||requested==null||committed==null||committed.status()!=AuditedPatientAccess.Status.OK)return Optional.empty();
  var p=committed.patient();
  return p!=null&&requested.equals(p.patientId())&&PatientScopePolicy.canReadPatient(actor,p.tenantId(),p.facilityId(),p.patientId())?Optional.of(p):Optional.empty();
 }
}
