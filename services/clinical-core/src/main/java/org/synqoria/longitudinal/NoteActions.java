package org.synqoria.longitudinal;

import java.util.*;
import static org.synqoria.longitudinal.ClinicalCaseCore.*;
import static org.synqoria.longitudinal.ClinicalActionInput.*;

/** M05 append-only clinical documentation: signed notes are never edited or deleted. */
public final class NoteActions {
 private NoteActions(){}
 public static final List<String> OPERATIONS=List.of(
  "note.create_draft", "note.title_draft", "note.write_draft", "note.set_section",
  "note.remove_section", "note.add_diagnosis", "note.remove_diagnosis", "note.add_problem",
  "note.rename_problem", "note.attach_reference", "note.detach_reference", "note.mark_confidential",
  "note.request_review", "note.reviewer_comment", "note.request_revision", "note.resume_draft",
  "note.ready_to_sign", "note.sign", "note.request_cosign", "note.cosign",
  "note.start_amendment", "note.update_amendment", "note.sign_amendment", "note.entered_in_error"
 );
 public static Result run(ClinicalCaseCore core,Scope scope,Actor actor,Command command,
     String action,Map<String,String> args){
  if(!OPERATIONS.contains(action))throw new IllegalArgumentException("unknown note action");
  Map<String,String> in=switch(action) {
            case "note.create_draft" -> validateExact(args,"noteId");
            case "note.title_draft" -> validateExact(args,"noteId","title");
            case "note.write_draft" -> validateExact(args,"noteId","body");
            case "note.set_section" -> validateExact(args,"noteId","section","text");
            case "note.remove_section" -> validateExact(args,"noteId","section");
            case "note.add_diagnosis" -> validateExact(args,"noteId","code");
            case "note.remove_diagnosis" -> validateExact(args,"noteId","code");
            case "note.add_problem" -> validateExact(args,"noteId","problemId","text");
            case "note.rename_problem" -> validateExact(args,"noteId","problemId","text");
            case "note.attach_reference" -> validateExact(args,"noteId","reference");
            case "note.detach_reference" -> validateExact(args,"noteId","reference");
            case "note.mark_confidential" -> validateExact(args,"noteId","confidential");
            case "note.request_review" -> validateExact(args,"noteId","reviewerId");
            case "note.reviewer_comment" -> validateExact(args,"noteId","comment");
            case "note.request_revision" -> validateExact(args,"noteId");
            case "note.resume_draft" -> validateExact(args,"noteId");
            case "note.ready_to_sign" -> validateExact(args,"noteId");
            case "note.sign" -> validateExact(args,"noteId");
            case "note.request_cosign" -> validateExact(args,"noteId","reviewerId");
            case "note.cosign" -> validateExact(args,"noteId");
            case "note.start_amendment" -> validateExact(args,"noteId","parentId");
            case "note.update_amendment" -> validateExact(args,"noteId","body");
            case "note.sign_amendment" -> validateExact(args,"noteId");
            case "note.entered_in_error" -> validateExact(args,"noteId","reason");
            default -> throw new IllegalArgumentException("unknown action");
        };
  return core.execute(scope,actor,command,"note.write",action,canonical(in),s->apply(s,actor,command,action,in));
 }
 private static Note draft(State s,UUID id){var n=note(s,id);require(n.status==Note.Status.DRAFT);return n;}
 private static Note editable(State s,UUID id){var n=note(s,id);require(!n.isImmutable()&&n.status!=Note.Status.READY);return n;}
 private static void apply(State s,Actor actor,Command cmd,String action,Map<String,String> in){
  UUID id=requiredId(in,"noteId");
  switch(action){
   case "note.create_draft" -> { require(s.stage==Stage.ACTIVE && !s.notes.containsKey(id));
      s.notes.put(id,new Note(id,actor.id(),null));}
   case "note.title_draft" -> {var n=draft(s,id);require(n.author.equals(actor.id()));n.title=required(in,"title",160);}
   case "note.write_draft" -> {var n=draft(s,id);require(n.author.equals(actor.id()));n.body=required(in,"body",8192);}
   case "note.set_section" -> {var n=draft(s,id);require(n.author.equals(actor.id()));
      n.sections.put(required(in,"section",80),required(in,"text",4096));}
   case "note.remove_section" -> {var n=draft(s,id);require(n.author.equals(actor.id()));
      require(n.sections.remove(required(in,"section",80))!=null);}
   case "note.add_diagnosis" -> {var n=draft(s,id);require(n.author.equals(actor.id()));
      // A code is explicitly entered by a clinician, never inferred by this software.
      require(n.diagnosisCodes.add(required(in,"code",128)));}
   case "note.remove_diagnosis" -> {var n=draft(s,id);require(n.author.equals(actor.id()));
      require(n.diagnosisCodes.remove(required(in,"code",128)));}
   case "note.add_problem" -> {var n=draft(s,id);require(n.author.equals(actor.id()));
      var key=required(in,"problemId",64);require(!n.problemLabels.containsKey(key));
      n.problemLabels.put(key,required(in,"text",512));}
   case "note.rename_problem" -> {var n=draft(s,id);require(n.author.equals(actor.id()));
      var key=required(in,"problemId",64);require(n.problemLabels.containsKey(key));
      n.problemLabels.put(key,required(in,"text",512));}
   case "note.attach_reference" -> {var n=draft(s,id);require(n.author.equals(actor.id()));
      var r=required(in,"reference",128);require(!n.references.contains(r));n.references.add(r);}
   case "note.detach_reference" -> {var n=draft(s,id);require(n.author.equals(actor.id()));
      require(n.references.remove(required(in,"reference",128)));}
   case "note.mark_confidential" -> {var n=draft(s,id);require(n.author.equals(actor.id()));
      String value=required(in,"confidential",5);require(value.equals("true")||value.equals("false"));
      n.confidential=Boolean.parseBoolean(value);}
   case "note.request_review" -> {var n=draft(s,id);require(n.author.equals(actor.id())&&!n.body.isBlank());
      n.reviewer=requiredId(in,"reviewerId");require(!n.reviewer.equals(actor.id()));n.status=Note.Status.REVIEW;}
   case "note.reviewer_comment" -> {var n=note(s,id);require(n.status==Note.Status.REVIEW && actor.id().equals(n.reviewer));
      n.reviewComment=required(in,"comment",2048);}
   case "note.request_revision" -> {var n=note(s,id);require(n.status==Note.Status.REVIEW&&actor.id().equals(n.reviewer)&&!n.reviewComment.isBlank());
      n.status=Note.Status.DRAFT;}
   case "note.resume_draft" -> {var n=note(s,id);require(n.status==Note.Status.DRAFT&&n.author.equals(actor.id()));
      // Explicit versioned revision and cleared reviewer comment for the next review round.
      n.reviewComment="";}
   case "note.ready_to_sign" -> {var n=note(s,id);require(n.status==Note.Status.REVIEW&&n.reviewer.equals(actor.id())&&!n.body.isBlank());
      n.status=Note.Status.READY;}
   case "note.sign" -> {var n=note(s,id);require(n.status==Note.Status.READY&&n.author.equals(actor.id()));
      actor.require("note.sign");n.status=Note.Status.SIGNED;n.signer=actor.id();n.signedAt=cmd.at();}
   case "note.request_cosign" -> {var n=note(s,id);require(n.status==Note.Status.SIGNED&&n.signer.equals(actor.id())&&!n.requiresCosign);
      n.reviewer=requiredId(in,"reviewerId");require(!n.reviewer.equals(n.signer));n.requiresCosign=true;}
   case "note.cosign" -> {var n=note(s,id);require(n.status==Note.Status.SIGNED&&n.requiresCosign&&
      n.reviewer.equals(actor.id())&&n.cosigner==null);actor.require("note.cosign");
      n.cosigner=actor.id();n.cosignedAt=cmd.at();}
   case "note.start_amendment" -> {var parent=note(s,requiredId(in,"parentId"));
      require(parent.isImmutable()&&parent.status!=Note.Status.ENTERED_IN_ERROR&&s.stage!=Stage.PLANNED&& !s.notes.containsKey(id));
      s.notes.put(id,new Note(id,actor.id(),parent.id));}
   case "note.update_amendment" -> {var n=draft(s,id);require(n.parent!=null&&n.author.equals(actor.id()));
      n.body=required(in,"body",8192);}
   case "note.sign_amendment" -> {var n=draft(s,id);require(n.parent!=null&&n.author.equals(actor.id())&&!n.body.isBlank());
      var parent=note(s,n.parent);require(parent.status==Note.Status.SIGNED||parent.status==Note.Status.AMENDED);
      actor.require("note.sign");n.signer=actor.id();n.signedAt=cmd.at();n.status=Note.Status.SIGNED;
      parent.status=Note.Status.AMENDED;}
   case "note.entered_in_error" -> {var n=note(s,id);require(n.isImmutable()&&n.status!=Note.Status.ENTERED_IN_ERROR);
      actor.require("note.error.flag");n.errorReason=required(in,"reason",1024);n.status=Note.Status.ENTERED_IN_ERROR;}
   default -> throw new IllegalArgumentException("unknown note action");
  }
 }
}
