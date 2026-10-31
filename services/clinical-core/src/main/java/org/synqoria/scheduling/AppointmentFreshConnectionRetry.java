package org.synqoria.scheduling;

import javax.sql.DataSource;
import java.sql.*;

/** Retry entire transaction only on a NEW connection, never reuse a failed transaction. */
public final class AppointmentFreshConnectionRetry {
    private AppointmentFreshConnectionRetry() {}
    @FunctionalInterface public interface Work<T> {T execute(Connection connection) throws SQLException;}
    public static <T> T execute(DataSource source, Work<T> work) throws SQLException {
        if(source==null||work==null)throw new SQLException("transaction retry inputs required");
        for(int attempt=1;;attempt++) {
            try(Connection c=source.getConnection()) {
                if(c==null||!c.getAutoCommit()||c.isReadOnly())
                    throw new SQLException("connection not in clean writable state");
                c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                c.setAutoCommit(false);
                try {
                    // Work MUST commit itself (e.g. AppointmentRestrictedTransaction) on success.
                    return work.execute(c);
                }catch(SQLException|RuntimeException failure) {
                    try{c.rollback();}catch(SQLException rollback){failure.addSuppressed(rollback);}
                    throw failure;
                }
            }catch(SQLException failure) {
                if(!AppointmentRetryPolicy.retryable(failure,attempt))throw failure;
            }
        }
    }
}
