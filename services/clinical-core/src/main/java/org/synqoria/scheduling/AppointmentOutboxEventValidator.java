package org.synqoria.scheduling;
import java.util.Map;import java.util.Set;import java.util.UUID;
/** Validates mandatory metadata and rejects unexpected fields that might leak PHI. */
public final class AppointmentOutboxEventValidator{
 private AppointmentOutboxEventValidator(){}
 private static final Set<String> KEYS=Set.of("event_id","tenant_id","facility_id","revision","schema_version");
 public static void require(Map<String,String> headers){
  if(headers==null||!headers.keySet().equals(KEYS))throw new IllegalArgumentException("unsafe or incomplete headers");
  for(String key:new String[]{"event_id","tenant_id","facility_id"}){
   try{UUID u=UUID.fromString(headers.get(key));if(!u.toString().equals(headers.get(key)))throw new IllegalArgumentException("noncanonical uuid");}
   catch(RuntimeException e){throw new IllegalArgumentException("invalid "+key,e);}
  }
  try{long r=Long.parseLong(headers.get("revision"));if(r<=0||!Long.toString(r).equals(headers.get("revision")))throw new IllegalArgumentException();}
  catch(RuntimeException e){throw new IllegalArgumentException("invalid revision",e);}
  if(!"1".equals(headers.get("schema_version")))throw new IllegalArgumentException("unsupported schema");
 }
}
