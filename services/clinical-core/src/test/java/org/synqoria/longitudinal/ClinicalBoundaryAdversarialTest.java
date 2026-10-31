package org.synqoria.longitudinal;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.synqoria.longitudinal.ClinicalCaseCore.*;

/** Hostile synthetic tests of the M04-M06 INTERNAL boundary; not a substitute for live RLS/OIDC tests. */
public final class ClinicalBoundaryAdversarialTest {
 static int checks;
 static void ok(boolean yes){checks++;if(!yes)throw new AssertionError("adversarial assertion failed: "+checks);}
 static void denies(Runnable r){boolean rejected=false;try{r.run();}catch(SecurityException|IllegalArgumentException|IllegalStateException e){rejected=true;}ok(rejected);}
 static final ClinicalVerticalBatchTest.F F=new ClinicalVerticalBatchTest.F();
 static void verify()throws Exception{
  F.active();State s=F.state();ok(auditValid(s));ok(s.events.size()==2&&s.outbox.size()==2&&s.receipts.size()==2);
  var other=new Scope(s.scope.tenant(),s.scope.facility(),UUID.randomUUID(),s.scope.encounter());
  denies(()->EncounterActions.run(F.core,other,F.a,F.cmd(),"encounter.record_room",Map.of("room","X")));
  denies(()->ObservationActions.timeline(s,other,F.a,"local:pulse",10));
  denies(()->NoteActions.run(F.core,F.s,new Actor(UUID.randomUUID(),Set.of()),F.cmd(),"note.create_draft",Map.of("noteId",UUID.randomUUID().toString())));
  long revision=F.state().revision;
  var stale=new Command(UUID.randomUUID(),0,Instant.now());
  denies(()->EncounterActions.run(F.core,F.s,F.a,stale,"encounter.record_room",Map.of("room","X")));
  ok(F.state().revision==revision);
  var cmd=F.cmd();var result=EncounterActions.run(F.core,F.s,F.a,cmd,"encounter.record_room",Map.of("room","Ward B"));
  var duplicate=EncounterActions.run(F.core,F.s,F.a,cmd,"encounter.record_room",Map.of("room","Ward B"));
  ok(result.revision()==duplicate.revision()&&duplicate.replay());
  denies(()->EncounterActions.run(F.core,F.s,F.a,cmd,"encounter.record_room",Map.of("room","Ward C")));
  ok(F.state().revision==revision+1&&auditValid(F.state()));
  F.store.corruptAuditForTest();denies(()->F.e("encounter.record_room",Map.of("room","Y")));
  // Separate fixtures for unaffected state checks.
  var notes=new ClinicalVerticalBatchTest.F();notes.active();notes.signed();
  String frozen=notes.state().notes.get(ClinicalVerticalBatchTest.N).body;
  denies(()->notes.n("note.write_draft",Map.of("noteId",ClinicalVerticalBatchTest.N.toString(),"body","Overwrite")));
  ok(notes.state().notes.get(ClinicalVerticalBatchTest.N).body.equals(frozen));
  denies(()->notes.nWith(notes.b,"note.entered_in_error",Map.of("noteId",ClinicalVerticalBatchTest.N.toString(),"reason","")));
  var n2=new ClinicalVerticalBatchTest.F();n2.active();n2.review();
  denies(()->n2.n("note.ready_to_sign",Map.of("noteId",ClinicalVerticalBatchTest.N.toString())));
  n2.nWith(n2.b,"note.ready_to_sign",Map.of("noteId",ClinicalVerticalBatchTest.N.toString()));
  denies(()->n2.nWith(n2.b,"note.sign",Map.of("noteId",ClinicalVerticalBatchTest.N.toString())));
  var obs=new ClinicalVerticalBatchTest.F();obs.active();obs.pending();
  denies(()->obs.o("observation.verify",Map.of("observationId",ClinicalVerticalBatchTest.O.toString())));
  obs.oWith(obs.b,"observation.verify",Map.of("observationId",ClinicalVerticalBatchTest.O.toString()));
  denies(()->obs.o("observation.attach_device",Map.of("observationId",ClinicalVerticalBatchTest.O.toString(),"device","Overwrite")));
  var inp=new ClinicalVerticalBatchTest.F();inp.active();
  denies(()->inp.o("observation.capture_numeric",Map.of("observationId",ClinicalVerticalBatchTest.O.toString(),"code","hr","unit","/min","value","NaN","measuredAt","2026-10-31T08:59:00Z")));
  denies(()->inp.o("observation.capture_numeric",Map.of("observationId",ClinicalVerticalBatchTest.O.toString(),"code","hr","unit","/min","value","Infinity","measuredAt","2026-10-31T08:59:00Z")));
  denies(()->inp.o("observation.capture_numeric",Map.of("observationId",ClinicalVerticalBatchTest.O.toString(),"code","hr","unit","/min","value","12","measuredAt","invalid")));
  ok(inp.state().observations.isEmpty());
  var adm=new ClinicalVerticalBatchTest.F();adm.active();adm.e("encounter.request_admission",Map.of("reason","Clinician request"));
  denies(()->adm.e("encounter.approve_admission",Map.of()));
  adm.eWith(adm.b,"encounter.approve_admission",Map.of());
  var tr=new ClinicalVerticalBatchTest.F();tr.active();tr.transfer();
  denies(()->tr.e("encounter.accept_transfer",Map.of()));
  tr.eWith(tr.b,"encounter.accept_transfer",Map.of());
  ok(auditValid(tr.state()));
  // Source-only bed proposals must never claim a real occupied bed.
  adm.e("encounter.propose_bed",Map.of("bedId",UUID.randomUUID().toString()));
  ok(adm.state().bed==null&&adm.state().bedReservation!=null);
  // Scope and version concurrency: exactly one update can commit the same revision.
  var race=new ClinicalVerticalBatchTest.F();race.active();long rev=race.state().revision;
  var gate=new CountDownLatch(1);var done=new CountDownLatch(2);var success=new AtomicInteger();
  for(int n=0;n<2;n++)new Thread(()->{try{gate.await();
   EncounterActions.run(race.core,race.s,race.a,new Command(UUID.randomUUID(),rev,Instant.now()),
      "encounter.record_room",Map.of("room","Same revision"));success.incrementAndGet();
  }catch(IllegalStateException e){}catch(InterruptedException e){Thread.currentThread().interrupt();}
    finally{done.countDown();}}).start();
  gate.countDown();ok(done.await(5,TimeUnit.SECONDS));ok(success.get()==1);ok(race.state().revision==rev+1);
  ok(auditValid(race.state()));
  var injected=new ClinicalVerticalBatchTest.F();injected.active();long before=injected.state().revision;
  denies(()->injected.core.execute(injected.s,injected.a,injected.cmd(),"encounter.write",
      "encounter.inject_failure","fixture",state->{state.room="UNCOMMITTED";throw new IllegalStateException("synthetic rollback");}));
  ok(injected.state().revision==before&&injected.state().room.isBlank());
  // Fuzz admission/notes/observations with forged scope and untrusted extra input.
  denies(()->inp.o("observation.capture_text",Map.of("observationId",ClinicalVerticalBatchTest.O.toString(),"code","x","unit","u","value","a","measuredAt","2026-10-31T09:00:00Z","unrecognized","malicious")));
  denies(()->EncounterActions.run(inp.core,inp.s,inp.a,inp.cmd(),"encounter.unknown",Map.of()));
  ok(auditValid(inp.state()));
 }
 public static void main(String[] args)throws Exception {verify();System.out.println("PASS "+checks+" adversarial assertions for longitudinal clinical core");}
}
