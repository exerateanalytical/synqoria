package org.synqoria.scheduling;
/** Allow-listed SQL identifiers; never interpolate untrusted resource identifiers. */
public final class AppointmentSqlIdentifier {
 private AppointmentSqlIdentifier(){}
 public static String require(String value){
  if(value==null||!value.matches("[a-z][a-z0-9_]{0,62}"))throw new IllegalArgumentException("invalid SQL identifier");
  if(value.equals("public")||value.equals("pg_catalog")||value.equals("information_schema"))throw new IllegalArgumentException("reserved schema");
  return value;
 }
}
