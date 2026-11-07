package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0133: Bounded event schema compatibility and monotonic security version floor. Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxSchemaNegotiation {
 private InboxSchemaNegotiation(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 public record Contract(int minVersion,int maxVersion,int securityFloor){
  public Contract{require(minVersion>=1&&maxVersion>=minVersion&&maxVersion<=32&&securityFloor>=minVersion&&securityFloor<=maxVersion);}
 }
 public static void admit(Map<String,Contract> registry,String type,int version,int lastAccepted){
  Objects.requireNonNull(registry);Contract c=registry.get(type);require(c!=null&&version>=c.securityFloor()&&version<=c.maxVersion()&&version>=lastAccepted);
 }

}
