package org.synqoria.registration;
import java.util.*;import java.sql.*;import java.lang.reflect.*;import java.time.*;
/** Test-only JDBC proxy; not a PostgreSQL integration or RLS acceptance. */
public final class V240ReviewIntegrationTest {
 static int n=0;static void ok(boolean b){n++;if(!b)throw new AssertionError("assertion "+n);}
 static UUID id(){return UUID.randomUUID();}static String A="a".repeat(64),B="b".repeat(64);
 static UUID t=id(),f=id(),r=id(),c=id();
 static List<ReviewScopedHistoryV2.Event> history(){
  String prev=ReviewScopedDigestV2.genesis();String d=ReviewScopedDigestV2.digest(t,f,r,c,1,ReviewEventTransition.Kind.REVIEW_APPROVED,A,prev);
  return List.of(new ReviewScopedHistoryV2.Event(t,f,r,c,1,ReviewEventTransition.Kind.REVIEW_APPROVED,A,prev,d));
 }
 static void jdbc()throws Exception{
  var rows=new ArrayList<>(history());var sqls=new ArrayList<String>();var binds=new ArrayList<Object>();int[] closed={0};
  Connection conn=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->switch(m.getName()){
   case "isClosed"->false;case "getAutoCommit"->false;case "isReadOnly"->false;case "getTransactionIsolation"->Connection.TRANSACTION_SERIALIZABLE;
   case "prepareStatement"->{sqls.add((String)a[0]);yield Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class[]{PreparedStatement.class},(pp,pm,pa)->switch(pm.getName()){
    case "setQueryTimeout"->null;case "setObject"->{binds.add(pa[1]);yield null;}case "close"->{closed[0]++;yield null;}
    case "executeQuery"->Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class[]{ResultSet.class},new InvocationHandler(){int i=-1;
     public Object invoke(Object ppp,Method mm,Object[] aa)throws Throwable{return switch(mm.getName()){
      case "next"->++i<rows.size();case "getObject"->switch((int)aa[0]){case 1->rows.get(i).tenant();case 2->rows.get(i).facility();case 3->rows.get(i).review();case 4->rows.get(i).command();default->throw new SQLException("bad index");};
      case "getLong"->rows.get(i).version();case "getString"->switch((int)aa[0]){case 6->rows.get(i).kind().name();case 7->rows.get(i).binding();case 8->rows.get(i).previous();case 9->rows.get(i).digest();default->throw new SQLException("bad index");};
      case "close"->null;default->throw new SQLException("unexpected result method "+mm.getName());};}});
    default->throw new SQLException("unexpected statement method "+pm.getName());});}
   default->throw new SQLException("unexpected connection method "+m.getName());});
  ok(ReviewEventJdbcReadV2.read(conn,t,f,r).equals(rows));ok(sqls.size()==1);ok(sqls.get(0).contains("LIMIT 3"));
  ok(sqls.get(0).contains("tenant_id=? AND facility_id=? AND review_id=?"));ok(binds.equals(List.of(t,f,r)));ok(closed[0]==1);
  rows.clear();var e=history().get(0);rows.add(new ReviewScopedHistoryV2.Event(e.tenant(),e.facility(),e.review(),e.command(),e.version(),e.kind(),B,e.previous(),e.digest()));
  boolean denied=false;try{ReviewEventJdbcReadV2.read(conn,t,f,r);}catch(SQLException x){denied=true;}ok(denied);
  denied=false;try{ReviewEventJdbcReadV2.read(conn,id(),f,r);}catch(SQLException x){denied=true;}ok(denied);
  denied=false;try{ReviewEventJdbcReadV2.read(conn,null,f,r);}catch(SQLException x){denied=true;}ok(denied);
 }
 static void sqlPlan(){
  UUID ev=id(),actor=id(),candidate=id();var plan=ReviewEventSqlPlanV2.prepare(t,f,ev,r,actor,c,candidate,3,1,ReviewEventTransition.Kind.REVIEW_APPROVED,A,ReviewScopedDigestV2.genesis(),Instant.EPOCH);
  ok(plan.sql().startsWith("INSERT INTO patient_registration_review_events"));ok(plan.params().size()==14);ok(plan.params().get(0).equals(t));ok(plan.params().get(1).equals(f));
  ok(plan.params().get(13).equals(plan.digest()));ok(plan.digest().equals(history().get(0).digest()));
  boolean denied=false;try{ReviewEventSqlPlanV2.prepare(t,f,ev,r,actor,c,candidate,-1,1,ReviewEventTransition.Kind.REVIEW_APPROVED,A,ReviewScopedDigestV2.genesis(),Instant.EPOCH);}catch(IllegalArgumentException e){denied=true;}ok(denied);
 }
 static void replay(){
  var h=history();ok(ReviewReplayGuardV2.inspect(h,t,f,r,c,ReviewEventTransition.Kind.REVIEW_APPROVED,A)==ReviewReplayGuardV2.Result.REPLAY_REQUIRES_DB_PROOF);
  ok(ReviewReplayGuardV2.inspect(h,t,f,r,c,ReviewEventTransition.Kind.REVIEW_APPROVED,B)==ReviewReplayGuardV2.Result.CONFLICT);
  ok(ReviewReplayGuardV2.inspect(h,t,f,r,id(),ReviewEventTransition.Kind.REVIEW_REVOKED,B)==ReviewReplayGuardV2.Result.NEW_CANDIDATE_NOT_AUTHORIZED);
  ok(ReviewReplayGuardV2.inspect(h,id(),f,r,id(),ReviewEventTransition.Kind.REVIEW_REVOKED,B)==ReviewReplayGuardV2.Result.DENIED);
  ok(ReviewReplayGuardV2.inspect(List.of(),t,f,r,id(),ReviewEventTransition.Kind.REVIEW_REVOKED,B)==ReviewReplayGuardV2.Result.DENIED);
  ok(ReviewReplayGuardV2.inspect(List.of(),t,f,r,id(),ReviewEventTransition.Kind.REVIEW_APPROVED,A)==ReviewReplayGuardV2.Result.NEW_CANDIDATE_NOT_AUTHORIZED);
 }
 static void outbox(){
  var ev=ReviewOutboxEvidenceV2.from(history(),t,f,r,id());ok(ev.isPresent());ok(ev.get().eventType().equals("review.approved.v2"));
  ok(ev.get().digest().equals(history().get(0).digest()));ok(ReviewOutboxEvidenceV2.from(history(),id(),f,r,id()).isEmpty());
  ok(ReviewOutboxEvidenceV2.from(history(),t,f,r,null).isEmpty());
  var h=new ArrayList<>(history());var first=h.get(0);h.set(0,new ReviewScopedHistoryV2.Event(t,f,r,c,1,first.kind(),B,first.previous(),first.digest()));
  ok(ReviewOutboxEvidenceV2.from(h,t,f,r,id()).isEmpty());
 }
 static void receipt(){
  var x=ReviewDisclosureReceiptV2.from(history(),t,f,r);ok(x.isPresent());ok(x.get().status().equals("REVIEWED_NOT_RELEASED"));
  ok(x.get().revision()==1);ok(!x.get().toString().contains(t.toString()));ok(!x.get().toString().contains(r.toString()));
  ok(ReviewDisclosureReceiptV2.from(history(),id(),f,r).isEmpty());
  var h=new ArrayList<>(history());var first=h.get(0);h.set(0,new ReviewScopedHistoryV2.Event(t,f,r,c,1,first.kind(),B,first.previous(),first.digest()));
  ok(ReviewDisclosureReceiptV2.from(h,t,f,r).isEmpty());
 }
 public static void main(String[] a)throws Exception{
  switch(a[0]){case "P05-B0048"->jdbc();case "P05-B0049"->sqlPlan();case "P05-B0050"->replay();case "P05-B0051"->outbox();case "P05-B0052"->receipt();default->throw new IllegalArgumentException("Unknown batch");}
  System.out.println("PASS "+a[0]+" "+n+" assertions; no live DB/OIDC/clinical release");
 }
}
