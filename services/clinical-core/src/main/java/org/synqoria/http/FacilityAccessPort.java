package org.synqoria.http;
import org.synqoria.security.TrustedAccessContext;
import org.synqoria.facility.FacilityBinding;
import java.util.Optional;
/** Server-only current facility resolver: never read tenant, cell or facility from request headers. */
@FunctionalInterface public interface FacilityAccessPort {
 Optional<FacilityBinding> resolve(TrustedAccessContext actor);
}
