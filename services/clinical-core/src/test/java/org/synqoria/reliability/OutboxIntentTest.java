package org.synqoria.reliability;
import java.util.*;
public final class OutboxIntentTest {
 static int n=0; static void ok(boolean yes){if(!yes)throw new AssertionError("Outbox "+n);n++;}
 public static void main(String[]args){var t=UUID.randomUUID();var a=UUID.randomUUID();var hash="d".repeat(64);
  var first=OutboxIntent.create(t,a,1,"patient.created","v1",hash);var retry=OutboxIntent.create(t,a,1,"patient.created","v1",hash);
  ok(first.eventId().equals(retry.eventId())); ok(first.tenantId().equals(t)); ok(first.aggregateVersion()==1);
  ok(!first.eventId().equals(OutboxIntent.create(t,a,2,"patient.created","v1",hash).eventId()));
  ok(!first.eventId().equals(OutboxIntent.create(UUID.randomUUID(),a,1,"patient.created","v1",hash).eventId()));
  ok(!first.eventId().equals(OutboxIntent.create(t,a,1,"patient.updated","v1",hash).eventId()));
  boolean fail=false;try{OutboxIntent.create(t,a,0,"patient.created","v1",hash);}catch(IllegalArgumentException x){fail=true;}ok(fail);
  fail=false;try{OutboxIntent.create(t,a,1,"patient.created","v1","raw patient text");}catch(IllegalArgumentException x){fail=true;}ok(fail);
  fail=false;try{OutboxIntent.create(t,a,1,"invalid / type","v1",hash);}catch(IllegalArgumentException x){fail=true;}ok(fail);
  System.out.println("PASS: "+n+" deterministic outbox intent assertions (no transaction yet)");
 }
}
