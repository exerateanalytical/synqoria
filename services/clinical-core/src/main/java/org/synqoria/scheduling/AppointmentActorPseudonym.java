package org.synqoria.scheduling;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.UUID;

/** Server-keyed, tenant-separated pseudonym. Secret must come from trusted runtime KMS. */
public final class AppointmentActorPseudonym {
    private AppointmentActorPseudonym() {}
    public static String derive(UUID tenant, UUID actor, byte[] secret) {
        if(tenant==null||actor==null||secret==null||secret.length<32)
            throw new SecurityException("actor pseudonym key unavailable");
        int nonzero=0;for(byte b:secret)nonzero|=b;
        if(nonzero==0)throw new SecurityException("actor pseudonym key unavailable");
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret,"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("synqoria-actor-v1:"+tenant+":"+actor)
                .getBytes(StandardCharsets.US_ASCII)));
        }catch(GeneralSecurityException ex){throw new IllegalStateException("pseudonym provider unavailable",ex);}
    }
}
