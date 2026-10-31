package org.synqoria.scheduling;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.util.*;

/** Scoped append-only audit draft. Protected DB permissions and live integrity tests still required. */
public final class AppointmentAuditStore {
    private AppointmentAuditStore() {}
    public static final String PREVIOUS = "SELECT appointment_revision,audit_digest,previous_digest,command_digest,actor_pseudonym FROM synqoria_appointment_command_audit_quarantine "
        + "WHERE tenant_id=? AND facility_id=? AND appointment_id=? ORDER BY appointment_revision DESC LIMIT 1 FOR UPDATE";
    public static final String INSERT = "INSERT INTO synqoria_appointment_command_audit_quarantine "
        + "(event_id,tenant_id,facility_id,appointment_id,actor_pseudonym,appointment_revision,command_digest,previous_digest,audit_digest) "
        + "VALUES (?,?,?,?,?,?,?,?,?)";
    static String digest(String payload) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    public static String digestFor(AppointmentTenantContext scope, UUID appointmentId, long revision,
                                   String previous, String commandDigest, String actorPseudonym) {
        if(scope==null||appointmentId==null||revision<2||previous==null||!previous.matches("[0-9a-f]{64}")
            ||commandDigest==null||!commandDigest.matches("[0-9a-f]{64}")||actorPseudonym==null
            ||!actorPseudonym.matches("[A-Za-z0-9_-]{8,64}"))throw new IllegalArgumentException("invalid audit digest input");
        return digest("synqoria-appointment-audit-v1:"+scope.tenantId()+":"+scope.facilityId()+":"
            +appointmentId+":"+revision+":"+previous+":"+commandDigest+":"+actorPseudonym);
    }
    public static String append(Connection c, AppointmentTenantContext scope, UUID appointmentId, UUID eventId,
                                long newRevision, String commandDigest, String actorPseudonym) throws SQLException {
        AppointmentWriteFence.require(c);
        if (scope==null||appointmentId==null||eventId==null||newRevision<2||commandDigest==null
            ||!commandDigest.matches("[0-9a-f]{64}")||actorPseudonym==null
            ||!actorPseudonym.matches("[A-Za-z0-9_-]{8,64}")) throw new SQLException("invalid audit command");
        String previous="0".repeat(64);
        try (PreparedStatement ps=c.prepareStatement(PREVIOUS)) {
            ps.setObject(1,scope.tenantId());ps.setObject(2,scope.facilityId());ps.setObject(3,appointmentId);
            try(ResultSet rs=ps.executeQuery()) {
                if(rs.next()) {
                    long priorRevision=rs.getLong(1);
                    previous=rs.getString(2);
                    if(priorRevision!=newRevision-1||previous==null||!previous.matches("[0-9a-f]{64}"))
                        throw new SQLException("audit history discontinuity", "23514");
                    String computedPrior;
                    try {computedPrior=digestFor(scope,appointmentId,priorRevision,rs.getString(3),rs.getString(4),rs.getString(5));}
                    catch(IllegalArgumentException ex){throw new SQLException("prior audit record invalid", "23514",ex);}
                    if(!previous.equals(computedPrior)||rs.next())
                        throw new SQLException("prior audit digest integrity mismatch", "23514");
                } else if (newRevision != 2) {
                    throw new SQLException("audit genesis revision discontinuity", "23514");
                }
            }
        }
        String next=digestFor(scope,appointmentId,newRevision,previous,commandDigest,actorPseudonym);
        try(PreparedStatement ps=c.prepareStatement(INSERT)) {
            ps.setObject(1,eventId);ps.setObject(2,scope.tenantId());ps.setObject(3,scope.facilityId());
            ps.setObject(4,appointmentId);ps.setString(5,actorPseudonym);ps.setLong(6,newRevision);
            ps.setString(7,commandDigest);ps.setString(8,previous);ps.setString(9,next);
            if(ps.executeUpdate()!=1)throw new SQLException("audit insert conflict", "40001");
        }
        return next;
    }
}
