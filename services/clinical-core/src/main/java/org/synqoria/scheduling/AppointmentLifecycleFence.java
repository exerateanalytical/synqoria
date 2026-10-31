package org.synqoria.scheduling;
import java.util.*;
/** Conservative M02 appointment lifecycle; terminal states cannot be silently reopened. */
public final class AppointmentLifecycleFence {
 public enum State {REQUESTED,CONFIRMED,ARRIVED,FULFILLED,CANCELLED,NO_SHOW}
 public static boolean mayTransition(State from,State to){
 if(from==null||to==null||from==to)return false;
 return switch(from){case REQUESTED -> to==State.CONFIRMED||to==State.CANCELLED;
 case CONFIRMED -> to==State.ARRIVED||to==State.CANCELLED||to==State.NO_SHOW;
 case ARRIVED -> to==State.FULFILLED||to==State.CANCELLED;
 default -> false;};
 }
 public static void require(State from,State to,String reason){
 if(!mayTransition(from,to)||(to==State.CANCELLED&&(reason==null||reason.isBlank())))throw new IllegalStateException("Appointment transition denied");
 }
 private AppointmentLifecycleFence(){}
}
