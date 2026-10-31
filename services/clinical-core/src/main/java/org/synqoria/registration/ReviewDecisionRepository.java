package org.synqoria.registration;
import java.sql.*;import java.util.*;import java.time.*;
/** Restricted-role read of an already committed reviewer decision; no patient list or automatic match. */
public final class ReviewDecisionRepository {
 private ReviewDecisionRepository(){}
 public record Binding(UUID tenant,UUID facility,UUID reviewId,UUID reviewer,UUID registrar,UUID candidate,UUID commandId,UUID patientId,byte[] fingerprint,byte[] mac,long revision){
 public Binding {if(commandId==null||patientId==null||fingerprint==null||fingerprint.length!=32||mac==null||mac.length!=32)throw new IllegalArgumentException("Invalid review binding");fingerprint=fingerprint.clone();mac=mac.clone();}
 @Override public byte[] fingerprint(){return fingerprint.clone();}
 @Override public byte[] mac(){return mac.clone();}
 }
 public static boolean committed(Connection c,Binding b) throws SQLException {
  if(c==null||b==null||b.tenant()==null||b.facility()==null||b.reviewId()==null||b.reviewer()==null||b.registrar()==null||b.candidate()==null||b.revision()<0||c.getAutoCommit()||c.isReadOnly())return false;
  final String sql="SELECT 1 FROM patient_registration_review_decisions WHERE tenant_id=? AND facility_id=? AND review_id=? AND reviewer_id=? AND registrar_id=? AND candidate_id=? AND candidate_revision=? AND command_id=? AND patient_id=? AND fingerprint_hmac=? AND mac=? AND outcome='NO_UNRESOLVED_MATCH' AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP LIMIT 1";
  try(PreparedStatement s=c.prepareStatement(sql)){
   s.setQueryTimeout(5);s.setObject(1,b.tenant());s.setObject(2,b.facility());s.setObject(3,b.reviewId());s.setObject(4,b.reviewer());s.setObject(5,b.registrar());s.setObject(6,b.candidate());s.setLong(7,b.revision());s.setObject(8,b.commandId());s.setObject(9,b.patientId());s.setBytes(10,b.fingerprint());s.setBytes(11,b.mac());
   try(ResultSet rows=s.executeQuery()){return rows.next();}
  }
 }
}
