/** Canonical SYNQORIA screen preview contract. NOT an approved clinical field dictionary. */
export const SCREEN_LAYOUTS = {
  "F": "form",
  "L": "worklist",
  "D": "detail",
  "X": "review",
  "B": "board",
  "T": "timeline-analytics",
  "H": "dashboard"
};
export const UI_STATES = ["S00","S01","S02","S03","S04","S05","S06","S07","S08","S09","S10","S11","S12","S13"];
export const UI_DIALOGS = [
  "Unsaved work",
  "Patient switch verification",
  "High-risk confirmation",
  "Correction reason",
  "Stale-version comparison",
  "Session expiry",
  "Access request",
  "Scoped export",
  "Attachment preview",
  "Server-confirmed status lookup",
  "Date and resource picker",
  "Keyboard shortcuts and help"
];
export const UI_PRINTS = [
  "Patient identity card",
  "Appointment preparation slip",
  "Specimen label",
  "Laboratory report",
  "Radiology report",
  "Prescription",
  "Medication administration export",
  "Discharge summary",
  "Referral letter",
  "Invoice",
  "Payment receipt",
  "Consent copy",
  "Operative summary",
  "Transfusion record",
  "Purchase order",
  "Goods receipt",
  "Custody/release record",
  "Audit export manifest"
];
export const UI_COMPONENTS = [
  "Application shell",
  "Patient context",
  "Encounter context",
  "Role/task header",
  "Action toolbar",
  "Filter panel",
  "Accessible data table",
  "Record card",
  "Form field/error",
  "Terminology selector",
  "Measurement with unit",
  "Status badge",
  "Alert",
  "Timeline",
  "Clinical note editor",
  "Version comparison",
  "Signature review",
  "Provenance display",
  "Connectivity/freshness strip",
  "Pagination",
  "Chart and data table",
  "File attachment",
  "Empty state",
  "Command status",
  "Proxy banner",
  "Print document shell"
];
export const UI_LAYOUT_CONFIG = {
  "F": {
    "tabs": [
      "Form",
      "Review",
      "Workflow"
    ],
    "controls": [
      "form fields",
      "validation summary",
      "clear draft",
      "review draft",
      "primary command locked",
      "cancel"
    ],
    "filters": []
  },
  "L": {
    "tabs": [
      "Results",
      "Filters",
      "Workflow"
    ],
    "controls": [
      "search",
      "filter toggle",
      "sort",
      "page size",
      "pagination",
      "clear filters",
      "record action locked"
    ],
    "filters": [
      "scope",
      "status",
      "date range"
    ]
  },
  "D": {
    "tabs": [
      "Overview",
      "Details",
      "History"
    ],
    "controls": [
      "details",
      "history",
      "documents",
      "primary command locked"
    ],
    "filters": []
  },
  "X": {
    "tabs": [
      "Compare",
      "Evidence",
      "Decision"
    ],
    "controls": [
      "source comparison",
      "evidence",
      "reason",
      "review decision",
      "primary command locked"
    ],
    "filters": []
  },
  "B": {
    "tabs": [
      "Board",
      "List",
      "Workflow"
    ],
    "controls": [
      "date",
      "scope",
      "view switch",
      "filter toggle",
      "clear filters",
      "item action locked"
    ],
    "filters": [
      "date",
      "scope",
      "status"
    ]
  },
  "T": {
    "tabs": [
      "Overview",
      "Data table",
      "Provenance"
    ],
    "controls": [
      "date range",
      "scope",
      "metric",
      "data table",
      "export locked"
    ],
    "filters": [
      "date range",
      "scope"
    ]
  },
  "H": {
    "tabs": [
      "Tasks",
      "Exceptions",
      "Service status"
    ],
    "controls": [
      "quick action links",
      "task list",
      "urgent exceptions",
      "service status"
    ],
    "filters": []
  }
};
export function buildScreenContract(s){
 const groups = s.data_input_groups.split(",").map(x=>x.trim()).filter(Boolean);
 const layout=UI_LAYOUT_CONFIG[s.layout];
 if(!layout)throw new Error("Unsupported canonical layout: "+s.layout);
 return {screenId:s.id,module:s.module,title:s.name,layout:s.layout,fieldGroups:groups,tabs:layout.tabs,controls:layout.controls,filters:layout.filters,primaryAction:s.primary_action,saveMode:"memory-only-preview",authorization:"unconnected",release:"denied",contentStatus:"contract-generated-proposal",uiStateIds:["S00","S01","S02","S03","S08","S09","S10","S11","S12","S13",...(["F","X"].includes(s.layout)?["S04","S05","S06","S07"]:[])]};
}
