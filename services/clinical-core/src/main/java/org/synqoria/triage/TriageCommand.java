package org.synqoria.triage;
import java.time.Instant;
import java.util.UUID;
/** Server-side command envelope; caller is expected to obtain actor and timestamp from trusted server context. */
public record TriageCommand(UUID commandId,UUID actor,Instant trustedNow,int expectedRevision,String rationale) {
 public TriageCommand {if(commandId==null||actor==null||trustedNow==null||expectedRevision<0||rationale!=null&&rationale.length()>500)throw new IllegalArgumentException("invalid command envelope");}
}
