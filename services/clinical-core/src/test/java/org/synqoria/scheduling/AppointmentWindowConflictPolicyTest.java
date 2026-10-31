package org.synqoria.scheduling;
import java.time.*;
public class AppointmentWindowConflictPolicyTest {
 static int n=0;static void yes(boolean v){if(!v)throw new AssertionError();n++;}static void no(boolean v){if(v)throw new AssertionError();n++;}
 static void deny(Runnable r){try{r.run();throw new AssertionError();}catch(IllegalArgumentException e){n++;}}
 public static void main(String[]a){Instant t=Instant.EPOCH;
 yes(AppointmentWindowConflictPolicy.overlaps(t,t.plusSeconds(60),t.plusSeconds(30),t.plusSeconds(90)));
 no(AppointmentWindowConflictPolicy.overlaps(t,t.plusSeconds(60),t.plusSeconds(60),t.plusSeconds(90)));
 yes(AppointmentWindowConflictPolicy.overlaps(t,t.plusSeconds(60),t,t.plusSeconds(60)));
 deny(()->AppointmentWindowConflictPolicy.overlaps(t,t,t,t.plusSeconds(1)));
 deny(()->AppointmentWindowConflictPolicy.overlaps(null,t,t,t.plusSeconds(1)));
 System.out.println("PASS B0091 "+n);}
}
