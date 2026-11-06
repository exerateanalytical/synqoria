package org.synqoria.operations.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class SevenModuleWorkflowTest {
 private static final UUID A=UUID.fromString("11111111-1111-4111-8111-111111111111");
 private static final UUID B=UUID.fromString("22222222-2222-4222-8222-222222222222");
 private static final UUID CELL=UUID.fromString("33333333-3333-4333-8333-333333333333");
 private static final UUID FAC=UUID.fromString("44444444-4444-4444-8444-444444444444");
 static OperationWorkflow.Actor actor(UUID id,String...caps){
  return new OperationWorkflow.Actor(id,CELL,FAC,Set.of(caps));
 }
 @Test void allSevenCanonicalModulesBindToExactlyThirtyFiveScreens(){
  assertEquals(Set.of("M21","M22","M23","M25","M29","M31","M37"),ModuleCatalog.all().keySet());
  assertEquals(35,ModuleCatalog.screenCount());
  assertEquals(35,ModuleCatalog.allScreens().size());
  assertEquals(6,ModuleCatalog.require("M21").screenIds().size());
  assertEquals(5,ModuleCatalog.require("M22").screenIds().size());
  assertEquals(5,ModuleCatalog.require("M23").screenIds().size());
  assertEquals(5,ModuleCatalog.require("M25").screenIds().size());
  assertEquals(4,ModuleCatalog.require("M29").screenIds().size());
  assertEquals(6,ModuleCatalog.require("M31").screenIds().size());
  assertEquals(4,ModuleCatalog.require("M37").screenIds().size());
 }
 @Test void everyModuleHasAWorkingCreateAndCapabilityFence(){
  for(var spec:ModuleCatalog.all().values()){
   String type=spec.recordTypes().iterator().next();
   var item=OperationWorkflow.create(spec.id(),type,UUID.randomUUID(),
    actor(A,spec.id().toLowerCase(Locale.ROOT)+":create"),Map.of("title",spec.name()));
   assertEquals(spec.initial(type),item.status());
   assertEquals(1,item.version());
   assertEquals(CELL,item.cell());assertEquals(FAC,item.facility());
   assertThrows(WorkflowViolation.class,()->OperationWorkflow.create(spec.id(),type,UUID.randomUUID(),
    actor(B),Map.of("title",spec.name())));
  }
 }
 @Test void procurementMakerCheckerAndOrderedInvoiceMatchCannotBeBypassed(){
  var creator=actor(A,"m21:create","m21:submit","m21:order","m21:receive","m21:close");
  var approver=actor(B,"m21:approve","m21:match");
  var item=OperationWorkflow.create("M21","REQUISITION",UUID.randomUUID(),creator,
   Map.of("title","Examination furniture","currency","XAF","amountMinor",10000,"supplierRef","supplier-39","costCentre","operations"));
  item=OperationWorkflow.advance(item,"SUBMIT",1,creator,OperationWorkflow.TrustedEvidence.none(),Map.of()).item();
  var submitted=item;
  assertThrows(WorkflowViolation.class,()->OperationWorkflow.advance(submitted,"APPROVE",2,
   actor(A,"m21:approve"),OperationWorkflow.TrustedEvidence.none(),Map.of()));
  assertThrows(WorkflowViolation.class,()->OperationWorkflow.advance(submitted,"ORDER",2,
   creator,OperationWorkflow.TrustedEvidence.none(),Map.of()));
  item=OperationWorkflow.advance(item,"APPROVE",2,approver,OperationWorkflow.TrustedEvidence.none(),Map.of()).item();
  item=OperationWorkflow.advance(item,"ORDER",3,creator,OperationWorkflow.TrustedEvidence.none(),Map.of("purchaseRef","PO-2026-001")).item();
  item=OperationWorkflow.advance(item,"RECEIVE",4,creator,new OperationWorkflow.TrustedEvidence(Set.of("purchase_receipt")),Map.of("receiptRef","GRN-2026-009")).item();
  item=OperationWorkflow.advance(item,"MATCH",5,approver,new OperationWorkflow.TrustedEvidence(Set.of("invoice_matched")),Map.of("invoiceRef","INV-2026-009")).item();
  item=OperationWorkflow.advance(item,"CLOSE",6,creator,OperationWorkflow.TrustedEvidence.none(),Map.of()).item();
  assertEquals("CLOSED",item.status());assertEquals(7,item.version());
 }
 @Test void inventoryQuarantineRequiresIndependentQualityEvidence(){
  var item=OperationWorkflow.create("M22","STOCK_ITEM",UUID.randomUUID(),
   actor(A,"m22:create"),Map.of("title","Sterile stock cabinets","quantity",3,"sku","CAB-001","lot","LOT-001","bin","MAIN","unit","piece","receiptRef","GRN-001"));
  var gate=item;
  assertThrows(WorkflowViolation.class,()->OperationWorkflow.advance(gate,"RELEASE",1,
   actor(B,"m22:release"),OperationWorkflow.TrustedEvidence.none(),Map.of()));
  var available=OperationWorkflow.advance(item,"RELEASE",1,actor(B,"m22:release"),
   new OperationWorkflow.TrustedEvidence(Set.of("stock_qc")),Map.of()).item();
  var reserved=OperationWorkflow.advance(available,"RESERVE",2,actor(B,"m22:reserve"),
   OperationWorkflow.TrustedEvidence.none(),Map.of()).item();
  assertEquals("RESERVED",reserved.status());
  assertThrows(WorkflowViolation.class,()->OperationWorkflow.advance(reserved,"ISSUE",1,
   actor(B,"m22:issue"),OperationWorkflow.TrustedEvidence.none(),Map.of()));
 }
 @Test void biomedicalAssetCannotReturnToServiceWithoutInspectedProof(){
  var item=OperationWorkflow.create("M23","ASSET",UUID.randomUUID(),
   actor(A,"m23:create"),Map.of("title","Diagnostic equipment","serial","BIO-109","locationRef","ward-1","inspectionRef","INSP-123"));
  var before=item;
  assertThrows(WorkflowViolation.class,()->OperationWorkflow.advance(before,"COMMISSION",1,
   actor(B,"m23:commission"),OperationWorkflow.TrustedEvidence.none(),Map.of()));
  item=OperationWorkflow.advance(item,"COMMISSION",1,actor(B,"m23:commission"),
   new OperationWorkflow.TrustedEvidence(Set.of("biomed_inspection")),Map.of()).item();
  item=OperationWorkflow.advance(item,"ACTIVATE",2,actor(B,"m23:activate"),
   OperationWorkflow.TrustedEvidence.none(),Map.of()).item();
  item=OperationWorkflow.advance(item,"TAKE_OFFLINE",3,actor(B,"m23:maintenance"),
   OperationWorkflow.TrustedEvidence.none(),Map.of()).item();
  var maintenance=item;
  assertThrows(WorkflowViolation.class,()->OperationWorkflow.advance(maintenance,"RETURN_TO_SERVICE",4,
   actor(B,"m23:return"),OperationWorkflow.TrustedEvidence.none(),Map.of()));
 }
 @Test void ambulanceHandoverIsNotEquivalentToArrival(){
  var item=OperationWorkflow.create("M25","TRIP",UUID.randomUUID(),
   actor(A,"m25:create"),Map.of("title","Interfacility transport","vehicleRef","AMB-1","driverRef","DRV-1","originRef","LOC-1","destinationRef","LOC-2"));
  item=OperationWorkflow.advance(item,"ASSIGN",1,actor(B,"m25:assign"),
   new OperationWorkflow.TrustedEvidence(Set.of("vehicle_driver_qualified")),Map.of()).item();
  item=OperationWorkflow.advance(item,"DISPATCH",2,actor(B,"m25:dispatch"),
   new OperationWorkflow.TrustedEvidence(Set.of("vehicle_driver_qualified")),Map.of()).item();
  item=OperationWorkflow.advance(item,"ARRIVE",3,actor(B,"m25:arrive"),
   OperationWorkflow.TrustedEvidence.none(),Map.of()).item();
  var arrived=item;
  assertThrows(WorkflowViolation.class,()->OperationWorkflow.advance(arrived,"COMPLETE",4,
   actor(B,"m25:complete"),OperationWorkflow.TrustedEvidence.none(),Map.of()));
  assertThrows(WorkflowViolation.class,()->OperationWorkflow.advance(arrived,"HANDOVER",4,
   actor(B,"m25:handover"),OperationWorkflow.TrustedEvidence.none(),Map.of()));
 }
 @Test void publishingFacilityConfigRequiresApprovalAndIndependentImpactAssessment(){
  var item=OperationWorkflow.create("M29","CONFIGURATION",UUID.randomUUID(),
   actor(A,"m29:create"),Map.of("title","Emergency ward","schemaVersion","v3","changeImpact","Scoped ward configuration"));
  var initialConfig=item;
  assertThrows(WorkflowViolation.class,()->OperationWorkflow.advance(initialConfig,"REVIEW",1,
   actor(A,"m29:review"),OperationWorkflow.TrustedEvidence.none(),Map.of()));
  item=OperationWorkflow.advance(item,"REVIEW",1,actor(B,"m29:review"),
   OperationWorkflow.TrustedEvidence.none(),Map.of()).item();
  var reviewed=item;
  assertThrows(WorkflowViolation.class,()->OperationWorkflow.advance(reviewed,"PUBLISH",2,
   actor(B,"m29:publish"),OperationWorkflow.TrustedEvidence.none(),Map.of()));
  assertThrows(WorkflowViolation.class,()->OperationWorkflow.advance(reviewed,"PUBLISH",2,
   actor(A,"m29:publish"),OperationWorkflow.TrustedEvidence.none(),Map.of()));
 }
 @Test void documentSignaturesAndAmendmentsRequireVerifiedProof(){
  var template=OperationWorkflow.create("M31","TEMPLATE",UUID.randomUUID(),
   actor(A,"m31:create"),Map.of("title","Lab report"));
  assertEquals("DRAFT",template.status());
  var doc=OperationWorkflow.create("M31","DOCUMENT",UUID.randomUUID(),
   actor(A,"m31:create"),Map.of("title","Report template","templateRef","TMPL-1","storageRef","OBJECT-1","contentDigest","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
  assertEquals("DOCUMENT_DRAFT",doc.status());
  var unsignedDocument=doc;
  assertThrows(WorkflowViolation.class,()->OperationWorkflow.advance(unsignedDocument,"SIGN",1,
   actor(B,"m31:sign"),OperationWorkflow.TrustedEvidence.none(),Map.of()));
  doc=OperationWorkflow.advance(doc,"SIGN",1,actor(B,"m31:sign"),
   new OperationWorkflow.TrustedEvidence(Set.of("verified_signature")),Map.of("signatureRef","SIG-001")).item();
  assertEquals("DOCUMENT_SIGNED",doc.status());
 }
 @Test void notificationDeliveryNeverCountsAsStaffAcknowledgement(){
  var notification=OperationWorkflow.create("M37","MESSAGE",UUID.randomUUID(),
   actor(A,"m37:create"),Map.of("title","Equipment recall","recipientRef","staff-3","templateRef","TPL-7","channel","internal","deliveryRef","DLV-001"));
  assertThrows(WorkflowViolation.class,()->OperationWorkflow.advance(notification,"DELIVER",1,
   actor(B,"m37:deliver"),OperationWorkflow.TrustedEvidence.none(),Map.of()));
  var delivered=OperationWorkflow.advance(notification,"DELIVER",1,actor(B,"m37:deliver"),
   new OperationWorkflow.TrustedEvidence(Set.of("delivery_receipt")),Map.of()).item();
  assertEquals("DELIVERED",delivered.status());
  assertNotEquals("ACKNOWLEDGED",delivered.status());
  var ack=OperationWorkflow.advance(delivered,"ACKNOWLEDGE",2,actor(B,"m37:acknowledge"),
   OperationWorkflow.TrustedEvidence.none(),Map.of()).item();
  assertEquals("ACKNOWLEDGED",ack.status());
 }
 @Test void rejectCrossFacilityMovesStaleVersionAndUnapprovedPatientData(){
  var item=OperationWorkflow.create("M21","REQUISITION",UUID.randomUUID(),
   actor(A,"m21:create"),Map.of("title","Equipment"));
  assertThrows(WorkflowViolation.class,()->OperationWorkflow.advance(item,"SUBMIT",1,
   new OperationWorkflow.Actor(B,CELL,UUID.randomUUID(),Set.of("m21:submit")),
   OperationWorkflow.TrustedEvidence.none(),Map.of()));
  assertThrows(WorkflowViolation.class,()->OperationWorkflow.advance(item,"SUBMIT",5,
   actor(B,"m21:submit"),OperationWorkflow.TrustedEvidence.none(),Map.of()));
  assertThrows(WorkflowViolation.class,()->OperationWorkflow.create("M25","TRIP",UUID.randomUUID(),
   actor(A,"m25:create"),Map.of("patientRef","PII-raw")));
  assertThrows(WorkflowViolation.class,()->OperationWorkflow.create("M22","STOCK_ITEM",UUID.randomUUID(),
   actor(A,"m22:create"),Map.of("quantity",-5)));
 }
}
