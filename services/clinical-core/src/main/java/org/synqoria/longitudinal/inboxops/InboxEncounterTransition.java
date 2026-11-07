package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0138: Explicit encounter event lifecycle transitions; deny post-discharge non-amendment writes. Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxEncounterTransition {
 private InboxEncounterTransition(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 public enum State{PLANNED,ACTIVE,DISCHARGE_READY,DISCHARGED}
 public enum Action{CHECK_IN,PLAN_DISCHARGE,FINAL_DISCHARGE,NOTE_AMEND,OBSERVATION_CORRECT}
 public static State next(State state,Action action){
  Objects.requireNonNull(state);Objects.requireNonNull(action);
  return switch(state){
    case PLANNED->action==Action.CHECK_IN?State.ACTIVE:deny();
    case ACTIVE->action==Action.PLAN_DISCHARGE?State.DISCHARGE_READY:deny();
    case DISCHARGE_READY->action==Action.FINAL_DISCHARGE?State.DISCHARGED:deny();
    case DISCHARGED->(action==Action.NOTE_AMEND||action==Action.OBSERVATION_CORRECT)?State.DISCHARGED:deny();
  };
 }
 private static State deny(){throw new SecurityException("clinical event unavailable");}

}
