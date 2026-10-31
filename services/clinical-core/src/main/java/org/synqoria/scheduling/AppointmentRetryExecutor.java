package org.synqoria.scheduling;
import java.sql.SQLException;
/** Retries only retryable SQL failures; never retries arbitrary application failures. */
public final class AppointmentRetryExecutor {
 private AppointmentRetryExecutor(){}
 @FunctionalInterface public interface SqlOperation<T>{T run() throws SQLException;}
 public static <T> T execute(SqlOperation<T> operation) throws SQLException {
  if(operation==null)throw new IllegalArgumentException("operation required");
  for(int attempt=1;;attempt++){
   try{return operation.run();}
   catch(SQLException failure){if(!AppointmentRetryPolicy.retryable(failure,attempt))throw failure;}
  }
 }
}
