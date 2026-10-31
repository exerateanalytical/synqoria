package org.synqoria.registration;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
/** Server-only HMAC fingerprint: framed UTF-8 fields prevent ambiguous concatenations. No raw PII hashes. */
public final class RegistrationFingerprint {
 private RegistrationFingerprint(){}
 private static boolean allSame(byte[] key){for(int i=1;i<key.length;i++)if(key[i]!=key[0])return false;return true;}
 private static void field(DataOutputStream d,String s) throws IOException { byte[] b=s.getBytes(StandardCharsets.UTF_8);d.writeInt(b.length);d.write(b); }
 public static byte[] sign(byte[] key,UUID tenant,UUID facility,RegistrationDraft draft) {
  if(key==null||key.length<32||allSame(key)||tenant==null||facility==null||draft==null)throw new IllegalArgumentException("Missing fingerprint authority");
  try {
   ByteArrayOutputStream buf=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(buf);
   field(d,"SYNQORIA-REGISTRATION-FINGERPRINT-v1");field(d,tenant.toString());field(d,facility.toString());
   // Names are retained exactly after trim; never assume different Unicode names represent the same person.
   field(d,draft.legalName()==null?"<absent>":"<present>"+draft.legalName());
   field(d,draft.birthInformation().name());field(d,draft.dateOfBirth()==null?"":draft.dateOfBirth().toString());
   field(d,draft.estimatedAgeYears()==null?"":draft.estimatedAgeYears().toString());
   field(d,draft.language());
   Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return mac.doFinal(buf.toByteArray());
  }catch(Exception e){throw new IllegalStateException("Fingerprint unavailable",e);}
 }
 public static boolean same(byte[] a,byte[] b){return a!=null&&b!=null&&java.security.MessageDigest.isEqual(a,b);}
}
