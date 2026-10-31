package org.synqoria.scheduling;
import org.synqoria.security.TrustedAccessContext;
import java.util.*;
/** Strict patient-scoped authorization. Input MUST be backend verified, never request headers. */
public final class AppointmentAccessPolicy {
 public enum Action { READ, REQUEST, MANAGE }
 public boolean permits(TrustedAccessContext ctx,AppointmentAggregate booking,Action action){
  if(ctx==null || booking==null || action==null)return false;
  if(!ctx.tenantId().equals(booking.tenant()) || !ctx.facilityId().equals(booking.facility()))return false;
  if(!ctx.permittedPatientIds().contains(booking.patient()))return false;
  String capability=switch(action){case READ->"appointment:read";case REQUEST->"appointment:request";case MANAGE->"appointment:manage";};
  return ctx.capabilities().contains(capability);
 }
}
