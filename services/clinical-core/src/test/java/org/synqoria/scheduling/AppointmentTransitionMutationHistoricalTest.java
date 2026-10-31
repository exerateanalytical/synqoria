package org.synqoria.scheduling;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AppointmentTransitionMutationHistoricalTest {
    public static void main(String[] args) throws Exception {
        List<String> binds = new ArrayList<>();
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
            PreparedStatement.class.getClassLoader(), new Class[]{PreparedStatement.class},
            (proxy, method, arguments) -> {
                if (method.getName().startsWith("set")) binds.add(arguments[0] + ":" + arguments[1]);
                return null;
            });
        UUID appointment = UUID.randomUUID(), tenant = UUID.randomUUID(), facility = UUID.randomUUID(), patient = UUID.randomUUID();
        AppointmentTransitionMutation.bind(statement, "REQUESTED", "CONFIRMED", appointment, tenant, facility, patient, 2);
        if (binds.size() != 7 || !binds.get(0).equals("1:CONFIRMED") || !binds.get(5).equals("6:2") || !binds.get(6).equals("7:REQUESTED")) throw new AssertionError("binding incorrect");
        int before = binds.size();
        try { AppointmentTransitionMutation.bind(statement, "CANCELLED", "CONFIRMED", appointment, tenant, facility, patient, 2); throw new AssertionError("terminal state bypass"); }
        catch (SQLException expected) { if (!"23514".equals(expected.getSQLState())) throw expected; }
        if (binds.size() != before) throw new AssertionError("forbidden transition mutated statement");
        try { AppointmentTransitionMutation.bind(statement, "CONFIRMED", "ARRIVED", appointment, tenant, facility, null, 2); throw new AssertionError("missing patient allowed"); }
        catch (IllegalArgumentException expected) { }
        if (binds.size() != before) throw new AssertionError("missing patient mutated statement");
        System.out.println("PASS AppointmentTransitionMutationTest assertions=5");
    }
}
