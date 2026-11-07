package org.synqoria.longitudinal.inboxops;
import java.util.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** P07-B0132: Broker topic, partition, consumer group and trusted principal route binding. Pure fail-closed policy only; caller supplies trusted context and transactional state. */
public final class InboxBrokerRouteFence {
 private InboxBrokerRouteFence(){}
 private static void require(boolean condition){if(!condition)throw new SecurityException("clinical event unavailable");}

 public record Route(String topic,String group,int partition,long offset,String principal){
  public Route{require(topic!=null&&topic.matches("[a-z][a-z0-9_.-]{1,95}"));require(group!=null&&group.matches("[a-z][a-z0-9_.-]{1,95}"));require(partition>=0&&partition<4096&&offset>=0);require(principal!=null&&principal.matches("[a-z0-9_.-]{3,96}"));}
 }
 public static void verify(Route actual,Route subscribed,Set<String> principalAllowlist){
  Objects.requireNonNull(actual);Objects.requireNonNull(subscribed);Objects.requireNonNull(principalAllowlist);
  require(actual.topic().equals(subscribed.topic())&&actual.group().equals(subscribed.group())&&actual.partition()==subscribed.partition()&&actual.principal().equals(subscribed.principal())&&principalAllowlist.contains(actual.principal()));
 }

}
