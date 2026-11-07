package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0157: Bounded rolling clinical-integrity failure threshold with timestamp validation. Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxEscalationWindow {
 private InboxEscalationWindow(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 public static boolean shouldEscalate(Instant now,Collection<Instant> failures,Duration window,int threshold){
  Objects.requireNonNull(now);Objects.requireNonNull(failures);Objects.requireNonNull(window);
  require(failures.size()<=10000&&threshold>=2&&threshold<=1000&&window.compareTo(Duration.ofSeconds(10))>=0&&window.compareTo(Duration.ofMinutes(60))<=0);
  long count=0;Instant from=now.minus(window);
  for(Instant at:failures){require(at!=null&&!at.isAfter(now));if(!at.isBefore(from))count++;}
  return count>=threshold;
 }

}
