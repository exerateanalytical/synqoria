package org.synqoria.scheduling;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/** Quarantined schema-aligned scoped CAS; no grants or HTTP write endpoint. */
public final class AppointmentScopedMutation {
    private AppointmentScopedMutation() {}
    public static final String SQL = "UPDATE synqoria_appointment_reservations_quarantine SET lifecycle = ?, revision = revision + 1 "
        + "WHERE appointment_id = ? AND tenant_id = ? AND facility_id = ? AND patient_id = ? AND revision = ?";
    public static void bind(PreparedStatement statement, String lifecycle, UUID appointment, UUID tenant,
                            UUID facility, UUID patient, long revision) throws SQLException {
        if (statement == null || appointment == null || tenant == null || facility == null || patient == null || revision < 1)
            throw new IllegalArgumentException("invalid scoped appointment mutation");
        AppointmentStateCodec.decode(lifecycle);
        statement.setString(1, lifecycle);
        statement.setObject(2, appointment);
        statement.setObject(3, tenant);
        statement.setObject(4, facility);
        statement.setObject(5, patient);
        statement.setLong(6, revision);
    }
    public static void requireExactlyOneRow(int affected) throws SQLException {
        if (affected != 1) throw new SQLException("appointment update unavailable", "40001");
    }
}
