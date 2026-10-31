package org.synqoria.scheduling;
import java.time.Instant;
/** Fail closed for a patient's conflicting bookings even across different resources. */
public final class AppointmentPatientConflictPolicy {
 private AppointmentPatientConflictPolicy() {}
 public static void requireNoOverlap(String tenantA,String patientA,Instant startA,Instant endA,String tenantB,String patientB,Instant startB,Instant endB) {
  if(blank(tenantA)||blank(tenantB)||blank(patientA)||blank(patientB)) throw new IllegalArgumentException("missing patient scope");
  boolean overlap=AppointmentWindowConflictPolicy.overlaps(startA,endA,startB,endB);
  if(tenantA.equals(tenantB)&&patientA.equals(patientB)&&overlap) throw new IllegalArgumentException("patient appointment conflict");
 }
 private static boolean blank(String v){return v==null||v.isBlank();}
}
