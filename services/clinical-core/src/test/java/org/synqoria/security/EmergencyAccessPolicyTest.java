package org.synqoria.security;
import java.time.Instant;import java.util.*;
public class EmergencyAccessPolicyTest {
 static int n;static void yes(boolean b){if(!b)throw new AssertionError();n++;}
 public static void main(String[] args){
  var tenant=UUID.randomUUID();var fac=UUID.randomUUID();var actor=UUID.randomUUID();var patient=UUID.randomUUID();var now=Instant.parse("2026-10-30T00:00:00Z");
  var context=new TrustedAccessContext(tenant,fac,actor,Set.of("emergency:request"),Set.of());
  var good=new EmergencyAccessPolicy.Request(tenant,fac,actor,patient,"IMMEDIATE_LIFE_THREAT","incident_12345678",now,now.plusSeconds(300),true,true);
  yes(EmergencyAccessPolicy.review(context,good,now)==EmergencyAccessPolicy.Result.REVIEWABLE_REQUEST);
  yes(EmergencyAccessPolicy.review(context,good,now.plusSeconds(300))==EmergencyAccessPolicy.Result.DENIED);
  yes(EmergencyAccessPolicy.review(context,new EmergencyAccessPolicy.Request(tenant,fac,actor,patient,"ROUTINE","incident_12345678",now,now.plusSeconds(300),true,true),now)==EmergencyAccessPolicy.Result.DENIED);
  yes(EmergencyAccessPolicy.review(context,new EmergencyAccessPolicy.Request(tenant,fac,actor,patient,"IMMEDIATE_LIFE_THREAT","incident_12345678",now,now.plusSeconds(300),true,false),now)==EmergencyAccessPolicy.Result.DENIED);
  yes(EmergencyAccessPolicy.review(context,new EmergencyAccessPolicy.Request(tenant,fac,actor,patient,"IMMEDIATE_LIFE_THREAT","incident_12345678",now,now.plusSeconds(601),true,true),now)==EmergencyAccessPolicy.Result.DENIED);
  yes(EmergencyAccessPolicy.review(context,new EmergencyAccessPolicy.Request(UUID.randomUUID(),fac,actor,patient,"IMMEDIATE_LIFE_THREAT","incident_12345678",now,now.plusSeconds(300),true,true),now)==EmergencyAccessPolicy.Result.DENIED);
  yes(EmergencyAccessPolicy.review(new TrustedAccessContext(tenant,fac,actor,Set.of(),Set.of()),good,now)==EmergencyAccessPolicy.Result.DENIED);
  System.out.println("PASS: "+n+" emergency request assertions");
 }
}
