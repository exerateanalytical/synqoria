package org.synqoria.scheduling;
import java.time.*;
public final class AppointmentTimeBounds {
 private AppointmentTimeBounds(){}
 public static void requireValid(Instant start,Instant end,Duration maximum){
  if(start==null||end==null||maximum==null||maximum.isNegative()||maximum.isZero())throw new IllegalArgumentException("invalid appointment bounds");
  if(!end.isAfter(start)||Duration.between(start,end).compareTo(maximum)>0)throw new IllegalArgumentException("appointment window denied");
 }
}
