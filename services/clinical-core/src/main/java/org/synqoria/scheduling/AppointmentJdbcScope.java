package org.synqoria.scheduling;

import java.sql.*;
import java.util.UUID;

/** Transaction-local scope installation. PostgreSQL must echo every bound value. */
public final class AppointmentJdbcScope {
    private AppointmentJdbcScope() {}
    public static final String SQL = "SELECT set_config('app.tenant_id', ?, true), "
        + "set_config('app.facility_id', ?, true), "
        + "set_config('app.actor_id', ?, true), "
        + "set_config('app.patient_id', ?, true)";
    public static void install(Connection connection, AppointmentTenantContext ctx) throws SQLException {
        AppointmentWriteFence.require(connection);
        if(ctx==null)throw new SQLException("trusted appointment scope required", "22023");
        UUID[] values={ctx.tenantId(),ctx.facilityId(),ctx.actorId(),ctx.patientId()};
        try(PreparedStatement stmt=connection.prepareStatement(SQL)) {
            for(int i=0;i<values.length;i++)stmt.setString(i+1,values[i].toString());
            try(ResultSet rs=stmt.executeQuery()) {
                if(!rs.next())throw new SQLException("appointment scope installation missing", "42501");
                for(int i=0;i<values.length;i++)
                    if(!values[i].toString().equals(rs.getString(i+1)))
                        throw new SQLException("appointment scope installation mismatch", "42501");
                if(rs.next())throw new SQLException("appointment scope installation ambiguous", "42501");
            }
        }
    }
}
