package org.synqoria.operations.domain;

import java.util.*;
import java.util.stream.Collectors;

/** Canonical M21/M22/M23/M25/M29/M31/M37 workflows; never a generic SQL dispatcher. */
public final class ModuleCatalog {
 private ModuleCatalog() {}
 public record Step(String from,String action,String to,String permission,boolean makerChecker,String evidence) {
  public Step { Objects.requireNonNull(from);Objects.requireNonNull(action);Objects.requireNonNull(to);
    Objects.requireNonNull(permission);Objects.requireNonNull(evidence); }
 }
 public record Module(String id,String name,String initialState,List<String> screenIds,
                      Set<String> recordTypes,Set<String> allowedFields,List<Step> steps) {
  public Module { screenIds=List.copyOf(screenIds);recordTypes=Set.copyOf(recordTypes);
    allowedFields=Set.copyOf(allowedFields);steps=List.copyOf(steps); }
  public Step transition(String state,String action) {
   return steps.stream().filter(s->s.from.equals(state)&&s.action.equals(action)).findFirst()
    .orElseThrow(()->new WorkflowViolation("transition_denied"));
  }
  public String initial(String recordType) {
   if(!recordTypes.contains(recordType))throw new WorkflowViolation("record_type_denied");
   return id.equals("M31")&&recordType.equals("DOCUMENT")?"DOCUMENT_DRAFT":initialState;
  }
 }
 private static Step s(String from,String action,String to,String permission) {
  return new Step(from,action,to,permission,false,"");
 }
 private static Step check(String from,String action,String to,String permission) {
  return new Step(from,action,to,permission,true,"");
 }
 private static Step proof(String from,String action,String to,String permission,String evidence) {
  return new Step(from,action,to,permission,false,evidence);
 }
 private static Step checkedProof(String from,String action,String to,String permission,String evidence) {
  return new Step(from,action,to,permission,true,evidence);
 }
 private static final Map<String,Module> MODULES;
 static {
  Map<String,Module> all=new LinkedHashMap<>();
  add(all,new Module("M21","Procurement & Suppliers","DRAFT",screens("M21",6),
   Set.of("REQUISITION"),Set.of("title","supplierRef","amountMinor","currency","costCentre","description","purchaseRef","receiptRef","invoiceRef"),
   List.of(s("DRAFT","SUBMIT","SUBMITTED","m21:submit"),check("SUBMITTED","APPROVE","APPROVED","m21:approve"),
    check("SUBMITTED","REJECT","REJECTED","m21:approve"),s("APPROVED","ORDER","ORDERED","m21:order"),
    s("ORDERED","RECEIVE","RECEIVED","m21:receive"),check("RECEIVED","MATCH","MATCHED","m21:match"),
    s("MATCHED","CLOSE","CLOSED","m21:close"))));
  add(all,new Module("M22","Non-medication Stores","QUARANTINED",screens("M22",5),
   Set.of("STOCK_ITEM"),Set.of("title","sku","lot","bin","unit","quantity","adjustmentReason","description","receiptRef","issueRef"),
   List.of(checkedProof("QUARANTINED","RELEASE","AVAILABLE","m22:release","stock_qc"),
    s("AVAILABLE","RESERVE","RESERVED","m22:reserve"),s("RESERVED","ISSUE","ISSUED","m22:issue"),
    s("ISSUED","RETURN","RETURNED","m22:return"),s("AVAILABLE","START_COUNT","COUNT_PENDING","m22:count"),
    check("COUNT_PENDING","APPROVE_ADJUSTMENT","AVAILABLE","m22:adjust"))));
  add(all,new Module("M23","Assets & Biomedical Maintenance","REGISTERED",screens("M23",5),
   Set.of("ASSET"),Set.of("title","serial","manufacturer","model","serviceRef","locationRef","safetyClass","description"),
   List.of(proof("REGISTERED","COMMISSION","COMMISSIONED","m23:commission","biomed_inspection"),
    s("COMMISSIONED","ACTIVATE","ACTIVE","m23:activate"),
    s("ACTIVE","TAKE_OFFLINE","MAINTENANCE","m23:maintenance"),
    proof("MAINTENANCE","RETURN_TO_SERVICE","ACTIVE","m23:return","biomed_inspection"),
    check("ACTIVE","RETIRE","RETIRED","m23:retire"),
    check("MAINTENANCE","RETIRE","RETIRED","m23:retire"))));
  add(all,new Module("M25","Transport & Ambulances","REQUESTED",screens("M25",5),
   Set.of("TRIP"),Set.of("title","vehicleRef","driverRef","originRef","destinationRef","requestPriority","description","handoverRef"),
   List.of(s("REQUESTED","ASSIGN","ASSIGNED","m25:assign"),
    s("ASSIGNED","DISPATCH","DISPATCHED","m25:dispatch"),
    s("DISPATCHED","ARRIVE","ARRIVED","m25:arrive"),
    proof("ARRIVED","HANDOVER","HANDED_OVER","m25:handover","transport_handover"),
    s("HANDED_OVER","COMPLETE","COMPLETED","m25:complete"),
    s("REQUESTED","CANCEL","CANCELLED","m25:cancel"),
    s("ASSIGNED","CANCEL","CANCELLED","m25:cancel"),
    s("DISPATCHED","ESCALATE","ESCALATED","m25:escalate"))));
  add(all,new Module("M29","Facility/Service Configuration","DRAFT",screens("M29",4),
   Set.of("CONFIGURATION"),Set.of("title","locationRef","departmentRef","serviceCode","schemaVersion","changeImpact","description"),
   List.of(check("DRAFT","REVIEW","REVIEWED","m29:review"),
    checkedProof("REVIEWED","PUBLISH","PUBLISHED","m29:publish","impact_approved"),
    s("PUBLISHED","SUPERSEDE","SUPERSEDED","m29:supersede"))));
  add(all,new Module("M31","Documents & Templates","DRAFT",screens("M31",6),
   Set.of("TEMPLATE","DOCUMENT"),Set.of("title","templateRef","schemaVersion","documentRef","contentDigest","signatureRef","description"),
   List.of(check("DRAFT","APPROVE","APPROVED","m31:approve"),
    s("APPROVED","PUBLISH","PUBLISHED","m31:publish"),
    check("PUBLISHED","RETIRE","RETIRED","m31:retire"),
    proof("DOCUMENT_DRAFT","SIGN","DOCUMENT_SIGNED","m31:sign","verified_signature"),
    proof("DOCUMENT_SIGNED","AMEND","DOCUMENT_AMENDED","m31:amend","document_lineage"))));
  add(all,new Module("M37","Notifications & Escalation","QUEUED",screens("M37",4),
   Set.of("MESSAGE"),Set.of("title","recipientRef","templateRef","channel","deliveryRef","dueAt","description"),
   List.of(proof("QUEUED","DELIVER","DELIVERED","m37:deliver","delivery_receipt"),
    s("QUEUED","FAIL","FAILED","m37:failure"),
    s("FAILED","RETRY","RETRYING","m37:retry"),
    proof("RETRYING","DELIVER","DELIVERED","m37:deliver","delivery_receipt"),
    s("DELIVERED","ACKNOWLEDGE","ACKNOWLEDGED","m37:acknowledge"),
    s("QUEUED","ESCALATE","ESCALATED","m37:escalate"),
    s("FAILED","ESCALATE","ESCALATED","m37:escalate"),
    s("DELIVERED","ESCALATE","ESCALATED","m37:escalate"))));
  MODULES=Collections.unmodifiableMap(all);
 }
 private static List<String> screens(String id,int count){
  List<String> list=new ArrayList<>();for(int i=1;i<=count;i++)list.add(id+"-S"+String.format("%02d",i));
  return list;
 }
 private static void add(Map<String,Module> map,Module m){
  if(map.putIfAbsent(m.id,m)!=null)throw new IllegalStateException("duplicate module");
  Set<String> arcs=new HashSet<>();
  for(Step step:m.steps)if(!arcs.add(step.from+"/"+step.action))throw new IllegalStateException("duplicate transition");
 }
 public static Map<String,Module> all(){return MODULES;}
 public static Module require(String id){
  Module module=MODULES.get(id);
  if(module==null)throw new WorkflowViolation("unknown_module");
  return module;
 }
 public static int screenCount(){return MODULES.values().stream().mapToInt(m->m.screenIds.size()).sum();}
 public static Set<String> allScreens(){return MODULES.values().stream().flatMap(m->m.screenIds.stream()).collect(Collectors.toUnmodifiableSet());}
}
