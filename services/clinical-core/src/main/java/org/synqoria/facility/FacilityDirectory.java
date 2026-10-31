package org.synqoria.facility;

import org.synqoria.security.TrustedAccessContext;
import java.util.*;

/**
 * Immutable in-process directory snapshot. An eventual implementation must load
 * authoritative, versioned records and enforce grants transactionally.
 * This does not resolve or authenticate a cell endpoint or validate a JWT.
 */
public final class FacilityDirectory {
    private final Map<UUID, FacilityBinding> facilities;
    public FacilityDirectory(Collection<FacilityBinding> bindings) {
        Objects.requireNonNull(bindings, "bindings");
        var snapshot = new HashMap<UUID, FacilityBinding>();
        for (var binding : bindings) {
            Objects.requireNonNull(binding, "binding");
            if (snapshot.putIfAbsent(binding.facilityId(), binding) != null) {
                throw new IllegalArgumentException("Duplicate facility ID");
            }
        }
        facilities = Map.copyOf(snapshot);
    }
    /** Fail closed on missing, mismatched, disabled or unverified facility context. */
    public boolean permitsActiveFacility(TrustedAccessContext trusted, UUID expectedCellId) {
        if (trusted == null || expectedCellId == null) return false;
        var binding = facilities.get(trusted.facilityId());
        return binding != null && binding.cellId().equals(expectedCellId)
                && binding.tenantId().equals(trusted.tenantId()) && "ACTIVE".equals(binding.status());
    }
    public Optional<FacilityBinding> findForTrustedContext(TrustedAccessContext trusted, UUID expectedCellId) {
        if (!permitsActiveFacility(trusted, expectedCellId)) return Optional.empty();
        return Optional.of(facilities.get(trusted.facilityId()));
    }
}
