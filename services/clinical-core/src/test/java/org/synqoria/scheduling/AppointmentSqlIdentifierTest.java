package org.synqoria.scheduling;
public class AppointmentSqlIdentifierTest {
 public static void main(String[] args){
  int n=0; for(String v:new String[]{"appointments","cell_2","a"}){if(!AppointmentSqlIdentifier.require(v).equals(v))throw new AssertionError();n++;}
  for(String v:new String[]{"", "Public", "a;DROP", "pg_catalog", "public", "_hidden", "a".repeat(64)}){try{AppointmentSqlIdentifier.require(v);throw new AssertionError("accepted "+v);}catch(IllegalArgumentException expected){n++;}}
  System.out.println("PASS P06-B0035 assertions="+n);
 }
}
