package org.synqoria.registration;
import java.sql.*;import java.lang.reflect.*;import java.util.*;
public final class V270EvidenceReconciliationTest {
 static class Db implements InvocationHandler {
  int rows=1,binds=0,timeout=0;String sql="",digest="a".repeat(64);boolean serial=true,fail=false;
  @SuppressWarnings("unchecked") <T>T proxy(Class<T> kind){return (T)Proxy.newProxyInstance(kind.getClassLoader(),new Class[]{kind},this);}
  public Object invoke(Object proxy,Method m,Object[] a)throws Throwable{
   String n=m.getName();
   switch(n){
    case "isClosed":return false;
    case "getAutoCommit":return false;
    case "isReadOnly":return false;
    case "getTransactionIsolation":return serial?Connection.TRANSACTION_SERIALIZABLE:Connection.TRANSACTION_READ_COMMITTED;
    case "prepareStatement":sql=(String)a[0];return proxy(PreparedStatement.class);
    case "setQueryTimeout":timeout=(Integer)a[0];return null;
    case "setObject":binds++;return null;
    case "executeQuery":if(fail)throw new SQLException("Synthetic database failure");return proxy(ResultSet.class);
    case "next":return --rows>=0;
    case "getString":return digest;
    case "close":return null;
    case "toString":return "Synthetic JDBC mock";
    default:throw new UnsupportedOperationException(n);
   }
  }
 }
 static void ok(boolean b){if(!b)throw new AssertionError("Expected safe result");}
 static UUID id(){return UUID.randomUUID();}
 public static void main(String[] args)throws Exception {
  String batch=args.length==0?"":args[0];Db db=new Db();Connection c=db.proxy(Connection.class);
  if(batch.equals("P05-B0062")){
   ok(ReviewEvidenceCrossCheck.consistent(c,id(),id(),id(),id(),id(),id(),db.digest));
   ok(db.binds==6&&db.timeout==5&&db.sql.contains("a.tenant_id=e.tenant_id")&&db.sql.contains("a.facility_id=e.facility_id")&&db.sql.contains("o.tenant_id=e.tenant_id"));
   db.rows=2;ok(!ReviewEvidenceCrossCheck.consistent(c,id(),id(),id(),id(),id(),id(),db.digest));
   db.rows=1;db.digest="b".repeat(64);ok(!ReviewEvidenceCrossCheck.consistent(c,id(),id(),id(),id(),id(),id(),"a".repeat(64)));
   db.serial=false;ok(!ReviewEvidenceCrossCheck.consistent(c,id(),id(),id(),id(),id(),id(),db.digest));
   db.serial=true;db.fail=true;try{ReviewEvidenceCrossCheck.consistent(c,id(),id(),id(),id(),id(),id(),db.digest);throw new AssertionError("SQLException must propagate");}catch(SQLException expected){}
  }else if(batch.equals("P05-B0063")){
   db.rows=0;ok(ReviewCommandReplayProbe.unseen(c,id(),id(),id()));
   ok(db.binds==3&&db.timeout==5&&db.sql.contains("tenant_id=? AND facility_id=? AND command_id=?"));
   db.rows=1;ok(!ReviewCommandReplayProbe.unseen(c,id(),id(),id()));
   db.serial=false;ok(!ReviewCommandReplayProbe.unseen(c,id(),id(),id()));
   db.serial=true;db.fail=true;try{ReviewCommandReplayProbe.unseen(c,id(),id(),id());throw new AssertionError("SQLException must propagate");}catch(SQLException expected){}
  }else throw new IllegalArgumentException("unknown batch");
  System.out.println("PASS "+batch+" mock JDBC assertions; NO live PostgreSQL or clinical authority");
 }
}
