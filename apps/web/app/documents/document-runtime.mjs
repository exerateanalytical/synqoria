/** SYNQORIA hospital document preview runtime contracts; no actual clinical issue or signature. */
export const CANONICAL_PRINT_CODES = Object.freeze(["P01","P02","P03","P04","P05","P06","P07","P08","P09","P10","P11","P12","P13","P14","P15","P16","P17","P18"]);
export const DOCUMENT_TABLE_COLUMNS = Object.freeze({
  "triage_3": [
    "Time",
    "Type",
    "Value",
    "Unit",
    "Source"
  ],
  "prescription_1": [
    "Medicine (generic) / strength",
    "Dose",
    "Unit",
    "Route",
    "Frequency",
    "Duration",
    "Quantity",
    "Instructions"
  ],
  "medAdministration_1": [
    "Dose ID",
    "Medicine",
    "Dose / unit",
    "Route",
    "Due / given time",
    "Status",
    "Administrator"
  ],
  "medicationReconcile_1": [
    "Medicine",
    "Source",
    "Strength",
    "Dose",
    "Route",
    "Frequency",
    "Last taken"
  ],
  "medicationReconcile_2": [
    "Medicine",
    "Previous",
    "Current",
    "Change rationale",
    "Review status"
  ],
  "labOrder_1": [
    "Test code",
    "Test name",
    "Specimen",
    "Priority",
    "Requested by"
  ],
  "labResults_1": [
    "Analyte / code",
    "Result",
    "Unit",
    "Reference interval",
    "Flag",
    "Method"
  ],
  "labSafety_4": [
    "Attempt time",
    "Recipient",
    "Channel",
    "Read-back",
    "Outcome"
  ],
  "microbiology_2": [
    "Organism",
    "Method",
    "Quantity",
    "Identification",
    "Comment"
  ],
  "microbiology_3": [
    "Agent",
    "Method",
    "Value/zone",
    "Interpretation",
    "Version"
  ],
  "pathology_6": [
    "Study",
    "Method",
    "Result",
    "Interpretation",
    "Source"
  ],
  "surgicalChecklist_1": [
    "Check",
    "Response",
    "Reviewer",
    "Timestamp"
  ],
  "surgicalChecklist_2": [
    "Check",
    "Response",
    "Reviewer",
    "Timestamp"
  ],
  "surgicalChecklist_3": [
    "Check",
    "Response",
    "Reviewer",
    "Timestamp"
  ],
  "procedure_7": [
    "Specimen / implant",
    "Site",
    "Identifier / batch",
    "Count",
    "Traceability"
  ],
  "anesthesia_4": [
    "Time",
    "Drug / fluid",
    "Dose / unit",
    "Route",
    "Amount",
    "Administrator"
  ],
  "anesthesia_5": [
    "Time",
    "Measure",
    "Value",
    "Unit",
    "Intervention"
  ],
  "transfusion_1": [
    "Component type",
    "Unit ID",
    "ABO/Rh",
    "Expiry",
    "Compatibility",
    "Release"
  ],
  "transfusion_4": [
    "Time",
    "Phase",
    "Vitals / units",
    "Reaction",
    "Reviewer"
  ],
  "financeLines_1": [
    "Code",
    "Item / service",
    "Qty",
    "Unit price",
    "Tax",
    "Line total"
  ],
  "payer_5": [
    "Service code",
    "Covered",
    "Excluded",
    "Limit",
    "Reason"
  ],
  "operationInventory_1": [
    "Item/SKU",
    "Lot / serial",
    "Quantity",
    "Unit",
    "Expiry",
    "Location"
  ],
  "purchase_3": [
    "Item / description",
    "SKU",
    "Quantity",
    "Unit",
    "Unit price"
  ],
  "purchase_4": [
    "Currency",
    "Rate",
    "Tax code",
    "Tax amount",
    "Gross"
  ],
  "goodsReceipt_3": [
    "Item",
    "Ordered",
    "Delivered",
    "Accepted",
    "Rejected",
    "Batch/expiry"
  ],
  "inventoryAudit_2": [
    "SKU / batch",
    "Expected",
    "Counted",
    "Variance",
    "Review"
  ],
  "asset_5": [
    "Date",
    "Service type",
    "Technician",
    "Outcome",
    "Next due"
  ],
  "payroll_3": [
    "Component",
    "Code",
    "Type",
    "Amount",
    "Currency"
  ],
  "transport_5": [
    "Timestamp",
    "Location",
    "Actor",
    "Event",
    "Acknowledged"
  ],
  "mortuary_4": [
    "Released by",
    "Received by",
    "Date/time",
    "Seal/reference",
    "Condition"
  ],
  "device_4": [
    "Time",
    "Measurement",
    "Unit",
    "Provenance",
    "Transfer status"
  ],
  "notification_4": [
    "Time",
    "Channel",
    "Recipient group",
    "Attempt",
    "Outcome"
  ],
  "platform_4": [
    "Check ID",
    "Source",
    "Result",
    "Reviewer",
    "Time"
  ],
  "recovery_3": [
    "Backup ID",
    "Point",
    "Checksum reference",
    "Integrity",
    "Verified by"
  ],
  "operations_3": [
    "Task",
    "Priority",
    "Assigned role",
    "Due",
    "Status"
  ]
});
export const DOCUMENT_STATUSES = Object.freeze(["DRAFT","PRELIMINARY","FINAL","AMENDED","CORRECTED","VOID","UNKNOWN"]);
export const PRINT_MEDIA = Object.freeze(["a4","a5","receipt","label","card"]);
export const DOCUMENT_RELEASE = Object.freeze({generation:"SAMPLE_ONLY",clinical:"DENIED",signatures:"NOT_LEGALLY_VALID",patientData:"NONE",realPrint:"NOT_AUTHORIZED",retention:"POLICY_PENDING"});
export function resolveTemplate(template,library){
 if(!template||!Array.isArray(template.groupKeys))throw new Error("Invalid template");
 const sections=template.groupKeys.map(key=>{
  const group=library[key];if(!group)throw new Error("Unknown group: "+key);
  return {id:key,title:group.title,fields:group.fields.map(f=>({...f,columns:f.type==="table"?DOCUMENT_TABLE_COLUMNS[f.id]??null:null}))};
 });
 return {...template,sections};
}
export function validateHospitalCatalog(catalog,library){
 const errors=[],ids=new Set(),canonical=new Map(),moduleCounts={};
 if(!catalog||!Array.isArray(catalog.templates))return ["missing template catalogue"];
 for(const doc of catalog.templates){
  if(ids.has(doc.id))errors.push("duplicate id "+doc.id);ids.add(doc.id);
  if(!/^M\d\d$/.test(doc.module))errors.push("invalid module "+doc.id);
  if(!doc.title?.trim())errors.push("missing title "+doc.id);
  if(!PRINT_MEDIA.includes(doc.media))errors.push("invalid media "+doc.id);
  if(!doc.groupKeys?.length||doc.groupKeys.length<3)errors.push("insufficient sections "+doc.id);
  if(doc.release?.mode!=="SAMPLE_ONLY"||doc.release?.clinical!=="DENIED")errors.push("unsafe release "+doc.id);
  if(doc.release?.dataSource!=="NO_LIVE_DATA"||doc.release?.authorization!=="SERVER_REQUIRED")errors.push("unsafe source "+doc.id);
  moduleCounts[doc.module]=(moduleCounts[doc.module]??0)+1;
  for(const key of doc.groupKeys||[]){
   if(!library[key])errors.push("missing group "+doc.id+" "+key);
   else for(const f of library[key].fields){
    if(!f.id||!f.label||!f.type)errors.push("invalid field "+doc.id+" "+key);
    if(f.type==="table"&&!DOCUMENT_TABLE_COLUMNS[f.id])errors.push("missing table columns "+f.id);
   }
  }
  if(doc.canonicalPrintCode){
   if(canonical.has(doc.canonicalPrintCode))errors.push("duplicate canonical "+doc.canonicalPrintCode);
   canonical.set(doc.canonicalPrintCode,doc.id);
  }
 }
 for(const id of CANONICAL_PRINT_CODES)if(!canonical.has(id))errors.push("missing canonical print "+id);
 if(Object.keys(moduleCounts).length!==40)errors.push("wrong module count");
 return errors;
}
