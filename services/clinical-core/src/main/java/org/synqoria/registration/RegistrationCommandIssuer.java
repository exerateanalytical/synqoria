package org.synqoria.registration;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.synqoria.security.TrustedAccessContext;
/** Server-owned command issuance; never consume patient/command IDs or HMAC from client JSON. */
public final class RegistrationCommandIssuer {
 private final SecureRandom random;private final byte[] secret;
 private static boolean allSame(byte[] key){for(int i=1;i<key.length;i++)if(key[i]!=key[0])return false;return true;}
 public RegistrationCommandIssuer(byte[] secret){this(secret,new SecureRandom());}
 RegistrationCommandIssuer(byte[] secret,SecureRandom random){if(secret==null||secret.length<32||allSame(secret))throw new IllegalArgumentException("Weak secret");this.secret=secret.clone();this.random=Objects.requireNonNull(random);}
 private UUID fresh(){byte[] b=new byte[16];random.nextBytes(b);b[6]=(byte)((b[6]&15)|0x40);b[8]=(byte)((b[8]&63)|0x80);java.nio.ByteBuffer q=java.nio.ByteBuffer.wrap(b);return new UUID(q.getLong(),q.getLong());}
 public RegistrationCommand issue(TrustedAccessContext actor,RegistrationDraft draft,Instant now){
  if(actor==null||draft==null||now==null||!draft.plausibleAt(LocalDate.ofInstant(now,ZoneOffset.UTC)))throw new IllegalArgumentException("Invalid issuance context");
  return new RegistrationCommand(fresh(),fresh(),fresh(),RegistrationFingerprint.sign(secret,actor.tenantId(),actor.facilityId(),draft),draft,now);
 }
}
