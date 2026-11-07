package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0145: Broker offset checkpoint ownership with consumer-group epoch and monotonicity. Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxConsumerCheckpointLease {
 private InboxConsumerCheckpointLease(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 public record Checkpoint(UUID owner,String group,int partition,long offset,long epoch){public Checkpoint{Objects.requireNonNull(owner);require(group!=null&&group.matches("[a-z][a-z0-9_.-]{1,95}")&&partition>=0&&offset>=-1&&epoch>=1);}}
 public static Checkpoint advance(Checkpoint prior,UUID owner,String group,int partition,long epoch,long nextOffset){
  require(prior!=null&&prior.owner().equals(owner)&&prior.group().equals(group)&&prior.partition()==partition&&prior.epoch()==epoch&&nextOffset==prior.offset()+1);
  return new Checkpoint(owner,group,partition,nextOffset,epoch);
 }

}
