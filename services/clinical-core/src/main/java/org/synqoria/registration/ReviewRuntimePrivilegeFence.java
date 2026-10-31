package org.synqoria.registration;
import java.sql.*;import java.util.*;
/** Defense in depth for an UNDEPLOYED reviewer role. Never a clinical release gate alone. */
final class ReviewRuntimePrivilegeFence {
 private ReviewRuntimePrivilegeFence(){}
 static boolean verify(Connection c,String role)throws SQLException {
  if(!ReviewDbRoleAttestation.verify(c,role))return false;
  String sql="SELECT c.relname, has_table_privilege(current_user,c.oid,'SELECT'), has_table_privilege(current_user,c.oid,'INSERT'), has_table_privilege(current_user,c.oid,'UPDATE'), has_table_privilege(current_user,c.oid,'DELETE') FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relname IN ('patient_registration_review_decisions','patient_registration_review_events','patient_registration_review_audit','clinical_outbox')";
  Set<String> names=new HashSet<>();
  try(PreparedStatement p=c.prepareStatement(sql)){
   p.setQueryTimeout(5);
   try(ResultSet r=p.executeQuery()){
    while(r.next()){
     String name=r.getString(1);
     if(!names.add(name)||r.getBoolean(4)||r.getBoolean(5))return false;
     // Decision rows must be read-only. Append-only event/audit/outbox need INSERT.
     if(!r.getBoolean(2)||(!name.equals("patient_registration_review_decisions")&&!r.getBoolean(3))
       ||(name.equals("patient_registration_review_decisions")&&r.getBoolean(3)))return false;
    }
   }
  }
  return names.equals(Set.of("patient_registration_review_decisions","patient_registration_review_events","patient_registration_review_audit","clinical_outbox"));
 }
}
