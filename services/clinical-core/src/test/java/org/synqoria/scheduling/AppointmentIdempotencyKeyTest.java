package org.synqoria.scheduling;
public class AppointmentIdempotencyKeyTest{
 static int n=0;static void deny(String k){try{AppointmentIdempotencyKey.validate(k);throw new AssertionError();}catch(IllegalArgumentException expected){n++;}}
 public static void main(String[]a){if(!AppointmentIdempotencyKey.validate("abcDEF1234567890").equals("abcDEF1234567890"))throw new AssertionError();n++;deny(null);deny("short");deny("abcdefghijklmnop!");deny("abcdefghijklmnop ");deny("abcdefghijklmnopé");deny("x".repeat(129));System.out.println("PASS AppointmentIdempotencyKey "+n);}
}
