package org.synqoria.scheduling;
public class AppointmentAuditSequenceTest{
 static int n=0;static void deny(long p,long s){try{AppointmentAuditSequence.next(p,s);throw new AssertionError();}catch(IllegalStateException expected){n++;}}
 public static void main(String[]a){if(AppointmentAuditSequence.next(0,1)!=1)throw new AssertionError();n++;if(AppointmentAuditSequence.next(9,10)!=10)throw new AssertionError();n++;deny(3,3);deny(3,5);deny(-1,0);deny(Long.MAX_VALUE,Long.MIN_VALUE);System.out.println("PASS AppointmentAuditSequence "+n);}
}
