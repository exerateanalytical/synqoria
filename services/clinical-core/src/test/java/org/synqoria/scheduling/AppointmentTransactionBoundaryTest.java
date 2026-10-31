package org.synqoria.scheduling;
import java.lang.reflect.*;import java.sql.*;
public final class AppointmentTransactionBoundaryTest {
 public static void main(String[] args)throws Exception {
  Connection c=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
   switch(m.getName()){case "getAutoCommit":return false;case "isReadOnly":return false;case "getTransactionIsolation":return Connection.TRANSACTION_READ_COMMITTED;default:throw new SQLException("No DB");}
  });
  try {AppointmentTransactionBoundary.require(c,"synqoria_app_role");throw new AssertionError("must deny unverified DB");}catch(SQLException expected){System.out.println("PASS fail closed without PostgreSQL");}
 }
}
