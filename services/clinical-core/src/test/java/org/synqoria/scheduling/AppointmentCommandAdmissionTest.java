package org.synqoria.scheduling;
import org.synqoria.security.*;import java.time.*;import java.util.*;
public final class AppointmentCommandAdmissionTest {
 static int checks;static void ok(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static void deny(Runnable r){try{r.run();throw new AssertionError("expected denial");}catch(SecurityException ex){ok("appointment operation denied".equals(ex.getMessage()));}}
 static TrustedClinicalAccessEvidence evidence(UUID t,UUID f,UUID a,UUID p,Instant now,boolean mfa,boolean shift,boolean consent){
  return new TrustedClinicalAccessEvidence(t,f,a,p,now.minusSeconds(5),now.plusSeconds(60),mfa,true,shift,true,consent,true,true,Set.of("appointment:manage"));}
 public static void main(String[] args){
  UUID t=UUID.randomUUID(),f=UUID.randomUUID(),a=UUID.randomUUID(),p=UUID.randomUUID();Instant now=Instant.parse("2026-10-30T10:00:00Z");
  var identity=new TrustedAccessContext(t,f,a,Set.of("appointment:manage"),Set.of(p));
  var scope=AppointmentCommandAdmission.require(identity,evidence(t,f,a,p,now,true,true,true),p,now);
  ok(scope.tenantId().equals(t));ok(scope.facilityId().equals(f));ok(scope.actorId().equals(a));ok(scope.patientId().equals(p));
  deny(()->AppointmentCommandAdmission.require(identity,evidence(t,f,a,p,now,false,true,true),p,now));
  deny(()->AppointmentCommandAdmission.require(identity,evidence(t,f,a,p,now,true,false,true),p,now));
  deny(()->AppointmentCommandAdmission.require(identity,evidence(t,f,a,p,now,true,true,false),p,now));
  deny(()->AppointmentCommandAdmission.require(identity,evidence(t,f,a,UUID.randomUUID(),now,true,true,true),p,now));
  deny(()->AppointmentCommandAdmission.require(identity,evidence(t,f,a,p,now,true,true,true),UUID.randomUUID(),now));
  deny(()->AppointmentCommandAdmission.require(identity,evidence(t,f,a,p,now,true,true,true),p,now.plusSeconds(90)));
  deny(()->AppointmentCommandAdmission.require(new TrustedAccessContext(t,f,a,Set.of(),Set.of(p)),evidence(t,f,a,p,now,true,true,true),p,now));
  System.out.println("PASS AppointmentCommandAdmissionTest assertions="+checks);
 }
}
