package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** Independently runnable P07-B0141 scenario. No production DB, Kafka or clinical authorization simulated. */
public final class InboxMetadataAllowlistTest {
 static int assertions=0;
 static void ok(boolean v){assertions++;if(!v)throw new AssertionError("P07-B0141 assertion #"+assertions);}
 static void denied(Runnable action){assertions++;try{action.run();throw new AssertionError("P07-B0141 unsafe input accepted");}catch(SecurityException|IllegalArgumentException|NullPointerException expected){}}
 public static void main(String[] args){

 var allowed=InboxMetadataAllowlist.filter(Map.of("revision","12","event_type","note.signed"));ok(allowed.size()==2);
 denied(()->InboxMetadataAllowlist.filter(Map.of("patient_name","Mary")));
 denied(()->InboxMetadataAllowlist.filter(Map.of("revision","1\nclinical note")));
 denied(()->InboxMetadataAllowlist.filter(Map.of("revision","a".repeat(129))));
 ok(InboxMetadataAllowlist.filter(Map.of()).isEmpty());

 System.out.println("PASS P07-B0141 MetadataAllowlist assertions="+assertions);
 }
}
