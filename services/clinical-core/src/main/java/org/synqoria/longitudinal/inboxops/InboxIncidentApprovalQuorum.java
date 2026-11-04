package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0150: Typed incident scoped four-party separation of duty for redrive decisions. Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxIncidentApprovalQuorum {
 private InboxIncidentApprovalQuorum(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 public record Approval(UUID incident,UUID requester,UUID securityReviewer,UUID clinicalReviewer,UUID operator,UUID patient,String ticket,Instant expiresAt){
  public Approval{Objects.requireNonNull(incident);Objects.requireNonNull(requester);Objects.requireNonNull(securityReviewer);Objects.requireNonNull(clinicalReviewer);Objects.requireNonNull(operator);Objects.requireNonNull(patient);Objects.requireNonNull(expiresAt);
   require(Set.of(requester,securityReviewer,clinicalReviewer,operator).size()==4&&ticket!=null&&ticket.matches("INC-[0-9]{4}-[0-9]{5}"));}
 }
 public static void authorize(Approval a,UUID patient,Instant now){require(a!=null&&a.patient().equals(patient)&&now.isBefore(a.expiresAt())&&a.expiresAt().isBefore(now.plus(Duration.ofHours(1))));}

}
