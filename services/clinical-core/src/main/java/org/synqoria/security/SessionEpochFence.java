package org.synqoria.security;
import java.time.Instant;
import java.util.*;
/** P03-B0012: fail-closed revocation/policy fence evaluated from trusted storage on admission. */
@FunctionalInterface public interface SessionEpochFence {
 boolean current(TrustedSessionAdmission.VerifiedIdentity identity,Instant now);
 static SessionEpochFence unavailable(){return (id,now)->false;}
}
