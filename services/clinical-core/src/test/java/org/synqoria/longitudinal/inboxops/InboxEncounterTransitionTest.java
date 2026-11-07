package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** Independently runnable P07-B0138 scenario. No production DB, Kafka or clinical authorization simulated. */
public final class InboxEncounterTransitionTest {
 static int assertions=0;
 static void ok(boolean v){assertions++;if(!v)throw new AssertionError("P07-B0138 assertion #"+assertions);}
 static void denied(Runnable action){assertions++;try{action.run();throw new AssertionError("P07-B0138 unsafe input accepted");}catch(SecurityException|IllegalArgumentException|NullPointerException expected){}}
 public static void main(String[] args){

 ok(InboxEncounterTransition.next(InboxEncounterTransition.State.PLANNED,InboxEncounterTransition.Action.CHECK_IN)==InboxEncounterTransition.State.ACTIVE);
 ok(InboxEncounterTransition.next(InboxEncounterTransition.State.ACTIVE,InboxEncounterTransition.Action.PLAN_DISCHARGE)==InboxEncounterTransition.State.DISCHARGE_READY);
 ok(InboxEncounterTransition.next(InboxEncounterTransition.State.DISCHARGE_READY,InboxEncounterTransition.Action.FINAL_DISCHARGE)==InboxEncounterTransition.State.DISCHARGED);
 ok(InboxEncounterTransition.next(InboxEncounterTransition.State.DISCHARGED,InboxEncounterTransition.Action.NOTE_AMEND)==InboxEncounterTransition.State.DISCHARGED);
 denied(()->InboxEncounterTransition.next(InboxEncounterTransition.State.PLANNED,InboxEncounterTransition.Action.FINAL_DISCHARGE));
 denied(()->InboxEncounterTransition.next(InboxEncounterTransition.State.DISCHARGED,InboxEncounterTransition.Action.CHECK_IN));

 System.out.println("PASS P07-B0138 EncounterTransition assertions="+assertions);
 }
}
