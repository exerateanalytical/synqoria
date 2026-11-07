package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0137: Enforce bounded cross-event predecessor set before any inbox projection applies. Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxCausalDependencyFence {
 private InboxCausalDependencyFence(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 public static Set<UUID> requireSatisfied(UUID current,Collection<UUID> predecessors,Set<UUID> committed){
  Objects.requireNonNull(current);Objects.requireNonNull(predecessors);Objects.requireNonNull(committed);
  require(predecessors.size()<=32);Set<UUID> deps=new HashSet<>();
  for(UUID id:predecessors){require(id!=null&&!id.equals(current)&&deps.add(id));}
  require(committed.containsAll(deps));return Set.copyOf(deps);
 }

}
