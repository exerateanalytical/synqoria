package org.synqoria.scheduling;
public class AppointmentRevisionFenceTest {public static void main(String[] a){
 if(AppointmentRevisionFence.next(1,1)!=2)throw new AssertionError();
 for(long[] p:new long[][]{{2,1},{0,0},{-1,-1},{Long.MAX_VALUE,Long.MAX_VALUE}}){try{AppointmentRevisionFence.next(p[0],p[1]);throw new AssertionError();}catch(IllegalStateException expected){}}
 System.out.println("AppointmentRevisionFenceTest PASS 5 checks");}}
