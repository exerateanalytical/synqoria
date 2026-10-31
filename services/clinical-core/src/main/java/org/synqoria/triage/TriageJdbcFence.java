package org.synqoria.triage;

import java.sql.*;

/** Fail-closed role and RLS preflight. The connection must be a restricted non-owner role. */
public final class TriageJdbcFence {
 private TriageJdbcFence(){}
 public static final String ROLE_SQL="SELECT current_user, r.rolsuper, r.rolbypassrls, r.rolcanlogin FROM pg_roles r WHERE r.rolname=current_user";
 public static final String RLS_SQL="SELECT row_security_active('synqoria_triage_cases_quarantine'::regclass), row_security_active('synqoria_triage_events_quarantine'::regclass), row_security_active('synqoria_triage_outbox_quarantine'::regclass)";
 public static void require(Connection c,String expectedRole)throws SQLException{
  if(c==null||expectedRole==null||!expectedRole.matches("[a-z][a-z0-9_]{2,62}")||c.getAutoCommit()||c.isReadOnly()||c.getTransactionIsolation()!=Connection.TRANSACTION_SERIALIZABLE)
   throw new SQLException("restricted serializable triage transaction required","42501");
  try(PreparedStatement s=c.prepareStatement(ROLE_SQL);ResultSet r=s.executeQuery()){
   if(!r.next()||!expectedRole.equals(r.getString(1))||r.getBoolean(2)||r.getBoolean(3)||!r.getBoolean(4)||r.next())throw new SQLException("triage role not restricted","42501");
  }
  try(PreparedStatement s=c.prepareStatement(RLS_SQL);ResultSet r=s.executeQuery()){
   if(!r.next()||!r.getBoolean(1)||!r.getBoolean(2)||!r.getBoolean(3)||r.next())throw new SQLException("triage RLS not active","42501");
  }
 }
 public static void budget(Connection c)throws SQLException{
  try(PreparedStatement s=c.prepareStatement("SELECT set_config('statement_timeout', '5000', true), set_config('lock_timeout', '1500', true), set_config('idle_in_transaction_session_timeout', '15000', true)");ResultSet r=s.executeQuery()){
   if(!r.next()||!"5s".equals(r.getString(1))&&!"5000".equals(r.getString(1)))throw new SQLException("triage timeout unavailable","42501");
  }
 }
}
