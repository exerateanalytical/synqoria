package org.synqoria.scheduling;
import java.sql.*; public final class AppointmentWriteFence {private AppointmentWriteFence(){} public static void require(Connection connection)throws SQLException {if(connection==null||connection.getAutoCommit()||connection.getTransactionIsolation()!=Connection.TRANSACTION_SERIALIZABLE)throw new SQLException("restricted serializable transaction required");} }
