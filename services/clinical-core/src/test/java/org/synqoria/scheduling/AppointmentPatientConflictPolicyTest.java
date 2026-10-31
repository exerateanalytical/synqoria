package org.synqoria.scheduling;
import java.time.*;
public class AppointmentPatientConflictPolicyTest {
 static int n=0;static void deny(Runnable r){try{r.run();throw new AssertionError();}catch(IllegalArgumentException e){n++;}}
 public static void main(String[]a){Instant t=Instant.EPOCH;Instant end=t.plusSeconds(3600);
 AppointmentPatientConflictPolicy.requireNoOverlap("t","p",t,end,"t","other",t,end);n++;
 AppointmentPatientConflictPolicy.requireNoOverlap("t","p",t,end,"other","p",t,end);n++;
 AppointmentPatientConflictPolicy.requireNoOverlap("t","p",t,end,"t","p",end,end.plusSeconds(60));n++;
 deny(()->AppointmentPatientConflictPolicy.requireNoOverlap("t","p",t,end,"t","p",t,end));
 deny(()->AppointmentPatientConflictPolicy.requireNoOverlap("t","",t,end,"t","p",t,end));
 System.out.println("PASS B0093 "+n);}
}
