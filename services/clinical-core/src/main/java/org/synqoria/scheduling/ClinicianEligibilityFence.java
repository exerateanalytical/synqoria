package org.synqoria.scheduling;
import java.time.*;import java.util.*;
/** Must be backed by live credential, shift and revocation authority before booking. */
public final class ClinicianEligibilityFence {
 public record Evidence(UUID clinicianId,SchedulingScope scope,Set<String> competencies,Instant shiftStart,Instant shiftEnd,long grantVersion,boolean active,boolean revoked){
 public Evidence {if(clinicianId==null||scope==null||competencies==null||shiftStart==null||shiftEnd==null||grantVersion<1||!shiftStart.isBefore(shiftEnd))throw new IllegalArgumentException("Invalid staff evidence");competencies=Set.copyOf(competencies);}
 }
 public static boolean eligible(Evidence e,SchedulingScope scope,String required,AppointmentWindow window,long authoritativeGrantVersion){
 return e!=null&&scope!=null&&window!=null&&required!=null&&!required.isBlank()&&e.scope().equals(scope)&&e.active()&&!e.revoked()&&authoritativeGrantVersion>0&&e.grantVersion()==authoritativeGrantVersion&&e.competencies().contains(required)&&!e.shiftStart().isAfter(window.start())&&!e.shiftEnd().isBefore(window.end());
 }
 private ClinicianEligibilityFence(){}
}
