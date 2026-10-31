package org.synqoria.scheduling;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.UUID;

/** Scoped, same-transaction proof that an audit event and outbox payload agree.
 *  Quarantined adapter: not a substitute for independent immutable audit attestation. */
public final class AppointmentEventIntegrityProof {
    private AppointmentEventIntegrityProof() {}
    public static final String SQL = "SELECT a.command_digest,a.previous_digest,a.actor_pseudonym,a.audit_digest,o.payload_digest "
        + "FROM synqoria_appointment_command_audit_quarantine a "
        + "JOIN synqoria_appointment_outbox_quarantine o "
        + "ON o.event_id=a.event_id AND o.tenant_id=a.tenant_id AND o.facility_id=a.facility_id "
        + "AND o.appointment_id=a.appointment_id AND o.appointment_revision=a.appointment_revision "
        + "JOIN synqoria_appointment_reservations_quarantine r "
        + "ON r.appointment_id=a.appointment_id AND r.tenant_id=a.tenant_id "
        + "AND r.facility_id=a.facility_id AND r.patient_id=? "
        + "WHERE a.event_id=? AND a.tenant_id=? AND a.facility_id=? "
        + "AND a.appointment_id=? AND a.appointment_revision=? "
        + "AND o.event_type='appointment.status_changed'";
    private static boolean equalDigest(String a,String b) {
        return a!=null&&b!=null&&a.matches("[0-9a-f]{64}")&&b.matches("[0-9a-f]{64}")
            &&MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII),b.getBytes(StandardCharsets.US_ASCII));
    }
    public static void require(Connection c, AppointmentTenantContext scope, UUID appointment,
                               UUID event, long revision, String expectedCommandDigest) throws SQLException {
        AppointmentWriteFence.require(c);
        if(scope==null||appointment==null||event==null||revision<2
            ||expectedCommandDigest!=null&&!expectedCommandDigest.matches("[0-9a-f]{64}"))
            throw new SQLException("invalid appointment event proof", "22023");
        try(PreparedStatement ps=c.prepareStatement(SQL)) {
            ps.setObject(1,scope.patientId());ps.setObject(2,event);ps.setObject(3,scope.tenantId());
            ps.setObject(4,scope.facilityId());ps.setObject(5,appointment);ps.setLong(6,revision);
            try(ResultSet rs=ps.executeQuery()) {
                if(!rs.next())throw new SQLException("appointment event proof unavailable", "40001");
                String command=rs.getString(1),previous=rs.getString(2),actor=rs.getString(3),
                    audit=rs.getString(4),outbox=rs.getString(5);
                if(expectedCommandDigest!=null&&!equalDigest(expectedCommandDigest,command))
                    throw new SQLException("appointment event command mismatch", "23514");
                String calculated;
                try {calculated=AppointmentAuditStore.digestFor(scope,appointment,revision,previous,command,actor);}
                catch(IllegalArgumentException ex){throw new SQLException("appointment event record invalid", "23514",ex);}
                if(!equalDigest(calculated,audit)||!equalDigest(audit,outbox)||rs.next())
                    throw new SQLException("appointment audit/outbox integrity mismatch", "23514");
            }
        }
    }
}
