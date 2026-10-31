package org.synqoria.security;
import java.time.*;import java.util.*;
/** P03-B0030. Immutable bounded externally verified keyset; never trusts arbitrary network JSON. */
public final class OidcPinnedKeyset {
 private final Map<String,OidcTrustedRsaJwk> keys;private final Instant validFrom,validUntil;
 public OidcPinnedKeyset(Collection<OidcTrustedRsaJwk> input,Instant from,Instant until){
  if(input==null||input.isEmpty()||input.size()>16||from==null||until==null||!until.isAfter(from)||Duration.between(from,until).compareTo(Duration.ofHours(24))>0)throw new IllegalArgumentException("Untrusted keyset");
  Map<String,OidcTrustedRsaJwk> tmp=new HashMap<>();
  for(var key:input)if(key==null||tmp.putIfAbsent(key.kid(),key)!=null)throw new IllegalArgumentException("Duplicate or null key");
  keys=Map.copyOf(tmp);validFrom=from;validUntil=until;
 }
 public Optional<OidcTrustedRsaJwk> resolve(String kid,Instant now,Set<String> revokedKids){
  if(kid==null||now==null||revokedKids==null||now.isBefore(validFrom)||!now.isBefore(validUntil)||revokedKids.contains(kid))return Optional.empty();
  return Optional.ofNullable(keys.get(kid));
 }
 public int size(){return keys.size();}
}
