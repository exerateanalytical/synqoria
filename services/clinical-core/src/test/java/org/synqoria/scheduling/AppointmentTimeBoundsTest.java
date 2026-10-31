package org.synqoria.scheduling;
import java.time.*;
public class AppointmentTimeBoundsTest {
 static int assertions=0;static void denied(Runnable r){try{r.run();throw new AssertionError();}catch(IllegalArgumentException ok){assertions++;}}
 public static void main(String[]args){Instant now=Instant.parse("2026-01-01T10:00:00Z");Duration cap=Duration.ofHours(2);
  AppointmentTimeBounds.requireValid(now,now.plusSeconds(3600),cap);assertions++;
  denied(()->AppointmentTimeBounds.requireValid(now,now,cap));
  denied(()->AppointmentTimeBounds.requireValid(now,now.minusSeconds(1),cap));
  denied(()->AppointmentTimeBounds.requireValid(now,now.plusSeconds(7201),cap));
  denied(()->AppointmentTimeBounds.requireValid(now,now.plusSeconds(1),Duration.ZERO));
  denied(()->AppointmentTimeBounds.requireValid(null,now,cap));
  System.out.println("PASS AppointmentTimeBounds "+assertions);
 }
}
