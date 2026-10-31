package org.synqoria.longitudinal;
import java.util.*;
import java.util.function.Function;
import static org.synqoria.longitudinal.ClinicalCaseCore.*;

/** Synthetic test-only atomic persistence. Never wire into a production HTTP route. */
public final class UnitTestClinicalStore implements ClinicalCaseCore.Persistence {
 private State current;
 public UnitTestClinicalStore(Scope scope){current=new State(scope);}
 @Override public synchronized <T> T transaction(Scope scope,Function<Tx,T> action){
  if(!current.scope.equals(scope))throw new SecurityException("clinical resource unavailable");
  final State original=new State(current);
  final State[] written={null};
  Tx tx=new Tx(){
    @Override public State loadLocked(){return new State(original);}
    @Override public void saveAtomically(State next,Event event,Receipt receipt){
      if(written[0]!=null||!original.scope.equals(next.scope)||next.revision!=original.revision+1||
         !next.events.contains(event)||!next.outbox.contains(event)||!receipt.eventHash().equals(event.hash())||
         !next.receipts.containsKey(receipt.commandId()))throw new IllegalStateException("atomic evidence constraint");
      written[0]=new State(next);
    }
  };
  T result=action.apply(tx);
  if(written[0]!=null)current=written[0];
  return result;
 }
 public synchronized State snapshot(){return new State(current);}
 public synchronized void corruptAuditForTest(){current.events.clear();}
}
