package org.synqoria.scheduling;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Server-side canonical command envelope; digest binds scope, target, revision and appointment. */
public record AppointmentTransitionCommand(UUID appointmentId, String targetLifecycle,
                                           long expectedRevision, String idempotencyKey, UUID eventId) {
    public AppointmentTransitionCommand {
        if (appointmentId==null||eventId==null||expectedRevision<1)throw new IllegalArgumentException("invalid transition envelope");
        AppointmentStateCodec.decode(targetLifecycle);
        AppointmentIdempotencyKey.validate(idempotencyKey);
    }
    public String digest(AppointmentTenantContext scope) {
        if(scope==null)throw new IllegalArgumentException("trusted scope required");
        try {
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            for(String part:List.of("synqoria-appointment-transition-v1",scope.tenantId().toString(),
                scope.facilityId().toString(),scope.actorId().toString(),scope.patientId().toString(),
                appointmentId.toString(),targetLifecycle,Long.toString(expectedRevision))) {
                byte[] b=part.getBytes(StandardCharsets.UTF_8);
                md.update(ByteBuffer.allocate(4).putInt(b.length).array());md.update(b);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch(NoSuchAlgorithmException ex) {throw new IllegalStateException(ex);}
    }
}
