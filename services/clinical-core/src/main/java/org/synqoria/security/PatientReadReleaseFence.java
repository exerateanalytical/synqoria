package org.synqoria.security;
import org.synqoria.persistence.TransactionalPatientReadJdbc;
/** P03-B0014. Only a committed read-with-audit result can reach a response mapper. */
public final class PatientReadReleaseFence {
 private PatientReadReleaseFence(){}
 public static PatientRecordEnvelope released(TransactionalPatientReadJdbc.Result result){
  return result!=null&&result.status()==TransactionalPatientReadJdbc.Status.OK?result.patient():null;
 }
}
