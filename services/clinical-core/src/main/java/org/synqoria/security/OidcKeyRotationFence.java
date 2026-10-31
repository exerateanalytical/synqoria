package org.synqoria.security;
import java.time.*;import java.util.*;
/** P03-B0022: authoritative key metadata lifecycle; cannot discover or trust arbitrary token-supplied keys. */
public final class OidcKeyRotationFence {
 public record KeyRecord(String kid,Instant activeFrom,Instant activeUntil,boolean revoked){
  public KeyRecord {if(kid==null||!kid.matches("[A-Za-z0-9_-]{8,80}")||activeFrom==null||activeUntil==null||!activeUntil.isAfter(activeFrom))throw new IllegalArgumentException("Invalid key record");}
 }
 private final Map<String,KeyRecord> keys;
 public OidcKeyRotationFence(Collection<KeyRecord> records){
  if(records==null||records.isEmpty()||records.size()>20)throw new IllegalArgumentException("Invalid trusted keys");
  Map<String,KeyRecord> found=new HashMap<>();for(KeyRecord record:records){if(record==null||found.putIfAbsent(record.kid(),record)!=null)throw new IllegalArgumentException("Duplicate key id");}
  keys=Map.copyOf(found);
 }
 public boolean active(String kid,Instant now){
  KeyRecord r=keys.get(kid);return r!=null&&now!=null&&!r.revoked()&&!now.isBefore(r.activeFrom())&&now.isBefore(r.activeUntil());
 }
}
