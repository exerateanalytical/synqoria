package org.synqoria.scheduling;
import java.util.*; public final class AppointmentActorFenceTest {public static void main(String[]x){AppointmentActorFence.require(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());try{AppointmentActorFence.require(null,UUID.randomUUID(),UUID.randomUUID());throw new AssertionError();}catch(SecurityException ok){}}}
