package org.synqoria.longitudinal;
import java.time.*;import java.util.*;
/** Partition generation and ownership fence; host must obtain assignment from authenticated broker. */
public final class LongitudinalInboxPartitionOwnership {
  private LongitudinalInboxPartitionOwnership(){}
  public record Assignment(String topic,int partition,UUID consumerId,long generation,Instant expiresAt){
    public Assignment {
      if(!"synqoria.clinical.events.v1".equals(topic)||partition<0||partition>4095||generation<1)
        throw new IllegalArgumentException("invalid partition assignment");
      Objects.requireNonNull(consumerId);Objects.requireNonNull(expiresAt);
    }
    public void require(UUID consumer,long activeGeneration,int actualPartition,Instant now){
      Objects.requireNonNull(now);
      if(!consumerId.equals(consumer)||generation!=activeGeneration||partition!=actualPartition||
          !now.isBefore(expiresAt))throw new SecurityException("partition ownership unavailable");
    }
  }
}
