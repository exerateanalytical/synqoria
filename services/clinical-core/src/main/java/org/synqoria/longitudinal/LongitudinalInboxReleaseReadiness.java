package org.synqoria.longitudinal;
import java.time.*;import java.util.*;
/** Hard production dependencies must be verified by distinct trusted sources before consumer start. */
public final class LongitudinalInboxReleaseReadiness {
 private LongitudinalInboxReleaseReadiness(){}
 public enum Dependency {OIDC,KMS,POSTGRES_RLS,POSTGRES_TXN,KAFKA_AUTH,SCHEMA_LOCK,AUDIT_SINK}
 public interface EvidenceSource {boolean verified(Dependency dependency,Instant now) throws Exception;}
 public static boolean ready(EvidenceSource source,Instant now){
  Objects.requireNonNull(source);Objects.requireNonNull(now);
  for(Dependency d:Dependency.values()){
   try{if(!source.verified(d,now))return false;}catch(Exception failure){return false;}
  }return true;
 }
 public static void require(EvidenceSource source,Instant now){
  if(!ready(source,now))throw new SecurityException("clinical inbox runtime unavailable");
 }
}
