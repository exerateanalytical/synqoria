package org.synqoria.longitudinal;
import java.time.*;
public class LongitudinalInboxFailureClassTest {
 public static void main(String[] args){var f=LongitudinalInboxFailureClass.class;
 if(LongitudinalInboxFailureClass.plan(LongitudinalInboxFailureClass.Code.DB_CONFLICT,1).delay().toSeconds()!=1)throw new AssertionError();
 if(LongitudinalInboxFailureClass.plan(LongitudinalInboxFailureClass.Code.DB_CONFLICT,12).decision()!=LongitudinalInboxFailureClass.Decision.QUARANTINE)throw new AssertionError();
 if(LongitudinalInboxFailureClass.plan(LongitudinalInboxFailureClass.Code.AUTH_DENIED,1).decision()!=LongitudinalInboxFailureClass.Decision.STOP)throw new AssertionError();
 if(LongitudinalInboxFailureClass.plan(LongitudinalInboxFailureClass.Code.UNKNOWN,1).decision()!=LongitudinalInboxFailureClass.Decision.QUARANTINE)throw new AssertionError();
 if(LongitudinalInboxFailureClass.plan(LongitudinalInboxFailureClass.Code.BROKER_TIMEOUT,11).delay().compareTo(Duration.ofMinutes(5))>0)throw new AssertionError();
 System.out.println("PASS B0124 failure taxonomy 5 checks");}
}
