package org.synqoria.scheduling;

import java.sql.*;
import java.util.regex.*;

/** Bounded, transaction-local PostgreSQL timeout configuration with server echo verification. */
public final class AppointmentSqlBudget {
    private AppointmentSqlBudget() {}
    public static final String SQL = "SELECT set_config('lock_timeout', ?, true), "
        + "set_config('statement_timeout', ?, true), "
        + "set_config('idle_in_transaction_session_timeout', ?, true)";
    private static final Pattern DURATION=Pattern.compile("([1-9][0-9]{0,8})(ms|s|min)");
    static long parseMillis(String text) throws SQLException {
        if(text==null)throw new SQLException("SQL timeout echo missing", "42501");
        Matcher matcher=DURATION.matcher(text);
        if(!matcher.matches())throw new SQLException("SQL timeout echo invalid", "42501");
        long value=Long.parseLong(matcher.group(1));
        return switch(matcher.group(2)) {
            case "ms" -> value;
            case "s" -> value*1000L;
            case "min" -> value*60000L;
            default -> throw new SQLException("SQL timeout echo unit invalid", "42501");
        };
    }
    public static void install(Connection c, int lockMs, int statementMs, int idleMs) throws SQLException {
        AppointmentWriteFence.require(c);
        if(lockMs<250||lockMs>10000||statementMs<1000||statementMs>30000||idleMs<statementMs
            ||idleMs>60000||lockMs>=statementMs)throw new SQLException("unsafe appointment SQL budget", "22023");
        int[] expected={lockMs,statementMs,idleMs};
        try(PreparedStatement ps=c.prepareStatement(SQL)) {
            for(int i=0;i<expected.length;i++)ps.setString(i+1,expected[i]+"ms");
            try(ResultSet rs=ps.executeQuery()) {
                if(!rs.next())throw new SQLException("SQL budget not installed", "42501");
                for(int i=0;i<expected.length;i++)
                    if(parseMillis(rs.getString(i+1))!=expected[i])
                        throw new SQLException("SQL timeout budget mismatch", "42501");
                if(rs.next())throw new SQLException("SQL budget ambiguous", "42501");
            }
        }
    }
}
