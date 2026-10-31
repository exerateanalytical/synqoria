package org.synqoria.scheduling;

import java.sql.SQLException;

public final class AppointmentTransitionPolicyTest {
    private static int assertions;
    private static void allow(String a, String b) throws Exception {AppointmentTransitionPolicy.requireAllowed(a,b); assertions++;}
    private static void deny(String a, String b) throws Exception {
        try { AppointmentTransitionPolicy.requireAllowed(a,b); throw new AssertionError("accepted forbidden transition: "+a+" -> "+b); }
        catch (SQLException expected) { if (!"23514".equals(expected.getSQLState())) throw new AssertionError("incorrect SQL state"); assertions++; }
    }
    public static void main(String[] args) throws Exception {
        allow("REQUESTED","CONFIRMED"); allow("REQUESTED","CANCELLED");
        allow("CONFIRMED","ARRIVED"); allow("CONFIRMED","NO_SHOW");
        allow("CONFIRMED","CANCELLED"); allow("ARRIVED","FULFILLED");
        deny("ARRIVED","CANCELLED");
        for (String terminal : new String[]{"FULFILLED","NO_SHOW","CANCELLED"}) {
            for (String next : new String[]{"REQUESTED","CONFIRMED","ARRIVED","FULFILLED","NO_SHOW","CANCELLED"}) deny(terminal,next);
        }
        deny(null,"CONFIRMED"); deny("CONFIRMED",null); deny("BOGUS","ARRIVED");
        deny("REQUESTED","FULFILLED"); deny("CONFIRMED","CONFIRMED"); deny("ARRIVED","NO_SHOW");
        System.out.println("PASS AppointmentTransitionPolicyTest assertions="+assertions);
    }
}
