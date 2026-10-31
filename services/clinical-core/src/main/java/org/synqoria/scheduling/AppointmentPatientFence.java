package org.synqoria.scheduling;
import java.util.*;public final class AppointmentPatientFence {private AppointmentPatientFence(){}public static void require(UUID requested,UUID permitted){if(requested==null||permitted==null||!requested.equals(permitted))throw new SecurityException("patient access denied");}}
