package org.synqoria.longitudinal;
import java.util.*;
/** Contiguous offset commit policy: no crossing an unacknowledged gap; no global offset sharing. */
public final class LongitudinalInboxPartitionOffset {
 private LongitudinalInboxPartitionOffset() {}
 public record Partition(String topic,int number) {
  public Partition {if(!"synqoria.clinical.events.v1".equals(topic)||number<0||number>4095)
   throw new IllegalArgumentException("invalid partition");}
 }
 public static final class Window {
  private final Partition partition;private long committed;private final NavigableSet<Long> completed=new TreeSet<>();
  public Window(Partition partition,long lastCommitted){this.partition=Objects.requireNonNull(partition);
   if(lastCommitted < -1)throw new IllegalArgumentException("offset");committed=lastCommitted;}
  public synchronized long completed(Partition p,long offset){
   if(!partition.equals(p)||offset<0||offset>committed+1000)throw new SecurityException("partition offset unavailable");
   if(offset<=committed)return committed;
   completed.add(offset);while(completed.remove(committed+1))committed++;
   return committed;
  }
  public synchronized long committed(){return committed;}
  public synchronized int pending(){return completed.size();}
 }
}
