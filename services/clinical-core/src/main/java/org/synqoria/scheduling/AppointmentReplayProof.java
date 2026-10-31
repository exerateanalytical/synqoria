package org.synqoria.scheduling;

import java.sql.*;
import java.util.UUID;

/** Before replay acknowledgment, prove the committed event still has durable audit and outbox records. */
public final class AppointmentReplayProof {
    private AppointmentReplayProof() {}
    private static void requireOne(Connection c, String sql, UUID event, AppointmentTenantContext scope,
                                   UUID appointment, long revision) throws SQLException {
        try(PreparedStatement ps=c.prepareStatement(sql)) {
            ps.setObject(1,event);ps.setObject(2,scope.tenantId());ps.setObject(3,scope.facilityId());
            ps.setObject(4,appointment);ps.setLong(5,revision);
            try(ResultSet rs=ps.executeQuery()) {
                if(!rs.next()||rs.getLong(1)!=1||rs.next())
                    throw new SQLException("idempotency replay evidence unavailable", "40001");
            }
        }
    }
    public static void require(Connection c, AppointmentTenantContext scope, UUID appointment,
                               UUID event, long revision) throws SQLException {
        require(c,scope,appointment,event,revision,null);
    }
    public static void require(Connection c, AppointmentTenantContext scope, UUID appointment,
                               UUID event, long revision, String expectedCommandDigest) throws SQLException {
        AppointmentWriteFence.require(c);
        if(scope==null||appointment==null||event==null||revision<2)
            throw new SQLException("invalid replay proof");
        requireOne(c,AppointmentCommitProof.AUDIT,event,scope,appointment,revision);
        requireOne(c,AppointmentCommitProof.OUTBOX,event,scope,appointment,revision);
        AppointmentEventIntegrityProof.require(c,scope,appointment,event,revision,expectedCommandDigest);
    }
}
