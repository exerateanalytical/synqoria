package org.synqoria.scheduling;
public class AppointmentStateCodecTest {public static void main(String[] a){
 for(String s:new String[]{"REQUESTED","CONFIRMED","ARRIVED","FULFILLED","CANCELLED","NO_SHOW"}) if(!AppointmentStateCodec.decode(s).equals(s))throw new AssertionError();
 for(String s:new String[]{"confirmed","UNKNOWN",""," CONFIRMED"}) {try{AppointmentStateCodec.decode(s);throw new AssertionError();}catch(IllegalArgumentException expected){}}
 if(!AppointmentStateCodec.active("ARRIVED")||AppointmentStateCodec.active("CANCELLED"))throw new AssertionError();
 System.out.println("AppointmentStateCodecTest PASS 12 checks");}}
