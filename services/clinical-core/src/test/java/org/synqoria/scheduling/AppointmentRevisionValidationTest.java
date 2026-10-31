package org.synqoria.scheduling;
public final class AppointmentRevisionValidationTest {
 public static void main(String[] args){if(AppointmentRevisionValidation.next(1,1)!=2)throw new AssertionError();
  for(long[] v:new long[][]{{1,0},{0,0},{Long.MAX_VALUE,Long.MAX_VALUE},{3,2}}){try{AppointmentRevisionValidation.next(v[0],v[1]);throw new AssertionError();}catch(IllegalStateException expected){}}
  System.out.println("PASS five revision cases");
 }
}
