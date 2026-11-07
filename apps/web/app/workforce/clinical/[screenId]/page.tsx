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
import modules from "../../../../../../registers/canonical-modules.json";
import {SchedulingWorkbench,type Screen as SchedulingScreen} from "../../../../components/scheduling/SchedulingWorkbench";
import {ReferralWorkbench,type Screen as ReferralScreen} from "../../../../components/referrals/ReferralWorkbench";
import {IntegrationWorkbench,type Screen as IntegrationScreen} from "../../../../components/integrations/IntegrationWorkbench";
import opsCatalog from "../../../../lib/operations-screen-catalog.json";
import mortuaryCatalog from "../../../../lib/mortuary-screen-catalog.json";
import {OperationsWorkbench} from "../../../../components/OperationsWorkbench";
import {MaintenancePanel} from "../../../../components/maintenance/MaintenancePanel";
import {MortuaryWorkbench} from "../../../../components/MortuaryWorkbench";
import {PrivacyWorkbench,type Screen as PrivacyScreen} from "../../../../components/privacy/PrivacyWorkbench";
import {AuditWorkbench,type Screen as AuditScreen} from "../../../../components/audit/AuditWorkbench";
import {Directory} from "../../../../components/hr/Directory";
import {Employment} from "../../../../components/hr/Employment";
import {Roster} from "../../../../components/hr/Roster";
import {Attendance} from "../../../../components/hr/Attendance";
import {Preparation} from "../../../../components/hr/Preparation";
import {Approval} from "../../../../components/hr/Approval";
import {Worklist} from "../../../../components/nutrition/Worklist";
import {Orders} from "../../../../components/nutrition/Orders";
import {Production} from "../../../../components/nutrition/Production";
import {Delivery} from "../../../../components/nutrition/Delivery";
import Dashboard from "../../../../components/analytics/Dashboard";
import Analytics from "../../../../components/analytics/Analytics";
import Reports from "../../../../components/analytics/Reports";
import Exports from "../../../../components/analytics/Exports";
import {Terminology} from "../../../../components/content/Terminology";
import {Formulary} from "../../../../components/content/Formulary";
import {Packages} from "../../../../components/content/Packages";
import {Approvals} from "../../../../components/content/Approvals";
import {Activation} from "../../../../components/content/Activation";

/** Screen routing is part of the real application, not an image catalogue.
 * An HTTP 200 is only route coverage; domain writes remain OFF unless explicitly integrated.
 */
export const dynamic="force-dynamic";
const allScreens=canonical.screen_register as CanonicalRouteSpec[];
const operational=new Set(implementation.rows.map(x=>x.id));
const ops001=new Set(["G-S01","G-S02","G-S04","G-S05","G-S07","G-S08","G-S09","M01-S02"]);
const longitudinal=new Set(["M04-S02","M05-S01","M05-S04","M05-S05","M06-S02","M06-S03","M06-S04","M06-S05"]);
// Resolve module display text from the authoritative 40-module register, not stale UI aliases.
const moduleNames:Record<string,string>={
 SHARED:"Shared identity and workspace",
 ...Object.fromEntries(modules.module_register.map(m=>[m.id,m.name_and_owned_records.split(";")[0]]))
};

// Existing module owners' screen components, not newly generated copies.
const sourceScreenComponents={
 "M24-S01":Directory,"M24-S02":Employment,"M24-S03":Roster,
 "M24-S04":Attendance,"M24-S05":Preparation,"M24-S06":Approval,
 "M26-S01":Worklist,"M26-S02":Orders,"M26-S03":Production,"M26-S04":Delivery,
 "M28-S01":Dashboard,"M28-S04":Reports,"M28-S05":Exports,
 "M30-S01":Terminology,"M30-S02":Formulary,"M30-S03":Packages,
 "M30-S04":Approvals,"M30-S05":Activation
};
if(allScreens.length!==261||new Set(allScreens.map(s=>s.id)).size!==261)throw new Error("CANONICAL_SCREEN_REGISTER_MISMATCH");
export function generateStaticParams(){return allScreens.map(s=>({screenId:s.id}));}
export default async function OperationalScreen({params}:{params:Promise<{screenId:string}>}){
 const {screenId}=await params;
 const screen=allScreens.find(s=>s.id===screenId);
 if(!screen)notFound();
 // Use the already registered canonical 261 URLs. No duplicate application screens.
 if(screen.module==="M02")return <SchedulingWorkbench screen={screenId as SchedulingScreen}/>;
 if(screen.module==="M16")return <ReferralWorkbench screen={screenId as ReferralScreen}/>;
 if(screen.module==="M35")return <IntegrationWorkbench screen={screenId as IntegrationScreen}/>;
 // Older independent backend modules now reuse their original components through one canonical screen.
 const ordinary=sourceScreenComponents[screenId as keyof typeof sourceScreenComponents];
 if(ordinary){const ExistingScreen=ordinary;return <ExistingScreen/>;}
 if(screenId==="M28-S02")return <Analytics domain="CLINICAL"/>;
 if(screenId==="M28-S03")return <Analytics domain="FINANCIAL"/>;
 if(screen.module==="M32")return <PrivacyWorkbench screen={screenId as PrivacyScreen}/>;
 if(screen.module==="M34")return <AuditWorkbench screen={screenId as AuditScreen}/>;
 const operationsModule=opsCatalog.modules.find(x=>x.id===screen.module);
 const opsScreen=operationsModule?.screens.find(x=>x.id===screenId);
 if(operationsModule&&opsScreen){
  const workbench=<OperationsWorkbench
   moduleId={operationsModule.id} screenId={opsScreen.id}
   name={opsScreen.name} nameFr={opsScreen.nameFr}
   action={opsScreen.primaryAction} inputGroups={opsScreen.dataInputGroups}/>;
  // M23 only: read-only preventive-maintenance views on the existing M23-S01/S03/S05 screens (no new route).
  return operationsModule.id==="M23"?<>{workbench}<MaintenancePanel screenId={opsScreen.id}/></>:workbench;
 }
 if(screen.module==="M27"){
  const mortuary=mortuaryCatalog.screens.find(x=>x.id===screenId);
  if(mortuary)return <MortuaryWorkbench screenId={mortuary.id} name={mortuary.name}
    nameFr={mortuary.nameFr} primaryAction={mortuary.primaryAction}/>;
 }

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
