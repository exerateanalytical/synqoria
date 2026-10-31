package org.synqoria.scheduling;
public final class AppointmentVersionFence {private AppointmentVersionFence(){}public static long next(long expected,long actual){if(expected<1||actual!=expected||actual==Long.MAX_VALUE)throw new IllegalStateException("revision conflict");return actual+1;}}
