package org.synqoria.scheduling;
import java.util.*; public final class AppointmentActorFence {private AppointmentActorFence(){} public static void require(UUID actor,UUID tenant,UUID facility){if(actor==null||tenant==null||facility==null)throw new SecurityException("missing identity context");} }
