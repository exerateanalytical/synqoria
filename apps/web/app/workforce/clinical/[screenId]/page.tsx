import {notFound,redirect} from "next/navigation";
import ClinicalReadWorkspace from "../ClinicalReadWorkspace";
import type {LiveScreenId} from "../ClinicalReadWorkspace";
import StaffPreferences from "../StaffPreferences";
import TriageQueueWorkspace from "../TriageQueueWorkspace";
import TriageReassessmentReview from "../TriageReassessmentReview";
import LongitudinalReadWorkspace from "../LongitudinalReadWorkspace";
import type {LongitudinalScreenId} from "../LongitudinalReadWorkspace";
import HelpAndSafeNavigation from "../HelpAndSafeNavigation";
import UnintegratedScreen from "../UnintegratedScreen";
import SharedOps001Workspace from "../SharedOps001Workspace";
import type {Ops001Screen} from "../SharedOps001Workspace";
import type {CanonicalRouteSpec,ExtendedSpec,FlowPath,DocumentLink} from "../UnintegratedScreen";
import canonical from "../../../../../../registers/canonical-screens.json";
import specials from "../../../../../../registers/ui-specific-252-prototype-contracts.json";
import flows from "../../../../../../registers/ui-cross-module-workflow-14.json";
import documents from "../../../../../../registers/hospital-document-template-catalog-v1.json";
import implementation from "../../../../../../registers/operational-screen-progress-v0.1.json";
import {DOCUMENT_CATALOG} from "../../../ui/printables/registry";

/** Screen routing is part of the real application, not an image catalogue.
 * An HTTP 200 is only route coverage; domain writes remain OFF unless explicitly integrated.
 */
export const dynamic="force-dynamic";
const allScreens=canonical.screen_register as CanonicalRouteSpec[];
const operational=new Set(implementation.rows.map(x=>x.id));
const ops001=new Set(["G-S01","G-S02","G-S04","G-S05","G-S07","G-S08","G-S09","M01-S02"]);
const longitudinal=new Set(["M04-S02","M05-S01","M05-S04","M05-S05","M06-S02","M06-S03","M06-S04","M06-S05"]);
const moduleNames:Record<string,string>={
 SHARED:"Shared identity and workspace",
 M01:"Patient registration and identity",M02:"Appointments and scheduling",
 M03:"Triage and queues",M04:"Encounters, admissions and beds",
 M05:"Clinical notes and diagnoses",M06:"Observations and vital signs",
 M07:"Allergies and reconciliation",M08:"Medication prescribing",
 M09:"Medication administration",M10:"Nursing and care plans",
 M11:"Laboratory services",M12:"Radiology and PACS",
 M13:"Pharmacy services",M14:"Blood bank",M15:"Surgery and theatres",
 M16:"Maternal and neonatal services",M17:"Clinical specialties",
 M18:"Clinical and allied health services",M19:"Hospital billing and finance",
 M20:"Insurance and claims",M21:"Procurement and vendors",
 M22:"Stores and inventory",M23:"Biomedical assets and facilities",
 M24:"Workforce, HR and payroll",M25:"Transport and ambulance",
 M26:"Catering and dietary",M27:"Mortuary",
 M28:"Operations, dashboards and BI",M29:"Facility hierarchy",
 M30:"Terminology and clinical rules",M31:"Forms, templates and documents",
 M32:"Consent and privacy",M33:"Identity, roles and privileges",
 M34:"Public health and reporting",M35:"Quality and patient safety",
 M36:"Health information exchange",M37:"Outreach and community health",
 M38:"Research and teaching",M39:"Tenant and hospital cell operations",
 M40:"Service operations and recovery"
};
if(allScreens.length!==261||new Set(allScreens.map(s=>s.id)).size!==261)throw new Error("CANONICAL_SCREEN_REGISTER_MISMATCH");
export function generateStaticParams(){return allScreens.map(s=>({screenId:s.id}));}
export default async function OperationalScreen({params}:{params:Promise<{screenId:string}>}){
 const {screenId}=await params;
 const screen=allScreens.find(s=>s.id===screenId);
 if(!screen)notFound();
 // SYNQ-OPS-001 source-integrated slices only. Never mutate clinical
 // acceptance or invent missing emergency / patient-registration APIs.
 if(ops001.has(screenId))
  return <SharedOps001Workspace screenId={screenId as Ops001Screen}/>;

 // Preserve the real, independently tested subset rather than replacing it with a generic renderer.
 if(operational.has(screenId)){
  if(longitudinal.has(screenId))return <LongitudinalReadWorkspace screenId={screenId as LongitudinalScreenId}/>;
  if(screenId==="G-S06")return <StaffPreferences/>;
  if(screenId==="M03-S01")return <TriageQueueWorkspace/>;
  if(screenId==="M03-S04")return <TriageReassessmentReview/>;
  if(screenId==="M03-S05")return <TriageQueueWorkspace screenId="M03-S05" initialView="PENDING_HANDOVERS" fixedView/>;
  if(screenId==="G-S11")return <HelpAndSafeNavigation mode="help"/>;
  if(screenId==="G-S12")return <HelpAndSafeNavigation mode="notFound"/>;
  if(screenId==="M31-S01")redirect("/hospital-documents");
  if(screenId==="M31-S03")redirect("/ui/printables");
  return <ClinicalReadWorkspace screenId={screenId as LiveScreenId}/>;
 }
 // Do not map arbitrary UI prototypes to unreviewed API mutations.
 const specific=specials.remainingSpecialized.find(x=>x.id===screenId) as ExtendedSpec|undefined;
 const peers=allScreens.filter(x=>x.module===screen.module).map(x=>({id:x.id,name:x.name}));
 const flowPaths=flows.flows.filter(x=>x.ids.includes(screenId)).map(x=>({id:x.id,title:x.title,path:x.path,exceptionPath:x.exceptionPath})) as FlowPath[];
 const structured=DOCUMENT_CATALOG.filter(x=>x.module===screen.module).slice(0,6).map(x=>({id:x.id,title:x.en,href:"/ui/printables/"+encodeURIComponent(x.id)}));
 const pdf=documents.templates.filter(x=>x.module===screen.module).slice(0,6).map(x=>({id:x.id,title:x.title,href:"/documents/"+encodeURIComponent(x.id)}));
 const related=[...structured,...pdf] satisfies DocumentLink[];
 return <UnintegratedScreen screen={screen} detail={specific}
  moduleName={specific?.moduleName||moduleNames[screen.module]||screen.module}
  peers={peers} flowPaths={flowPaths} documents={related}/>;
}
