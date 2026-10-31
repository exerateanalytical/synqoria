package org.synqoria.scheduling;
import java.sql.*;
/** Fail-closed transaction entry: caller owns commit and rollback. */
public final class AppointmentTransactionBoundary {
 private AppointmentTransactionBoundary(){}
 public static void require(Connection c,String role)throws SQLException {
  AppointmentJdbcSafetyGate.require(c,role);
  if(c.getTransactionIsolation()!=Connection.TRANSACTION_SERIALIZABLE)throw new SQLException("Serializable transaction required");
 }
}
