package org.synqoria.scheduling;

import java.sql.*;
import java.util.UUID;

/** Same-transaction consistency proof: appointment, audit, outbox and idempotency must agree. */
public final class AppointmentCommitProof {
    private AppointmentCommitProof() {}
    public static final String APPOINTMENT = "SELECT count(*) FROM synqoria_appointment_reservations_quarantine "
        + "WHERE appointment_id=? AND tenant_id=? AND facility_id=? AND patient_id=? AND revision=? AND lifecycle=?";
    public static final String AUDIT = "SELECT count(*) FROM synqoria_appointment_command_audit_quarantine "
        + "WHERE event_id=? AND tenant_id=? AND facility_id=? AND appointment_id=? AND appointment_revision=?";
    public static final String OUTBOX = "SELECT count(*) FROM synqoria_appointment_outbox_quarantine "
        + "WHERE event_id=? AND tenant_id=? AND facility_id=? AND appointment_id=? AND appointment_revision=? "
        + "AND event_type='appointment.status_changed'";
    public static final String IDEMPOTENCY = "SELECT count(*) FROM synqoria_appointment_idempotency_quarantine "
        + "WHERE tenant_id=? AND facility_id=? AND actor_id=? AND patient_id=? AND appointment_id=? "
        + "AND event_id=? AND result_revision=? AND state='COMMITTED'";
    public static final String IDEMPOTENCY_WITH_DIGEST = IDEMPOTENCY + " AND command_digest=?";
    private static void exactlyOne(Connection c, String sql, Object... args) throws SQLException {
        try(PreparedStatement ps=c.prepareStatement(sql)) {
            for(int i=0;i<args.length;i++)ps.setObject(i+1,args[i]);
            try(ResultSet rs=ps.executeQuery()) {
                if(!rs.next()||rs.getLong(1)!=1||rs.next())throw new SQLException("appointment transaction evidence incomplete", "40001");
            }
        }
    }
    public static void require(Connection c, AppointmentTenantContext scope, UUID appointmentId,
                               UUID eventId, long revision, String lifecycle) throws SQLException {
        require(c,scope,appointmentId,eventId,revision,lifecycle,null);
    }
    public static void require(Connection c, AppointmentTenantContext scope, UUID appointmentId,
                               UUID eventId, long revision, String lifecycle, String expectedCommandDigest) throws SQLException {
        AppointmentWriteFence.require(c);
        if(scope==null||appointmentId==null||eventId==null||revision<2)throw new SQLException("invalid commit proof");
        try {AppointmentStateCodec.decode(lifecycle);}catch(IllegalArgumentException ex){throw new SQLException("invalid lifecycle", "23514",ex);}
        exactlyOne(c,APPOINTMENT,appointmentId,scope.tenantId(),scope.facilityId(),scope.patientId(),revision,lifecycle);
        exactlyOne(c,AUDIT,eventId,scope.tenantId(),scope.facilityId(),appointmentId,revision);
        exactlyOne(c,OUTBOX,eventId,scope.tenantId(),scope.facilityId(),appointmentId,revision);
        if(expectedCommandDigest==null)
            exactlyOne(c,IDEMPOTENCY,scope.tenantId(),scope.facilityId(),scope.actorId(),scope.patientId(),appointmentId,eventId,revision);
        else {
            if(!expectedCommandDigest.matches("[0-9a-f]{64}"))throw new SQLException("invalid command proof digest", "22023");
            exactlyOne(c,IDEMPOTENCY_WITH_DIGEST,scope.tenantId(),scope.facilityId(),scope.actorId(),scope.patientId(),appointmentId,eventId,revision,expectedCommandDigest);
        }
        AppointmentEventIntegrityProof.require(c,scope,appointmentId,eventId,revision,expectedCommandDigest);
    }
}
