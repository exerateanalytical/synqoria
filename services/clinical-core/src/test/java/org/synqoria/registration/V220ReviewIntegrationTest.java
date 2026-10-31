package org.synqoria.registration;
import java.util.*;import java.time.*;import java.sql.*;import java.lang.reflect.*;
/** Synthetic fixtures only; JDBC proxy is not PostgreSQL or RLS evidence. */
public final class V220ReviewIntegrationTest {
 static int n;
 static void ok(boolean v){n++;if(!v)throw new AssertionError("assertion "+n);}
 static void invalid(Runnable r){boolean failed=false;try{r.run();}catch(IllegalArgumentException e){failed=true;}ok(failed);}
 static UUID id(){return UUID.randomUUID();}
 static final String A="a".repeat(64),B="b".repeat(64);
 static ReviewEventJournal journal(UUID t,UUID f,UUID r){return new ReviewEventJournal(t,f,r);}
 static List<ReviewEventJournal.Entry> two(UUID t,UUID f,UUID r){
  var j=journal(t,f,r);j.append(t,f,r,id(),0,ReviewEventTransition.Kind.REVIEW_APPROVED,A);
  j.append(t,f,r,id(),1,ReviewEventTransition.Kind.REVIEW_REVOKED,B);return j.history();
 }
 static void testJournal(){
  UUID t=id(),f=id(),r=id(),c=id();var j=journal(t,f,r);
  var one=j.append(t,f,r,c,0,ReviewEventTransition.Kind.REVIEW_APPROVED,A);
  ok(one.version()==1);ok(one.previous().equals(ReviewAuditChain.genesis()));
  ok(j.snapshot().state()==ReviewEventTransition.State.APPROVED);
  invalid(()->j.append(t,f,r,c,1,ReviewEventTransition.Kind.REVIEW_REVOKED,B));
  invalid(()->j.append(id(),f,r,id(),1,ReviewEventTransition.Kind.REVIEW_REVOKED,B));
  invalid(()->j.append(t,f,r,id(),0,ReviewEventTransition.Kind.REVIEW_REVOKED,B));
  var two=j.append(t,f,r,id(),1,ReviewEventTransition.Kind.REVIEW_REVOKED,B);
  ok(two.version()==2);ok(two.previous().equals(one.digest()));
  ok(j.snapshot().state()==ReviewEventTransition.State.REVOKED);
  invalid(()->j.append(t,f,r,id(),2,ReviewEventTransition.Kind.REVIEW_APPROVED,A));
  boolean immutable=false;try{j.history().clear();}catch(UnsupportedOperationException e){immutable=true;}ok(immutable);
 }
 static void testRebuilder(){
  UUID t=id(),f=id(),r=id();var rows=two(t,f,r);
  var v=ReviewHistoryRebuilder.rebuild(rows,t,f,r);ok(v.isPresent());ok(v.get().snapshot().state()==ReviewEventTransition.State.REVOKED);
  ok(v.get().headDigest().equals(rows.get(1).digest()));
  ok(ReviewHistoryRebuilder.rebuild(rows,id(),f,r).isEmpty());
  ok(ReviewHistoryRebuilder.rebuild(List.of(rows.get(1),rows.get(0)),t,f,r).isEmpty());
  var first=rows.get(0);var tampered=new ReviewEventJournal.Entry(t,f,r,first.command(),1,first.kind(),B,first.previous(),first.digest());
  ok(ReviewHistoryRebuilder.rebuild(List.of(tampered,rows.get(1)),t,f,r).isEmpty());
  var duplicate=new ReviewEventJournal.Entry(t,f,r,first.command(),2,ReviewEventTransition.Kind.REVIEW_REVOKED,B,first.digest(),rows.get(1).digest());
  ok(ReviewHistoryRebuilder.rebuild(List.of(first,duplicate),t,f,r).isEmpty());
  ok(ReviewHistoryRebuilder.rebuild(List.of(),t,f,r).isEmpty());
 }
 static void testReceipt(){
  UUID t=id(),f=id(),r=id();var rows=two(t,f,r);
  var receipt=ReviewAuditExport.receipt(rows,t,f,r);ok(receipt.isPresent());
  ok(receipt.get().events()==2);ok(receipt.get().terminalState().equals("REVOKED"));
  ok(receipt.get().receiptDigest().matches("[0-9a-f]{64}"));
  ok(!receipt.toString().contains(t.toString()));ok(!receipt.toString().contains(r.toString()));
  ok(ReviewAuditExport.receipt(rows,id(),f,r).isEmpty());
  var corrupt=List.of(rows.get(1));ok(ReviewAuditExport.receipt(corrupt,t,f,r).isEmpty());
  ok(ReviewAuditExport.receipt(rows,t,f,r).get().receiptDigest().equals(receipt.get().receiptDigest()));
 }
 static void testPreflight(){
  UUID t=id(),f=id(),rev=id(),registrar=id(),candidate=id(),commandId=id();Instant now=Instant.parse("2026-10-30T08:00:00Z");
  var session=new VerifiedReviewerSession(t,f,rev,id(),Set.of("patient:identity-review:approve"),5,now.minusSeconds(30),now.plusSeconds(120),false);
  var command=new ReviewCommandEnvelope(t,f,commandId,candidate,registrar,rev,3,A);
  var passed=ReviewDecisionPreflight.Result.PRECONDITIONS_MET_NOT_AUTHORIZED;var denied=ReviewDecisionPreflight.Result.DENIED;
  ok(ReviewDecisionPreflight.inspect(session,command,t,f,3,5,now)==passed);
  ok(ReviewDecisionPreflight.inspect(session,command,id(),f,3,5,now)==denied);
  ok(ReviewDecisionPreflight.inspect(session,command,t,f,4,5,now)==denied);
  ok(ReviewDecisionPreflight.inspect(session,command,t,f,3,6,now)==denied);
  ok(ReviewDecisionPreflight.inspect(session,command,t,f,3,5,now.plusSeconds(121))==denied);
  ok(ReviewDecisionPreflight.inspect(session,command,t,f,3,5,null)==denied);
  ok(ReviewDecisionPreflight.inspect(null,command,t,f,3,5,now)==denied);
  var wrongReviewer=new ReviewCommandEnvelope(t,f,commandId,candidate,registrar,id(),3,A);
  ok(ReviewDecisionPreflight.inspect(session,wrongReviewer,t,f,3,5,now)==denied);
  var revoked=new VerifiedReviewerSession(t,f,rev,id(),Set.of("patient:identity-review:approve"),5,now.minusSeconds(30),now.plusSeconds(120),true);
  ok(ReviewDecisionPreflight.inspect(revoked,command,t,f,3,5,now)==denied);
 }
 static void testJdbc() throws Exception {
  UUID t=id(),f=id(),r=id();final List<ReviewEventJournal.Entry> rows=new ArrayList<>(two(t,f,r));
  List<Object> bindings=new ArrayList<>();List<String> sqls=new ArrayList<>();int[] closed={0};
  Connection connection=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->switch(m.getName()){
   case "isClosed"->false;case "getAutoCommit"->false;case "isReadOnly"->false;
   case "getTransactionIsolation"->Connection.TRANSACTION_SERIALIZABLE;
   case "prepareStatement"->{sqls.add((String)a[0]);yield Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class[]{PreparedStatement.class},(pp,pm,pa)->switch(pm.getName()){
    case "setQueryTimeout"->null;case "setObject"->{bindings.add(pa[1]);yield null;}
    case "close"->{closed[0]++;yield null;}
    case "executeQuery"->Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class[]{ResultSet.class},new InvocationHandler(){int idx=-1;
     public Object invoke(Object ppp,Method mm,Object[] aa)throws Throwable{
      return switch(mm.getName()){
       case "next"->++idx<rows.size();case "getObject"->switch((int)aa[0]){case 1->rows.get(idx).tenant();case 2->rows.get(idx).facility();case 3->rows.get(idx).review();case 4->rows.get(idx).command();default->throw new SQLException("invalid column");};
       case "getLong"->rows.get(idx).version();
       case "getString"->switch((int)aa[0]){case 6->rows.get(idx).kind().name();case 7->rows.get(idx).binding();case 8->rows.get(idx).previous();case 9->rows.get(idx).digest();default->throw new SQLException("invalid column");};
       case "close"->null;default->throw new SQLException("unexpected rs method "+mm.getName());};
     }});
    default->throw new SQLException("unexpected statement method "+pm.getName());});}
   default->throw new SQLException("unexpected connection method "+m.getName());});
  var read=ReviewEventJdbcRead.read(connection,t,f,r);ok(read.equals(rows));ok(sqls.size()==1);
  ok(sqls.get(0).contains("tenant_id=? AND facility_id=? AND review_id=?"));ok(sqls.get(0).contains("LIMIT 3"));
  ok(bindings.equals(List.of(t,f,r)));ok(closed[0]==1);
  boolean denied=false;try{ReviewEventJdbcRead.read(connection,null,f,r);}catch(SQLException e){denied=true;}ok(denied);
  // Fail-closed on corrupted or cross-scope event returned by an untrusted driver.
  rows.clear();rows.add(new ReviewEventJournal.Entry(id(),f,r,id(),1,ReviewEventTransition.Kind.REVIEW_APPROVED,A,ReviewAuditChain.genesis(),ReviewAuditChain.append(ReviewAuditChain.genesis(),A,1,"REVIEW_APPROVED")));
  denied=false;try{ReviewEventJdbcRead.read(connection,t,f,r);}catch(SQLException e){denied=true;}ok(denied);
 }
 public static void main(String[] args)throws Exception{
  n=0;switch(args[0]){
   case "P05-B0038"->testJournal();case "P05-B0039"->testRebuilder();case "P05-B0040"->testReceipt();
   case "P05-B0041"->testPreflight();case "P05-B0042"->testJdbc();default->throw new IllegalArgumentException("Unknown batch");
  }System.out.println("PASS "+args[0]+" "+n+" assertions");
 }
}
