package org.synqoria.scheduling;
import java.util.*;
public class AppointmentOutboxEventValidatorTest{
 public static void main(String[] args){int n=0;UUID u=UUID.randomUUID();Map<String,String> m=AppointmentOutboxHeaders.create(u,UUID.randomUUID(),UUID.randomUUID(),1);AppointmentOutboxEventValidator.require(m);n++;
  for(String key:new String[]{"event_id","tenant_id","facility_id","revision","schema_version"}){
   var invalid=new HashMap<>(m);invalid.remove(key);try{AppointmentOutboxEventValidator.require(invalid);throw new AssertionError();}catch(IllegalArgumentException expected){n++;}
  }
  var leak=new HashMap<>(m);leak.put("patient_name","private");try{AppointmentOutboxEventValidator.require(leak);throw new AssertionError();}catch(IllegalArgumentException expected){n++;}
  for(String bad:new String[]{"0","-1","01","not-a-number"}){var invalid=new HashMap<>(m);invalid.put("revision",bad);try{AppointmentOutboxEventValidator.require(invalid);throw new AssertionError();}catch(IllegalArgumentException expected){n++;}}
  System.out.println("PASS P06-B0039 assertions="+n);
 }
}