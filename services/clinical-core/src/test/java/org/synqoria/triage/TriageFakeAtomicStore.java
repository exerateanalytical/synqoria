package org.synqoria.triage;
import java.util.*;
import java.util.function.Function;
/** Transaction-copy fixture; provides executable atomicity regression only, not a real DB. */
public final class TriageFakeAtomicStore implements TriagePersistencePort {
 public Map<Scope,TriageCase> cases=new HashMap<>();
 public Map<String,Receipt> receipts=new HashMap<>();
 public Map<UUID,TriageCase.Change> audits=new HashMap<>();
 public Map<UUID,String> outbox=new HashMap<>();
 public void seed(TriageCase c){cases.put(new Scope(c.tenant(),c.facility(),c.patient(),c.id()),c);}
 @Override public synchronized <T> T transaction(Function<Unit,T> work){
  var newCases=new HashMap<>(cases);var newReceipts=new HashMap<>(receipts);var newAudits=new HashMap<>(audits);var newOutbox=new HashMap<>(outbox);
  Unit unit=new Unit(){
   public TriageCase loadLocked(Scope scope){return newCases.get(scope);}
   public Receipt receipt(Scope scope,UUID id){return newReceipts.get(scope+"|"+id);}
   public void create(TriageCase c){Scope key=new Scope(c.tenant(),c.facility(),c.patient(),c.id());if(newCases.putIfAbsent(key,c)!=null)throw new IllegalStateException("duplicate case");}
   public void update(TriageCase before,TriageCase after){Scope key=new Scope(before.tenant(),before.facility(),before.patient(),before.id());if(!newCases.replace(key,before,after))throw new IllegalStateException("cas conflict");}
   public void appendAuditAndOutbox(TriageCase c,TriageCase.Change entry){if(newAudits.putIfAbsent(entry.commandId(),entry)!=null)throw new IllegalStateException("duplicate audit");if(newOutbox.putIfAbsent(entry.commandId(),entry.digest())!=null)throw new IllegalStateException("duplicate outbox");}
   public void saveReceipt(Scope scope,Receipt receipt){if(newReceipts.putIfAbsent(scope+"|"+receipt.commandId(),receipt)!=null)throw new IllegalStateException("duplicate receipt");}
  };
  T result=work.apply(unit);cases=newCases;receipts=newReceipts;audits=newAudits;outbox=newOutbox;return result;
 }
}
