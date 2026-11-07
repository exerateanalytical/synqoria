package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** Independently runnable P07-B0157 scenario. No production DB, Kafka or clinical authorization simulated. */
public final class InboxEscalationWindowTest {
 static int assertions=0;
 static void ok(boolean v){assertions++;if(!v)throw new AssertionError("P07-B0157 assertion #"+assertions);}
 static void denied(Runnable action){assertions++;try{action.run();throw new AssertionError("P07-B0157 unsafe input accepted");}catch(SecurityException|IllegalArgumentException|NullPointerException expected){}}
 public static void main(String[] args){

 Instant n=Instant.parse("2026-11-04T10:00:00Z");
 ok(InboxEscalationWindow.shouldEscalate(n,List.of(n,n.minusSeconds(5),n.minusSeconds(15)),Duration.ofSeconds(30),3));
 ok(!InboxEscalationWindow.shouldEscalate(n,List.of(n,n.minusSeconds(40)),Duration.ofSeconds(30),2));
 denied(()->InboxEscalationWindow.shouldEscalate(n,List.of(n.plusSeconds(1)),Duration.ofSeconds(30),2));
 denied(()->InboxEscalationWindow.shouldEscalate(n,List.of(n),Duration.ofSeconds(1),2));
 denied(()->InboxEscalationWindow.shouldEscalate(n,List.of(n),Duration.ofSeconds(30),1));

 System.out.println("PASS P07-B0157 EscalationWindow assertions="+assertions);
 }
}
