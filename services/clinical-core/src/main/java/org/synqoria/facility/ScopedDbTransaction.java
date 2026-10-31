package org.synqoria.facility;
import org.synqoria.security.TrustedAccessContext;
import java.sql.*;import java.util.*;
/** SET LOCAL on an existing explicit transaction, using parameter binding, never arbitrary tenant headers. */
public final class ScopedDbTransaction {
 private ScopedDbTransaction(){}
 public static void bind(Connection connection,TrustedAccessContext actor) throws SQLException {
  if(connection==null||actor==null||connection.getAutoCommit()||connection.isReadOnly())throw new SQLException("Explicit writable trusted transaction required");
  try(PreparedStatement p=connection.prepareStatement("SELECT set_config('app.tenant_id', ?, true), set_config('app.facility_id', ?, true), set_config('app.actor_id', ?, true)")){
   p.setString(1,actor.tenantId().toString());p.setString(2,actor.facilityId().toString());p.setString(3,actor.actorId().toString());p.setQueryTimeout(5);
   try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("Missing scope confirmation");}
  }
 }
}
