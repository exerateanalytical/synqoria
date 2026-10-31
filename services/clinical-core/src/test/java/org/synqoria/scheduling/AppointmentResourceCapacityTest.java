package org.synqoria.scheduling;
public class AppointmentResourceCapacityTest{
 static int n=0;static void ok(boolean b){if(!b)throw new AssertionError();n++;}
 static void denied(int c,int o,int r){try{AppointmentResourceCapacity.requireAvailable(c,o,r);throw new AssertionError();}catch(IllegalArgumentException expected){n++;}}
 public static void main(String[]a){AppointmentResourceCapacity.requireAvailable(3,1,2);ok(true);denied(0,0,1);denied(2,2,1);denied(2,-1,1);denied(2,1,0);denied(2,3,1);System.out.println("PASS AppointmentResourceCapacity "+n);}
}
