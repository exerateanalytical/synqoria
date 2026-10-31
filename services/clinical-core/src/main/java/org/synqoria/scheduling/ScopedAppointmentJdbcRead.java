package org.synqoria.scheduling;
import java.sql.*;import java.util.*;
/** LEGACY RECOVERY QUARANTINE: references retired scheduling_appointments table.
 * Not wired to any runtime; MUST NOT be enabled without table mapping, cell scoping,
 * live non-owner PostgreSQL RLS and security review. */
public final class ScopedAppointmentJdbcRead {
 public static final String SQL="SELECT appointment_id FROM scheduling_appointments WHERE cell_id = ? AND tenant_id = ? AND facility_id = ? AND appointment_id = ? AND status <> 'PURGED'";
 public static boolean exists(Connection cx,SchedulingScope scope,UUID appointmentId)throws SQLException{
  if(cx==null||scope==null||appointmentId==null)throw new IllegalArgumentException("Missing lookup evidence");
  if(cx.isClosed()||cx.getAutoCommit()||cx.isReadOnly()==false||cx.getTransactionIsolation()<Connection.TRANSACTION_REPEATABLE_READ)throw new SQLException("Restricted read transaction required");
  try(PreparedStatement st=cx.prepareStatement(SQL)){
   st.setObject(1,scope.cellId());st.setObject(2,scope.tenantId());st.setObject(3,scope.facilityId());st.setObject(4,appointmentId);
   st.setMaxRows(2);try(ResultSet rs=st.executeQuery()){
    if(!rs.next())return false;
    UUID actual=(UUID)rs.getObject(1);if(!appointmentId.equals(actual))throw new SQLException("Lookup mismatch");
    if(rs.next())throw new SQLException("Non-unique appointment");return true;
   }
  }
 }
 private ScopedAppointmentJdbcRead(){}
}
