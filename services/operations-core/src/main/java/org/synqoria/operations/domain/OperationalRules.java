package org.synqoria.operations.domain;

import java.util.*;

/** Module-specific invariant checks before any persisted state transition. */
public final class OperationalRules {
 private OperationalRules(){}
 public static void requireFields(String module,String type,String action,Map<String,Object> values){
  String key=module+":"+type+":"+action;
  String[] required=switch(key){
   case "M21:REQUISITION:SUBMIT" -> new String[]{"supplierRef","currency","amountMinor","costCentre"};
   case "M21:REQUISITION:ORDER" -> new String[]{"purchaseRef"};
   case "M21:REQUISITION:RECEIVE" -> new String[]{"receiptRef"};
   case "M21:REQUISITION:MATCH" -> new String[]{"invoiceRef"};
   case "M21:SUPPLIER:QUALIFY","M21:SUPPLIER:REQUALIFY" ->
      new String[]{"registrationRef","taxRef","qualificationRef"};
   case "M22:STOCK_ITEM:RELEASE" -> new String[]{"sku","lot","bin","unit","quantity","receiptRef"};
   case "M22:STOCK_ITEM:RESERVE","M22:STOCK_ITEM:ISSUE" ->
      new String[]{"sku","lot","bin","quantity"};
   case "M22:STOCK_REQUEST:APPROVE_REQUEST" -> new String[]{"sku","quantity","requestedByRef"};
   case "M22:STOCK_REQUEST:FULFIL_REQUEST" -> new String[]{"issueRef"};
   case "M23:ASSET:COMMISSION" -> new String[]{"serial","locationRef","inspectionRef"};
   case "M23:ASSET:RETURN_TO_SERVICE" -> new String[]{"workOrderRef","inspectionRef"};
   case "M23:WORK_ORDER:OPEN_WORK" -> new String[]{"assetRef","dueAt"};
   case "M23:WORK_ORDER:ASSIGN_WORK" -> new String[]{"technicianRef"};
   case "M23:WORK_ORDER:COMPLETE_WORK" -> new String[]{"inspectionRef"};
   case "M23:WORK_ORDER:VERIFY_WORK" -> new String[]{"workOrderRef"};
   case "M25:TRIP:ASSIGN","M25:TRIP:DISPATCH" ->
      new String[]{"vehicleRef","driverRef","originRef","destinationRef"};
   case "M25:TRIP:HANDOVER" -> new String[]{"handoverRef"};
   case "M25:VEHICLE:QUALIFY_VEHICLE","M25:VEHICLE:RESTORE_VEHICLE" ->
      new String[]{"plate","inspectionRef","vehicleType"};
   case "M29:CONFIGURATION:REVIEW","M29:CONFIGURATION:PUBLISH" ->
      new String[]{"schemaVersion","changeImpact"};
   case "M29:SERVICE:REVIEW_SERVICE","M29:SERVICE:PUBLISH_SERVICE" ->
      new String[]{"serviceCode","changeImpact"};
   case "M31:TEMPLATE:APPROVE","M31:TEMPLATE:PUBLISH" ->
      new String[]{"schemaVersion","contentDigest","storageRef"};
   case "M31:DOCUMENT:SIGN","M31:DOCUMENT:RESIGN" ->
      new String[]{"templateRef","contentDigest","signatureRef","storageRef"};
   case "M31:DOCUMENT:AMEND" -> new String[]{"amendmentReason","previousRef"};
   case "M37:MESSAGE:DELIVER" -> new String[]{"recipientRef","templateRef","channel","deliveryRef"};
   case "M37:MESSAGE:ACKNOWLEDGE" -> new String[]{"recipientRef"};
   case "M37:ROUTE:REVIEW_ROUTE","M37:ROUTE:ACTIVATE_ROUTE" ->
      new String[]{"routingRef","retryPolicy","channel"};
   default -> new String[0];
  };
  for(String field:required){
   Object value=values.get(field);
   if(value==null||(value instanceof String s && s.isBlank()))
    throw new WorkflowViolation("required_business_field_missing");
  }
  if(module.equals("M31")&&Set.of("SIGN","RESIGN","APPROVE","PUBLISH").contains(action)){
   Object digest=values.get("contentDigest");
   if(!(digest instanceof String s)&&digest!=null)
    throw new WorkflowViolation("invalid_document_digest");
   if(digest instanceof String s&&!s.matches("[a-f0-9]{64}"))
    throw new WorkflowViolation("invalid_document_digest");
  }
  if(module.equals("M22")){
   Object qty=values.get("quantity");
   if(qty instanceof Number n && (n.longValue()<1||n.longValue()>1_000_000))
    throw new WorkflowViolation("invalid_stock_quantity");
  }
 }
 public static void validateNew(String module,String type,Map<String,Object>fields){
  if(!(fields.get("title") instanceof String title)||title.isBlank())
   throw new WorkflowViolation("title_required");
  if(module.equals("M31")&&type.equals("DOCUMENT")&&fields.containsKey("signatureRef"))
   throw new WorkflowViolation("signature_cannot_be_client_declared");
 }
}
