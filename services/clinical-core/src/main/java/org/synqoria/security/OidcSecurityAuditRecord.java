package org.synqoria.security;
import javax.crypto.Mac;import javax.crypto.spec.SecretKeySpec;import java.nio.charset.StandardCharsets;import java.security.*;import java.time.*;import java.util.*;
/** P03-B0028: PHI-free security decision metadata with HMAC-pseudonymous session reference. */
public final class OidcSecurityAuditRecord {
 public record Record(String event,Instant at,String sessionReference,String decision){
  public Record {if(!Set.of("TOKEN_REJECTED","KEY_REVOKED","GRANT_DENIED","SESSION_EXPIRED").contains(event)||at==null||sessionReference==null||!sessionReference.matches("[A-Za-z0-9_-]{43}")||!Set.of("DENY","LOCK").contains(decision))throw new IllegalArgumentException("Invalid security event");}
  public String safeLine(){return at+" event="+event+" session_hmac="+sessionReference+" decision="+decision;}
 }
 private OidcSecurityAuditRecord(){}
 public static Record denied(String event,Instant now,String opaqueSessionId,byte[] secret){
  if(now==null||opaqueSessionId==null||!opaqueSessionId.matches("[A-Za-z0-9_-]{8,128}")||secret==null||secret.length<32)throw new IllegalArgumentException("Invalid audit input");
  try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));
   String ref=Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(("SYNQORIA-AUDIT-SESSION-v1:"+opaqueSessionId).getBytes(StandardCharsets.UTF_8)));
   return new Record(event,now,ref,"DENY");
  }catch(GeneralSecurityException e){throw new IllegalStateException("Audit cryptography unavailable",e);}
 }
}
