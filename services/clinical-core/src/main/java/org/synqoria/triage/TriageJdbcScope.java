package org.synqoria.triage;

import java.sql.*;
import java.util.*;

/** Installs and verifies backend-derived patient scope inside the caller's SQL transaction. */
public final class TriageJdbcScope {
 private TriageJdbcScope(){}
 public record Scope(UUID tenant,UUID facility,UUID patient,UUID actor){
  public Scope{if(tenant==null||facility==null||patient==null||actor==null)throw new IllegalArgumentException("missing triage scope");}
 }
 public static final String SQL="SELECT set_config('app.tenant_id', ?, true), set_config('app.facility_id', ?, true), set_config('app.patient_id', ?, true), set_config('app.actor_id', ?, true)";
 public static void install(Connection c,Scope scope)throws SQLException {
  if(c==null||scope==null||c.getAutoCommit())throw new SQLException("transactional scope required","42501");
  UUID[] ids={scope.tenant(),scope.facility(),scope.patient(),scope.actor()};
  try(PreparedStatement s=c.prepareStatement(SQL)){
   for(int i=0;i<ids.length;i++)s.setString(i+1,ids[i].toString());
   try(ResultSet rs=s.executeQuery()){
    if(!rs.next())throw new SQLException("scope echo unavailable","42501");
    for(int i=0;i<ids.length;i++)if(!ids[i].toString().equals(rs.getString(i+1)))throw new SQLException("scope echo mismatch","42501");
    if(rs.next())throw new SQLException("ambiguous scope echo","42501");
   }
  }
 }
}
