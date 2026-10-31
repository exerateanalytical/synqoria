package org.synqoria.persistence;
import java.sql.*;
import java.util.*;
/** P01-B0022. Same-connection insert only; NEVER call commit from this component. */
public final class AuditWriteJdbc {
 public static final String SQL="INSERT INTO clinical_read_audit (tenant_id, facility_id, event_id, actor_id, patient_id, operation, occurred_at, correlation_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
 private AuditWriteJdbc() {}
 public static void insert(Connection connection,ClinicalAuditEntry e) throws SQLException {
  Objects.requireNonNull(connection);Objects.requireNonNull(e);
  if(connection.getAutoCommit()||connection.isReadOnly())throw new SQLException("Audit requires writable caller transaction");
  try(PreparedStatement q=connection.prepareStatement(SQL)){
   q.setObject(1,e.tenantId());q.setObject(2,e.facilityId());q.setObject(3,e.eventId());
   q.setObject(4,e.actorId());q.setObject(5,e.patientId());q.setString(6,e.operation());
   q.setTimestamp(7,Timestamp.from(e.occurredAt()));q.setObject(8,e.correlationId());
   q.setQueryTimeout(3);
   if(q.executeUpdate()!=1)throw new SQLException("Audit insert did not commit one row");
  }
 }
}
