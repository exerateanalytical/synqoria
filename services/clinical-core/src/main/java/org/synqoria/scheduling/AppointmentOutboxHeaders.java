package org.synqoria.scheduling;
import java.util.Map;import java.util.UUID;
/** Minimal safe event metadata: no patient name, diagnosis, or appointment reason. */
public final class AppointmentOutboxHeaders {
 private AppointmentOutboxHeaders(){}
 public static Map<String,String> create(UUID eventId,UUID tenantId,UUID facilityId,long revision){
  if(eventId==null||tenantId==null||facilityId==null||revision<=0)throw new IllegalArgumentException("invalid event metadata");
  return Map.of("event_id",eventId.toString(),"tenant_id",tenantId.toString(),"facility_id",facilityId.toString(),"revision",Long.toString(revision),"schema_version","1");
 }
}
