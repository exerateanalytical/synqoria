import enhanced from "../../../../registers/hospital-document-template-catalog-v1.json";
import {DOCUMENT_CATALOG} from "../ui/printables/registry";
import UnifiedHospitalDocuments from "./UnifiedHospitalDocuments";
import type {UnifiedDocumentItem} from "./UnifiedHospitalDocuments";

function buildCombinedItems():UnifiedDocumentItem[]{
 const byId=new Map<string,UnifiedDocumentItem>();
 for(const t of DOCUMENT_CATALOG){
  byId.set(t.id,{
   id:t.id,title:t.en,titleFr:t.fr,module:t.module,department:t.domainEn,
   kind:t.kind,sources:["structured-form"],
   href:"/ui/printables/"+encodeURIComponent(t.id),canonical:t.canonical,
   fields:t.sections.reduce((sum,s)=>sum+s.fields.length,0)
  });
 }
 for(const t of enhanced.templates){
  const link="/documents/"+encodeURIComponent(t.id);
  const match=byId.get(t.id);
  if(match){
   byId.set(t.id,{...match,sources:["structured-form","pdf-sample"],alternateHref:link});
  }else{
   byId.set(t.id,{
    id:t.id,title:t.title,titleFr:"",module:t.module,department:t.module,
    kind:t.groupKeys[0]||"document",
    sources:["pdf-sample"],href:link,
    canonical:Boolean(t.canonicalPrintCode),fields:t.groupKeys.length
   });
  }
 }
 return [...byId.values()];
}
export default function UnifiedHospitalDocumentCatalogue(){
 return <UnifiedHospitalDocuments items={buildCombinedItems()}/>;
}
