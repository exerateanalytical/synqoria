package org.synqoria.scheduling;

import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;

public final class AppointmentSchemaAlignmentTest {
    private static int assertions;
    private static void check(boolean b) { assertions++; if (!b) throw new AssertionError("check " + assertions); }
    public static void main(String[] args) throws Exception {
        String sql = AppointmentTransitionMutation.SQL;
        check(sql.contains("UPDATE synqoria_appointment_reservations_quarantine"));
        check(sql.contains("SET lifecycle = ?"));
        check(sql.contains("patient_id = ?"));
        check(sql.contains("revision = ? AND lifecycle = ?"));
        check(!sql.contains("clinical.appointments"));
        Map<Integer,Object> argsBound = new HashMap<>();
        PreparedStatement stmt = (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
            new Class<?>[]{PreparedStatement.class}, (p,m,a) -> {
                if (m.getName().startsWith("set")) { argsBound.put((Integer)a[0],a[1]); return null; }
                throw new UnsupportedOperationException(m.getName());
            });
        UUID id=UUID.randomUUID(),t=UUID.randomUUID(),f=UUID.randomUUID(),pat=UUID.randomUUID();
        AppointmentTransitionMutation.bind(stmt,"REQUESTED","CONFIRMED",id,t,f,pat,7);
        check(argsBound.size()==7);
        check("CONFIRMED".equals(argsBound.get(1)));
        check(id.equals(argsBound.get(2)));
        check(t.equals(argsBound.get(3)));
        check(f.equals(argsBound.get(4)));
        check(pat.equals(argsBound.get(5)));
        check(Long.valueOf(7).equals(argsBound.get(6)));
        check("REQUESTED".equals(argsBound.get(7)));
        argsBound.clear();
        try { AppointmentTransitionMutation.bind(stmt,"CANCELLED","CONFIRMED",id,t,f,pat,7); throw new AssertionError(); }
        catch (SQLException expected) { check("23514".equals(expected.getSQLState())); }
        check(argsBound.isEmpty());
        try { AppointmentScopedMutation.bind(stmt,"BOGUS",id,t,f,pat,7); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { check(true); }
        try { AppointmentTransitionMutation.requireUpdated(0); throw new AssertionError(); }
        catch (SQLException expected) { check("40001".equals(expected.getSQLState())); }
        AppointmentTransitionMutation.requireUpdated(1); check(true);
        System.out.println("PASS AppointmentSchemaAlignmentTest assertions="+assertions);
    }
}
