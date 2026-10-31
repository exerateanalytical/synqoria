package org.synqoria.scheduling;
import java.util.UUID;
/** Deny cross-cell and cross-patient appointment access by default. */
public final class AppointmentScopeFence {
 private AppointmentScopeFence(){}
 public static void require(UUID actorTenant,UUID actorFacility,UUID permittedPatient,UUID rowTenant,UUID rowFacility,UUID rowPatient){
 if(actorTenant==null||actorFacility==null||permittedPatient==null||rowTenant==null||rowFacility==null||rowPatient==null||!actorTenant.equals(rowTenant)||!actorFacility.equals(rowFacility)||!permittedPatient.equals(rowPatient))throw new SecurityException("appointment scope denied");
 }
}
