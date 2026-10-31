package org.synqoria.triage;

import java.time.Instant;
import java.util.*;

/** M03 cross-check between protected event/receipt/outbox rows and the restored encrypted snapshot.
 * The database still needs independently reviewed RLS, key custody and append-only controls.
 */
public final class TriageAuditEvidence {
    private TriageAuditEvidence(){}
    public record Event(int revision,UUID commandId,String operation,String previousDigest,String stateDigest,
                        String eventDigest,String actorDigest,Instant at,String receiptDigest,String outboxDigest){
        public Event{
            if(revision<1||commandId==null||!TriageCase.safe(operation,80)||!TriageCase.hex(previousDigest)||
               !TriageCase.hex(stateDigest)||!TriageCase.hex(eventDigest)||!TriageCase.hex(actorDigest)||
               at==null||!TriageCase.hex(receiptDigest)||!TriageCase.hex(outboxDigest))
                throw new IllegalArgumentException("invalid triage persisted evidence");
        }
    }
    public static boolean receiptMatchesCase(TriageCase c,TriagePersistencePort.Receipt receipt){
        if(c==null||receipt==null||receipt.revision()>c.revision()||!TriageAudit.verify(c))return false;
        if(receipt.revision()==1)return c.genesis()!=null&&c.genesis().commandId().equals(receipt.commandId())&&
            c.genesis().digest().equals(receipt.eventDigest())&&c.genesis().at().equals(receipt.committedAt());
        return c.changes().stream().anyMatch(e->e.revision()==receipt.revision()&&e.commandId().equals(receipt.commandId())&&
            e.digest().equals(receipt.eventDigest())&&e.at().equals(receipt.committedAt()));
    }
    public static void requireFullChain(TriageCase c,List<Event> rows){
        if(c==null||rows==null||c.genesis()==null||!TriageAudit.verify(c)||rows.size()!=c.revision())
            throw new SecurityException("triage ledger is missing or unanchored");
        String prev="0".repeat(64);var commands=new HashSet<UUID>();
        for(int index=0;index<rows.size();index++){
            Event row=rows.get(index);
            TriageCase.Change expected=index==0?c.genesis():c.changes().get(index-1);
            if(row.revision()!=index+1||row.revision()!=expected.revision()||!commands.add(row.commandId())||
               !row.commandId().equals(expected.commandId())||!row.operation().equals(expected.operation())||
               !row.previousDigest().equals(prev)||!row.previousDigest().equals(expected.previousDigest())||
               !row.stateDigest().equals(expected.stateDigest())||!row.eventDigest().equals(expected.digest())||
               !row.at().equals(expected.at())||
               !row.actorDigest().equals(TriageAudit.hash("triage-actor-v1|"+expected.actor()))||
               !row.receiptDigest().equals(row.eventDigest())||!row.outboxDigest().equals(row.eventDigest()))
                throw new SecurityException("triage ledger mismatch at revision "+(index+1));
            prev=row.eventDigest();
        }
        if(!rows.get(rows.size()-1).stateDigest().equals(TriageAudit.stateDigest(c)))
            throw new SecurityException("triage ledger does not match encrypted state");
    }
}
