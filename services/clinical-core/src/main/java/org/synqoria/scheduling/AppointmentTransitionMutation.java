package org.synqoria.scheduling;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/** Atomic state/revision compare-and-swap; both prior and target states are constrained. */
public final class AppointmentTransitionMutation {
    private AppointmentTransitionMutation() {}
    public static final String SQL = "UPDATE synqoria_appointment_reservations_quarantine SET lifecycle = ?, revision = revision + 1 "
        + "WHERE appointment_id = ? AND tenant_id = ? AND facility_id = ? AND patient_id = ? AND revision = ? AND lifecycle = ?";
    public static void bind(PreparedStatement statement, String current, String target, UUID appointment, UUID tenant,
                            UUID facility, UUID patient, long expectedRevision) throws SQLException {
        AppointmentTransitionPolicy.requireAllowed(current, target);
        AppointmentScopedMutation.bind(statement, target, appointment, tenant, facility, patient, expectedRevision);
        statement.setString(7, current);
    }
    public static void requireUpdated(int affected) throws SQLException { AppointmentScopedMutation.requireExactlyOneRow(affected); }
}
