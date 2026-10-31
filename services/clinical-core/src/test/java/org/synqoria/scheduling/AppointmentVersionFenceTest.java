package org.synqoria.scheduling;
public final class AppointmentVersionFenceTest {public static void main(String[]x){if(AppointmentVersionFence.next(1,1)!=2)throw new AssertionError();try{AppointmentVersionFence.next(1,2);throw new AssertionError();}catch(IllegalStateException ok){}}}
