package org.synqoria.scheduling;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.HexFormat;
import java.util.UUID;

/** Draft transactional idempotency adapter for quarantined schema; no live database verification. */
public final class AppointmentIdempotencyStore {
    private AppointmentIdempotencyStore() {}
    public enum Kind { NEW, REPLAY }
    public record Receipt(Kind kind, long resultRevision, UUID eventId) {}
    public static final String INSERT = "INSERT INTO synqoria_appointment_idempotency_quarantine "
        + "(tenant_id,facility_id,actor_id,patient_id,key_digest,command_digest,state,appointment_id) "
        + "VALUES (?,?,?,?,?,?,'PENDING',?) ON CONFLICT (tenant_id,facility_id,actor_id,key_digest) DO NOTHING";
    public static final String SELECT = "SELECT patient_id,command_digest,state,appointment_id,result_revision,event_id "
        + "FROM synqoria_appointment_idempotency_quarantine "
        + "WHERE tenant_id=? AND facility_id=? AND actor_id=? AND key_digest=? FOR UPDATE";
    public static final String COMPLETE = "UPDATE synqoria_appointment_idempotency_quarantine "
        + "SET state='COMMITTED',result_revision=?,event_id=? "
        + "WHERE tenant_id=? AND facility_id=? AND actor_id=? AND patient_id=? AND key_digest=? "
        + "AND command_digest=? AND appointment_id=? AND state='PENDING'";
    public static String keyDigest(String key) {
        AppointmentIdempotencyKey.validate(key);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(("synqoria-idempotency-v1:" + key).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    public static Receipt acquire(Connection c, AppointmentTenantContext scope, UUID appointmentId,
                                  String key, String commandDigest) throws SQLException {
        AppointmentWriteFence.require(c);
        if (scope == null || appointmentId == null || commandDigest == null || !commandDigest.matches("[0-9a-f]{64}"))
            throw new SQLException("invalid command envelope");
        String keyHash;
        try { keyHash = keyDigest(key); }
        catch (IllegalArgumentException ex) { throw new SQLException("invalid idempotency key", "22023", ex); }
        int inserted;
        try (PreparedStatement ps = c.prepareStatement(INSERT)) {
            ps.setObject(1,scope.tenantId()); ps.setObject(2,scope.facilityId()); ps.setObject(3,scope.actorId());
            ps.setObject(4,scope.patientId()); ps.setString(5,keyHash); ps.setString(6,commandDigest);
            ps.setObject(7,appointmentId); inserted = ps.executeUpdate();
        }
        if (inserted < 0 || inserted > 1) throw new SQLException("idempotency insert anomaly");
        try (PreparedStatement ps = c.prepareStatement(SELECT)) {
            ps.setObject(1,scope.tenantId()); ps.setObject(2,scope.facilityId());
            ps.setObject(3,scope.actorId()); ps.setString(4,keyHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("idempotency unavailable");
                UUID patient=(UUID)rs.getObject(1), storedAppointment=(UUID)rs.getObject(4);
                String storedDigest=rs.getString(2), state=rs.getString(3);
                long revision=rs.getLong(5); boolean revisionNull=rs.wasNull(); UUID event=(UUID)rs.getObject(6);
                if (rs.next() || !scope.patientId().equals(patient) || !appointmentId.equals(storedAppointment)
                    || !MessageDigest.isEqual(commandDigest.getBytes(StandardCharsets.US_ASCII),
                                               (storedDigest==null?"":storedDigest).getBytes(StandardCharsets.US_ASCII)))
                    throw new SQLException("idempotency scope or payload conflict", "23505");
                if (inserted==1 && "PENDING".equals(state) && event==null && revisionNull)
                    return new Receipt(Kind.NEW,0,null);
                if (inserted==0 && "COMMITTED".equals(state) && !revisionNull && revision>0 && event!=null)
                    return new Receipt(Kind.REPLAY,revision,event);
                throw new SQLException("idempotency transaction pending or invalid", "40001");
            }
        }
    }
    public static void complete(Connection c, AppointmentTenantContext scope, UUID appointmentId,
                                String key, String commandDigest, long revision, UUID eventId) throws SQLException {
        AppointmentWriteFence.require(c);
        if (scope==null||appointmentId==null||revision<1||eventId==null||commandDigest==null||!commandDigest.matches("[0-9a-f]{64}"))
            throw new SQLException("invalid idempotency outcome");
        String hash=keyDigest(key);
        try(PreparedStatement ps=c.prepareStatement(COMPLETE)) {
            ps.setLong(1,revision); ps.setObject(2,eventId); ps.setObject(3,scope.tenantId());
            ps.setObject(4,scope.facilityId()); ps.setObject(5,scope.actorId()); ps.setObject(6,scope.patientId());
            ps.setString(7,hash); ps.setString(8,commandDigest); ps.setObject(9,appointmentId);
            if(ps.executeUpdate()!=1)throw new SQLException("idempotency completion conflict", "40001");
        }
    }
}
