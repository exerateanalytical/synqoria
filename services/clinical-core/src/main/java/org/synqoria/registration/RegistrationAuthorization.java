package org.synqoria.registration;
import org.synqoria.security.TrustedAccessContext;
import java.util.*;
/** P03-B0017. Facility and actor come from verified session context, not payload. */
public final class RegistrationAuthorization {
 private RegistrationAuthorization(){}
 public static boolean canRegister(TrustedAccessContext actor, UUID facility, boolean activeFacility, boolean validShift, boolean currentGrants){
  return actor!=null&&facility!=null&&facility.equals(actor.facilityId())&&activeFacility&&validShift&&currentGrants
    &&actor.capabilities().contains("patient:register")&&actor.capabilities().contains("patient:identity-review");
 }
}
