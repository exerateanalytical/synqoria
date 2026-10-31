package org.synqoria.facility;
import java.util.*;import java.util.concurrent.atomic.AtomicReference;
/**
 * P04-B0002 versioned lifecycle transition reference. In-memory only; persistence
 * MUST use compare-and-swap UPDATE ... WHERE revision=? and append audit/outbox
 * atomically. Approval flags MUST come from reviewed server-side workflow.
 */
public final class FacilityLifecycle {
 public enum Result { APPLIED, REVISION_CONFLICT, TRANSITION_DENIED, APPROVAL_REQUIRED }
 private final AtomicReference<FacilityBinding> current;
 public FacilityLifecycle(FacilityBinding initial){current=new AtomicReference<>(Objects.requireNonNull(initial));}
 public FacilityBinding snapshot(){return current.get();}
 public Result transition(long expectedRevision,String nextStatus,boolean serverApproved) {
  if(!Set.of("ACTIVE","SUSPENDED","RETIRED").contains(nextStatus))return Result.TRANSITION_DENIED;
  while(true){
   var before=current.get();
   if(before.revision()!=expectedRevision)return Result.REVISION_CONFLICT;
   if(before.status().equals(nextStatus))return Result.TRANSITION_DENIED;
   boolean allowed=switch(before.status()){
    case "ACTIVE" -> "SUSPENDED".equals(nextStatus);
    case "SUSPENDED" -> "ACTIVE".equals(nextStatus)||"RETIRED".equals(nextStatus);
    default -> false;
   };
   if(!allowed)return Result.TRANSITION_DENIED;
   if(!serverApproved)return Result.APPROVAL_REQUIRED;
   var after=new FacilityBinding(before.cellId(),before.tenantId(),before.facilityId(),nextStatus,Math.addExact(before.revision(),1));
   if(current.compareAndSet(before,after))return Result.APPLIED;
  }
 }
}
