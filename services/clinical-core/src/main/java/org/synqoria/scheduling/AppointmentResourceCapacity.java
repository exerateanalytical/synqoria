package org.synqoria.scheduling;
public final class AppointmentResourceCapacity {
 private AppointmentResourceCapacity(){}
 public static void requireAvailable(int capacity,int occupied,int requested){
  if(capacity<1||occupied<0||requested<1||occupied>capacity||requested>capacity-occupied)throw new IllegalArgumentException("resource capacity denied");
 }
}
