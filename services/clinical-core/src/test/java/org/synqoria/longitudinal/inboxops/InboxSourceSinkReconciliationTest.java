package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** Independently runnable P07-B0153 scenario. No production DB, Kafka or clinical authorization simulated. */
public final class InboxSourceSinkReconciliationTest {
 static int assertions=0;
 static void ok(boolean v){assertions++;if(!v)throw new AssertionError("P07-B0153 assertion #"+assertions);}
 static void denied(Runnable action){assertions++;try{action.run();throw new AssertionError("P07-B0153 unsafe input accepted");}catch(SecurityException|IllegalArgumentException|NullPointerException expected){}}
 public static void main(String[] args){

 UUID t=UUID.randomUUID(),f=UUID.randomUUID(),p=UUID.randomUUID(),e=UUID.randomUUID(),id=UUID.randomUUID();
 var x=new InboxSourceSinkReconciliation.Fingerprint(t,f,p,e,id,5,"a".repeat(64));
 InboxSourceSinkReconciliation.verify(x,x);ok(true);
 denied(()->InboxSourceSinkReconciliation.verify(x,new InboxSourceSinkReconciliation.Fingerprint(t,f,p,e,id,6,"a".repeat(64))));
 denied(()->InboxSourceSinkReconciliation.verify(x,new InboxSourceSinkReconciliation.Fingerprint(t,f,p,e,id,5,"b".repeat(64))));
 denied(()->InboxSourceSinkReconciliation.verify(x,new InboxSourceSinkReconciliation.Fingerprint(t,f,UUID.randomUUID(),e,id,5,"a".repeat(64))));
 denied(()->InboxSourceSinkReconciliation.verify(x,null));

 System.out.println("PASS P07-B0153 SourceSinkReconciliation assertions="+assertions);
 }
}
