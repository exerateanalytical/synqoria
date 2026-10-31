package org.synqoria.scheduling;

import java.sql.*;
import java.util.UUID;

/** Read a single scoped appointment under a row lock in an existing SERIALIZABLE transaction. */
public record AppointmentLockedSnapshot(UUID appointmentId, String lifecycle, long revision) {
    public static final String SQL = "SELECT lifecycle, revision FROM synqoria_appointment_reservations_quarantine "
        + "WHERE appointment_id = ? AND tenant_id = ? AND facility_id = ? AND patient_id = ? FOR UPDATE";
    public AppointmentLockedSnapshot {
        if (appointmentId == null || revision < 1) throw new IllegalArgumentException("invalid snapshot");
        AppointmentStateCodec.decode(lifecycle);
    }
    public static AppointmentLockedSnapshot read(Connection connection, AppointmentTenantContext scope,
                                                  UUID appointmentId) throws SQLException {
        AppointmentWriteFence.require(connection);
        if (scope == null || appointmentId == null) throw new SQLException("invalid appointment scope");
        try (PreparedStatement ps = connection.prepareStatement(SQL)) {
            ps.setObject(1, appointmentId);
            ps.setObject(2, scope.tenantId());
            ps.setObject(3, scope.facilityId());
            ps.setObject(4, scope.patientId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("appointment unavailable", "02000");
                String state = rs.getString(1);
                long revision = rs.getLong(2);
                if (rs.wasNull() || revision < 1) throw new SQLException("invalid stored revision", "23514");
                AppointmentLockedSnapshot snapshot;
                try { snapshot = new AppointmentLockedSnapshot(appointmentId,state,revision); }
                catch (IllegalArgumentException ex) { throw new SQLException("invalid stored lifecycle", "23514",ex); }
                if (rs.next()) throw new SQLException("nonunique appointment result", "21000");
                return snapshot;
            }
        }
    }
}
