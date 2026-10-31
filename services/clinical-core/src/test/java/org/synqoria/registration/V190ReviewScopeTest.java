package org.synqoria.registration;
import java.util.UUID;
public final class V190ReviewScopeTest {
 public static void main(String[] a){UUID tenant=UUID.randomUUID(),facility=UUID.randomUUID(),candidate=UUID.randomUUID(),cmd=UUID.randomUUID(),registrar=UUID.randomUUID(),reviewer=UUID.randomUUID();
  var s=new ReviewScopeFence.Scope(tenant,facility,candidate,cmd,registrar,reviewer,4,4);
  assert ReviewScopeFence.permits(s,tenant,facility,cmd,4);assert !ReviewScopeFence.permits(s,UUID.randomUUID(),facility,cmd,4);
  assert !ReviewScopeFence.permits(s,tenant,UUID.randomUUID(),cmd,4);assert !ReviewScopeFence.permits(s,tenant,facility,UUID.randomUUID(),4);
  assert !ReviewScopeFence.permits(s,tenant,facility,cmd,3);
  assert !ReviewScopeFence.permits(new ReviewScopeFence.Scope(tenant,facility,candidate,cmd,registrar,registrar,4,4),tenant,facility,cmd,4);
  assert !ReviewScopeFence.permits(new ReviewScopeFence.Scope(tenant,facility,candidate,cmd,registrar,reviewer,4,5),tenant,facility,cmd,4);
  try{new ReviewScopeFence.Scope(null,facility,candidate,cmd,registrar,reviewer,4,4);throw new AssertionError();}catch(IllegalArgumentException expected){}
  System.out.println("PASS P05-B0028 8 scope checks");
 }
}
