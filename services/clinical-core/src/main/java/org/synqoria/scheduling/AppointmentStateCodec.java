package org.synqoria.scheduling;
import java.util.Set;
/** Strict database state decoding; never interpret unknown state as bookable. */
public final class AppointmentStateCodec {
 private static final Set<String> ALLOWED=Set.of("REQUESTED","CONFIRMED","ARRIVED","FULFILLED","CANCELLED","NO_SHOW");
 private AppointmentStateCodec(){}
 public static String decode(String raw){if(raw==null || !ALLOWED.contains(raw)) throw new IllegalArgumentException("invalid appointment lifecycle");return raw;}
 public static boolean active(String raw){String state=decode(raw);return state.equals("CONFIRMED")||state.equals("ARRIVED");}
}
