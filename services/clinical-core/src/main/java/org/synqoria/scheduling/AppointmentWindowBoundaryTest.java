package org.synqoria.scheduling;

import java.time.*;

/** Synthetic, executable M02 scheduling time-zone and overlap boundary regression. */
public final class AppointmentWindowBoundaryTest {
    private static int assertions;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    private static void rejects(Runnable action, String message) {
        boolean rejected=false;
        try { action.run(); } catch (IllegalArgumentException expected) { rejected=true; }
        check(rejected,message);
    }
    public static void main(String[] args) {
        ZoneId zone=ZoneId.of("Europe/Paris");
        rejects(()->AppointmentWindow.fromLocal(LocalDateTime.of(2026,3,29,2,30),zone,null,Duration.ofMinutes(30)),"DST gap must be rejected");
        rejects(()->AppointmentWindow.fromLocal(LocalDateTime.of(2026,10,25,2,30),zone,null,Duration.ofMinutes(30)),"DST overlap must require explicit offset");
        rejects(()->AppointmentWindow.fromLocal(LocalDateTime.of(2026,10,25,2,30),zone,ZoneOffset.UTC,Duration.ofMinutes(30)),"invalid offset must be rejected");
        AppointmentWindow early=AppointmentWindow.fromLocal(LocalDateTime.of(2026,10,25,2,30),zone,ZoneOffset.ofHours(2),Duration.ofMinutes(30));
        AppointmentWindow late=AppointmentWindow.fromLocal(LocalDateTime.of(2026,10,25,2,30),zone,ZoneOffset.ofHours(1),Duration.ofMinutes(30));
        check(!early.start().equals(late.start()),"DST overlap offsets must identify different instants");
        check(!early.overlaps(late),"distinct 30-minute DST occurrences must not overlap");
        AppointmentWindow adjacent=new AppointmentWindow(early.end(),early.end().plusSeconds(60),zone);
        check(!early.overlaps(adjacent),"adjacent half-open intervals must not overlap");
        AppointmentWindow conflict=new AppointmentWindow(early.start().plusSeconds(1),early.end(),zone);
        check(early.overlaps(conflict),"intersecting intervals must overlap");
        rejects(()->new AppointmentWindow(early.start(),early.start(),zone),"zero duration must be rejected");
        rejects(()->new AppointmentWindow(early.start(),early.start().plus(Duration.ofHours(25)),zone),"duration over 24 hours must be rejected");
        System.out.println("PASS "+assertions+" appointment window boundary assertions");
    }
}
