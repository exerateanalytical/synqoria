package org.synqoria.scheduling;
import java.util.*;public final class AppointmentPatientFenceTest {public static void main(String[]x){UUID p=UUID.randomUUID();AppointmentPatientFence.require(p,p);try{AppointmentPatientFence.require(p,UUID.randomUUID());throw new AssertionError();}catch(SecurityException ok){}}}
