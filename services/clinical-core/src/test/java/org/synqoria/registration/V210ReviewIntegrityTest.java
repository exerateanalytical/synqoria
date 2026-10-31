package org.synqoria.registration;
import java.util.*;import java.sql.*;import java.lang.reflect.*;import java.time.*;
public final class V210ReviewIntegrityTest {
 static int assertions=0;
 static void check(boolean b){assertions++;if(!b)throw new AssertionError("Assertion "+assertions);}
 static void bad(Runnable r){boolean caught=false;try{r.run();}catch(IllegalArgumentException e){caught=true;}check(caught);}
 static UUID id(){return UUID.randomUUID();}
 static ReviewCommandEnvelope command(){return new ReviewCommandEnvelope(id(),id(),id(),id(),id(),id(),2,"a".repeat(64));}
 static Connection conn(boolean closed,boolean auto,boolean readOnly,int isolation,boolean fails){
  return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
   if(fails)throw new SQLException("denied");
   return switch(m.getName()){case "isClosed"->closed;case "getAutoCommit"->auto;case "isReadOnly"->readOnly;case "getTransactionIsolation"->isolation;default->throw new SQLException("unexpected");};
  });
 }
 public static void main(String[] args){String batch=args[0];assertions=0;
  switch(batch){
   case "P05-B0033"->{var a=command();check(a.sameScope(a));check(!a.sameScope(command()));bad(()->new ReviewCommandEnvelope(a.tenant(),a.facility(),a.command(),a.candidate(),a.registrar(),a.registrar(),2,a.digest()));bad(()->new ReviewCommandEnvelope(a.tenant(),a.facility(),a.command(),a.candidate(),a.registrar(),a.reviewer(),-1,a.digest()));bad(()->new ReviewCommandEnvelope(a.tenant(),a.facility(),a.command(),a.candidate(),a.registrar(),a.reviewer(),2,"bad"));}
   case "P05-B0034"->{var a=command();check(ReviewCommandReplayFence.classify(a,null)==ReviewCommandReplayFence.Decision.FIRST);check(ReviewCommandReplayFence.classify(a,a)==ReviewCommandReplayFence.Decision.SAME);check(ReviewCommandReplayFence.classify(a,new ReviewCommandEnvelope(a.tenant(),a.facility(),a.command(),a.candidate(),a.registrar(),a.reviewer(),2,"b".repeat(64)))==ReviewCommandReplayFence.Decision.CONFLICT);check(ReviewCommandReplayFence.classify(a,command())==ReviewCommandReplayFence.Decision.CROSS_SCOPE);bad(()->ReviewCommandReplayFence.classify(null,a));}
   case "P05-B0035"->{var none=new ReviewEventTransition.Snapshot(ReviewEventTransition.State.NONE,0);var approved=new ReviewEventTransition.Snapshot(ReviewEventTransition.State.APPROVED,1);var revoked=new ReviewEventTransition.Snapshot(ReviewEventTransition.State.REVOKED,2);check(ReviewEventConsistencyFence.consistent(none,approved,ReviewEventTransition.Kind.REVIEW_APPROVED));check(ReviewEventConsistencyFence.consistent(approved,revoked,ReviewEventTransition.Kind.REVIEW_REVOKED));check(!ReviewEventConsistencyFence.consistent(revoked,approved,ReviewEventTransition.Kind.REVIEW_APPROVED));check(!ReviewEventConsistencyFence.consistent(none,revoked,ReviewEventTransition.Kind.REVIEW_REVOKED));check(!ReviewEventConsistencyFence.consistent(approved,approved,ReviewEventTransition.Kind.REVIEW_APPROVED));}
   case "P05-B0036"->{check(ReviewTransactionFence.eligible(conn(false,false,false,Connection.TRANSACTION_SERIALIZABLE,false)));check(!ReviewTransactionFence.eligible(conn(false,true,false,Connection.TRANSACTION_SERIALIZABLE,false)));check(!ReviewTransactionFence.eligible(conn(false,false,true,Connection.TRANSACTION_SERIALIZABLE,false)));check(!ReviewTransactionFence.eligible(conn(false,false,false,Connection.TRANSACTION_READ_COMMITTED,false)));check(!ReviewTransactionFence.eligible(conn(true,false,false,Connection.TRANSACTION_SERIALIZABLE,false)));check(!ReviewTransactionFence.eligible(conn(false,false,false,Connection.TRANSACTION_SERIALIZABLE,true)));}
   case "P05-B0037"->{var a=command();var event=new ReviewOutboxDescriptor(a.tenant(),a.facility(),id(),a.command(),1,ReviewOutboxDescriptor.Kind.REVIEW_APPROVED,Instant.now(),"c".repeat(64));check(ReviewOutboxLinkFence.linked(event,a,1,"c".repeat(64)));check(!ReviewOutboxLinkFence.linked(event,a,2,"c".repeat(64)));check(!ReviewOutboxLinkFence.linked(event,a,1,"d".repeat(64)));check(!ReviewOutboxLinkFence.linked(event,command(),1,"c".repeat(64)));check(!ReviewOutboxLinkFence.linked(null,a,1,"c".repeat(64)));}
   default->throw new IllegalArgumentException(batch);
  }
  System.out.println("PASS "+batch+" "+assertions+" assertions");
 }
}
