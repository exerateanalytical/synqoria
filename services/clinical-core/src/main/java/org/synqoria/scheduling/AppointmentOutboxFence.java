package org.synqoria.scheduling;
import java.util.*;public final class AppointmentOutboxFence {private AppointmentOutboxFence(){}public static void require(UUID eventId,UUID appointmentId,String type){if(eventId==null||appointmentId==null||type==null||!type.matches("[A-Z_]{3,64}"))throw new IllegalArgumentException("invalid outbox envelope");}}
