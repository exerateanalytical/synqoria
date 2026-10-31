package org.synqoria.scheduling;

import java.sql.*;
import java.util.UUID;

/** Transactional outbox draft: only an allowlisted event type and opaque digest. */
public final class AppointmentOutboxStore {
    private AppointmentOutboxStore() {}
    public static final String EVENT_TYPE = "appointment.status_changed";
    public static final String INSERT = "INSERT INTO synqoria_appointment_outbox_quarantine "
        + "(event_id,tenant_id,facility_id,appointment_id,appointment_revision,event_type,payload_digest) "
        + "VALUES (?,?,?,?,?,?,?)";
    public static void append(Connection c, AppointmentTenantContext scope, UUID appointmentId, UUID eventId,
                              long revision, String eventType, String payloadDigest) throws SQLException {
        AppointmentWriteFence.require(c);
        if (scope==null||appointmentId==null||eventId==null||revision<2||!EVENT_TYPE.equals(eventType)
            ||payloadDigest==null||!payloadDigest.matches("[a-f0-9]{64}"))
            throw new SQLException("invalid outbox event");
        try(PreparedStatement ps=c.prepareStatement(INSERT)) {
            ps.setObject(1,eventId);ps.setObject(2,scope.tenantId());ps.setObject(3,scope.facilityId());
            ps.setObject(4,appointmentId);ps.setLong(5,revision);ps.setString(6,eventType);ps.setString(7,payloadDigest);
            if(ps.executeUpdate()!=1)throw new SQLException("outbox append conflict", "40001");
        }
    }
}
