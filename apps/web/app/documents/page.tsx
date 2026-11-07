import catalog from "../../../../registers/hospital-document-template-catalog-v1.json";
import {DocumentCatalog} from "./DocumentCatalog";
export default function HospitalDocumentLibrary(){return <DocumentCatalog items={catalog.templates}/>;}