import type {PrintTemplate} from "./registry";

export type PrintMedia="a4-portrait"|"a4-landscape"|"a5-proof"|"label-proof"|"receipt-proof";
export type PrintProfile={media:PrintMedia;columns:readonly string[];signers:readonly string[];safetyText:string;requiresSourceApproval:boolean};
const norm=(cols:string[],signers:string[],media:PrintMedia="a4-portrait",safetyText="Not valid without a server-verified source document and authorized sign-off."):PrintProfile=>({
 columns:cols,signers,media,safetyText,requiresSourceApproval:true
});
const empty:string[]=[];
const profiles:Record<string,PrintProfile>={
 "card":norm(empty,["Authorized issuing officer"],"a5-proof"),
 "ticket":norm(empty,["Authorizing service"],"a5-proof"),
 "notice":norm(empty,["Authorizing officer"]),
 "instructions":norm(empty,["Responsible clinician","Recipient or representative"]),
 "form":norm(empty,["Requester","Responsible reviewer"]),
 "assessment":norm(empty,["Qualified assessor","Reviewer if required"]),
 "screening":norm(["Screen item","Recorded response","Evidence / source","Reviewer"],["Qualified screener"]),
 "clinical-note":norm(empty,["Responsible clinician"]),
 "clinical-report":norm(empty,["Report author","Verifying clinician"]),
 "certificate":norm(empty,["Authorized certifying officer"]),
 "discharge":norm(["Discharge medicine or follow-up","Source / unit","Instructions","Responsible team"],["Attending clinician","Recipient / witness"]),
 "referral":norm(empty,["Referring clinician","Receiving clinician / service"]),
 "handover":norm(["Item or responsibility","Source reference","Status","Receiver acknowledgment"],["Sending staff","Receiving staff"]),
 "acknowledgment":norm(empty,["Information provider","Recipient or representative"]),
 "checklist":norm(["Safety / completion item","Yes","No","N/A","Observer / time"],["Responsible staff","Independent checker if required"]),
 "review":norm(["Comparison element","Existing version","Proposed version","Evidence / reviewer"],["Review initiator","Independent approver"]),
 "amendment":norm(["Original element","Proposed correction","Reason","Linked revision"],["Author of correction","Independent reviewer"]),
 "log":norm(["Timestamp","Action or record","Source reference","Responsible staff"],["Log owner","Supervisor"]),
 "table":norm(["Category / service","Value / status","Source","Reviewer"],["Prepared by","Authorized reviewer"],"a4-landscape"),
 "financial-statement":norm(["Date","Entry","Debit XAF","Credit XAF","Running balance XAF"],["Finance preparer","Approver"],"a4-landscape","Amounts and balances must originate from a reconciled authoritative OHADA-compatible ledger; no calculations are certified by this sample."),
 "invoice":norm(["Service / product code","Description","Quantity","Unit price XAF","Tax category","Line total XAF"],["Billing issuer","Finance verifier"],"a4-portrait","Jurisdiction-specific tax rates, legal numbering, issuer identifiers and statutory disclosures must be configured and verified before issuance."),
 "receipt":norm(["Paid invoice / component","Method","Reference","Amount XAF"],["Cashier / collector","Ledger reconciliation checker"],"receipt-proof","Payment confirmation requires a durable authoritative ledger/gateway receipt, never a UI-generated value."),
 "estimate":norm(["Service / product","Estimated quantity","Estimated unit cost XAF","Estimated total XAF"],["Estimate preparer","Authorized reviewer"]),
 "payslip":norm(["Pay component","Earnings XAF","Deductions XAF","Basis / policy"],["Payroll controller","Human resources approver"]),
 "reconciliation":norm(["Source A","Source B","Difference","Resolution / reviewer"],["Preparer","Independent reviewer"]),
 "claim":norm(["Claim/service code","Date","Billed XAF","Approved XAF","Denied XAF","Reason"],["Claims officer","Payer authorized representative"]),
 "order":norm(["Service / item / medication","Code and specification","Quantity / unit","Priority","Reviewer"],["Requester","Authorized fulfiller"]),
 "purchase-order":norm(["SKU / description","Quantity","Unit","Unit price XAF","Tax","Line total XAF"],["Procurement officer","Authorized approver"]),
 "goods-receipt":norm(["Item and lot","Ordered qty","Delivered qty","Accepted qty","Condition / expiry"],["Warehouse receiver","Quality checker"]),
 "procurement":norm(["Supplier / candidate","Specification","Quoted price XAF","Compliance","Decision"],["Evaluator","Procurement approver"]),
 "inventory":norm(["Item and lot / serial","Stock / quantity","Unit","Condition","Movement / owner"],["Stock custodian","Independent checker"]),
 "label":norm(empty,["Label verifier"],"label-proof","Printer media dimensions, barcode symbology, device readability and patient ID match require per-printer testing. No simulated barcode is issued."),
 "lab-result":norm(["Analyte / test code","Observed result","Unit","Reference interval and source","Flag / interpretation","Method"],["Performing analyst","Authorized results validator"],"a4-landscape","Values, units, reference intervals, critical flags and issued status must be bound to versioned laboratory observations. No synthetic medical results are shown."),
 "pathology":norm(["Specimen / block","Sample or site","Result / interpretation","Classification","Reviewer"],["Pathologist","Authorized results validator"]),
 "radiology":norm(["Study / sequence","Site / laterality","Finding","Impression / follow-up"],["Reporting radiologist","Authorized reviewer"]),
 "observation":norm(["Observation time","Measurement / source","Value with unit","Reference / flag","Reviewer"],["Recording clinician","Qualified checker"]),
 "triage":norm(["Assessment item","Time","Observation","Priority protocol / reason","Reviewer"],["Triage nurse","Receiving clinician"]),
 "resuscitation":norm(["Event time","Assessment / intervention","Dose and unit if ordered","Response","Operator"],["Resuscitation lead","Recording nurse"],"a4-landscape"),
 "mar":norm(["Scheduled time","Medication and order ID","Dose with unit","Route","Actual time / outcome","Administering staff"],["Administering nurse","Independent checker when required"],"a4-landscape","No dose, route, administration or dispense event can be inferred from a prescription or this blank template."),
 "medication":norm(["Medication / code","Home or ordered dose + unit","Route","Frequency","Source / discrepancy"],["Reconciling clinician","Independent reviewer"]),
 "prescription":norm(["Medication / code","Strength / form","Dose + unit","Route","Frequency","Duration","Qty / refills"],["Licensed prescriber","Authorized dispenser if required"],"a4-landscape","Prescription is not legal or clinically valid until licensed prescriber identity, medication coding, unit/route, allergy review, signature and authoritative release are verified."),
 "dispense":norm(["Order and medicine","Batch / expiry","Authorized quantity","Released quantity","Custody reference"],["Dispensing pharmacist","Receiving party"]),
 "compounding":norm(["Ingredient and lot","Concentration / quantity","Unit","Method / verification","Preparation time"],["Compounder","Independent pharmacist reviewer"]),
 "adverse":norm(["Event / onset","Medication / product","Clinical observations","Immediate response","Reporter"],["Safety officer","Qualified clinician"]),
 "fluid":norm(["Date/time","Intake source / mL","Output source / mL","Balance evidence","Nurse"],["Responsible nurse"]),
 "wound":norm(["Wound site","Dimensions / unit","Dressing / intervention","Assessment","Reviewer"],["Wound clinician"]),
 "anesthesia":norm(["Timestamp","Measured vital / source","Agent / dose and unit","Intervention","Clinician"],["Anesthesia clinician","Recovery receiver"],"a4-landscape"),
 "operative":norm(["Procedure stage","Clinical finding or action","Time","Surgeon / reviewer"],["Primary surgeon","Theatre reviewer"]),
 "procedure":norm(["Procedure step","Intervention / source","Timestamp","Operator"],["Procedure lead"]),
 "transfusion":norm(["Blood product / unit ID","Compatibility source","Start/end time","Observations and reaction","Verifier"],["Two-person bedside check","Transfusion supervisor"],"a4-landscape","Identity and component compatibility must be independently verified at the bedside and by the authoritative blood bank."),
 "vaccination":norm(["Vaccine and lot","Dose / unit","Route / site","Date","Administering clinician"],["Vaccinator"]),
 "consent":norm(empty,["Patient / authorized representative","Qualified explaining clinician","Independent witness / interpreter if required"],"a4-portrait","The procedure-specific explanation, material risks, alternatives, capacity, voluntary choice, and applicable local legal text require qualified approval. This blank sample cannot capture valid consent."),
 "privacy":norm(["Recipient / purpose","Permitted data scope","Consent or legal basis","Expiry / restriction"],["Privacy officer","Data subject or legal representative when applicable"]),
 "critical":norm(["Event time","Critical finding","Notified role / channel","Readback / response","Escalation"],["Reporter","Receiving qualified clinician"]),
 "audit":norm(["Event time","Actor / scope","Action","Source digest / provenance","Reviewer"],["Export owner","Independent audit reviewer"],"a4-landscape","Audit records require verified integrity, least-privilege disclosure, retention and encrypted transfer before issuance."),
 "quality":norm(["Control / criterion","Evidence","Outcome","Owner","Follow-up date"],["Quality assessor","Independent reviewer"]),
 "public-health":norm(["Indicator / code","Period","Verified aggregate","Source / denominator","Reviewer"],["Data officer","Authorized public health reviewer"],"a4-landscape"),
 "custody":norm(["Item / seal ID","Source and destination","Date / time","Sender","Receiver"],["Sending custodian","Receiving custodian"]),
 "care-plan":norm(["Problem / objective","Intervention","Owner","Review schedule","Outcome"],["Care plan author","Receiving team"]),
 "survey":norm(["Question","Response","Comment","Action owner"],["Service quality lead"])
};
const defaultProfile=norm(["Record item","Value or status","Source / version","Reviewer"],["Record owner","Authorized reviewer"]);
export function profileFor(template:Pick<PrintTemplate,"kind">):PrintProfile{return profiles[template.kind]??defaultProfile;}
export function isSafetyCritical(template:Pick<PrintTemplate,"kind"|"context">):boolean{
 return template.context==="patient"||template.context==="decedent"||["consent","prescription","lab-result","radiology","mar","transfusion","receipt"].includes(template.kind);
}
export const DOCUMENT_TYPES=Object.freeze(Object.keys(profiles));
export const DOCUMENT_RELEASE_STATUS="BLANK_SPECIMEN_ONLY" as const;
