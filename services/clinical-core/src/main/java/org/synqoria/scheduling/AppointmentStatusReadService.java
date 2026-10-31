package org.synqoria.scheduling;

import org.synqoria.security.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Scope-checked administrative status read; no PHI or appointment-existence disclosure to denied callers. */
public final class AppointmentStatusReadService {
    private AppointmentStatusReadService() {}
    public record Status(String lifecycle, long revision) {
        public Status { AppointmentStateCodec.decode(lifecycle);if(revision<1)throw new IllegalArgumentException("invalid revision"); }
    }
    public static final String SQL = "SELECT lifecycle,revision FROM synqoria_appointment_reservations_quarantine "
        + "WHERE appointment_id=? AND tenant_id=? AND facility_id=? AND patient_id=?";
    public static Optional<Status> read(Connection c, TrustedAccessContext identity,
                                        TrustedClinicalAccessEvidence proof, UUID patient, Instant serverNow,
                                        String restrictedRole, UUID appointmentId) throws SQLException {
        if(identity==null||patient==null||serverNow==null||appointmentId==null||
            ClinicalAccessPolicy.authorize(identity,proof,identity.tenantId(),identity.facilityId(),patient,
                "appointment:read",serverNow)!=ClinicalAccessPolicy.Decision.ALLOWED)
            throw new SecurityException("appointment read denied");
        AppointmentTenantContext scope=new AppointmentTenantContext(identity.tenantId(),identity.facilityId(),identity.actorId(),patient);
        return AppointmentRestrictedTransaction.execute(c,scope,restrictedRole,connection->{
            try(PreparedStatement ps=connection.prepareStatement(SQL)) {
                ps.setObject(1,appointmentId);ps.setObject(2,scope.tenantId());
                ps.setObject(3,scope.facilityId());ps.setObject(4,scope.patientId());
                try(ResultSet rs=ps.executeQuery()) {
                    if(!rs.next())return Optional.empty();
                    Status status;
                    try{status=new Status(rs.getString(1),rs.getLong(2));}
                    catch(IllegalArgumentException ex){throw new SQLException("invalid appointment state", "23514",ex);}
                    if(rs.next())throw new SQLException("ambiguous appointment state", "21000");
                    return Optional.of(status);
                }
            }
        });
    }
}
