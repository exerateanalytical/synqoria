package org.synqoria.reliability;
import java.nio.charset.StandardCharsets;import java.util.*;
/** Pure deterministic event identity. Must be persisted with the business write in ONE DB transaction. */
public record OutboxIntent(UUID eventId, UUID tenantId, UUID aggregateId, long aggregateVersion,
                           String eventType, String schemaVersion, String payloadSha256) {
  public OutboxIntent {
    Objects.requireNonNull(eventId);Objects.requireNonNull(tenantId);Objects.requireNonNull(aggregateId);
    if(aggregateVersion<1 || !safe(eventType) || !safe(schemaVersion) || payloadSha256==null || !payloadSha256.matches("[a-f0-9]{64}"))
      throw new IllegalArgumentException("Invalid event intent");
  }
  private static boolean safe(String s){return s!=null && s.matches("[A-Za-z0-9_.-]{1,96}");}
  public static OutboxIntent create(UUID tenant,UUID aggregate,long version,String type,String schema,String payloadHash){
    Objects.requireNonNull(tenant);Objects.requireNonNull(aggregate);
    if(!safe(type)||!safe(schema))throw new IllegalArgumentException("Invalid event type");
    var key=tenant+"/"+aggregate+"/"+version+"/"+type;
    return new OutboxIntent(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)),tenant,aggregate,version,type,schema,payloadHash);
  }
}
