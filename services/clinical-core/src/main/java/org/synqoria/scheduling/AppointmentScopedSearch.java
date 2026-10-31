package org.synqoria.scheduling;
import org.synqoria.security.TrustedAccessContext;import java.util.*;
/** Privacy-safe in-memory projection reference; never return counts of inaccessible patients. */
public final class AppointmentScopedSearch {
 public record Page(List<AppointmentAggregate> items,boolean hasMore){public Page{items=List.copyOf(items);}}
 public Page page(TrustedAccessContext ctx,Collection<AppointmentAggregate> rows,int offset,int limit){
  if(offset<0||limit<1||limit>100)throw new IllegalArgumentException("invalid page bounds");
  if(ctx==null||!ctx.capabilities().contains("appointment:read")||rows==null)return new Page(List.of(),false);
  var authorized=rows.stream().filter(r->r!=null&&new AppointmentAccessPolicy().permits(ctx,r,AppointmentAccessPolicy.Action.READ))
   .sorted(Comparator.comparing((AppointmentAggregate r)->r.window().start()).thenComparing(AppointmentAggregate::id)).toList();
  if(offset>=authorized.size())return new Page(List.of(),false);
  int end=(int)Math.min((long)authorized.size(),(long)offset+limit);
  return new Page(authorized.subList(offset,end),end<authorized.size());
 }
}
