package org.synqoria.scheduling;
import java.sql.*;
public final class AppointmentTransactionExecutor {
 private AppointmentTransactionExecutor(){}
 @FunctionalInterface public interface Work<T>{T execute(Connection connection)throws SQLException;}
 public static <T> T execute(Connection connection,AppointmentTenantContext context,Work<T> work)throws SQLException{
  if(connection==null||context==null||work==null)throw new SQLException("transaction inputs required");
  AppointmentWriteFence.require(connection);
  try{AppointmentJdbcScope.install(connection,context);T value=work.execute(connection);connection.commit();return value;}
  catch(SQLException|RuntimeException failure){try{connection.rollback();}catch(SQLException rollback){failure.addSuppressed(rollback);}throw failure;}
 }
}
