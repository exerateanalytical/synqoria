package org.synqoria.longitudinal;

import java.time.Instant;
import java.util.*;
import static org.synqoria.longitudinal.ClinicalCaseCore.*;
import static org.synqoria.longitudinal.ClinicalActionInput.*;

/** M06 measurement evidence; no automatic priority, diagnostic or dose decisions. */
public final class ObservationActions {
 private ObservationActions(){}
 public static final List<String> OPERATIONS=List.of(
  "observation.capture_numeric", "observation.capture_text", "observation.capture_categorical",
  "observation.attach_device", "observation.record_context", "observation.attach_source",
  "observation.add_metadata", "observation.remove_metadata", "observation.request_verification",
  "observation.verify", "observation.reject", "observation.request_recheck",
  "observation.capture_correction", "observation.verify_correction", "observation.invalidate",
  "observation.create_set", "observation.join_set", "observation.complete_set",
  "observation.timeline", "observation.correction_lineage"
 );
 public static Result run(ClinicalCaseCore core,Scope scope,Actor actor,Command cmd,
                         String action,Map<String,String> args){
  if(!OPERATIONS.contains(action)||action.equals("observation.timeline")||action.equals("observation.correction_lineage"))
   throw new IllegalArgumentException("write operation unavailable");
  Map<String,String> in=switch(action) {
            case "observation.capture_numeric" -> validateExact(args,"observationId","code","unit","value","measuredAt");
            case "observation.capture_text" -> validateExact(args,"observationId","code","unit","value","measuredAt");
            case "observation.capture_categorical" -> validateExact(args,"observationId","code","unit","value","measuredAt");
            case "observation.attach_device" -> validateExact(args,"observationId","device");
            case "observation.record_context" -> validateExact(args,"observationId","context");
            case "observation.attach_source" -> validateExact(args,"observationId","source");
            case "observation.add_metadata" -> validateExact(args,"observationId","key","metadata");
            case "observation.remove_metadata" -> validateExact(args,"observationId","key");
            case "observation.request_verification" -> validateExact(args,"observationId");
            case "observation.verify" -> validateExact(args,"observationId");
            case "observation.reject" -> validateExact(args,"observationId","reason");
            case "observation.request_recheck" -> validateExact(args,"observationId","reason");
            case "observation.capture_correction" -> validateExact(args,"observationId","priorId","code","unit","value","measuredAt");
            case "observation.verify_correction" -> validateExact(args,"observationId");
            case "observation.invalidate" -> validateExact(args,"observationId","reason");
            case "observation.create_set" -> validateExact(args,"setId");
            case "observation.join_set" -> validateExact(args,"setId","observationId");
            case "observation.complete_set" -> validateExact(args,"setId");
            default -> throw new IllegalArgumentException("unknown action");
        };
  return core.execute(scope,actor,cmd,"observation.write",action,canonical(in),s->apply(s,actor,cmd,action,in));
 }
 private static Observation mutable(State s,UUID id){var o=observation(s,id);
  require(o.status==Observation.Status.CAPTURED);return o;}
 private static void capture(State s,Actor a,UUID id,Map<String,String> in,String kind,UUID prior){
  require(s.stage==Stage.ACTIVE&&!s.observations.containsKey(id));
  String value=required(in,"value",256);
  if(kind.equals("numeric")){
   try{double v=Double.parseDouble(value);if(!Double.isFinite(v))throw new NumberFormatException();}
   catch(NumberFormatException e){throw new IllegalArgumentException("invalid numeric measurement");}
  }
  var o=new Observation(id,a.id(),prior,required(in,"code",128),required(in,"unit",64),value,kind,instant(in,"measuredAt"));
  if(prior!=null){var previous=observation(s,prior);require(previous.status==Observation.Status.VERIFIED||
      previous.status==Observation.Status.REJECTED||previous.status==Observation.Status.INVALIDATED);
      require(previous.code.equals(o.code));previous.status=Observation.Status.CORRECTED;}
  s.observations.put(id,o);
 }
 private static void apply(State s,Actor a,Command c,String op,Map<String,String> in){
  UUID id=op.equals("observation.create_set")||op.equals("observation.complete_set") ? null:requiredId(in,"observationId");
  switch(op){
   case "observation.capture_numeric" -> capture(s,a,id,in,"numeric",null);
   case "observation.capture_text" -> capture(s,a,id,in,"text",null);
   case "observation.capture_categorical" -> capture(s,a,id,in,"categorical",null);
   case "observation.attach_device" -> {var o=mutable(s,id);require(o.author.equals(a.id()));
      o.device=required(in,"device",128);}
   case "observation.record_context" -> {var o=mutable(s,id);require(o.author.equals(a.id()));
      o.context=required(in,"context",256);}
   case "observation.attach_source" -> {var o=mutable(s,id);require(o.author.equals(a.id()));
      o.metadata.put("evidence-source",required(in,"source",128));}
   case "observation.add_metadata" -> {var o=mutable(s,id);require(o.author.equals(a.id()));
      String key=required(in,"key",64);forbid(key.equals("evidence-source")||key.startsWith("system:"));
      require(!o.metadata.containsKey(key));o.metadata.put(key,required(in,"metadata",512));}
   case "observation.remove_metadata" -> {var o=mutable(s,id);require(o.author.equals(a.id()));
      String key=required(in,"key",64);forbid(key.equals("evidence-source"));require(o.metadata.remove(key)!=null);}
   case "observation.request_verification" -> {var o=mutable(s,id);require(o.author.equals(a.id()));
      o.status=Observation.Status.PENDING;}
   case "observation.verify" -> {var o=observation(s,id);require(o.status==Observation.Status.PENDING&&!o.author.equals(a.id()));
      a.require("observation.verify");o.verifier=a.id();o.status=Observation.Status.VERIFIED;}
   case "observation.reject" -> {var o=observation(s,id);require(o.status==Observation.Status.PENDING&&!o.author.equals(a.id()));
      a.require("observation.verify");o.verifier=a.id();o.verificationReason=required(in,"reason",512);
      o.status=Observation.Status.REJECTED;}
   case "observation.request_recheck" -> {var o=observation(s,id);
      require(o.status==Observation.Status.VERIFIED||o.status==Observation.Status.REJECTED);
      o.metadata.put("system:recheck-requested",required(in,"reason",512));}
   case "observation.capture_correction" -> {var prior=requiredId(in,"priorId");require(!id.equals(prior));
      capture(s,a,id,in,"numeric",prior);}
   case "observation.verify_correction" -> {var o=observation(s,id);require(o.replaces!=null&&o.status==Observation.Status.PENDING&&!o.author.equals(a.id()));
      a.require("observation.verify");o.status=Observation.Status.VERIFIED;o.verifier=a.id();}
   case "observation.invalidate" -> {var o=observation(s,id);
      require(o.status==Observation.Status.CAPTURED||o.status==Observation.Status.REJECTED||o.status==Observation.Status.VERIFIED);
      a.require("observation.invalidate");o.status=Observation.Status.INVALIDATED;
      o.invalidationReason=required(in,"reason",512);}
   case "observation.create_set" -> {UUID set=requiredId(in,"setId");require(s.stage==Stage.ACTIVE&&!s.observationSets.containsKey(set));
      s.observationSets.put(set,new ArrayList<>());}
   case "observation.join_set" -> {UUID set=requiredId(in,"setId");
      var group=s.observationSets.get(set);require(group!=null&&!s.verifiedSets.contains(set));
      var o=observation(s,id);require(o.setId==null);o.setId=set;group.add(id);}
   case "observation.complete_set" -> {UUID set=requiredId(in,"setId");var group=s.observationSets.get(set);
      require(group!=null&&!group.isEmpty()&&!s.verifiedSets.contains(set));
      require(group.stream().allMatch(x->observation(s,x).status==Observation.Status.VERIFIED));
      s.verifiedSets.add(set);}
   default -> throw new IllegalArgumentException("unknown observation operation");
  }
 }
 /** Authorized in-memory read-model projections; caller must enforce backend row scope when loading case. */
 public static List<Observation> timeline(State source,Scope scope,Actor actor,String code,int limit){
  actor.require("observation.read");if(!scope.equals(source.scope))throw new SecurityException("clinical resource unavailable");
  if(limit<1||limit>500)throw new IllegalArgumentException("page limit");String sought=text(code,128);
  return source.observations.values().stream().filter(o->o.code.equals(sought))
    .sorted(Comparator.comparing((Observation o)->o.measuredAt).thenComparing(o->o.id))
    .limit(limit).map(Observation::new).toList();
 }
 public static List<Observation> correctionLineage(State source,Scope scope,Actor actor,UUID selected){
  actor.require("observation.read");if(!scope.equals(source.scope))throw new SecurityException("clinical resource unavailable");
  var root=observation(source,selected);Set<UUID> ids=new LinkedHashSet<>();
  // A correction selected directly must reveal its verified predecessor, not only descendants.
  for(Observation cur=root;cur!=null;cur=cur.replaces==null?null:observation(source,cur.replaces)){
   if(!ids.add(cur.id)||ids.size()>100)throw new SecurityException("invalid observation correction lineage");
  }
  boolean changed;do {changed=false;for(var o:source.observations.values()){
      if(o.replaces!=null&&ids.contains(o.replaces)&&ids.add(o.id)){changed=true;
       if(ids.size()>100)throw new SecurityException("observation correction lineage exceeds bound");}
   }}while(changed);
  return ids.stream().map(id->new Observation(observation(source,id)))
   .sorted(Comparator.comparing((Observation o)->o.measuredAt).thenComparing(o->o.id)).toList();
 }
}
