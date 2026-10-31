package org.synqoria.scheduling;
/** Optimistic concurrency fence for a single appointment revision. */
public final class AppointmentRevisionFence {
 private AppointmentRevisionFence(){}
 public static long next(long stored,long expected){if(stored<=0||expected<=0||stored!=expected||stored==Long.MAX_VALUE)throw new IllegalStateException("stale or invalid revision");return stored+1;}
}
