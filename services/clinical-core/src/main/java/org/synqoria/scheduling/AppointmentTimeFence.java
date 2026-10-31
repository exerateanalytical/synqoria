package org.synqoria.scheduling;
import java.time.*; public final class AppointmentTimeFence {private AppointmentTimeFence(){} public static void require(Instant start,Instant end){if(start==null||end==null||!end.isAfter(start)||Duration.between(start,end).toMinutes()>1440)throw new IllegalArgumentException("invalid appointment interval");} }
