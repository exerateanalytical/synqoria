package org.synqoria.scheduling;
import java.util.*;
public final class AppointmentActorPseudonymTest {
 static int checks;static void ok(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 public static void main(String[] args){
  UUID t=UUID.randomUUID(),a=UUID.randomUUID();byte[] k=new byte[32];Arrays.fill(k,(byte)7);
  String p=AppointmentActorPseudonym.derive(t,a,k);
  ok(p.matches("[a-f0-9]{64}"));ok(p.equals(AppointmentActorPseudonym.derive(t,a,k)));
  ok(!p.equals(AppointmentActorPseudonym.derive(UUID.randomUUID(),a,k)));
  ok(!p.equals(AppointmentActorPseudonym.derive(t,UUID.randomUUID(),k)));
  byte[] other=k.clone();other[1]++;ok(!p.equals(AppointmentActorPseudonym.derive(t,a,other)));
  try{AppointmentActorPseudonym.derive(t,a,new byte[32]);throw new AssertionError();}catch(SecurityException ex){ok(true);}
  try{AppointmentActorPseudonym.derive(t,a,new byte[16]);throw new AssertionError();}catch(SecurityException ex){ok(true);}
  try{AppointmentActorPseudonym.derive(t,a,null);throw new AssertionError();}catch(SecurityException ex){ok(true);}
  System.out.println("PASS AppointmentActorPseudonymTest assertions="+checks);
 }
}
