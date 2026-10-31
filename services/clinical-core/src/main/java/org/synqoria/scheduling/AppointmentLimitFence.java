package org.synqoria.scheduling;
public final class AppointmentLimitFence {private AppointmentLimitFence(){} public static void require(int existing,int maximum){if(existing<0||maximum<=0||existing>=maximum)throw new IllegalStateException("appointment capacity exceeded");} }
