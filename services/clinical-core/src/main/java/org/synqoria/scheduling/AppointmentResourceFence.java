package org.synqoria.scheduling;
import java.util.*; public final class AppointmentResourceFence {private AppointmentResourceFence(){} public static void require(UUID resource,UUID assignedFacility,UUID requestFacility){if(resource==null||assignedFacility==null||requestFacility==null||!assignedFacility.equals(requestFacility))throw new SecurityException("resource facility mismatch");} }
