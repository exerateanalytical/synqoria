package org.synqoria.scheduling;
import java.util.*;public final class AppointmentOutboxFenceTest {public static void main(String[]x){UUID u=UUID.randomUUID();AppointmentOutboxFence.require(u,u,"APPOINTMENT_UPDATED");try{AppointmentOutboxFence.require(u,u,"unsafe");throw new AssertionError();}catch(IllegalArgumentException ok){}}}
