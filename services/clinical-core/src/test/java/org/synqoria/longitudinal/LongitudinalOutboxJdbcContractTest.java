package org.synqoria.longitudinal;

import java.lang.reflect.*;
import java.sql.*;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import javax.sql.DataSource;
import static org.synqoria.longitudinal.ClinicalCaseCore.Scope;
import static org.synqoria.longitudinal.LongitudinalOutboxDelivery.*;

/** Synthetic JDBC interaction tests; no PostgreSQL instance or real RLS permissions asserted. */
public final class LongitudinalOutboxJdbcContractTest {
  private static int checks;
  private static void ok(boolean x){checks++;if(!x)throw new AssertionError("jdbc dispatch #"+checks);}
  private static void denied(Runnable action){checks++;try {action.run();throw new AssertionError("accepted forbidden transaction");}
    catch(SecurityException|IllegalArgumentException expected){}}
  @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> api, InvocationHandler h){
    return (T) Proxy.newProxyInstance(api.getClassLoader(),new Class<?>[]{api},h);
  }
  private static Object fallback(Class<?> type){if(type==boolean.class)return false;if(type==int.class)return 0;
    if(type==long.class)return 0L;return null;}
  private static Object basic(Object self,Method method,Object[] a){return switch(method.getName()){
    case "equals"->self==a[0];case "hashCode"->System.identityHashCode(self);
    case "toString"->"fake-jdbc";default->null;};}
  static final Scope SCOPE=LongitudinalOutboxDeliveryTest.SCOPE;
  static final Instant NOW=LongitudinalOutboxDeliveryTest.NOW;
  static final EventRef EVENT=LongitudinalOutboxDeliveryTest.event(1);
  static class FakeDB {
    boolean deniedScope,emptyClaim,unmanaged,ambiguous,ackConflict,unknownSql,failedSeed;
    int connections,scopeInstalls,commits,rollbacks,seedCalls,claims,ackCalls,deferCalls;
    UUID issuedLease;long revision=1;int attempts=0;
    DataSource datasource(){return proxy(DataSource.class,(obj,m,a)->{
      if(m.getDeclaringClass()==Object.class)return basic(obj,m,a);
      if(m.getName().equals("getConnection")){connections++;return connection();}
      return fallback(m.getReturnType());
    });}
    Connection connection(){
      final boolean[] autocommit={!unmanaged},closed={false};
      return proxy(Connection.class,(obj,m,a)->{
        if(m.getDeclaringClass()==Object.class)return basic(obj,m,a);
        switch(m.getName()){
          case "getAutoCommit" -> {return autocommit[0];}
          case "setAutoCommit" -> {autocommit[0]=(boolean)a[0];return null;}
          case "setTransactionIsolation" -> {ok((int)a[0]==Connection.TRANSACTION_READ_COMMITTED);return null;}
          case "createStatement" -> {return proxy(Statement.class,(o,method,aa)->{
            if(method.getName().equals("execute")){ok(((String)aa[0]).startsWith("SET LOCAL "));return true;}
            return fallback(method.getReturnType());
          });}
          case "prepareStatement" -> {return statement((String)a[0]);}
          case "commit" -> {commits++;return null;}
          case "rollback" -> {rollbacks++;return null;}
          case "close" -> {closed[0]=true;return null;}
          case "isClosed" -> {return closed[0];}
        }
        return fallback(m.getReturnType());
      });
    }
    PreparedStatement statement(String sql){
      Map<Integer,Object> bound=new HashMap<>();
      return proxy(PreparedStatement.class,(obj,m,a)->{
        if(m.getDeclaringClass()==Object.class)return basic(obj,m,a);
        switch(m.getName()){
          case "setObject","setLong","setString","setBytes","setInt" -> {bound.put((Integer)a[0],a[1]);return null;}
          case "setQueryTimeout" -> {ok((Integer)a[0]>0);return null;}
          case "executeUpdate" -> {
            if(sql.startsWith("INSERT INTO synqoria_longitudinal_delivery_quarantine")){
              seedCalls++;ok(sql.contains("NOT EXISTS")&&sql.contains("ORDER BY revision LIMIT 100")&&sql.contains("ON CONFLICT DO NOTHING"));
              verifyScope(bound,1);if(failedSeed)throw new SQLException("denied", "42501");return 1;
            }
            if(sql.startsWith("UPDATE synqoria_longitudinal_delivery_quarantine SET parked_at")){
              ok(sql.contains("attempts>=12")&&sql.contains("lease_expires_at<=clock_timestamp()"));
              verifyScope(bound,1);return 0;
            }
            if(sql.startsWith("UPDATE synqoria_longitudinal_delivery_quarantine SET published_at")){
              ackCalls++;verifyScope(bound,1);ok(bound.get(5).equals(EVENT.commandId()));
              ok(bound.get(6).equals(EVENT.revision())&&bound.get(7).equals(EVENT.eventHash()));
              ok(bound.get(8).equals(issuedLease));return ackConflict?0:1;
            }
            if(sql.startsWith("UPDATE synqoria_longitudinal_delivery_quarantine SET lease_token=NULL")){
              deferCalls++;verifyScope(bound,2);ok(bound.get(1).equals(4L));
              ok(bound.get(6).equals(EVENT.commandId())&&bound.get(7).equals(EVENT.revision()));
              ok(bound.get(8).equals(EVENT.eventHash())&&bound.get(9).equals(issuedLease));return 1;
            }
            throw new AssertionError("unexpected executeUpdate: "+sql);
          }
          case "executeQuery" -> {
            if(sql.contains("set_config('app.tenant_id'")){
              scopeInstalls++;ok(!autocommitInScope(bound));
              String[] scopeEcho={SCOPE.tenant().toString(),SCOPE.facility().toString(),
                deniedScope?UUID.randomUUID().toString():SCOPE.patient().toString(),SCOPE.encounter().toString()};
              return resultRows(Collections.singletonList((Object[])scopeEcho));
            }
            if(sql.startsWith("WITH earliest AS (")){
              claims++;ok(sql.contains("FOR UPDATE OF d SKIP LOCKED"));
              ok(sql.contains("SELECT min(x.revision)"));ok(sql.contains("o.event_hash=d.event_hash"));
              verifyScope(bound,1);verifyScope(bound,6);
              issuedLease=(UUID)bound.get(5);
              if(emptyClaim)return resultRows(Collections.emptyList());
              Object[] row={EVENT.commandId(),EVENT.revision(),EVENT.eventHash(),EVENT.eventType(),++attempts};
              return resultRows(ambiguous?List.of(row,row):Collections.singletonList(row));
            }
            throw new AssertionError("unexpected executeQuery: "+sql);
          }
        }
        return fallback(m.getReturnType());
      });
    }
    boolean autocommitInScope(Map<Integer,Object> x){for(int i=1;i<=4;i++)if(x.get(i)==null)return true;return false;}
    void verifyScope(Map<Integer,Object> args,int from){ok(SCOPE.tenant().equals(args.get(from))&&
      SCOPE.facility().equals(args.get(from+1))&&SCOPE.patient().equals(args.get(from+2))&&
      SCOPE.encounter().equals(args.get(from+3)));}
    static ResultSet resultRows(List<Object[]> rows){final int[] index={-1};return proxy(ResultSet.class,(obj,m,a)->{
      if(m.getDeclaringClass()==Object.class)return basic(obj,m,a);
      if(m.getName().equals("next")){index[0]++;return index[0]<rows.size();}
      if(Set.of("getString","getObject","getLong","getInt").contains(m.getName())){
        Object v=rows.get(index[0])[(Integer)a[0]-1];
        if(m.getName().equals("getLong"))return ((Number)v).longValue();
        if(m.getName().equals("getInt"))return ((Number)v).intValue();
        return v;
      }
      return fallback(m.getReturnType());
    });}
  }
  static void claim(){FakeDB f=new FakeDB();var c=new LongitudinalOutboxJdbc(f.datasource()).claim(SCOPE,UUID.randomUUID(),NOW).orElseThrow();
    ok(c.event().equals(EVENT));ok(f.commits==1&&f.rollbacks==0&&f.seedCalls==1&&f.scopeInstalls==1);
  }
  static void noRows(){FakeDB f=new FakeDB();f.emptyClaim=true;
    ok(new LongitudinalOutboxJdbc(f.datasource()).claim(SCOPE,UUID.randomUUID(),NOW).isEmpty());
    ok(f.commits==1&&f.rollbacks==0);
  }
  static void ack(){FakeDB f=new FakeDB();LongitudinalOutboxJdbc adapter=new LongitudinalOutboxJdbc(f.datasource());
    Claimed token=adapter.claim(SCOPE,UUID.randomUUID(),NOW).orElseThrow();
    ok(adapter.acknowledge(token,NOW.plusSeconds(1)));ok(f.ackCalls==1&&f.commits==2);
  }
  static void staleAck(){FakeDB f=new FakeDB();LongitudinalOutboxJdbc adapter=new LongitudinalOutboxJdbc(f.datasource());
    Claimed token=adapter.claim(SCOPE,UUID.randomUUID(),NOW).orElseThrow();f.ackConflict=true;
    ok(!adapter.acknowledge(token,NOW.plusSeconds(1)));
  }
  static void defer(){FakeDB f=new FakeDB();LongitudinalOutboxJdbc adapter=new LongitudinalOutboxJdbc(f.datasource());
    Claimed token=adapter.claim(SCOPE,UUID.randomUUID(),NOW).orElseThrow();
    ok(adapter.defer(token,NOW,Duration.ofSeconds(4)));ok(f.deferCalls==1&&f.commits==2);
    denied(()->adapter.defer(token,NOW,Duration.ZERO));
    denied(()->adapter.defer(token,NOW,Duration.ofDays(2)));
  }
  static void deniedScope(){FakeDB f=new FakeDB();f.deniedScope=true;
    denied(()->new LongitudinalOutboxJdbc(f.datasource()).claim(SCOPE,UUID.randomUUID(),NOW));
    ok(f.rollbacks==1&&f.commits==0);
  }
  static void unmanaged(){FakeDB f=new FakeDB();f.unmanaged=true;
    denied(()->new LongitudinalOutboxJdbc(f.datasource()).claim(SCOPE,UUID.randomUUID(),NOW));
    ok(f.commits==0);
  }
  static void duplicate(){FakeDB f=new FakeDB();f.ambiguous=true;
    denied(()->new LongitudinalOutboxJdbc(f.datasource()).claim(SCOPE,UUID.randomUUID(),NOW));
    ok(f.commits==0&&f.rollbacks==1);
  }
  static void deniedSeed(){FakeDB f=new FakeDB();f.failedSeed=true;
    denied(()->new LongitudinalOutboxJdbc(f.datasource()).claim(SCOPE,UUID.randomUUID(),NOW));
    ok(f.commits==0&&f.rollbacks==1);
  }
  static final List<String> CASES=List.of("claim","no-rows","ack","stale-ack","defer","scope-echo","unmanaged","ambiguous","seed-denied");
  public static void main(String[] args){if(args.length!=1)throw new IllegalArgumentException("scenario");
    if(args[0].equals("all"))for(String x:CASES)run(x);else run(args[0]);
    System.out.println("PASS JDBC outbox "+args[0]+" ("+checks+" synthetic assertions)");
  }
  private static void run(String x){switch(x){case "claim"->claim();case "no-rows"->noRows();
    case "ack"->ack();case "stale-ack"->staleAck();case "defer"->defer();case "scope-echo"->deniedScope();
    case "unmanaged"->unmanaged();case "ambiguous"->duplicate();case "seed-denied"->deniedSeed();
    default ->throw new IllegalArgumentException(x);}}
}
