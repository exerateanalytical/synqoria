package org.synqoria.registration;
import java.sql.*;import java.util.*;
/** P05-B0054: restricted-role metadata and FORCE-RLS preflight. Not a substitute for real DB security tests. */
final class ReviewDbRoleAttestation {
 private ReviewDbRoleAttestation(){}
 static boolean verify(Connection c,String expectedRole)throws SQLException {
  if(!ReviewTransactionFence.eligible(c)||expectedRole==null||!expectedRole.matches("[a-z][a-z0-9_]{3,62}"))return false;
  if(!"PostgreSQL".equals(c.getMetaData().getDatabaseProductName()))return false;
  try(PreparedStatement p=c.prepareStatement("SELECT current_user, current_setting('row_security'), rolsuper, rolbypassrls FROM pg_roles WHERE rolname=current_user")){
   p.setQueryTimeout(5);
   try(ResultSet r=p.executeQuery()){
    if(!r.next()||!expectedRole.equals(r.getString(1))||!"on".equals(r.getString(2))||r.getBoolean(3)||r.getBoolean(4)||r.next())return false;
   }
  }
  Set<String> expected=Set.of("patient_registration_review_decisions","patient_registration_review_events","patient_registration_review_audit","clinical_outbox");
  Set<String> seen=new HashSet<>();
  try(PreparedStatement p=c.prepareStatement("SELECT c.relname,c.relrowsecurity,c.relforcerowsecurity FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relname IN ('patient_registration_review_decisions','patient_registration_review_events','patient_registration_review_audit','clinical_outbox')")){
   p.setQueryTimeout(5);
   try(ResultSet r=p.executeQuery()){
    while(r.next()){
     String table=r.getString(1);
     if(!expected.contains(table)||!seen.add(table)||!r.getBoolean(2)||!r.getBoolean(3))return false;
    }
   }
  }
  return seen.equals(expected);
 }
}
