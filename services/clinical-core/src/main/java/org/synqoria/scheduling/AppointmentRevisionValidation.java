package org.synqoria.scheduling;
/** Rejects stale or overflowing appointment revisions. */
public final class AppointmentRevisionValidation {
 private AppointmentRevisionValidation(){}
 public static long next(long stored,long expected){if(stored<=0||expected!=stored||stored==Long.MAX_VALUE)throw new IllegalStateException("Stale or invalid revision");return stored+1;}
}
