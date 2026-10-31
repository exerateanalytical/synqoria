package org.synqoria.scheduling;

import java.sql.*;
import java.util.UUID;

/** Restricted quarantined-schema CAS; does not grant clinical write access. */
public final class AppointmentRevisionUpdate {
    private AppointmentRevisionUpdate() {}
    public static void requireUpdated(Connection c, AppointmentTenantContext ctx, UUID appointmentId,
                                      long expectedRevision, String newState) throws SQLException {
        AppointmentWriteFence.require(c);
        if (ctx == null || appointmentId == null || expectedRevision < 1)
            throw new SQLException("invalid appointment revision command");
        try { AppointmentStateCodec.decode(newState); }
        catch (IllegalArgumentException ex) { throw new SQLException("invalid lifecycle", "23514", ex); }
        try (PreparedStatement ps = c.prepareStatement(AppointmentScopedMutation.SQL)) {
            AppointmentScopedMutation.bind(ps, newState, appointmentId, ctx.tenantId(), ctx.facilityId(), ctx.patientId(), expectedRevision);
            AppointmentScopedMutation.requireExactlyOneRow(ps.executeUpdate());
        }
    }
}
