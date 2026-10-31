package org.synqoria.registration;
/** Only committed JDBC evidence may produce success, no raw database/internal exception/PII in outcome. */
public final class RegistrationOutcomeEnvelope {
 public enum Status{REJECTED,UNKNOWN_OUTCOME,COMMITTED}
 public record Outcome(Status status,String code,boolean retrySameCommandOnly){ }
 private RegistrationOutcomeEnvelope(){}
 public static Outcome from(RegistrationJdbc.Result db,boolean authoritativeCommitRecord){
  if(db==null)return new Outcome(Status.UNKNOWN_OUTCOME,"REGISTRATION_OUTCOME_UNKNOWN",true);
  if(db.state()==RegistrationJdbc.State.REJECTED)return new Outcome(Status.REJECTED,"REGISTRATION_NOT_AUTHORIZED",false);
  if(db.state()==RegistrationJdbc.State.COMMITTED&&db.patientId()!=null&&authoritativeCommitRecord)return new Outcome(Status.COMMITTED,"REGISTRATION_COMMITTED",false);
  return new Outcome(Status.UNKNOWN_OUTCOME,"REGISTRATION_OUTCOME_UNKNOWN",true);
 }
}
