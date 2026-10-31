package org.synqoria.scheduling;
import java.time.*;
public class AppointmentBookingHorizonPolicyTest {
 static int n=0;static void deny(Runnable r){try{r.run();throw new AssertionError();}catch(IllegalArgumentException e){n++;}}
 public static void main(String[]a){Instant now=Instant.EPOCH;Duration horizon=Duration.ofDays(30);
 AppointmentBookingHorizonPolicy.requireWithin(now,now,horizon);n++;
 AppointmentBookingHorizonPolicy.requireWithin(now.plus(horizon),now,horizon);n++;
 deny(()->AppointmentBookingHorizonPolicy.requireWithin(now.minusSeconds(1),now,horizon));
 deny(()->AppointmentBookingHorizonPolicy.requireWithin(now.plus(horizon).plusSeconds(1),now,horizon));
 deny(()->AppointmentBookingHorizonPolicy.requireWithin(now,now,Duration.ofSeconds(-1)));
 System.out.println("PASS B0092 "+n);}
}
