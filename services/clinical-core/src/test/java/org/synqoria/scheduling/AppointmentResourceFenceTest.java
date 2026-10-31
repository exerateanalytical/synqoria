package org.synqoria.scheduling;
import java.util.*; public final class AppointmentResourceFenceTest {public static void main(String[]x){UUID f=UUID.randomUUID();AppointmentResourceFence.require(UUID.randomUUID(),f,f);try{AppointmentResourceFence.require(UUID.randomUUID(),f,UUID.randomUUID());throw new AssertionError();}catch(SecurityException ok){}}}
