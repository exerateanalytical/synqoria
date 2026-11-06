package org.synqoria.operations.domain;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/** Stateless, testable domain operation boundary. No database or HTTP authorization claims. */
public final class OperationWorkflow {
 private OperationWorkflow(){}
 private static final Pattern CODE=Pattern.compile("[a-zA-Z0-9_.:/-]{1,160}");
 public record Actor(UUID actor,UUID cell,UUID facility,Set<String> permissions) {
  public Actor {Objects.requireNonNull(actor);Objects.requireNonNull(cell);Objects.requireNonNull(facility);
   permissions=Set.copyOf(Objects.requireNonNull(permissions));}
  public void require(String permission) {
   if(!permissions.contains(permission))throw new WorkflowViolation("capability_denied");
  }
 }
 /** Filled ONLY by reviewed trusted integrations, never from HTTP request body. */
 public record TrustedEvidence(Set<String> verified) {
  public TrustedEvidence {verified=Set.copyOf(Objects.requireNonNull(verified));}
  public static TrustedEvidence none(){return new TrustedEvidence(Set.of());}
  public boolean proves(String requirement){return requirement.isEmpty()||verified.contains(requirement);}
 }
 public record Item(UUID id,UUID cell,UUID facility,String module,String recordType,String status,
                    long version,UUID createdBy,UUID lastActor,Map<String,Object> fields) {
  public Item {fields=Map.copyOf(fields);}
 }
 public record Next(Item item,ModuleCatalog.Step step){}
 public static Item create(String moduleId,String type,UUID id,Actor actor,Map<String,Object> fields){
  ModuleCatalog.Module spec=ModuleCatalog.require(moduleId);
  actor.require(moduleId.toLowerCase(Locale.ROOT)+":create");
  validateFields(spec,fields);
  return new Item(Objects.requireNonNull(id),actor.cell,actor.facility,moduleId,type,
   spec.initial(type),1L,actor.actor,actor.actor,fields);
 }
 public static Next advance(Item current,String action,long expectedVersion,Actor actor,
                             TrustedEvidence evidence,Map<String,Object> changedFields){
  Objects.requireNonNull(current);Objects.requireNonNull(actor);Objects.requireNonNull(evidence);
  if(!actor.cell.equals(current.cell)||!actor.facility.equals(current.facility))
   throw new WorkflowViolation("scope_denied");
  if(expectedVersion!=current.version)throw new WorkflowViolation("stale_version");
  ModuleCatalog.Module spec=ModuleCatalog.require(current.module);
  var step=spec.transition(current.status,action);
  actor.require(step.permission());
  if(step.makerChecker()&&actor.actor.equals(current.createdBy))
   throw new WorkflowViolation("maker_checker_denied");
  if(!evidence.proves(step.evidence()))throw new WorkflowViolation("trusted_evidence_required");
  validateFields(spec,changedFields);
  var nextFields=new LinkedHashMap<>(current.fields);
  nextFields.putAll(changedFields);
  validateFields(spec,nextFields);
  var next=new Item(current.id,current.cell,current.facility,current.module,current.recordType,
   step.to(),current.version+1,current.createdBy,actor.actor,nextFields);
  return new Next(next,step);
 }
 public static void validateFields(ModuleCatalog.Module module,Map<String,Object> fields){
  if(fields==null||fields.size()>24)throw new WorkflowViolation("invalid_fields");
  for(var e:fields.entrySet()){
   if(!module.allowedFields().contains(e.getKey())||e.getValue()==null)
    throw new WorkflowViolation("field_not_allowed");
   Object v=e.getValue();
   if(v instanceof String s){
    if(s.isBlank()||s.length()>2048||s.chars().anyMatch(c->c<32))
     throw new WorkflowViolation("invalid_field_value");
    if(e.getKey().endsWith("Ref")||e.getKey().equals("sku")||e.getKey().equals("currency")||
       e.getKey().equals("schemaVersion")||e.getKey().equals("serviceCode")){
     if(!CODE.matcher(s).matches())throw new WorkflowViolation("invalid_reference");
    }
    if(e.getKey().equals("currency")&&!s.matches("[A-Z]{3}"))
      throw new WorkflowViolation("invalid_currency");
   }else if(v instanceof Integer n){
    if(n<0)throw new WorkflowViolation("negative_quantity");
   }else if(v instanceof Long n){
    if(n<0)throw new WorkflowViolation("negative_amount");
   }else if(v instanceof BigDecimal n){
    if(n.signum()<0||n.scale()>4||n.precision()>16)throw new WorkflowViolation("invalid_amount");
   }else throw new WorkflowViolation("untrusted_value_type");
   if(Set.of("quantity","amountMinor").contains(e.getKey())&& !(v instanceof Number))
    throw new WorkflowViolation("amount_type_denied");
  }
 }
}
