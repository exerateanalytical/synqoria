package org.synqoria.http;
import org.synqoria.facility.*;
import org.synqoria.security.TrustedAccessContext;
import java.util.*;
/** Checks independently configured cell, not a caller-supplied X-Tenant-ID. */
public final class CellFacilityGate {
 private final FacilityDirectory facilities;private final UUID cell;
 public CellFacilityGate(FacilityDirectory facilities,UUID cell){this.facilities=Objects.requireNonNull(facilities);this.cell=Objects.requireNonNull(cell);}
 public Optional<FacilityBinding> resolve(TrustedAccessContext trusted){return facilities.findForTrustedContext(trusted,cell);}
}
