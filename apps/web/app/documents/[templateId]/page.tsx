import Link from "next/link";
import {notFound} from "next/navigation";
import documentCatalog from "../../../../../registers/hospital-document-template-catalog-v1.json";
import fieldLibrary from "../../../../../registers/hospital-document-field-library.json";
import {resolveTemplate} from "../document-runtime.mjs";
import {DocumentRenderer} from "../DocumentRenderer";
import type {DocumentTemplate} from "../DocumentRenderer";

export function generateStaticParams(){return documentCatalog.templates.map(t=>({templateId:t.id}));}
export function generateMetadata({params}: {params:Promise<{templateId:string}>}){
 return params.then(({templateId})=>({title:"SYNQORIA document preview · "+templateId,robots:{index:false,follow:false}}));
}
export default async function HospitalDocumentTemplate({params}:{params:Promise<{templateId:string}>}){
 const {templateId}=await params;
 const template=documentCatalog.templates.find(t=>t.id===templateId);
 if(!template)notFound();
 const rendered=resolveTemplate(template,fieldLibrary.fieldGroups) as DocumentTemplate;
 return <><DocumentRenderer template={rendered}/><div className="syn-doc-screen-legal"><Link href="/documents">SYNQORIA document library</Link></div></>;
}
