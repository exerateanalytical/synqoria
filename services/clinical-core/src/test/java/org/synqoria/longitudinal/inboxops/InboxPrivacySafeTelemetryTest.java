package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** Independently runnable P07-B0155 scenario. No production DB, Kafka or clinical authorization simulated. */
public final class InboxPrivacySafeTelemetryTest {
 static int assertions=0;
 static void ok(boolean v){assertions++;if(!v)throw new AssertionError("P07-B0155 assertion #"+assertions);}
 static void denied(Runnable action){assertions++;try{action.run();throw new AssertionError("P07-B0155 unsafe input accepted");}catch(SecurityException|IllegalArgumentException|NullPointerException expected){}}
 public static void main(String[] args){

 var m=InboxPrivacySafeTelemetry.record("clinical_inbox_events_total",InboxPrivacySafeTelemetry.Operation.INBOX_APPLY,InboxPrivacySafeTelemetry.Outcome.SUCCESS,3);
 ok(m.count()==3);ok(!m.toString().contains("patient"));
 denied(()->InboxPrivacySafeTelemetry.record("patient_mary",InboxPrivacySafeTelemetry.Operation.INBOX_APPLY,InboxPrivacySafeTelemetry.Outcome.SUCCESS,1));
 denied(()->InboxPrivacySafeTelemetry.record("clinical_inbox_events_total",InboxPrivacySafeTelemetry.Operation.INBOX_APPLY,InboxPrivacySafeTelemetry.Outcome.SUCCESS,-1));
 denied(()->InboxPrivacySafeTelemetry.record("clinical_inbox_events_total",InboxPrivacySafeTelemetry.Operation.INBOX_APPLY,InboxPrivacySafeTelemetry.Outcome.SUCCESS,1000001));

 System.out.println("PASS P07-B0155 PrivacySafeTelemetry assertions="+assertions);
 }
}
