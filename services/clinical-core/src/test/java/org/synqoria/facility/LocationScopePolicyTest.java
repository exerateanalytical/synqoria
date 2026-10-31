package org.synqoria.facility;
import org.synqoria.security.TrustedAccessContext;import java.util.*;
public final class LocationScopePolicyTest {
 static int n=0;static void ok(boolean yes){if(!yes)throw new AssertionError("Location "+n);n++;}
 public static void main(String[]args){var cell=UUID.randomUUID();var tenant=UUID.randomUUID();var facility=UUID.randomUUID();
  var c=new TrustedAccessContext(tenant,facility,UUID.randomUUID(),Set.of(),Set.of());
  var directory=new FacilityDirectory(List.of(new FacilityBinding(cell,tenant,facility,"ACTIVE",1)));
  var dept=new LocationScopePolicy.Department(tenant,facility,UUID.randomUUID(),true);
  var loc=new LocationScopePolicy.Location(tenant,facility,dept.id(),UUID.randomUUID(),true);
  ok(LocationScopePolicy.permits(c,directory,cell,dept,loc));
  ok(!LocationScopePolicy.permits(c,directory,UUID.randomUUID(),dept,loc));
  ok(!LocationScopePolicy.permits(c,directory,cell,new LocationScopePolicy.Department(tenant,facility,dept.id(),false),loc));
  ok(!LocationScopePolicy.permits(c,directory,cell,dept,new LocationScopePolicy.Location(tenant,facility,dept.id(),loc.id(),false)));
  ok(!LocationScopePolicy.permits(c,directory,cell,dept,new LocationScopePolicy.Location(tenant,facility,UUID.randomUUID(),loc.id(),true)));
  ok(!LocationScopePolicy.permits(c,directory,cell,dept,new LocationScopePolicy.Location(UUID.randomUUID(),facility,dept.id(),loc.id(),true)));
  ok(!LocationScopePolicy.permits(c,directory,cell,dept,new LocationScopePolicy.Location(tenant,UUID.randomUUID(),dept.id(),loc.id(),true)));
  ok(!LocationScopePolicy.permits(c,new FacilityDirectory(List.of(new FacilityBinding(cell,tenant,facility,"SUSPENDED",2))),cell,dept,loc));
  ok(!LocationScopePolicy.permits(null,directory,cell,dept,loc));
  System.out.println("PASS: "+n+" location hierarchy fail-closed assertions; no repository integration");
 }
}
