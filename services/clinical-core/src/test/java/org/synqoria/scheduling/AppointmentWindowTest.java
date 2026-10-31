package org.synqoria.scheduling;
import java.time.*;
public class AppointmentWindowTest {
 static int n=0;
 static void ok(boolean b){if(!b)throw new AssertionError();n++;}
 static void deny(Runnable r){try{r.run();throw new AssertionError("should deny");}catch(IllegalArgumentException e){n++;}}
 public static void main(String[] args){
  var zone=ZoneId.of("Europe/Paris");
  deny(()->AppointmentWindow.fromLocal(LocalDateTime.of(2026,3,29,2,30),zone,null,Duration.ofMinutes(30)));
  deny(()->AppointmentWindow.fromLocal(LocalDateTime.of(2026,10,25,2,30),zone,null,Duration.ofMinutes(30)));
  var first=AppointmentWindow.fromLocal(LocalDateTime.of(2026,10,25,2,30),zone,ZoneOffset.ofHours(2),Duration.ofMinutes(30));
  var second=AppointmentWindow.fromLocal(LocalDateTime.of(2026,10,25,2,30),zone,ZoneOffset.ofHours(1),Duration.ofMinutes(30));
  ok(!first.start().equals(second.start()));
  deny(()->AppointmentWindow.fromLocal(LocalDateTime.of(2026,10,25,2,30),zone,ZoneOffset.UTC,Duration.ofMinutes(30)));
  var a=new AppointmentWindow(Instant.parse("2026-11-02T09:00:00Z"),Instant.parse("2026-11-02T09:30:00Z"),ZoneId.of("Africa/Douala"));
  var b=new AppointmentWindow(a.end(),a.end().plusSeconds(600),a.zone());
  ok(!a.overlaps(b));ok(a.overlaps(new AppointmentWindow(a.start().plusSeconds(60),a.end(),a.zone())));
  deny(()->new AppointmentWindow(a.start(),a.start(),a.zone()));
  deny(()->new AppointmentWindow(a.start(),a.start().plus(Duration.ofDays(2)),a.zone()));
  System.out.println("PASS P06-B0001 "+n+" DST, zone, interval and overlap assertions (unit)");
 }
}
