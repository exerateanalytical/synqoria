package org.synqoria.scheduling;
import java.sql.*;import java.lang.reflect.*;import java.util.*;
public class AppointmentTransactionExecutorTest {
 static int assertions=0;static void check(boolean value){if(!value)throw new AssertionError();assertions++;}
 @SuppressWarnings("unchecked") static <T>T proxy(Class<T> cls,InvocationHandler handler){return (T)Proxy.newProxyInstance(cls.getClassLoader(),new Class[]{cls},handler);}
 public static void main(String[]args)throws Exception{
  UUID u=UUID.randomUUID();AppointmentTenantContext context=new AppointmentTenantContext(u,u,u,u);
  int[] commits={0},rollbacks={0},scope={0};
  Map<Integer,String> bound=new HashMap<>();
  
  PreparedStatement ps=proxy(PreparedStatement.class,(p,m,a)->{if(m.getName().equals("setString")){bound.put((Integer)a[0],(String)a[1]);return null;}if(m.getName().equals("executeQuery")){scope[0]++;final int[] row={0};return proxy(ResultSet.class,(p2,m2,a2)->switch(m2.getName()){case "next"->++row[0]==1;case "getString"->bound.get((Integer)a2[0]);default->null;});}return null;});
  Connection conn=proxy(Connection.class,(p,m,a)->switch(m.getName()){
   case "getAutoCommit"->false;case "getTransactionIsolation"->Connection.TRANSACTION_SERIALIZABLE;
   case "prepareStatement"->ps;case "commit"->{commits[0]++;yield null;}
   case "rollback"->{rollbacks[0]++;yield null;}default->null;});
  check(AppointmentTransactionExecutor.execute(conn,context,c->"ok").equals("ok"));
  check(commits[0]==1&&rollbacks[0]==0);check(scope[0]==1);
  try{AppointmentTransactionExecutor.execute(conn,context,c->{throw new SQLException("denied");});throw new AssertionError();}
  catch(SQLException expected){check(rollbacks[0]==1&&commits[0]==1);}
  try{AppointmentTransactionExecutor.execute(conn,context,c->{throw new SecurityException("denied");});throw new AssertionError();}
  catch(SecurityException expected){check(rollbacks[0]==2);}
  System.out.println("PASS AppointmentTransactionExecutor "+assertions);
 }
}
