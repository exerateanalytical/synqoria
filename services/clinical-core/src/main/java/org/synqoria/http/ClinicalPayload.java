package org.synqoria.http;
import org.synqoria.security.PatientRecordEnvelope;
import org.synqoria.facility.FacilityBinding;
/** Serialize only validated safe application DTOs; no arbitrary string concatenation. */
public final class ClinicalPayload {
 private ClinicalPayload(){}
 public static String patient(PatientRecordEnvelope p){
  if(p==null||!java.util.Set.of("verified","provisional","merged").contains(p.identityStatus())||p.revision()<1)throw new IllegalArgumentException("Unsafe patient DTO");
  return "{\"patientId\":\""+p.patientId()+"\",\"identityStatus\":\""+p.identityStatus()+"\",\"revision\":"+p.revision()+"}";
 }
 public static String facility(FacilityBinding f){
  if(f==null||!"ACTIVE".equals(f.status())||f.revision()<1)throw new IllegalArgumentException("Inactive facility DTO");
  return "{\"facilityId\":\""+f.facilityId()+"\",\"revision\":"+f.revision()+"}";
 }
}
