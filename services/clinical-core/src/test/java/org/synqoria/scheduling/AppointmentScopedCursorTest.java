package org.synqoria.scheduling;
import java.util.UUID;
public class AppointmentScopedCursorTest{
 public static void main(String[] args){int n=0;UUID t=UUID.randomUUID(),f=UUID.randomUUID(),p=UUID.randomUUID(),a=UUID.randomUUID();var c=new AppointmentScopedCursor(t,f,p,1,a);c.requireScope(t,f,p);n++;
  for(int i=0;i<3;i++){try{c.requireScope(i==0?UUID.randomUUID():t,i==1?UUID.randomUUID():f,i==2?UUID.randomUUID():p);throw new AssertionError();}catch(SecurityException expected){n++;}}
  for(int i=0;i<5;i++){try{new AppointmentScopedCursor(i==0?null:t,i==1?null:f,i==2?null:p,i==3?0:1,i==4?null:a);throw new AssertionError();}catch(IllegalArgumentException expected){n++;}}
  System.out.println("PASS P06-B0040 assertions="+n);
 }
}