package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** Independently runnable P07-B0156 scenario. No production DB, Kafka or clinical authorization simulated. */
public final class InboxDependencyCircuitBreakerTest {
 static int assertions=0;
 static void ok(boolean v){assertions++;if(!v)throw new AssertionError("P07-B0156 assertion #"+assertions);}
 static void denied(Runnable action){assertions++;try{action.run();throw new AssertionError("P07-B0156 unsafe input accepted");}catch(SecurityException|IllegalArgumentException|NullPointerException expected){}}
 public static void main(String[] args){

 Instant n=Instant.parse("2026-11-04T09:00:00Z");
 var c=new InboxDependencyCircuitBreaker.Circuit(InboxDependencyCircuitBreaker.State.CLOSED,1,n);
 var fail=InboxDependencyCircuitBreaker.fail(c,n);ok(fail.consecutiveFailures()==2);
 var open=InboxDependencyCircuitBreaker.fail(fail,n);ok(open.state()==InboxDependencyCircuitBreaker.State.OPEN);
 denied(()->InboxDependencyCircuitBreaker.probe(open,n.plusSeconds(29)));
 var half=InboxDependencyCircuitBreaker.probe(open,n.plusSeconds(30));ok(half.state()==InboxDependencyCircuitBreaker.State.HALF_OPEN);
 ok(InboxDependencyCircuitBreaker.succeed(half).state()==InboxDependencyCircuitBreaker.State.CLOSED);
 denied(()->InboxDependencyCircuitBreaker.succeed(open));

 System.out.println("PASS P07-B0156 DependencyCircuitBreaker assertions="+assertions);
 }
}
