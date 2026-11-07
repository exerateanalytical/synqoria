import {notFound} from "next/navigation";
import {DOCUMENT_CATALOG,findPrintable} from "../registry";
import PrintablePreview from "../PrintablePreview";
export function generateStaticParams(){return DOCUMENT_CATALOG.map(d=>({templateId:d.id}));}
export default async function DocumentTemplateRoute({params}:{params:Promise<{templateId:string}>}){
 const {templateId}=await params;
 const template=findPrintable(templateId);
 if(!template)notFound();
 return <PrintablePreview template={template}/>;
}
