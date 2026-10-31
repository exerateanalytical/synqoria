package org.synqoria.scheduling;
import java.sql.*;import java.lang.reflect.*;
public class AppointmentJdbcSafetyGateTest{
 static int n=0;static void ok(boolean b){if(!b)throw new AssertionError();n++;}
 static Object proxy(Class<?> c,InvocationHandler h){return Proxy.newProxyInstance(c.getClassLoader(),new Class<?>[]{c},h);}
 static Connection conn(boolean auto,boolean read,String role,boolean superuser,boolean bypass){
  ResultSet rs=(ResultSet)proxy(ResultSet.class,new InvocationHandler(){int row=0;public Object invoke(Object p,Method m,Object[] a){return switch(m.getName()){case "next"->++row==1;case "getString"->role;case "getBoolean"->((int)a[0]==2?superuser:bypass);case "close"->null;default->throw new AssertionError(m.getName());};}});
  Statement st=(Statement)proxy(Statement.class,(p,m,a)->switch(m.getName()){case "executeQuery"->{ok(a[0].toString().contains("current_user"));yield rs;}case "close"->null;default->throw new AssertionError(m.getName());});
  return (Connection)proxy(Connection.class,(p,m,a)->switch(m.getName()){case "getAutoCommit"->auto;case "isReadOnly"->read;case "createStatement"->st;default->throw new AssertionError(m.getName());});
 }
 static void denied(Connection c,String role){try{AppointmentJdbcSafetyGate.require(c,role);throw new AssertionError("must deny");}catch(SQLException expected){n++;}}
 public static void main(String[]a)throws Exception{AppointmentJdbcSafetyGate.require(conn(false,false,"synqoria_appointment_role",false,false),"synqoria_appointment_role");n++;denied(conn(true,false,"synqoria_appointment_role",false,false),"synqoria_appointment_role");denied(conn(false,true,"synqoria_appointment_role",false,false),"synqoria_appointment_role");denied(conn(false,false,"other_role",false,false),"synqoria_appointment_role");denied(conn(false,false,"synqoria_appointment_role",true,false),"synqoria_appointment_role");denied(conn(false,false,"synqoria_appointment_role",false,true),"synqoria_appointment_role");denied(null,"synqoria_appointment_role");denied(conn(false,false,"synqoria_appointment_role",false,false),"invalid role");System.out.println("PASS "+n+" mocked JDBC gate assertions; NO PostgreSQL integration");}
}
