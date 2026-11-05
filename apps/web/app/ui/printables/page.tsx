import {DOCUMENT_CATALOG} from "./registry";
import PrintLibraryClient from "./PrintLibraryClient";
export default function HospitalDocumentLibraryPage(){return <PrintLibraryClient templates={DOCUMENT_CATALOG}/>;}
