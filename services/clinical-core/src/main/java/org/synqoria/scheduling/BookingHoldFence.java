package org.synqoria.scheduling;
import java.time.*;import java.util.*;
/** Short lease is a presentation preflight, not a durable resource lock. */
public record BookingHoldFence(UUID holdId,SchedulingScope scope,Instant issuedAt,Instant expiresAt){
 public BookingHoldFence {if(holdId==null||scope==null||issuedAt==null||expiresAt==null||!issuedAt.isBefore(expiresAt)||Duration.between(issuedAt,expiresAt).compareTo(Duration.ofMinutes(15))>0)throw new IllegalArgumentException("Invalid hold");}
 public boolean current(Instant now,SchedulingScope requested){return now!=null&&requested!=null&&scope.equals(requested)&&!now.isBefore(issuedAt)&&now.isBefore(expiresAt);}
}
