package org.synqoria.scheduling;
import java.time.*;import java.util.*;
public class AppointmentCommandFingerprintTest {
 static int n=0;static void ok(boolean b){if(!b)throw new AssertionError();n++;}
 public static void main(String[] args){
  var a=new AppointmentAggregate(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),new AppointmentWindow(Instant.now(),Instant.now().plusSeconds(600),ZoneId.of("Africa/Douala")),AppointmentAggregate.State.REQUESTED,1,null);
  var one=AppointmentCommandFingerprint.sha256(a,"CONFIRM",1);
  ok(one.matches("[0-9a-f]{64}"));ok(one.equals(AppointmentCommandFingerprint.sha256(a,"CONFIRM",1)));
  ok(!one.equals(AppointmentCommandFingerprint.sha256(a,"CANCEL",1)));
  ok(!one.equals(AppointmentCommandFingerprint.sha256(a,"CONFIRM",2)));
  var b=new AppointmentAggregate(a.id(),a.tenant(),a.facility(),UUID.randomUUID(),a.resource(),a.window(),a.state(),a.revision(),a.reason());
  ok(!one.equals(AppointmentCommandFingerprint.sha256(b,"CONFIRM",1)));
  ok(!one.contains(a.patient().toString()));
  try{AppointmentCommandFingerprint.sha256(a,"confirm;DROP",1);throw new AssertionError();}catch(IllegalArgumentException expected){n++;}
  System.out.println("PASS P06-B0005 "+n+" stable scoped command fingerprint assertions (unit)");
 }
}
