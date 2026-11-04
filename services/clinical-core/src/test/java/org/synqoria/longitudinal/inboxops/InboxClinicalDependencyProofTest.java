package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** Independently runnable P07-B0158 scenario. No production DB, Kafka or clinical authorization simulated. */
public final class InboxClinicalDependencyProofTest {
 static int assertions=0;
 static void ok(boolean v){assertions++;if(!v)throw new AssertionError("P07-B0158 assertion #"+assertions);}
 static void denied(Runnable action){assertions++;try{action.run();throw new AssertionError("P07-B0158 unsafe input accepted");}catch(SecurityException|IllegalArgumentException|NullPointerException expected){}}
 public static void main(String[] args){

 Instant n=Instant.parse("2026-11-04T11:00:00Z");var p=new InboxClinicalDependencyProof.Proof(true,true,true,true,true,n.plusSeconds(30));
 InboxClinicalDependencyProof.admit(p,n);ok(true);
 denied(()->InboxClinicalDependencyProof.admit(new InboxClinicalDependencyProof.Proof(false,true,true,true,true,n.plusSeconds(30)),n));
 denied(()->InboxClinicalDependencyProof.admit(new InboxClinicalDependencyProof.Proof(true,false,true,true,true,n.plusSeconds(30)),n));
 denied(()->InboxClinicalDependencyProof.admit(new InboxClinicalDependencyProof.Proof(true,true,false,true,true,n.plusSeconds(30)),n));
 denied(()->InboxClinicalDependencyProof.admit(new InboxClinicalDependencyProof.Proof(true,true,true,false,true,n.plusSeconds(30)),n));
 denied(()->InboxClinicalDependencyProof.admit(new InboxClinicalDependencyProof.Proof(true,true,true,true,false,n.plusSeconds(30)),n));
 denied(()->InboxClinicalDependencyProof.admit(p,n.plusSeconds(30)));

 System.out.println("PASS P07-B0158 ClinicalDependencyProof assertions="+assertions);
 }
}
