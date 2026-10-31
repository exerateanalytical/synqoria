package org.synqoria.scheduling;

import org.synqoria.security.TrustedAccessContext;
import org.synqoria.security.TrustedClinicalAccessEvidence;
import java.sql.*;
import java.time.Instant;
import java.util.UUID;

/** Quarantined, non-HTTP atomic adapter. No live RLS, restricted role or clinical approval verified. */
public final class AppointmentTransitionService {
    private AppointmentTransitionService() {}
    public record Outcome(long revision, UUID eventId, boolean replay) {}
    public static Outcome execute(Connection c, TrustedAccessContext identity,
                                  TrustedClinicalAccessEvidence evidence, UUID patient, Instant serverNow,
                                  String restrictedDbRole, byte[] pseudonymKey, AppointmentTransitionCommand command) throws SQLException {
        // This must occur before ANY DB interaction. Only a server-trusted identity/evidence is admissible.
        AppointmentTenantContext scope=AppointmentCommandAdmission.require(identity,evidence,patient,serverNow);
        if(command==null)throw new SQLException("appointment command required");
        String actorPseudonym=AppointmentActorPseudonym.derive(scope.tenantId(),scope.actorId(),pseudonymKey);
        return AppointmentRestrictedTransaction.execute(c,scope,restrictedDbRole,connection->{
            String digest=command.digest(scope);
            var receipt=AppointmentIdempotencyStore.acquire(connection,scope,command.appointmentId(),
                command.idempotencyKey(),digest);
            if(receipt.kind()==AppointmentIdempotencyStore.Kind.REPLAY) {
                AppointmentReplayProof.require(connection,scope,command.appointmentId(),receipt.eventId(),receipt.resultRevision(),digest);
                AppointmentAuditIntegrityReader.verify(connection,scope,command.appointmentId());
                return new Outcome(receipt.resultRevision(),receipt.eventId(),true);
            }
            var snapshot=AppointmentLockedSnapshot.read(connection,scope,command.appointmentId());
            if(snapshot.revision()!=command.expectedRevision())
                throw new SQLException("appointment unavailable or stale revision", "40001");
            AppointmentTransitionPolicy.requireAllowed(snapshot.lifecycle(),command.targetLifecycle());
            // Verify the complete bounded audit/outbox history before mutating clinical scheduling state.
            AppointmentAuditIntegrityReader.verify(connection,scope,command.appointmentId());
            long newRevision;
            try {newRevision=Math.addExact(snapshot.revision(),1);}
            catch(ArithmeticException ex) {throw new SQLException("revision exhausted", "22003",ex);}
            try(PreparedStatement ps=connection.prepareStatement(AppointmentTransitionMutation.SQL)) {
                AppointmentTransitionMutation.bind(ps,snapshot.lifecycle(),command.targetLifecycle(),
                    command.appointmentId(),scope.tenantId(),scope.facilityId(),scope.patientId(),snapshot.revision());
                AppointmentTransitionMutation.requireUpdated(ps.executeUpdate());
            }
            String auditDigest=AppointmentAuditStore.append(connection,scope,command.appointmentId(),
                command.eventId(),newRevision,digest,actorPseudonym);
            AppointmentOutboxStore.append(connection,scope,command.appointmentId(),command.eventId(),
                newRevision,AppointmentOutboxStore.EVENT_TYPE,auditDigest);
            AppointmentIdempotencyStore.complete(connection,scope,command.appointmentId(),
                command.idempotencyKey(),digest,newRevision,command.eventId());
            AppointmentCommitProof.require(connection,scope,command.appointmentId(),command.eventId(),
                newRevision,command.targetLifecycle(),digest);
            return new Outcome(newRevision,command.eventId(),false);
        });
    }
}
