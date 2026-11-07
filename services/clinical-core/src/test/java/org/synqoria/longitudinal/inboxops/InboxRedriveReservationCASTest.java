package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** Independently runnable P07-B0151 scenario. No production DB, Kafka or clinical authorization simulated. */
public final class InboxRedriveReservationCASTest {
 static int assertions=0;
 static void ok(boolean v){assertions++;if(!v)throw new AssertionError("P07-B0151 assertion #"+assertions);}
 static void denied(Runnable action){assertions++;try{action.run();throw new AssertionError("P07-B0151 unsafe input accepted");}catch(SecurityException|IllegalArgumentException|NullPointerException expected){}}
 public static void main(String[] args){

 UUID inc=UUID.randomUUID(),holder=UUID.randomUUID();Instant now=Instant.parse("2026-11-04T06:00:00Z");
 var r=new InboxRedriveReservationCAS.Reservation(inc,holder,4,now.plusSeconds(20),false);
 var used=InboxRedriveReservationCAS.consume(r,inc,holder,4,now);ok(used.consumed());ok(used.generation()==5);
 denied(()->InboxRedriveReservationCAS.consume(used,inc,holder,5,now));
 denied(()->InboxRedriveReservationCAS.consume(r,inc,holder,3,now));
 denied(()->InboxRedriveReservationCAS.consume(r,inc,UUID.randomUUID(),4,now));
 denied(()->InboxRedriveReservationCAS.consume(r,inc,holder,4,now.plusSeconds(20)));

 System.out.println("PASS P07-B0151 RedriveReservationCAS assertions="+assertions);
 }
}
