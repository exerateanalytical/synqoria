package org.synqoria.scheduling;
import java.util.*;import java.security.MessageDigest;
/** Detects inconsistent audit+outbox evidence before any successful response; not persistence. */
public final class BookingAuditOutboxFence {
 public record Receipt(SchedulingScope scope,UUID commandId,UUID correlationId,UUID eventId,byte[] digest,boolean recorded){
 public Receipt{if(scope==null||commandId==null||correlationId==null||eventId==null||digest==null||digest.length!=32)throw new IllegalArgumentException("Invalid receipt");digest=digest.clone();}
 @Override public byte[] digest(){return digest.clone();}
 }
 public static boolean consistent(Receipt audit,Receipt outbox,boolean committed){
 return committed&&audit!=null&&outbox!=null&&audit.recorded()&&outbox.recorded()&&audit.scope().equals(outbox.scope())&&audit.commandId().equals(outbox.commandId())&&audit.correlationId().equals(outbox.correlationId())&&audit.eventId().equals(outbox.eventId())&&MessageDigest.isEqual(audit.digest(),outbox.digest());
 }
 private BookingAuditOutboxFence(){}
}
