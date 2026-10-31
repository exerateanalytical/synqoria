package org.synqoria.scheduling;
public final class AppointmentAuditSequence {
 private AppointmentAuditSequence(){}
 public static long next(long previous,long submitted){
  if(previous<0||previous==Long.MAX_VALUE||submitted!=previous+1)throw new IllegalStateException("audit sequence gap or replay");
  return submitted;
 }
}
