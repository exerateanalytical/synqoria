package org.synqoria.scheduling;

import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

/** Conservative administrative appointment transitions; no clinical order or care decisions. */
public final class AppointmentTransitionPolicy {
    private static final Map<String, Set<String>> ALLOWED = Map.of(
        "REQUESTED", Set.of("CONFIRMED", "CANCELLED"),
        "CONFIRMED", Set.of("ARRIVED", "CANCELLED", "NO_SHOW"),
        "ARRIVED", Set.of("FULFILLED"),
        "CANCELLED", Set.of(),
        "NO_SHOW", Set.of(),
        "FULFILLED", Set.of()
    );
    private AppointmentTransitionPolicy() {}
    public static void requireAllowed(String current, String target) throws SQLException {
        if (current == null || target == null || !ALLOWED.containsKey(current)
                || !ALLOWED.get(current).contains(target)) {
            throw new SQLException("appointment transition denied", "23514");
        }
    }
}
