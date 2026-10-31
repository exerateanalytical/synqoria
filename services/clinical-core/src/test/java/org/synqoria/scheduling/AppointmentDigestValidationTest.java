package org.synqoria.scheduling;
public final class AppointmentDigestValidationTest {
 public static void main(String[] args){
  String good="a".repeat(64);if(!AppointmentDigestValidation.require(good).equals(good))throw new AssertionError();
  for(String bad:new String[]{"A".repeat(64),"a".repeat(63),"z".repeat(64),""}){try{AppointmentDigestValidation.require(bad);throw new AssertionError("accepted invalid");}catch(IllegalArgumentException expected){}}
  try{AppointmentDigestValidation.require(null);throw new AssertionError();}catch(IllegalArgumentException expected){}
  System.out.println("PASS six digest validation cases");
 }
}
