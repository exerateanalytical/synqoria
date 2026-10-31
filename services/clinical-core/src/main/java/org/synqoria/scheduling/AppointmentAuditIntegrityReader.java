package org.synqoria.scheduling;

import java.sql.*;
import java.util.UUID;

/** Readback proof for a bounded appointment audit chain; independent tamper detection, not external attestation. */
public final class AppointmentAuditIntegrityReader {
    private AppointmentAuditIntegrityReader() {}
    public static final String SQL = "SELECT a.appointment_revision,a.previous_digest,a.command_digest,a.actor_pseudonym,a.audit_digest,o.payload_digest "
        + "FROM synqoria_appointment_command_audit_quarantine a "
        + "LEFT JOIN synqoria_appointment_outbox_quarantine o "
        + "ON o.event_id=a.event_id AND o.tenant_id=a.tenant_id AND o.facility_id=a.facility_id "
        + "AND o.appointment_id=a.appointment_id AND o.appointment_revision=a.appointment_revision "
        + "AND o.event_type='appointment.status_changed' "
        + "WHERE a.tenant_id=? AND a.facility_id=? AND a.appointment_id=? ORDER BY a.appointment_revision ASC";
    public record Result(long entries, String finalDigest) {}
    public static Result verify(Connection c, AppointmentTenantContext scope, UUID appointmentId) throws SQLException {
        AppointmentWriteFence.require(c);
        if(scope==null||appointmentId==null)throw new SQLException("invalid audit scope");
        // Audit rows have no patient_id; prove patient access through the authoritative appointment first.
        long authoritativeRevision=AppointmentLockedSnapshot.read(c,scope,appointmentId).revision();
        long expectedRevision=2,entries=0;String previous="0".repeat(64);
        try(PreparedStatement ps=c.prepareStatement(SQL)) {
            ps.setObject(1,scope.tenantId());ps.setObject(2,scope.facilityId());ps.setObject(3,appointmentId);
            try(ResultSet rs=ps.executeQuery()) {
                while(rs.next()) {
                    if(entries>=10000)throw new SQLException("audit verification requires bounded pagination");
                    long revision=rs.getLong(1);String storedPrevious=rs.getString(2);
                    String command=rs.getString(3),actor=rs.getString(4),storedDigest=rs.getString(5);
                    if(revision!=expectedRevision||!previous.equals(storedPrevious))
                        throw new SQLException("audit revision or chain discontinuity", "23514");
                    String computed;
                    try {computed=AppointmentAuditStore.digestFor(scope,appointmentId,revision,storedPrevious,command,actor);}
                    catch(IllegalArgumentException ex){throw new SQLException("invalid audit record", "23514",ex);}
                    if(!computed.equals(storedDigest))throw new SQLException("audit digest mismatch", "23514");
                    if(!computed.equals(rs.getString(6)))throw new SQLException("appointment outbox missing or mismatched", "23514");
                    previous=computed;expectedRevision++;entries++;
                }
            }
        }
        if(expectedRevision!=authoritativeRevision+1)
            throw new SQLException("appointment audit history incomplete for authoritative revision", "23514");
        return new Result(entries,previous);
    }
}
