package org.synqoria.scheduling;
import java.util.UUID;
public class AppointmentScopeKeysTest {
 static int assertions=0;static void denied(Runnable r){try{r.run();throw new AssertionError();}catch(SecurityException ok){assertions++;}}
 public static void main(String[]args){UUID u=UUID.randomUUID(),v=UUID.randomUUID();AppointmentTenantContext c=new AppointmentTenantContext(u,u,u,u);
  AppointmentScopeKeys.requireMatch(c,u,u,u);assertions++;
  denied(()->AppointmentScopeKeys.requireMatch(c,v,u,u));denied(()->AppointmentScopeKeys.requireMatch(c,u,v,u));
  denied(()->AppointmentScopeKeys.requireMatch(c,u,u,v));denied(()->AppointmentScopeKeys.requireMatch(null,u,u,u));
  denied(()->AppointmentScopeKeys.requireMatch(c,u,null,u));
  System.out.println("PASS AppointmentScopeKeys "+assertions);
 }
}
