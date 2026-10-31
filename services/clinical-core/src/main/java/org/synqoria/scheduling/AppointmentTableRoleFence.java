package org.synqoria.scheduling;

import java.sql.*;
import java.util.*;

/** Fail closed unless all four quarantined tables FORCE RLS and the role owns none of them. */
public final class AppointmentTableRoleFence {
    private AppointmentTableRoleFence() {}
    private static final Set<String> TABLES=Set.of(
        "synqoria_appointment_reservations_quarantine",
        "synqoria_appointment_command_audit_quarantine",
        "synqoria_appointment_outbox_quarantine",
        "synqoria_appointment_idempotency_quarantine");
    public static final String SQL = "SELECT c.relname,c.relrowsecurity,c.relforcerowsecurity,pg_get_userbyid(c.relowner) "
        + "FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace "
        + "WHERE n.nspname='public' AND c.relname IN (" +
        "'synqoria_appointment_reservations_quarantine','synqoria_appointment_command_audit_quarantine'," +
        "'synqoria_appointment_outbox_quarantine','synqoria_appointment_idempotency_quarantine')";
    public static void require(Connection c, String role) throws SQLException {
        if(c==null||role==null||!role.matches("[a-z][a-z0-9_]{3,62}"))throw new SQLException("invalid restricted role");
        Set<String> seen=new HashSet<>();
        try(Statement stmt=c.createStatement();ResultSet rs=stmt.executeQuery(SQL)) {
            while(rs.next()) {
                String table=rs.getString(1),owner=rs.getString(4);
                if(!TABLES.contains(table)||!seen.add(table)||!rs.getBoolean(2)||!rs.getBoolean(3)||role.equals(owner))
                    throw new SQLException("appointment table RLS/ownership proof denied");
            }
        }
        if(!seen.equals(TABLES))throw new SQLException("incomplete appointment RLS proof");
    }
}
