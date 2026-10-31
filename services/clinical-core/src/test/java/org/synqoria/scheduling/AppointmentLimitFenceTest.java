package org.synqoria.scheduling;
public final class AppointmentLimitFenceTest {public static void main(String[]x){AppointmentLimitFence.require(0,1);try{AppointmentLimitFence.require(1,1);throw new AssertionError();}catch(IllegalStateException ok){}}}
