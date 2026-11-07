package org.synqoria.longitudinal;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import static org.synqoria.longitudinal.ClinicalCaseCore.Scope;
import static org.synqoria.longitudinal.LongitudinalOutboxDelivery.*;

/**
 * Scoped PostgreSQL, at-least-once delivery lease adapter for V021 quarantine.
 * Runtime roles have NO grants until clinical cell RLS and dispatcher policies are reviewed.
 * Every claim/ack/defer uses a distinct DB transaction; publishing happens outside the DB.
 */
public final class LongitudinalOutboxJdbc implements Store {
    private final DataSource source;
    public LongitudinalOutboxJdbc(DataSource source) { this.source=Objects.requireNonNull(source); }
    @FunctionalInterface private interface Work<T> { T run(Connection db) throws SQLException; }
    private <T> T scoped(Scope scope, Work<T> operation) {
        Objects.requireNonNull(scope);
        try (Connection db=source.getConnection()) {
            if (!db.getAutoCommit()) throw new SQLException("unmanaged transaction refused", "42501");
            db.setAutoCommit(false);
            try {
                db.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                try (Statement s=db.createStatement()) {
                    s.execute("SET LOCAL lock_timeout = '3000ms'");
                    s.execute("SET LOCAL statement_timeout = '10000ms'");
                    s.execute("SET LOCAL idle_in_transaction_session_timeout = '12000ms'");
                }
                LongitudinalJdbcScope.install(db,scope);
                T response=operation.run(db);
                db.commit();return response;
            } catch (Throwable failure) {
                try { db.rollback(); } catch (SQLException e) {failure.addSuppressed(e);}
                if (failure instanceof Error e) throw e;
                if (failure instanceof RuntimeException e) throw e;
                throw new SecurityException("clinical dispatch transaction unavailable", failure);
            }
        } catch(SQLException e) { throw new SecurityException("clinical dispatch unavailable",e); }
    }
    private static void bind(PreparedStatement p, Scope s, int start) throws SQLException {
        p.setObject(start,s.tenant());p.setObject(start+1,s.facility());
        p.setObject(start+2,s.patient());p.setObject(start+3,s.encounter());
    }
    private static void checked(int rows) {
        if(rows<0||rows>1)throw new SecurityException("ambiguous clinical dispatch evidence");
    }
    private static final String SEED="INSERT INTO synqoria_longitudinal_delivery_quarantine "+
      "(tenant_id,facility_id,patient_id,encounter_id,command_id,revision,event_hash,event_type) "+
      "SELECT tenant_id,facility_id,patient_id,encounter_id,command_id,revision,event_hash,event_type "+
      "FROM synqoria_longitudinal_outbox_quarantine "+
      "WHERE tenant_id=? AND facility_id=? AND patient_id=? AND encounter_id=? "+
      "AND NOT EXISTS (SELECT 1 FROM synqoria_longitudinal_delivery_quarantine already "+
      "WHERE already.tenant_id=synqoria_longitudinal_outbox_quarantine.tenant_id "+
      "AND already.facility_id=synqoria_longitudinal_outbox_quarantine.facility_id "+
      "AND already.patient_id=synqoria_longitudinal_outbox_quarantine.patient_id "+
      "AND already.encounter_id=synqoria_longitudinal_outbox_quarantine.encounter_id "+
      "AND already.command_id=synqoria_longitudinal_outbox_quarantine.command_id) "+
      "ORDER BY revision LIMIT 100 ON CONFLICT DO NOTHING";
    // MIN over all unpublished, including parked entries, prevents later events overtaking a poison event.
    // FOR UPDATE SKIP LOCKED is on the single MIN candidate; a competing worker can't skip to revision+1.
    private static final String PARK_EXPIRED="UPDATE synqoria_longitudinal_delivery_quarantine " +
      "SET parked_at=clock_timestamp(),lease_token=NULL,lease_expires_at=NULL " +
      "WHERE tenant_id=? AND facility_id=? AND patient_id=? AND encounter_id=? " +
      "AND attempts>=12 AND published_at IS NULL AND parked_at IS NULL " +
      "AND lease_expires_at<=clock_timestamp()";
    private static final String CLAIM="WITH earliest AS (SELECT d.command_id,d.revision,d.event_hash,d.event_type "+
      "FROM synqoria_longitudinal_delivery_quarantine d "+
      "JOIN synqoria_longitudinal_outbox_quarantine o ON o.tenant_id=d.tenant_id AND o.facility_id=d.facility_id "+
      "AND o.patient_id=d.patient_id AND o.encounter_id=d.encounter_id AND o.command_id=d.command_id "+
      "AND o.revision=d.revision AND o.event_hash=d.event_hash AND o.event_type=d.event_type "+
      "WHERE d.tenant_id=? AND d.facility_id=? AND d.patient_id=? AND d.encounter_id=? "+
      "AND d.published_at IS NULL AND d.parked_at IS NULL AND d.attempts < 12 "+
      "AND (d.lease_expires_at IS NULL OR d.lease_expires_at <= clock_timestamp()) "+
      "AND d.revision=(SELECT min(x.revision) FROM synqoria_longitudinal_delivery_quarantine x "+
      "WHERE x.tenant_id=d.tenant_id AND x.facility_id=d.facility_id AND x.patient_id=d.patient_id "+
      "AND x.encounter_id=d.encounter_id AND x.published_at IS NULL) "+
      "ORDER BY d.revision LIMIT 1 FOR UPDATE OF d SKIP LOCKED) "+
      "UPDATE synqoria_longitudinal_delivery_quarantine d "+
      "SET lease_token=?,lease_expires_at=clock_timestamp()+interval '30 seconds',attempts=d.attempts+1 "+
      "FROM earliest e WHERE d.tenant_id=? AND d.facility_id=? AND d.patient_id=? AND d.encounter_id=? "+
      "AND d.command_id=e.command_id AND d.revision=e.revision AND d.event_hash=e.event_hash "+
      "AND d.published_at IS NULL AND d.parked_at IS NULL "+
      "RETURNING d.command_id,d.revision,d.event_hash,d.event_type,d.attempts";
    private static final String ACK="UPDATE synqoria_longitudinal_delivery_quarantine SET published_at=clock_timestamp(), "+
      "lease_token=NULL,lease_expires_at=NULL "+
      "WHERE tenant_id=? AND facility_id=? AND patient_id=? AND encounter_id=? AND command_id=? AND revision=? "+
      "AND event_hash=? AND lease_token=? AND lease_expires_at>clock_timestamp() "+
      "AND published_at IS NULL AND parked_at IS NULL";
    private static final String DEFER="UPDATE synqoria_longitudinal_delivery_quarantine SET lease_token=NULL, "+
      "lease_expires_at=CASE WHEN attempts>=12 THEN NULL ELSE clock_timestamp()+(? * interval '1 second') END, "+
      "parked_at=CASE WHEN attempts>=12 THEN clock_timestamp() ELSE NULL END "+
      "WHERE tenant_id=? AND facility_id=? AND patient_id=? AND encounter_id=? AND command_id=? AND revision=? "+
      "AND event_hash=? AND lease_token=? AND lease_expires_at>clock_timestamp() "+
      "AND published_at IS NULL AND parked_at IS NULL";
    @Override public Optional<Claimed> claim(Scope scope, UUID dispatcher, Instant now) {
        Objects.requireNonNull(dispatcher);Objects.requireNonNull(now);
        return scoped(scope,db->{
            try(PreparedStatement s=db.prepareStatement(SEED)) {
                s.setQueryTimeout(10);bind(s,scope,1);
                // INSERT may add 0..100 rows. This is not the claim count.
                int inserted=s.executeUpdate();if(inserted<0||inserted>100)
                    throw new SecurityException("clinical delivery projection unavailable");
            }
            try(PreparedStatement expired=db.prepareStatement(PARK_EXPIRED)) {
                expired.setQueryTimeout(10);bind(expired,scope,1);
                int parked=expired.executeUpdate();
                if(parked<0||parked>10000)throw new SecurityException("unexpected parked outbox rows");
            }
            UUID lease=UUID.randomUUID();
            try(PreparedStatement p=db.prepareStatement(CLAIM)) {
                p.setQueryTimeout(10);bind(p,scope,1);p.setObject(5,lease);bind(p,scope,6);
                try(ResultSet rs=p.executeQuery()) {
                    if(!rs.next())return Optional.empty();
                    EventRef ref=new EventRef(scope,(UUID)rs.getObject(1),rs.getLong(2),rs.getString(3),rs.getString(4));
                    Claimed claimed=new Claimed(ref,lease,rs.getInt(5));
                    if(rs.next())throw new SecurityException("multiple outbox leases");
                    return Optional.of(claimed);
                }
            }
        });
    }
    @Override public boolean acknowledge(Claimed claim, Instant now) {
        Objects.requireNonNull(claim);Objects.requireNonNull(now);
        return scoped(claim.event().scope(),db->{try(PreparedStatement p=db.prepareStatement(ACK)) {
            p.setQueryTimeout(10);bind(p,claim.event().scope(),1);
            p.setObject(5,claim.event().commandId());p.setLong(6,claim.event().revision());
            p.setString(7,claim.event().eventHash());p.setObject(8,claim.leaseToken());
            int rows=p.executeUpdate();checked(rows);return rows==1;
        }});
    }
    @Override public boolean defer(Claimed claim, Instant now,Duration retryDelay) {
        Objects.requireNonNull(claim);Objects.requireNonNull(now);
        if(retryDelay==null||retryDelay.isNegative()||retryDelay.isZero()||retryDelay.compareTo(MAX_BACKOFF)>0)
            throw new IllegalArgumentException("invalid retry delay");
        return scoped(claim.event().scope(),db->{try(PreparedStatement p=db.prepareStatement(DEFER)) {
            p.setQueryTimeout(10);p.setLong(1,retryDelay.toSeconds());bind(p,claim.event().scope(),2);
            p.setObject(6,claim.event().commandId());p.setLong(7,claim.event().revision());
            p.setString(8,claim.event().eventHash());p.setObject(9,claim.leaseToken());
            int rows=p.executeUpdate();checked(rows);return rows==1;
        }});
    }
}
