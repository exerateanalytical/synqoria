package org.synqoria.scheduling;
import javax.sql.DataSource;import java.sql.*;import java.lang.reflect.*;import java.util.*;import java.io.PrintWriter;import java.util.logging.Logger;
public final class AppointmentFreshConnectionRetryTest {
 static int checks;static void ok(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static Object zero(Class<?> t){if(t==boolean.class)return false;if(t==int.class)return 0;return null;}
 static final class Source implements DataSource {
  int connections;List<String> events=new ArrayList<>();
  public Connection getConnection(){int n=++connections;
   return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},new InvocationHandler(){boolean auto=true;
    public Object invoke(Object p,Method m,Object[] a){return switch(m.getName()){
     case "getAutoCommit" -> auto;
     case "isReadOnly" -> false;
     case "setAutoCommit" -> {auto=(Boolean)a[0];events.add(n+":autocommit="+auto);yield null;}
     case "setTransactionIsolation" -> {ok((Integer)a[0]==Connection.TRANSACTION_SERIALIZABLE);yield null;}
     case "rollback" -> {events.add(n+":rollback");yield null;}
     case "close" -> {events.add(n+":close");yield null;}
     default -> zero(m.getReturnType());};}});}
  public Connection getConnection(String u,String p){return getConnection();}
  public PrintWriter getLogWriter(){return null;}public void setLogWriter(PrintWriter p){}
  public void setLoginTimeout(int t){}public int getLoginTimeout(){return 0;}
  public Logger getParentLogger(){return Logger.getGlobal();}
  public <T>T unwrap(Class<T> c)throws SQLException{throw new SQLException("unsupported");}
  public boolean isWrapperFor(Class<?> c){return false;}
 }
 public static void main(String[] args)throws Exception{
  Source s=new Source();
  String result=AppointmentFreshConnectionRetry.execute(s,c->{if(s.connections==1)throw new SQLException("serialization", "40001");return "success";});
  ok("success".equals(result));ok(s.connections==2);ok(s.events.contains("1:rollback"));
  ok(s.events.contains("1:close"));ok(s.events.contains("2:close"));
  Source fatal=new Source();
  try{AppointmentFreshConnectionRetry.execute(fatal,c->{throw new SQLException("permission denied","42501");});throw new AssertionError();}
  catch(SQLException ex){ok("42501".equals(ex.getSQLState()));}
  ok(fatal.connections==1);ok(fatal.events.contains("1:rollback"));
  Source max=new Source();
  try{AppointmentFreshConnectionRetry.execute(max,c->{throw new SQLException("deadlock","40P01");});throw new AssertionError();}
  catch(SQLException ex){ok("40P01".equals(ex.getSQLState()));}
  ok(max.connections==3);ok(max.events.contains("3:close"));
  System.out.println("PASS AppointmentFreshConnectionRetryTest assertions="+checks);
 }
}
