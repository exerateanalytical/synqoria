package org.synqoria.longitudinal;
import java.time.*;import java.util.*;
import static org.synqoria.longitudinal.ClinicalCaseCore.Scope;
import static org.synqoria.longitudinal.LongitudinalInboxSafety.Envelope;
public class LongitudinalInboxClockFenceTest {
 public static void main(String[] args){Instant now=Instant.parse("2026-11-01T12:00:00Z");
 Scope s=new Scope(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
 for(int i=0;i<5;i++){
 Instant when=switch(i){case 0->now;case 1->now.plusSeconds(120);case 2->now.plusSeconds(121);
 case 3->now.minus(Duration.ofDays(7)).minusSeconds(1);default->now.minusSeconds(3600);};
 Envelope e=new Envelope(s,UUID.randomUUID(),UUID.randomUUID(),1,"a".repeat(64),"note.sign",when);
 boolean deny=i==2||i==3||i==4;
 try{LongitudinalInboxClockFence.check(e,now,i==4?now:now.minus(Duration.ofDays(20)));
 if(deny)throw new AssertionError("accepted event outside clock window "+i);
 }catch(SecurityException ex){if(!deny)throw new AssertionError(ex);}
 }System.out.println("PASS B0121 clock fence 5 checks");}
}
