package org.synqoria.longitudinal;

import java.sql.*;
import java.util.*;
import static org.synqoria.longitudinal.ClinicalCaseCore.*;

/** Scoped, transaction-local PostgreSQL GUC installation. PostgreSQL RLS must enforce matching scope. */
public final class LongitudinalJdbcScope {
 private LongitudinalJdbcScope(){}
 public static final String SQL="SELECT set_config('app.tenant_id', ?, true), set_config('app.facility_id', ?, true), "+
  "set_config('app.patient_id', ?, true), set_config('app.encounter_id', ?, true)";
 public static void install(Connection c,Scope scope)throws SQLException {
  if(c==null||scope==null||c.getAutoCommit())throw new SQLException("scoped transaction required","42501");
  UUID[] ids={scope.tenant(),scope.facility(),scope.patient(),scope.encounter()};
  try(PreparedStatement p=c.prepareStatement(SQL)){
   p.setQueryTimeout(10);
   for(int i=0;i<ids.length;i++)p.setString(i+1,ids[i].toString());
   try(ResultSet rs=p.executeQuery()){
    if(!rs.next())throw new SQLException("scope echo unavailable","42501");
    for(int i=0;i<ids.length;i++)if(!ids[i].toString().equals(rs.getString(i+1)))
      throw new SQLException("scope echo mismatch","42501");
    if(rs.next())throw new SQLException("ambiguous scope echo","42501");
   }
  }
 }
}
