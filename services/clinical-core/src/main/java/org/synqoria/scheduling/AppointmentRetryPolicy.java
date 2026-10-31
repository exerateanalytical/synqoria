package org.synqoria.scheduling;
import java.sql.SQLException;
/** Only serialization conflicts and deadlocks are candidates for bounded whole-transaction retries. */
public final class AppointmentRetryPolicy {
 private AppointmentRetryPolicy(){}
 public static boolean retryable(SQLException error,int attempt){
  if(error==null||attempt<1||attempt>=3)return false;
  String state=error.getSQLState();return "40001".equals(state)||"40P01".equals(state);
 }
}
