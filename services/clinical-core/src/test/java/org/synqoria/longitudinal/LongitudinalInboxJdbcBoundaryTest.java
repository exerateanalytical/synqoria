package org.synqoria.longitudinal;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import static org.synqoria.longitudinal.ClinicalCaseCore.Scope;

public final class LongitudinalInboxJdbcBoundaryTest {
  static int count;
  static final Scope S=new Scope(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
  static void yes(boolean ok){count++;if(!ok)throw new AssertionError("jdbc #"+count);}
  static void deny(Runnable r){count++;try{r.run();throw new AssertionError("unsafe JDBC admitted");}
    catch(SecurityException expected){}}
  static Object defaultValue(Class<?> t){if(t==boolean.class)return false;if(t==int.class)return 0;
    if(t==long.class)return 0L;return null;}
  static <T> T p(Class<T> cls,InvocationHandler fn){return cls.cast(Proxy.newProxyInstance(cls.getClassLoader(),new Class[]{cls},fn));}
  static final class Fake {
    boolean mismatch=false,bypass=false,failCommit=false,failWork=false,rollback=false,commit=false,scoped=false,closed=false;
    String role="synqoria_inbox_worker";
    DataSource source(){return p(DataSource.class,(o,m,a)->m.getName().equals("getConnection")?connection():defaultValue(m.getReturnType()));}
    Connection connection(){return p(Connection.class,(o,m,a)->switch(m.getName()){
      case "getAutoCommit"->true;
      case "prepareStatement"->statement((String)a[0]);
      case "setAutoCommit","setTransactionIsolation"->null;
      case "commit"->{if(failCommit)throw new SQLException("commit failure");commit=true;yield null;}
      case "rollback"->{rollback=true;yield null;}
      case "close"->{closed=true;yield null;}
      default->defaultValue(m.getReturnType());});}
    PreparedStatement statement(String sql){boolean binding=sql.contains("set_config");
      yes(binding?sql.contains("app.patient_id"):sql.contains("pg_roles"));
      return p(PreparedStatement.class,(o,m,a)->switch(m.getName()){
        case "setString"->{if(binding)scoped=true;yield null;}
        case "setQueryTimeout","close"->null;
        case "executeQuery"->result(binding);
        default->defaultValue(m.getReturnType());});}
    ResultSet result(boolean binding){return p(ResultSet.class,new InvocationHandler(){boolean first=true;
      public Object invoke(Object o,Method m,Object[] a){return switch(m.getName()){
        case "next"->{if(first){first=false;yield true;}yield false;}
        case "getString"->{int i=(Integer)a[0];if(binding){yield mismatch&&i==3?UUID.randomUUID().toString():
             (i==1?S.tenant():i==2?S.facility():i==3?S.patient():S.encounter()).toString();}
             yield i==1?role:"on";}
        case "getBoolean"->bypass;
        case "close"->null;
        default->defaultValue(m.getReturnType());};}});}
  }
  public static void main(String[] args){
    Fake good=new Fake();String result=LongitudinalInboxJdbcBoundary.transact(good.source(),S,good.role,db->"committed");
    yes(result.equals("committed")&&good.scoped&&good.commit&&!good.rollback&&good.closed);
    Fake wrong=new Fake();wrong.mismatch=true;
    deny(()->LongitudinalInboxJdbcBoundary.transact(wrong.source(),S,wrong.role,db->"bad"));
    yes(wrong.rollback&&!wrong.commit);
    Fake admin=new Fake();admin.bypass=true;
    deny(()->LongitudinalInboxJdbcBoundary.transact(admin.source(),S,admin.role,db->"bad"));
    yes(admin.rollback&&!admin.commit);
    Fake fail=new Fake();fail.failCommit=true;
    deny(()->LongitudinalInboxJdbcBoundary.transact(fail.source(),S,fail.role,db->"bad"));
    yes(fail.rollback&&!fail.commit);
    Fake body=new Fake();deny(()->LongitudinalInboxJdbcBoundary.transact(body.source(),S,body.role,db->{throw new SQLException("PHI");}));
    yes(body.rollback&&!body.commit);
    deny(()->LongitudinalInboxJdbcBoundary.transact(new Fake().source(),S,"postgres",db->"unsafe"));
    System.out.println("PASS P07-B0117 JDBC boundary "+count+" assertions (synthetic JDBC)");
  }
}
