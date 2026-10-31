package org.synqoria.scheduling;
import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
public final class AppointmentScopedMutationTest {
 static int assertions=0;
 static void check(boolean value){if(!value)throw new AssertionError();assertions++;}
 interface Checked {void run() throws SQLException;}
 static void rejects(Checked r){try{r.run();throw new AssertionError("expected rejection");}catch(IllegalArgumentException|IllegalStateException|SQLException expected){assertions++;}}
 public static void main(String[] args) throws Exception {
  UUID[] ids={UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID()};Map<Integer,Object> bound=new HashMap<>();
  PreparedStatement statement=(PreparedStatement)Proxy.newProxyInstance(AppointmentScopedMutationTest.class.getClassLoader(),new Class[]{PreparedStatement.class},(proxy,method,argv)->{if(method.getName().startsWith("set")){bound.put((Integer)argv[0],argv[1]);return null;}throw new UnsupportedOperationException(method.getName());});
  AppointmentScopedMutation.bind(statement,"CONFIRMED",ids[0],ids[1],ids[2],ids[3],4);
  check(bound.size()==6);check("CONFIRMED".equals(bound.get(1)));for(int i=0;i<4;i++)check(ids[i].equals(bound.get(i+2)));check(Long.valueOf(4).equals(bound.get(6)));
  check(AppointmentScopedMutation.SQL.contains("tenant_id = ?")&&AppointmentScopedMutation.SQL.contains("patient_id = ?")&&AppointmentScopedMutation.SQL.contains("revision = ?"));
  rejects(()->{try{AppointmentScopedMutation.bind(statement,"confirmed",ids[0],ids[1],ids[2],ids[3],4);}catch(java.sql.SQLException e){throw new RuntimeException(e);}});
  rejects(()->{try{AppointmentScopedMutation.bind(statement,"CONFIRMED",ids[0],ids[1],ids[2],null,4);}catch(java.sql.SQLException e){throw new RuntimeException(e);}});
  rejects(()->{try{AppointmentScopedMutation.bind(statement,"CONFIRMED",ids[0],ids[1],ids[2],ids[3],0);}catch(java.sql.SQLException e){throw new RuntimeException(e);}});
  AppointmentScopedMutation.requireExactlyOneRow(1);check(true);rejects(()->AppointmentScopedMutation.requireExactlyOneRow(0));rejects(()->AppointmentScopedMutation.requireExactlyOneRow(2));
  System.out.println("PASS P06-B0041 assertions="+assertions);
 }
}
