package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** Independently runnable P07-B0133 scenario. No production DB, Kafka or clinical authorization simulated. */
public final class InboxSchemaNegotiationTest {
 static int assertions=0;
 static void ok(boolean v){assertions++;if(!v)throw new AssertionError("P07-B0133 assertion #"+assertions);}
 static void denied(Runnable action){assertions++;try{action.run();throw new AssertionError("P07-B0133 unsafe input accepted");}catch(SecurityException|IllegalArgumentException|NullPointerException expected){}}
 public static void main(String[] args){

 var registry=Map.of("clinical.note",new InboxSchemaNegotiation.Contract(1,4,2));
 InboxSchemaNegotiation.admit(registry,"clinical.note",3,2);ok(true);
 denied(()->InboxSchemaNegotiation.admit(registry,"clinical.note",1,1));
 denied(()->InboxSchemaNegotiation.admit(registry,"clinical.note",5,2));
 denied(()->InboxSchemaNegotiation.admit(registry,"clinical.note",2,3));
 denied(()->InboxSchemaNegotiation.admit(registry,"clinical.lab",3,2));
 denied(()->new InboxSchemaNegotiation.Contract(5,4,5));

 System.out.println("PASS P07-B0133 SchemaNegotiation assertions="+assertions);
 }
}
