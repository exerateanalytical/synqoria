package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** Independently runnable P07-B0147 scenario. No production DB, Kafka or clinical authorization simulated. */
public final class InboxRecoveryOffsetWindowTest {
 static int assertions=0;
 static void ok(boolean v){assertions++;if(!v)throw new AssertionError("P07-B0147 assertion #"+assertions);}
 static void denied(Runnable action){assertions++;try{action.run();throw new AssertionError("P07-B0147 unsafe input accepted");}catch(SecurityException|IllegalArgumentException|NullPointerException expected){}}
 public static void main(String[] args){

 var w=InboxRecoveryOffsetWindow.request(101,150,100);ok(w.last()-w.first()==49);
 denied(()->InboxRecoveryOffsetWindow.request(100,150,100));
 denied(()->InboxRecoveryOffsetWindow.request(101,100,100));
 denied(()->InboxRecoveryOffsetWindow.request(101,700,100));
 denied(()->InboxRecoveryOffsetWindow.request(-1,10,-1));

 System.out.println("PASS P07-B0147 RecoveryOffsetWindow assertions="+assertions);
 }
}
