package org.synqoria.http;
import com.sun.net.httpserver.Headers;
import org.synqoria.security.TrustedSessionAdmission;
import org.synqoria.security.TrustedAccessContext;
import java.time.Instant;
import java.util.*;
/** A bearer is accepted only after the injected cryptographic verifier admits it. */
public final class BearerAdmission {
 private final java.util.function.BiFunction<String,Instant,Optional<TrustedAccessContext>> admitVerified;
 public BearerAdmission(TrustedSessionAdmission sessions){this.admitVerified=Objects.requireNonNull(sessions)::admit;}
 /** Production path must supply a cryptographically validating identity source and current grants/revocation. */
 public BearerAdmission(org.synqoria.security.ProductionAdmissionBoundary sessions){this.admitVerified=Objects.requireNonNull(sessions)::admit;}
 public Optional<TrustedAccessContext> admit(Headers headers,Instant now) {
  if(headers==null||now==null)return Optional.empty();
  var values=headers.get("Authorization");
  if(values==null||values.size()!=1)return Optional.empty();
  String raw=values.get(0);
  if(raw==null||!raw.startsWith("Bearer ")||raw.length()>8200)return Optional.empty();
  String token=raw.substring(7);
  if(!token.matches("[A-Za-z0-9_.~-]{16,8192}"))return Optional.empty();
  return admitVerified.apply(token,now);
 }
}
