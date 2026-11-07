package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** Independently runnable P07-B0132 scenario. No production DB, Kafka or clinical authorization simulated. */
public final class InboxBrokerRouteFenceTest {
 static int assertions=0;
 static void ok(boolean v){assertions++;if(!v)throw new AssertionError("P07-B0132 assertion #"+assertions);}
 static void denied(Runnable action){assertions++;try{action.run();throw new AssertionError("P07-B0132 unsafe input accepted");}catch(SecurityException|IllegalArgumentException|NullPointerException expected){}}
 public static void main(String[] args){

 var a=new InboxBrokerRouteFence.Route("clinical.private","synqoria.consumer",3,32,"svc-verified");
 var subscribed=new InboxBrokerRouteFence.Route("clinical.private","synqoria.consumer",3,0,"svc-verified");
 InboxBrokerRouteFence.verify(a,subscribed,Set.of("svc-verified"));ok(true);
 denied(()->InboxBrokerRouteFence.verify(a,new InboxBrokerRouteFence.Route("clinical.public","synqoria.consumer",3,0,"svc-verified"),Set.of("svc-verified")));
 denied(()->InboxBrokerRouteFence.verify(a,subscribed,Set.of("svc-blocked")));
 denied(()->new InboxBrokerRouteFence.Route("clinical.private","synqoria.consumer",4096,1,"svc-verified"));
 denied(()->new InboxBrokerRouteFence.Route("clinical.private","synqoria.consumer",0,-1,"svc-verified"));

 System.out.println("PASS P07-B0132 BrokerRouteFence assertions="+assertions);
 }
}
