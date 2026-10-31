package org.synqoria.scheduling;
public final class AppointmentReleaseFenceTest {public static void main(String[]x){try{AppointmentReleaseFence.require(false,false,false,false);throw new AssertionError();}catch(SecurityException ok){} AppointmentReleaseFence.require(true,true,true,true);}}
