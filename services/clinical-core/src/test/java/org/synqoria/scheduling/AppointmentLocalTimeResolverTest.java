package org.synqoria.scheduling;
import java.time.*;
public class AppointmentLocalTimeResolverTest {
 static int n=0;static void deny(Runnable r){try{r.run();throw new AssertionError("expected denial");}catch(IllegalArgumentException expected){n++;}}
 public static void main(String[]a){ZoneId zone=ZoneId.of("Europe/Paris");LocalDateTime ambiguous=LocalDateTime.of(2026,10,25,2,30);
  Instant first=AppointmentLocalTimeResolver.resolve(ambiguous,zone,ZoneOffset.ofHours(2));n++;
  Instant second=AppointmentLocalTimeResolver.resolve(ambiguous,zone,ZoneOffset.ofHours(1));n++;
  if(!second.equals(first.plusSeconds(3600)))throw new AssertionError();n++;
  deny(()->AppointmentLocalTimeResolver.resolve(LocalDateTime.of(2026,3,29,2,30),zone,ZoneOffset.ofHours(1)));
  deny(()->AppointmentLocalTimeResolver.resolve(ambiguous,zone,ZoneOffset.UTC));
  deny(()->AppointmentLocalTimeResolver.resolve(null,zone,ZoneOffset.UTC));
  System.out.println("PASS B0090 "+n);
 }
}
