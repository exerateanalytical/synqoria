package org.synqoria.scheduling;
import org.synqoria.security.TrustedAccessContext;
import java.time.*;import java.util.*;
public class AppointmentAccessPolicyTest {
 static int n=0;static void ok(boolean b){if(!b)throw new AssertionError();n++;}
 public static void main(String[] args){
  var tenant=UUID.randomUUID();var facility=UUID.randomUUID();var patient=UUID.randomUUID();var actor=UUID.randomUUID();
  var a=new AppointmentAggregate(UUID.randomUUID(),tenant,facility,patient,UUID.randomUUID(),new AppointmentWindow(Instant.now(),Instant.now().plusSeconds(600),ZoneId.of("Africa/Douala")),AppointmentAggregate.State.REQUESTED,1,null);
  var policy=new AppointmentAccessPolicy();
  var ctx=new TrustedAccessContext(tenant,facility,actor,Set.of("appointment:read","appointment:request"),Set.of(patient));
  ok(policy.permits(ctx,a,AppointmentAccessPolicy.Action.READ));
  ok(policy.permits(ctx,a,AppointmentAccessPolicy.Action.REQUEST));
  ok(!policy.permits(ctx,a,AppointmentAccessPolicy.Action.MANAGE));
  ok(!policy.permits(new TrustedAccessContext(UUID.randomUUID(),facility,actor,ctx.capabilities(),Set.of(patient)),a,AppointmentAccessPolicy.Action.READ));
  ok(!policy.permits(new TrustedAccessContext(tenant,UUID.randomUUID(),actor,ctx.capabilities(),Set.of(patient)),a,AppointmentAccessPolicy.Action.READ));
  ok(!policy.permits(new TrustedAccessContext(tenant,facility,actor,ctx.capabilities(),Set.of()),a,AppointmentAccessPolicy.Action.READ));
  ok(!policy.permits(null,a,AppointmentAccessPolicy.Action.READ));
  ok(!policy.permits(ctx,a,null));
  System.out.println("PASS P06-B0004 "+n+" verified tenant/facility/patient/capability denial assertions (unit)");
 }
}
