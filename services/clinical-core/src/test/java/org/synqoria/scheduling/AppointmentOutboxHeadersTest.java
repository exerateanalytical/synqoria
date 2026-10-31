package org.synqoria.scheduling;
import java.util.*;
public class AppointmentOutboxHeadersTest {
 public static void main(String[] args){
  UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();var m=AppointmentOutboxHeaders.create(a,b,c,1);int n=0;
  if(m.size()!=5||!m.get("event_id").equals(a.toString()))throw new AssertionError();n++;
  for(int i=0;i<4;i++){try{AppointmentOutboxHeaders.create(i==0?null:a,i==1?null:b,i==2?null:c,i==3?0:1);throw new AssertionError();}catch(IllegalArgumentException expected){n++;}}
  try{m.put("patient_name","unsafe");throw new AssertionError();}catch(UnsupportedOperationException expected){n++;}
  System.out.println("PASS P06-B0036 assertions="+n);
 }
}
