package org.synqoria.reliability;
import java.util.*;
public final class InboxSequenceGuardTest{
 static int n=0;static void ok(boolean yes){if(!yes)throw new AssertionError("Inbox "+n);n++;}
 public static void main(String[] args){var s=new InboxSequenceGuard();var tenant=UUID.randomUUID();var aggregate=UUID.randomUUID();
  ok(s.accept(tenant,aggregate,"analytics",1)==InboxSequenceGuard.Decision.ACCEPT_NEXT);
  ok(s.accept(tenant,aggregate,"analytics",1)==InboxSequenceGuard.Decision.DUPLICATE_OR_STALE);
  ok(s.accept(tenant,aggregate,"analytics",3)==InboxSequenceGuard.Decision.GAP_REQUIRES_REPLAY);
  ok(s.checkpoint(tenant,aggregate,"analytics")==1);
  ok(s.accept(tenant,aggregate,"analytics",2)==InboxSequenceGuard.Decision.ACCEPT_NEXT);
  ok(s.accept(tenant,aggregate,"analytics",3)==InboxSequenceGuard.Decision.ACCEPT_NEXT);
  ok(s.accept(tenant,aggregate,"analytics",2)==InboxSequenceGuard.Decision.DUPLICATE_OR_STALE);
  ok(s.accept(tenant,aggregate,"clinical",1)==InboxSequenceGuard.Decision.ACCEPT_NEXT);
  ok(s.accept(UUID.randomUUID(),aggregate,"analytics",1)==InboxSequenceGuard.Decision.ACCEPT_NEXT);
  ok(s.accept(tenant,UUID.randomUUID(),"analytics",1)==InboxSequenceGuard.Decision.ACCEPT_NEXT);
  boolean fail=false;try{s.accept(tenant,aggregate,"analytics",0);}catch(IllegalArgumentException x){fail=true;}ok(fail);
  System.out.println("PASS: "+n+" inbox sequence assertions (in-memory only)");
 }
}
