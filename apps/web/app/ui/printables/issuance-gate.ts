/** SYNQORIA hospital document issuance safety contract.
 * This module deliberately NEVER issues, signs or sends a clinical/financial document.
 * A real issuer must implement the listed server-side checks and obtain approved release evidence.
 */
import type {PrintTemplate} from "./registry";
import {isSafetyCritical} from "./print-profiles";

export type DocumentLifecycle="DRAFT"|"IN_REVIEW"|"APPROVED"|"ISSUED"|"CORRECTED"|"VOIDED"|"RETIRED";
export type PrintEligibilityInput={
  authenticated:boolean; tenantVerified:boolean; facilityVerified:boolean; resourceAuthorized:boolean;
  permittedDisclosure:boolean; currentServerVersion:boolean; sourceImmutable:boolean;
  templateVersionApproved:boolean; authorVerified:boolean; signerVerified:boolean;
  independentReviewCompleted:boolean; retentionPolicyResolved:boolean;
  clinicalApproval:boolean; jurisdictionVerified:boolean; qualityControlVerified:boolean;
  auditOutboxCommitted:boolean; serverIssueReceiptVerified:boolean;
  patientContextVerified:boolean; secondIdentifierChecked:boolean;
  paymentLedgerReconciled:boolean; prescriptionSafetyVerified:boolean;
  resultValidationCompleted:boolean; barcodePrinterValidated:boolean;
  nonceReplayProtected:boolean;
};
export type PrintGate={allowed:false;eligibleForBackendReview:boolean;blockers:string[];status:"BLANK_SPECIMEN_UNAPPROVED"};
export const NEVER_ISSUE_FROM_UI=true as const;
export const PRINT_DOCUMENT_STATES:readonly DocumentLifecycle[]=[
 "DRAFT","IN_REVIEW","APPROVED","ISSUED","CORRECTED","VOIDED","RETIRED"
];
const required:readonly (keyof PrintEligibilityInput)[]=[
 "authenticated","tenantVerified","facilityVerified","resourceAuthorized",
 "permittedDisclosure","currentServerVersion","sourceImmutable",
 "templateVersionApproved","authorVerified","signerVerified",
 "retentionPolicyResolved","auditOutboxCommitted","serverIssueReceiptVerified",
 "nonceReplayProtected"
];
export function checkPrintGate(
 template:Pick<PrintTemplate,"kind"|"context"|"status">,
 flags:PrintEligibilityInput
):PrintGate{
 const blockers:string[]=[];
 for(const gate of required)if(flags[gate]!==true)blockers.push(gate);
 if(isSafetyCritical(template)){
  for(const gate of ["clinicalApproval","independentReviewCompleted","qualityControlVerified"] as const){
   if(flags[gate]!==true)blockers.push(gate);
  }
 }
 if(template.context==="patient"||template.context==="decedent"){
  for(const gate of ["patientContextVerified","secondIdentifierChecked"] as const){
   if(flags[gate]!==true)blockers.push(gate);
  }
 }
 if(["invoice","receipt","financial-statement","payslip","claim"].includes(template.kind)){
  for(const gate of ["paymentLedgerReconciled","jurisdictionVerified"] as const){
   if(flags[gate]!==true)blockers.push(gate);
  }
 }
 if(template.kind==="prescription"||template.kind==="mar"||template.kind==="dispense"){
  if(flags.prescriptionSafetyVerified!==true)blockers.push("prescriptionSafetyVerified");
 }
 if(["lab-result","radiology","pathology"].includes(template.kind)){
  if(flags.resultValidationCompleted!==true)blockers.push("resultValidationCompleted");
 }
 if(template.kind==="label"&&flags.barcodePrinterValidated!==true)blockers.push("barcodePrinterValidated");
 if(template.status!=="APPROVED_TEMPLATE_VERSION")blockers.push("templateApprovalNotRecorded");
 // UI-only contracts cannot issue documents even if every booleans is manipulated.
 blockers.push("productionIssuanceServiceNotImplemented");
 return {allowed:false,eligibleForBackendReview:blockers.length===1,status:"BLANK_SPECIMEN_UNAPPROVED",blockers};
}
export function denyDocumentIssue():never{
 throw new Error("SYNQORIA_PRINT_BLOCKED_UNAPPROVED: use an authorized server document issuer");
}
