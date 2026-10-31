package org.synqoria.registration;
import java.lang.reflect.*;import java.sql.*;import java.time.*;import java.util.*;
/** Pure mock JDBC unit tests. No PostgreSQL driver/server, grants, or clinical authority. */
public final class V250ReviewTransactionTest {
 static int checks=0;static void ok(boolean v){checks++;if(!v)throw new AssertionError("check "+checks);}
 static UUID tenant=UUID.fromString("00000000-0000-4000-8000-000000000001"),facility=UUID.fromString("00000000-0000-4000-8000-000000000002"),review=UUID.fromString("00000000-0000-4000-8000-000000000003"),
 candidate=UUID.fromString("00000000-0000-4000-8000-000000000004"),actor=UUID.fromString("00000000-0000-4000-8000-000000000005"),command=UUID.fromString("00000000-0000-4000-8000-000000000006"),
 event=UUID.fromString("00000000-0000-4000-8000-000000000007"),audit=UUID.fromString("00000000-0000-4000-8000-000000000008"),outbox=UUID.fromString("00000000-0000-4000-8000-000000000009");
 static Instant now=Instant.parse("2026-10-30T10:00:00Z");static String binding="a".repeat(64);
 static ReviewAtomicAppend.Request request(long version,ReviewEventTransition.Kind kind,String prev){return new ReviewAtomicAppend.Request(tenant,facility,review,event,audit,outbox,actor,command,candidate,2,version,kind,binding,prev,now);}
 static Object defaultReturn(Class<?> t){if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;return null;}
 static final class DB {
  String role="synqoria_review_runtime",product="PostgreSQL",rls="on";boolean superuser=false,bypass=false,forced=true,expired=false,readOnly=false,autoCommit=false;
  int isolation=Connection.TRANSACTION_SERIALIZABLE, failInsertAt=0, inserts=0,rollbacks=0,commits=0;boolean grantUpdate=false,grantInsert=true,grantRead=true,grantDelete=false;
  List<String> sqls=new ArrayList<>();List<List<Object>> binds=new ArrayList<>();
  Connection connection(){return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->switch(m.getName()){
   case "getAutoCommit"->autoCommit;case "isReadOnly"->readOnly;case "isClosed"->false;case "getTransactionIsolation"->isolation;
   case "rollback"->{rollbacks++;yield null;}case "commit"->{commits++;yield null;}
   case "getMetaData"->Proxy.newProxyInstance(DatabaseMetaData.class.getClassLoader(),new Class[]{DatabaseMetaData.class},(p2,m2,a2)->m2.getName().equals("getDatabaseProductName")?product:defaultReturn(m2.getReturnType()));
   case "prepareStatement"->{String sql=(String)a[0];sqls.add(sql);List<Object> b=new ArrayList<>();binds.add(b);yield statement(sql,b);}
   default->throw new SQLException("unexpected connection method "+m.getName());});}
  Object statement(String sql,List<Object> b){return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class[]{PreparedStatement.class},(p,m,a)->switch(m.getName()){
   case "setQueryTimeout","close"->null;
   case "setObject","setString","setLong","setTimestamp"->{int i=(Integer)a[0];while(b.size()<i)b.add(null);b.set(i-1,a[1]);yield null;}
   case "executeQuery"->result(sql);
   case "executeUpdate"->{inserts++;if(inserts==failInsertAt)throw new SQLException("injected insert failure");yield 1;}
   default->throw new SQLException("unexpected statement method "+m.getName());});}
  Object result(String sql){List<Object[]> rows=new ArrayList<>();
   if(sql.contains("FROM pg_roles"))rows.add(new Object[]{role,rls,superuser,bypass});
   else if(sql.contains("has_table_privilege"))for(String t:List.of("patient_registration_review_decisions","patient_registration_review_events","patient_registration_review_audit","clinical_outbox"))rows.add(new Object[]{t,grantRead,!t.equals("patient_registration_review_decisions")&&grantInsert,grantUpdate,grantDelete});
   else if(sql.contains("FROM pg_class"))for(String t:List.of("patient_registration_review_decisions","patient_registration_review_events","patient_registration_review_audit","clinical_outbox"))rows.add(new Object[]{t,true,forced});
   else if(sql.contains("FOR UPDATE NOWAIT"))rows.add(new Object[]{2L,Timestamp.from(expired?now.minusSeconds(10):now.plusSeconds(3600)),null});
   else if(sql.contains("ORDER BY event_version ASC")){/* new review, no history */}
   else throw new IllegalArgumentException("unexpected query "+sql);
   return Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class[]{ResultSet.class},new InvocationHandler(){int i=-1;
    public Object invoke(Object p,Method m,Object[] a)throws Throwable{return switch(m.getName()){
     case "next"->++i<rows.size();case "getString"->rows.get(i)[(Integer)a[0]-1];case "getLong"->rows.get(i)[(Integer)a[0]-1];
     case "getBoolean"->rows.get(i)[(Integer)a[0]-1];case "getTimestamp"->rows.get(i)[(Integer)a[0]-1];case "close"->null;
     default->throw new SQLException("unexpected result method "+m.getName());};}});
  }
 }
 static void deny(Throwing work){boolean failed=false;try{work.run();}catch(SQLException|IllegalArgumentException e){failed=true;}ok(failed);}
 interface Throwing{void run()throws SQLException;}
 static void migration(){ // DDL verified separately by Python, Java confirms no executable permission bypass
  ok(ReviewTransactionFence.eligible(new DB().connection()));ok(!ReviewTransactionFence.eligible(null));
 }
 static void role()throws Exception{
  DB d=new DB();ok(ReviewDbRoleAttestation.verify(d.connection(),d.role));ok(d.sqls.size()==2);
  d=new DB();d.superuser=true;DB x=d;ok(!ReviewDbRoleAttestation.verify(x.connection(),x.role));
  d=new DB();d.forced=false;DB y=d;ok(!ReviewDbRoleAttestation.verify(y.connection(),y.role));
  d=new DB();d.product="H2";DB z=d;ok(!ReviewDbRoleAttestation.verify(z.connection(),z.role));
  d=new DB();d.autoCommit=true;DB a=d;ok(!ReviewDbRoleAttestation.verify(a.connection(),a.role));
  d=new DB();d.role="postgres";DB b=d;ok(!ReviewDbRoleAttestation.verify(b.connection(),"synqoria_review_runtime"));
 }
 static void lock()throws Exception{
  DB d=new DB();var history=ReviewEventRowLock.lockAndRead(d.connection(),tenant,facility,review,candidate,2,now);
  ok(history.isEmpty());ok(d.sqls.get(0).contains("FOR UPDATE NOWAIT"));ok(d.binds.get(0).equals(List.of(tenant,facility,review,candidate)));
  ok(d.sqls.get(1).contains("LIMIT 3"));
  d=new DB();d.expired=true;DB x=d;deny(()->ReviewEventRowLock.lockAndRead(x.connection(),tenant,facility,review,candidate,2,now));
  d=new DB();DB y=d;deny(()->ReviewEventRowLock.lockAndRead(y.connection(),tenant,facility,review,candidate,3,now));
  d=new DB();DB z=d;deny(()->ReviewEventRowLock.lockAndRead(z.connection(),null,facility,review,candidate,2,now));
 }
 static void append()throws Exception{
  DB d=new DB();String digest=ReviewAtomicAppend.append(d.connection(),request(1,ReviewEventTransition.Kind.REVIEW_APPROVED,ReviewScopedDigestV2.genesis()));
  ok(digest.matches("[0-9a-f]{64}"));ok(d.inserts==3);ok(d.rollbacks==0);ok(d.commits==0);
  ok(d.sqls.get(0).startsWith("INSERT INTO patient_registration_review_events"));ok(d.sqls.get(1).contains("clinical_outbox"));ok(d.sqls.get(2).contains("patient_registration_review_audit"));
  ok(d.binds.get(1).get(7).toString().contains(digest));
  d=new DB();d.failInsertAt=2;DB x=d;deny(()->ReviewAtomicAppend.append(x.connection(),request(1,ReviewEventTransition.Kind.REVIEW_APPROVED,ReviewScopedDigestV2.genesis())));ok(d.rollbacks==1);ok(d.commits==0);
  d=new DB();d.failInsertAt=3;DB y=d;deny(()->ReviewAtomicAppend.append(y.connection(),request(1,ReviewEventTransition.Kind.REVIEW_APPROVED,ReviewScopedDigestV2.genesis())));ok(d.rollbacks==1);
 }
 static void integration()throws Exception{
  DB d=new DB();String digest=ReviewRestrictedTransaction.execute(d.connection(),d.role,request(1,ReviewEventTransition.Kind.REVIEW_APPROVED,ReviewScopedDigestV2.genesis()),now);
  ok(digest.matches("[0-9a-f]{64}"));ok(d.inserts==3);ok(d.commits==0);ok(d.rollbacks==0);ok(d.sqls.size()==8);
  d=new DB();DB x=d;deny(()->ReviewRestrictedTransaction.execute(x.connection(),x.role,request(2,ReviewEventTransition.Kind.REVIEW_REVOKED,ReviewScopedDigestV2.genesis()),now));ok(d.inserts==0);ok(d.rollbacks==1);
  d=new DB();d.expired=true;DB y=d;deny(()->ReviewRestrictedTransaction.execute(y.connection(),y.role,request(1,ReviewEventTransition.Kind.REVIEW_APPROVED,ReviewScopedDigestV2.genesis()),now));ok(d.inserts==0);ok(d.rollbacks==1);
  d=new DB();d.failInsertAt=2;DB z=d;deny(()->ReviewRestrictedTransaction.execute(z.connection(),z.role,request(1,ReviewEventTransition.Kind.REVIEW_APPROVED,ReviewScopedDigestV2.genesis()),now));ok(d.rollbacks>=1);ok(d.commits==0);
 }
 public static void main(String[] args)throws Exception{
  switch(args[0]){
   case "P05-B0053"->migration();case "P05-B0054"->role();case "P05-B0055"->lock();case "P05-B0056"->append();case "P05-B0057"->integration();
   default->throw new IllegalArgumentException("Unknown batch");
  }
  System.out.println("PASS "+args[0]+" "+checks+" mock-JDBC assertions; no live PostgreSQL/clinical authorization");
 }
}
