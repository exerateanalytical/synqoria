package org.synqoria.scheduling;
import java.sql.*;import java.util.*;
/** Internal restricted DB safety gate; NOT a transaction adapter and never commits. */
public final class AppointmentJdbcSafetyGate {
 private AppointmentJdbcSafetyGate(){}
 public static void require(Connection connection,String expectedRole)throws SQLException{
  if(connection==null||expectedRole==null||!expectedRole.matches("[a-z][a-z0-9_]{3,62}")||connection.getAutoCommit()||connection.isReadOnly())throw new SQLException("Appointment transaction denied");
  try(Statement statement=connection.createStatement();ResultSet rs=statement.executeQuery("SELECT current_user, rolsuper, rolbypassrls FROM pg_roles WHERE rolname = current_user")){
   if(!rs.next()||!expectedRole.equals(rs.getString(1))||rs.getBoolean(2)||rs.getBoolean(3)||rs.next())throw new SQLException("Appointment role denied");
  }
 }
}
