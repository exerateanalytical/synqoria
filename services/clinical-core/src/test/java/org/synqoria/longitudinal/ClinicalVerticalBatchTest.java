package org.synqoria.longitudinal;

import java.time.*;
import java.util.*;
import static org.synqoria.longitudinal.ClinicalCaseCore.*;

/** Runnable batch-by-batch behavioral tests. Run with -ea and a single P07-Bxxxx argument. */
public final class ClinicalVerticalBatchTest {
 static final UUID CASE=UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
 static final UUID A=UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
 static final UUID B=UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003");
 static final UUID N=UUID.fromString("aaaaaaaa-0000-0000-0000-000000000004");
 static final UUID P=UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005");
 static final UUID O=UUID.fromString("aaaaaaaa-0000-0000-0000-000000000006");
 static final UUID OC=UUID.fromString("aaaaaaaa-0000-0000-0000-000000000007");
 static final UUID BED=UUID.fromString("aaaaaaaa-0000-0000-0000-000000000008");
 static final UUID DEST=UUID.fromString("aaaaaaaa-0000-0000-0000-000000000009");
 static final UUID SET=UUID.fromString("aaaaaaaa-0000-0000-0000-00000000000a");
 static final List<String> NOTE_COUNTED=List.of("note.create_draft","note.title_draft","note.write_draft",
  "note.set_section","note.remove_section","note.add_diagnosis","note.remove_diagnosis","note.add_problem",
  "note.rename_problem","note.attach_reference","note.detach_reference","note.mark_confidential",
  "note.request_review","note.reviewer_comment","note.request_revision","note.ready_to_sign",
  "note.sign","note.start_amendment","note.sign_amendment","note.entered_in_error");
 static final List<String> ALL;
 static {List<String> l=new ArrayList<>();l.addAll(EncounterActions.OPERATIONS);l.addAll(NOTE_COUNTED);
  l.addAll(ObservationActions.OPERATIONS);ALL=List.copyOf(l);if(ALL.size()!=60||new HashSet<>(ALL).size()!=60)throw new AssertionError("batch mapping");}
 static final class F {
  final Scope s=new Scope(UUID.fromString("10000000-0000-0000-0000-000000000001"),
         UUID.fromString("10000000-0000-0000-0000-000000000002"),
         UUID.fromString("10000000-0000-0000-0000-000000000003"),CASE);
  final Actor a=new Actor(A,Set.of("encounter.write","encounter.assign","encounter.admission.approve","encounter.transfer.accept",
          "encounter.discharge.authorize","note.write","note.sign","note.cosign","note.error.flag",
          "observation.write","observation.verify","observation.invalidate","observation.read"));
  final Actor b=new Actor(B,a.capabilities());
  final UnitTestClinicalStore store=new UnitTestClinicalStore(s);
  final ClinicalCaseCore core=new ClinicalCaseCore(store);
  int tick=0;
  Command cmd(){return new Command(UUID.randomUUID(),store.snapshot().revision,
   Instant.parse("2026-10-31T09:00:00Z").plusSeconds(++tick));}
  Result e(String op,Map<String,String> arg){return eWith(a,op,arg);}
  Result eWith(Actor actor,String op,Map<String,String> arg){return EncounterActions.run(core,s,actor,cmd(),op,arg);}
  Result n(String op,Map<String,String> arg){return nWith(a,op,arg);}
  Result nWith(Actor actor,String op,Map<String,String> arg){return NoteActions.run(core,s,actor,cmd(),op,arg);}
  Result o(String op,Map<String,String> arg){return oWith(a,op,arg);}
  Result oWith(Actor actor,String op,Map<String,String> arg){return ObservationActions.run(core,s,actor,cmd(),op,arg);}
  State state(){return store.snapshot();}
  void checkin(){e("encounter.checkin",Map.of());}
  void active(){checkin();e("encounter.activate",Map.of());}
  void assign(){e("encounter.assign_clinician",Map.of("clinicianId",A.toString()));}
  void admission(){e("encounter.request_admission",Map.of("reason","Clinician-documented admission request"));
     eWith(b,"encounter.approve_admission",Map.of());}
  void transfer(){e("encounter.request_transfer",Map.of("destinationId",DEST.toString()));}
  void draft(){n("note.create_draft",Map.of("noteId",N.toString()));}
  void body(){n("note.write_draft",Map.of("noteId",N.toString(),"body","Synthetic clinician authored observation."));}
  void review(){draft();body();n("note.request_review",Map.of("noteId",N.toString(),"reviewerId",B.toString()));}
  void ready(){review();nWith(b,"note.ready_to_sign",Map.of("noteId",N.toString()));}
  void signed(){ready();n("note.sign",Map.of("noteId",N.toString()));}
  void measured(){o("observation.capture_numeric",standard(O));}
  void pending(){measured();o("observation.request_verification",Map.of("observationId",O.toString()));}
  void verified(){pending();oWith(b,"observation.verify",Map.of("observationId",O.toString()));}
  void corrected(){verified();o("observation.capture_correction",correction());}
  Map<String,String> standard(UUID id){return Map.of("observationId",id.toString(),"code","local:pulse",
    "value","82","unit","/min","measuredAt","2026-10-31T08:59:00Z");}
  Map<String,String> correction(){return Map.of("observationId",OC.toString(),"priorId",O.toString(),
    "code","local:pulse","value","83","unit","/min","measuredAt","2026-10-31T09:04:00Z");}
 }
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static void denies(Class<? extends RuntimeException> type,Runnable r){try{r.run();throw new AssertionError("unsafe command accepted");}
  catch(RuntimeException ex){if(!type.isInstance(ex))throw new AssertionError("wrong refusal: "+ex,ex);}}
 static void testEncounter(int index){
  F f=new F();String action=EncounterActions.OPERATIONS.get(index);
  if(index==1){f.checkin();}
  else if(index>1){f.active();}
  if(Set.of(5,18,19).contains(index))f.assign();
  if(index==4){f.e("encounter.join_team",Map.of("clinicianId",B.toString()));}
  if(index==5){f.e("encounter.join_team",Map.of("clinicianId",B.toString()));}
  if(index==7){f.e("encounter.request_admission",Map.of("reason","request"));}
  if(index==8||index==9){f.admission();}
  if(index==9)f.e("encounter.propose_bed",Map.of("bedId",BED.toString()));
  if(index==13||index==14||index==15)f.transfer();
  if(index==15)f.eWith(f.b,"encounter.accept_transfer",Map.of());
  if(index==17)f.e("encounter.record_leave",Map.of());
  if(index==19){f.e("encounter.submit_discharge_plan",Map.of("plan","Synthetic clinician-authored discharge plan"));}
  Map<String,String> arg=switch(index){
   case 2,3,4,5 -> Map.of("clinicianId",(index==2?A:B).toString());
   case 6 -> Map.of("reason","Clinician-documented admission request");
   case 8 -> Map.of("bedId",BED.toString());
   case 10 -> Map.of("room","Room 2B");
   case 11 -> Map.of("location","Ward East");
   case 12 -> Map.of("destinationId",DEST.toString());
   case 18 -> Map.of("plan","Synthetic documented discharge plan");
   case 19 -> Map.of("authority","Verified responsible clinician attestation");
   default -> Map.of();};
  long before=f.state().revision;Result result=(index==7||index==13||index==19)?f.eWith(f.b,action,arg):f.e(action,arg);
  check(result.revision()==before+1&&!result.replay(),"write revision");State s=f.state();
  switch(index){
   case 0 -> check(s.checkedInAt!=null && s.stage==Stage.PLANNED,"checkin");
   case 1 -> check(s.stage==Stage.ACTIVE && s.startedAt!=null,"activate");
   case 2 -> check(A.equals(s.responsible),"assigned");
   case 3 -> check(s.careTeam.contains(B),"joined");
   case 4 -> check(!s.careTeam.contains(B),"left");
   case 5 -> check(B.equals(s.responsible),"responsibility");
   case 6 -> check(s.admissionRequested&&s.admissionReason.contains("admission"),"admission request");
   case 7 -> check(s.admissionApproved,"approved");
   case 8 -> check(BED.equals(s.bedReservation)&&s.bed==null,"proposal not occupancy");
   case 9 -> check(s.bedReservation==null,"proposal released");
   case 10 -> check(s.room.equals("Room 2B"),"room");
   case 11 -> check(s.location.equals("Ward East"),"location");
   case 12 -> check(s.transferRequested&&DEST.equals(s.transferDestination),"transfer request");
   case 13 -> check(s.transferAccepted,"transfer accepted");
   case 14 -> check(!s.transferRequested&&s.transferDestination==null,"transfer cancelled");
   case 15 -> check(!s.transferRequested&&s.location.contains("transfer-accepted"),"transfer evidence");
   case 16 -> check(s.onLeave,"leave");
   case 17 -> check(!s.onLeave,"return");
   case 18 -> check(s.stage==Stage.DISCHARGE_PLANNED&&s.dischargePlan.contains("Synthetic"),"discharge planning");
   case 19 -> check(s.stage==Stage.DISCHARGED&&s.dischargeAuthorized,"discharge authorization");
  }
  check(ClinicalCaseCore.auditValid(s),"audit consistency");
 }
 static void testNote(int index){
  F f=new F();f.active();String action=NOTE_COUNTED.get(index);
  if(index>0){if(index>=17){f.signed();}else if(index==16){f.ready();}
   else if(index>=13&&index<=15){f.review();}else{f.draft();}}
  if(index==12)f.body();
  if(index==4)f.n("note.set_section",Map.of("noteId",N.toString(),"section","history","text","Synthetic history"));
  if(index==6)f.n("note.add_diagnosis",Map.of("noteId",N.toString(),"code","ICD-10:Z00.0"));
  if(index==8)f.n("note.add_problem",Map.of("noteId",N.toString(),"problemId","problem-001","text","Documented problem"));
  if(index==10)f.n("note.attach_reference",Map.of("noteId",N.toString(),"reference","ref-001"));
  if(index==14)f.nWith(f.b,"note.reviewer_comment",Map.of("noteId",N.toString(),"comment","Please clarify context"));
  if(index==18){f.n("note.start_amendment",Map.of("noteId",P.toString(),"parentId",N.toString()));
   f.n("note.update_amendment",Map.of("noteId",P.toString(),"body","Clinician-authored linked amendment"));}
  Map<String,String> arg=switch(index){
   case 1 -> Map.of("noteId",N.toString(),"title","Clinical entry");
   case 2 -> Map.of("noteId",N.toString(),"body","Synthetic note text");
   case 3 -> Map.of("noteId",N.toString(),"section","assessment","text","Clinician-entered assessment");
   case 4 -> Map.of("noteId",N.toString(),"section","history");
   case 5,6 -> Map.of("noteId",N.toString(),"code","ICD-10:Z00.0");
   case 7 -> Map.of("noteId",N.toString(),"problemId","problem-001","text","Documented problem");
   case 8 -> Map.of("noteId",N.toString(),"problemId","problem-001","text","Amended problem description");
   case 9,10 -> Map.of("noteId",N.toString(),"reference","ref-001");
   case 11 -> Map.of("noteId",N.toString(),"confidential","true");
   case 12 -> Map.of("noteId",N.toString(),"reviewerId",B.toString());
   case 13 -> Map.of("noteId",N.toString(),"comment","Please clarify the circumstances");
   case 17 -> Map.of("noteId",P.toString(),"parentId",N.toString());
   case 18 -> Map.of("noteId",P.toString());
   case 19 -> Map.of("noteId",N.toString(),"reason","Wrong-documentation context reported by signer");
   default -> Map.of("noteId",N.toString());};
  long before=f.state().revision;
  Result r=(index==13||index==14||index==15)?f.nWith(f.b,action,arg):f.n(action,arg);
  check(r.revision()==before+1&&!r.replay(),"note change");var notes=f.state().notes;Note n=notes.get(index==17||index==18?P:N);
  check(n!=null,"note exists");
  switch(index){
   case 0 -> check(n.status==Note.Status.DRAFT,"draft");
   case 1 -> check(n.title.equals("Clinical entry"),"title");
   case 2 -> check(n.body.equals("Synthetic note text"),"body");
   case 3 -> check(n.sections.containsKey("assessment"),"section");
   case 4 -> check(!n.sections.containsKey("history"),"section removed");
   case 5 -> check(n.diagnosisCodes.contains("ICD-10:Z00.0"),"diagnosis");
   case 6 -> check(!n.diagnosisCodes.contains("ICD-10:Z00.0"),"diagnosis removed");
   case 7 -> check(n.problemLabels.containsKey("problem-001"),"problem");
   case 8 -> check(n.problemLabels.get("problem-001").contains("Amended"),"problem revised");
   case 9 -> check(n.references.contains("ref-001"),"reference");
   case 10 -> check(!n.references.contains("ref-001"),"reference removed");
   case 11 -> check(n.confidential,"confidential");
   case 12 -> check(n.status==Note.Status.REVIEW,"review");
   case 13 -> check(n.reviewComment.contains("clarify"),"reviewer comment");
   case 14 -> check(n.status==Note.Status.DRAFT,"returned to draft");
   case 15 -> check(n.status==Note.Status.READY,"ready");
   case 16 -> {check(n.status==Note.Status.SIGNED&&A.equals(n.signer)&&n.signedAt!=null,"signature");
     denies(IllegalStateException.class,()->f.n("note.write_draft",Map.of("noteId",N.toString(),"body","Tampered")));}
   case 17 -> check(N.equals(n.parent)&&n.status==Note.Status.DRAFT,"amendment link");
   case 18 -> check(n.status==Note.Status.SIGNED&&notes.get(N).status==Note.Status.AMENDED,"signed amendment");
   case 19 -> check(n.status==Note.Status.ENTERED_IN_ERROR&&!n.body.isBlank()&& !n.errorReason.isBlank(),"preserved error record");
  }
  check(ClinicalCaseCore.auditValid(f.state()),"note audit");
 }
 static void testObservation(int index){
  F f=new F();f.active();String action=ObservationActions.OPERATIONS.get(index);
  if(index>2 && !Set.of(15,18).contains(index)){
   if(index==10){f.pending();}
   else if(Set.of(11,12,13,19).contains(index)){f.verified();}
   else if(index==9){f.pending();}
   else if(index==14){f.measured();}
   else if(index==17){f.o("observation.create_set",Map.of("setId",SET.toString()));f.verified();f.o("observation.join_set",Map.of("setId",SET.toString(),"observationId",O.toString()));}
   else if(index==16){f.o("observation.create_set",Map.of("setId",SET.toString()));f.measured();}
   else f.measured();
  }
  if(index==13){f.o("observation.capture_correction",f.correction());f.o("observation.request_verification",Map.of("observationId",OC.toString()));}
  if(index==19){f.o("observation.capture_correction",f.correction());}
  if(index==18){f.measured();f.o("observation.capture_numeric",Map.of("observationId",OC.toString(),"code","local:pulse",
     "value","81","unit","/min","measuredAt","2026-10-31T09:03:00Z"));}
  Map<String,String> arg=switch(index){
   case 0,1,2 -> Map.of("observationId",O.toString(),"code","local:pulse", "unit","/min",
     "value",index==0?"82":index==1?"observed": "reported", "measuredAt","2026-10-31T08:59:00Z");
   case 3 -> Map.of("observationId",O.toString(),"device","device:synthetic-1");
   case 4 -> Map.of("observationId",O.toString(),"context","Patient sitting");
   case 5 -> Map.of("observationId",O.toString(),"source","nurse-verified-manual");
   case 6 -> Map.of("observationId",O.toString(),"key","method","metadata","manual observation");
   case 7 -> Map.of("observationId",O.toString(),"key","method");
   case 10,11,14 -> Map.of("observationId",O.toString(),"reason","Clinician-supplied reason");
   case 12 -> f.correction();
   case 13 -> Map.of("observationId",OC.toString());
   case 15,17 -> Map.of("setId",SET.toString());
   case 16 -> Map.of("setId",SET.toString(),"observationId",O.toString());
   default -> Map.of("observationId",O.toString());};
  if(index==7)f.o("observation.add_metadata",Map.of("observationId",O.toString(),"key","method","metadata","manual"));
  long before=f.state().revision;
  if(index==18){var projection=ObservationActions.timeline(f.state(),f.s,f.b,"local:pulse",10);
    check(projection.size()==2&&projection.get(0).measuredAt.isBefore(projection.get(1).measuredAt),"timeline sorted");}
  else if(index==19){var chain=ObservationActions.correctionLineage(f.state(),f.s,f.b,O);
    check(chain.size()==2&&chain.get(1).replaces.equals(O),"correction lineage");}
  else{
   Result result=(index==9||index==10||index==13)?f.oWith(f.b,action,arg):f.o(action,arg);
   check(result.revision()==before+1,"observation write");
   State s=f.state();Observation o=s.observations.get(index==12||index==13?OC:O);
   switch(index){
    case 0,1,2 -> check(o!=null&&o.kind.equals(index==0?"numeric":index==1?"text":"categorical"),"captured type");
    case 3 -> check(o.device.equals("device:synthetic-1"),"device");
    case 4 -> check(o.context.equals("Patient sitting"),"context");
    case 5 -> check(o.metadata.containsKey("evidence-source"),"source");
    case 6 -> check(o.metadata.containsKey("method"),"metadata");
    case 7 -> check(!o.metadata.containsKey("method"),"metadata remove");
    case 8 -> check(o.status==Observation.Status.PENDING,"verification requested");
    case 9 -> check(o.status==Observation.Status.VERIFIED&&B.equals(o.verifier),"verified");
    case 10 -> check(o.status==Observation.Status.REJECTED&&!o.verificationReason.isBlank(),"rejected");
    case 11 -> check(o.metadata.containsKey("system:recheck-requested"),"recheck");
    case 12 -> check(O.equals(o.replaces)&&s.observations.get(O).status==Observation.Status.CORRECTED,"correction");
    case 13 -> check(o.status==Observation.Status.VERIFIED&&B.equals(o.verifier),"correction verified");
    case 14 -> check(o.status==Observation.Status.INVALIDATED,"invalidation");
    case 15 -> check(s.observationSets.containsKey(SET)&&s.observationSets.get(SET).isEmpty(),"new set");
    case 16 -> check(SET.equals(o.setId)&&s.observationSets.get(SET).contains(O),"joined set");
    case 17 -> check(s.verifiedSets.contains(SET),"completed set");
   }
  }
  check(ClinicalCaseCore.auditValid(f.state()),"observation audit");
 }
 public static void main(String[] args){
   if(args.length!=1)throw new IllegalArgumentException("pass P07-Bxxxx or all");
   List<Integer> indices=new ArrayList<>();
   if(args[0].equals("all")){for(int i=0;i<60;i++)indices.add(i);}
   else {if(!args[0].matches("P07-B\\d{4}"))throw new IllegalArgumentException("invalid batch id");
      int i=Integer.parseInt(args[0].substring(5))-1;
      if(i<0||i>=60)throw new IllegalArgumentException("unassigned batch");indices.add(i);}
   for(int i:indices){String op=ALL.get(i);
     if(i<20)testEncounter(i);else if(i<40)testNote(i-20);else testObservation(i-40);
     System.out.println("PASS "+String.format("P07-B%04d",i+1)+" "+op);
   }
 }
}
