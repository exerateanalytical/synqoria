package org.synqoria.longitudinal;

import java.sql.*;
import java.util.Objects;
import javax.sql.DataSource;
import static org.synqoria.longitudinal.ClinicalCaseCore.Scope;

/** Transaction-local tenant/facility/patient/encounter context and role eligibility gate.
 * Deployment requires a genuinely restricted, non-owner database role and verified V022 RLS.
 * This utility is NOT an application grant or proof of real database isolation. */
public final class LongitudinalInboxJdbcBoundary {
  private LongitudinalInboxJdbcBoundary() {}
  public interface Work<T>{T execute(Connection db) throws SQLException;}
  private static final String BIND="SELECT set_config('app.tenant_id', ?, true),"+
      "set_config('app.facility_id', ?, true),set_config('app.patient_id', ?, true),"+
      "set_config('app.encounter_id', ?, true)";
  private static final String ROLE="SELECT current_user, current_setting('row_security'),"+
      "COALESCE((SELECT rolbypassrls OR rolsuper FROM pg_roles WHERE rolname=current_user),true)";
  private static void unique(ResultSet rs)throws SQLException{
    if(rs.next())throw new SQLException("ambiguous connection proof","42501");
  }
  public static <T> T transact(DataSource dataSource,Scope scope,String approvedRestrictedRole,Work<T> work) {
    Objects.requireNonNull(dataSource);Objects.requireNonNull(scope);Objects.requireNonNull(work);
    if(approvedRestrictedRole==null||!approvedRestrictedRole.matches("synqoria_inbox_[a-z0-9_]{1,48}"))
      throw new SecurityException("clinical inbox role unavailable");
    try(Connection db=dataSource.getConnection()) {
      if(!db.getAutoCommit())throw new SecurityException("unmanaged connection refused");
      db.setAutoCommit(false);
      try {
        db.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        try(PreparedStatement ps=db.prepareStatement(BIND)) {
          ps.setString(1,scope.tenant().toString());ps.setString(2,scope.facility().toString());
          ps.setString(3,scope.patient().toString());ps.setString(4,scope.encounter().toString());
          ps.setQueryTimeout(5);
          try(ResultSet rs=ps.executeQuery()) {
            if(!rs.next()||!rs.getString(1).equals(scope.tenant().toString())||
              !rs.getString(2).equals(scope.facility().toString())||
              !rs.getString(3).equals(scope.patient().toString())||
              !rs.getString(4).equals(scope.encounter().toString()))
              throw new SecurityException("clinical inbox scope unavailable");
            unique(rs);
          }
        }
        try(PreparedStatement ps=db.prepareStatement(ROLE)) {
          ps.setQueryTimeout(5);
          try(ResultSet rs=ps.executeQuery()) {
            if(!rs.next()||!approvedRestrictedRole.equals(rs.getString(1))||
                !"on".equals(rs.getString(2))||rs.getBoolean(3))
              throw new SecurityException("clinical inbox restricted role unavailable");
            unique(rs);
          }
        }
        T result=work.execute(db);
        db.commit();return result;
      } catch(Throwable failure) {
        try{db.rollback();}catch(SQLException rollback){failure.addSuppressed(rollback);}
        if(failure instanceof Error e)throw e;
        throw new SecurityException("clinical inbox transaction unavailable");
      }
    } catch(SQLException e){throw new SecurityException("clinical inbox database unavailable");}
  }
}
