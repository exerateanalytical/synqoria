package org.synqoria.registration;
import java.util.UUID;
/** P05-B0037: audit/outbox metadata linkage before any restricted transaction. */
public final class ReviewOutboxLinkFence {
 private ReviewOutboxLinkFence(){}
 public static boolean linked(ReviewOutboxDescriptor event,ReviewCommandEnvelope command,long version,String auditDigest){
  if(event==null||command==null||auditDigest==null)return false;
  return event.tenant().equals(command.tenant())&&event.facility().equals(command.facility())
   &&event.commandId().equals(command.command())&&event.version()==version&&version>0
   &&event.auditDigest().equals(auditDigest)&&event.kind()!=null;
 }
}
