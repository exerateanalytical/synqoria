package org.synqoria.scheduling;
public final class AppointmentReplayFenceTest {public static void main(String[]x){AppointmentReplayFence.require("abc","abc");try{AppointmentReplayFence.require("abc","def");throw new AssertionError();}catch(SecurityException ok){}}}
