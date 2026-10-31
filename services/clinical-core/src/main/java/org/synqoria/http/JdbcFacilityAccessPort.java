package org.synqoria.http;
import org.synqoria.facility.*;
import org.synqoria.security.TrustedAccessContext;
import java.util.*;
/** Wraps a fresh DB-backed facility gate; DB role/grants still require live validation. */
public final class JdbcFacilityAccessPort implements FacilityAccessPort {
 private final JdbcFacilityGate gate;
 public JdbcFacilityAccessPort(JdbcFacilityGate gate){this.gate=Objects.requireNonNull(gate);}
 public Optional<FacilityBinding> resolve(TrustedAccessContext actor){return gate.resolve(actor);}
}
