package org.synqoria.scheduling;
import java.time.*; public final class AppointmentTimeFenceTest {public static void main(String[]x){AppointmentTimeFence.require(Instant.parse("2026-01-01T10:00:00Z"),Instant.parse("2026-01-01T10:30:00Z"));try{AppointmentTimeFence.require(Instant.EPOCH,Instant.EPOCH);throw new AssertionError();}catch(IllegalArgumentException ok){}}}
