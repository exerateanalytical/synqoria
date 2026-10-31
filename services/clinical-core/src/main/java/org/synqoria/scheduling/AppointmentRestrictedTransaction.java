package org.synqoria.scheduling;

import java.sql.*;

/** Single restricted-role serializable transaction. Connection is caller-owned. */
public final class AppointmentRestrictedTransaction {
    private AppointmentRestrictedTransaction() {}
    @FunctionalInterface public interface Work<T> { T execute(Connection c) throws SQLException; }
    public static <T> T execute(Connection c, AppointmentTenantContext scope, String expectedDbRole,
                                Work<T> work) throws SQLException {
        if(c==null||scope==null||expectedDbRole==null||work==null)throw new SQLException("transaction inputs required");
        try {
            AppointmentTransactionBoundary.require(c,expectedDbRole);
            AppointmentTableRoleFence.require(c,expectedDbRole);
            AppointmentJdbcScope.install(c,scope);
            AppointmentSqlBudget.install(c,5000,15000,30000);
            T result=work.execute(c);
            c.commit();
            return result;
        } catch(SQLException|RuntimeException failure) {
            try {c.rollback();} catch(SQLException rollback) {failure.addSuppressed(rollback);}
            throw failure;
        }
    }
}
