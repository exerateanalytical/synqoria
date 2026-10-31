package org.synqoria.registration;
import java.time.*;import java.util.*;import java.sql.*;
/** All tests mock JDBC; do not represent real PostgreSQL RLS or clinical approval. */
public final class V260ReviewerGateTest {
 static int assertions=0;
 static void ok(boolean b){assertions++;if(!b)throw new AssertionError("assertion "+assertions);}
 static void denied(V250ReviewTransactionTest.Throwing f){boolean threw=false;try{f.run();}catch(SQLException|IllegalArgumentException e){threw=true;}ok(threw);}
 static ReviewAtomicAppend.Request q(){return V250ReviewTransactionTest.request(1,ReviewEventTransition.Kind.REVIEW_APPROVED,ReviewScopedDigestV2.genesis());}
 static void privileges()throws Exception{
  var db=new V250ReviewTransactionTest.DB();ok(ReviewRuntimePrivilegeFence.verify(db.connection(),db.role));
  db=new V250ReviewTransactionTest.DB();db.grantUpdate=true;var d=db;ok(!ReviewRuntimePrivilegeFence.verify(d.connection(),d.role));
  db=new V250ReviewTransactionTest.DB();db.grantRead=false;d=db;ok(!ReviewRuntimePrivilegeFence.verify(d.connection(),d.role));
  db=new V250ReviewTransactionTest.DB();db.grantInsert=false;d=db;ok(!ReviewRuntimePrivilegeFence.verify(d.connection(),d.role));
  db=new V250ReviewTransactionTest.DB();db.grantDelete=true;d=db;ok(!ReviewRuntimePrivilegeFence.verify(d.connection(),d.role));
  db=new V250ReviewTransactionTest.DB();db.bypass=true;d=db;ok(!ReviewRuntimePrivilegeFence.verify(d.connection(),d.role));
 }
 static void freshness(){
  Instant now=V250ReviewTransactionTest.now;
  ok(ReviewCommandFreshnessFence.valid(q(),now));
  var orig=q();
  var future=new ReviewAtomicAppend.Request(orig.tenant(),orig.facility(),orig.review(),orig.event(),orig.audit(),orig.outbox(),orig.actor(),orig.command(),orig.candidate(),2,1,orig.kind(),orig.binding(),orig.previous(),now.plusSeconds(31));
  ok(!ReviewCommandFreshnessFence.valid(future,now));
  var old=new ReviewAtomicAppend.Request(orig.tenant(),orig.facility(),orig.review(),orig.event(),orig.audit(),orig.outbox(),orig.actor(),orig.command(),orig.candidate(),2,1,orig.kind(),orig.binding(),orig.previous(),now.minusSeconds(301));
  ok(!ReviewCommandFreshnessFence.valid(old,now));
  var collision=new ReviewAtomicAppend.Request(orig.tenant(),orig.facility(),orig.review(),orig.event(),orig.audit(),orig.event(),orig.actor(),orig.command(),orig.candidate(),2,1,orig.kind(),orig.binding(),orig.previous(),now);
  ok(!ReviewCommandFreshnessFence.valid(collision,now));
  var broken=new ReviewAtomicAppend.Request(orig.tenant(),orig.facility(),orig.review(),orig.event(),orig.audit(),orig.outbox(),orig.actor(),orig.command(),orig.candidate(),2,1,orig.kind(),"INVALID",orig.previous(),now);
  ok(!ReviewCommandFreshnessFence.valid(broken,now));
  ok(!ReviewCommandFreshnessFence.valid(null,now));ok(!ReviewCommandFreshnessFence.valid(q(),null));
 }
 static void transaction()throws Exception{
  var db=new V250ReviewTransactionTest.DB();String digest=ReviewRestrictedTransaction.execute(db.connection(),db.role,q(),V250ReviewTransactionTest.now);
  ok(digest.matches("[0-9a-f]{64}"));ok(db.inserts==3);ok(db.commits==0);ok(db.rollbacks==0);
  db=new V250ReviewTransactionTest.DB();db.grantUpdate=true;final var deniedRole=db;
  denied(()->ReviewRestrictedTransaction.execute(deniedRole.connection(),deniedRole.role,q(),V250ReviewTransactionTest.now));ok(deniedRole.inserts==0);ok(deniedRole.commits==0);
  db=new V250ReviewTransactionTest.DB();final var stale=db;
  denied(()->ReviewRestrictedTransaction.execute(stale.connection(),stale.role,q(),V250ReviewTransactionTest.now.plusSeconds(400)));ok(stale.inserts==0);
  db=new V250ReviewTransactionTest.DB();db.failInsertAt=3;final var failure=db;
  denied(()->ReviewRestrictedTransaction.execute(failure.connection(),failure.role,q(),V250ReviewTransactionTest.now));ok(failure.rollbacks>=1);ok(failure.commits==0);
 }
 public static void main(String[] args)throws Exception{
  switch(args[0]){
   case "P05-B0058"->privileges();
   case "P05-B0060"->freshness();
   case "P05-B0061"->transaction();
   default->throw new IllegalArgumentException("unknown batch");
  }
  System.out.println("PASS "+args[0]+" "+assertions+" mock-JDBC assertions; live PostgreSQL not tested");
 }
}
