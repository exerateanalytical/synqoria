package org.synqoria.triage;
import org.synqoria.security.*;
import java.time.*;
import java.util.*;
/** Ten distinct patient-scoped projection integrations over the transactional port (synthetic store). */
public final class TriageAuthenticatedReadBatchesTest {
 static final UUID T=TriageWorkflowBatchesTest.T,F=TriageWorkflowBatchesTest.F,P=TriageWorkflowBatchesTest.P,A=TriageWorkflowBatchesTest.A,ST=TriageWorkflowBatchesTest.ST,OTHER=TriageWorkflowBatchesTest.OTHER;
 static final Instant NOW=TriageWorkflowBatchesTest.NOW;
 static final TrustedAccessContext ACTOR=TriageWorkflowBatchesTest.a;
 static final TrustedClinicalAccessEvidence EVIDENCE=TriageWorkflowBatchesTest.e;
 static int checks=0;
 static void ok(boolean val){checks++;if(!val)throw new AssertionError("read check "+checks);}
 static void reject(Runnable run){checks++;try{run.run();throw new AssertionError("access denial expected");}catch(SecurityException|IllegalArgumentException|IllegalStateException expected){}}
 static TriageCase fixture(TriageCase.Stage stage,Map<String,String> meta,String escalation,boolean held,UUID assignee){
  return new TriageCase(UUID.randomUUID(),T,F,P,null,ST,assignee,stage,1,"clinician-entered",escalation,"handover evidence",false,held,true,NOW.minusSeconds(3600),null,Map.of(),meta,List.of());
 }
 static void scenario(String id){
  TriageFakeAtomicStore db=new TriageFakeAtomicStore();
  List<TriageCase> source=new ArrayList<>();
  TriageAuthenticatedReadGateway.View view;
  UUID ref=null;Instant since=null;
  switch(id){
   case "P06-B0218" -> {view=TriageAuthenticatedReadGateway.View.CASE_SUMMARY;source.add(fixture(TriageCase.Stage.WAITING,Map.of(),null,false,A));}
   case "P06-B0219" -> {view=TriageAuthenticatedReadGateway.View.FACILITY_QUEUE;source.add(fixture(TriageCase.Stage.WAITING,Map.of(),null,false,A));source.add(fixture(TriageCase.Stage.CLOSED,Map.of(),null,false,A));}
   case "P06-B0220" -> {view=TriageAuthenticatedReadGateway.View.STATION_QUEUE;ref=ST;source.add(fixture(TriageCase.Stage.WAITING,Map.of(),null,false,A));source.add(fixture(TriageCase.Stage.ASSESSED,Map.of(),null,false,A));}
   case "P06-B0221" -> {view=TriageAuthenticatedReadGateway.View.PENDING_HANDOVERS;source.add(fixture(TriageCase.Stage.ASSIGNED,Map.of("handoverRequestedAt",NOW.toString()),null,false,A));source.add(fixture(TriageCase.Stage.ASSIGNED,Map.of(),null,false,A));}
   case "P06-B0222" -> {view=TriageAuthenticatedReadGateway.View.MANUAL_ESCALATIONS;source.add(fixture(TriageCase.Stage.ASSIGNED,Map.of(),"manual escalation",false,A));source.add(fixture(TriageCase.Stage.ASSIGNED,Map.of(),null,false,A));}
   case "P06-B0223" -> {view=TriageAuthenticatedReadGateway.View.PENDING_REASSESSMENTS;source.add(fixture(TriageCase.Stage.PRIORITIZED,Map.of("reassessmentRequestedAt",NOW.toString()),null,false,A));source.add(fixture(TriageCase.Stage.PRIORITIZED,Map.of(),null,false,A));}
   case "P06-B0224" -> {view=TriageAuthenticatedReadGateway.View.SAFETY_HOLDS;source.add(fixture(TriageCase.Stage.WAITING,Map.of(),null,true,A));source.add(fixture(TriageCase.Stage.WAITING,Map.of(),null,false,A));}
   case "P06-B0225" -> {view=TriageAuthenticatedReadGateway.View.CLINICIAN_WORKLIST;ref=A;source.add(fixture(TriageCase.Stage.ASSIGNED,Map.of(),null,false,A));source.add(fixture(TriageCase.Stage.ASSIGNED,Map.of(),null,false,OTHER));}
   case "P06-B0226" -> {view=TriageAuthenticatedReadGateway.View.ARRIVALS_SINCE;since=NOW.minusSeconds(7200);source.add(fixture(TriageCase.Stage.ASSESSED,Map.of(),null,false,A));}
   case "P06-B0227" -> {
    view=TriageAuthenticatedReadGateway.View.CASE_HISTORY;
    var base=fixture(TriageCase.Stage.WAITING,Map.of(),null,false,A);
    var cmd=new TriageCommand(UUID.randomUUID(),A,NOW,1,"Reviewed rationale");
    source.add(new TriageQueueService().call(ACTOR,EVIDENCE,base,cmd));
   }
   default -> throw new IllegalArgumentException("unknown read batch");
  }
  for(var c:source)db.seed(c);
  List<UUID> ids=source.stream().map(TriageCase::id).toList();
  var gateway=new TriageAuthenticatedReadGateway(db);
  var result=gateway.read(ACTOR,EVIDENCE,P,ids,view,NOW,ref,since);
  if(view==TriageAuthenticatedReadGateway.View.CASE_HISTORY){ok(result.history().size()==1&&result.summaries().isEmpty());}
  else {ok(result.history().isEmpty()&&result.summaries().size()==1);ok(result.summaries().get(0).id().equals(source.get(0).id()));}
  ok(db.audits.isEmpty()&&db.receipts.isEmpty()&&db.outbox.isEmpty());
  var alien=new TrustedAccessContext(UUID.randomUUID(),F,A,TriageWorkflowBatchesTest.CAPS,Set.of(P));
  final UUID selectedRef=ref;final Instant selectedSince=since;final TriageAuthenticatedReadGateway.View selectedView=view;
  reject(()->gateway.read(alien,EVIDENCE,P,ids,selectedView,NOW,selectedRef,selectedSince));
  reject(()->gateway.read(ACTOR,EVIDENCE,UUID.randomUUID(),ids,selectedView,NOW,selectedRef,selectedSince));
  reject(()->gateway.read(ACTOR,EVIDENCE,P,List.of(ids.get(0),ids.get(0)),selectedView,NOW,selectedRef,selectedSince));
  reject(()->gateway.read(ACTOR,EVIDENCE,P,Collections.nCopies(51,UUID.randomUUID()),selectedView,NOW,selectedRef,selectedSince));
  reject(()->gateway.read(ACTOR,EVIDENCE,P,List.of(UUID.randomUUID()),TriageAuthenticatedReadGateway.View.CASE_SUMMARY,NOW,null,null));
  System.out.println(id+" PASS checks="+checks+" summaries="+result.summaries().size()+" audit="+result.history().size());
 }
 public static void main(String[] args){if(args.length!=1)throw new IllegalArgumentException("one batch ID required");scenario(args[0]);}
}
