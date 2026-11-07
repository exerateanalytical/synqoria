package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0141: Bounded metadata allowlist rejects PHI fields and unexpected keys. Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxMetadataAllowlist {
 private InboxMetadataAllowlist(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 private static final Set<String> ALLOWED=Set.of("event_type","schema_version","revision","event_hash","producer_id","key_id","occurred_at","correlation_id");
 public static Map<String,String> filter(Map<String,String> input){
  Objects.requireNonNull(input);require(input.size()<=8);int bytes=0;Map<String,String> result=new TreeMap<>();
  for(var e:input.entrySet()){
   require(e.getKey()!=null&&ALLOWED.contains(e.getKey())&&e.getValue()!=null&&e.getValue().length()<=128&&e.getValue().chars().allMatch(c->c>=33&&c<=126));
   bytes+=e.getKey().length()+e.getValue().length();require(bytes<=512);result.put(e.getKey(),e.getValue());
  }return Map.copyOf(result);
 }

}
