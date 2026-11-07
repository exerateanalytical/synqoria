/** SYNQORIA document library: 376 blank prototype templates. Not a legal, financial or clinical issuance service. */
import d1 from "./catalog-pat.json";
import d2 from "./catalog-opd.json";
import d3 from "./catalog-emr.json";
import d4 from "./catalog-nur.json";
import d5 from "./catalog-lab.json";
import d6 from "./catalog-rad.json";
import d7 from "./catalog-phm.json";
import d8 from "./catalog-sur.json";
import d9 from "./catalog-blo.json";
import d10 from "./catalog-mat.json";
import d11 from "./catalog-ped.json";
import d12 from "./catalog-con.json";
import d13 from "./catalog-fin.json";
import d14 from "./catalog-ins.json";
import d15 from "./catalog-sup.json";
import d16 from "./catalog-mor.json";
import d17 from "./catalog-gov.json";
import d18 from "./catalog-fac.json";
import d19 from "./catalog-amb.json";
import d20 from "./catalog-hr.json";
import d21 from "./catalog-qlt.json";
import d22 from "./catalog-reh.json";
import d23 from "./catalog-spc.json";
import d24 from "./catalog-sys.json";

export type PrintContext="patient"|"financial"|"administrative"|"decedent";
export type PrintSection={id:string;heading:string;fields:string[]};
export type PrintTemplate={
 id:string;en:string;fr:string;domain:string;domainEn:string;domainFr:string;module:string;
 kind:string;context:string;sections:PrintSection[];
 canonical:boolean;status:string;
};
export const DOCUMENT_CATALOG:PrintTemplate[]=[...d1, ...d2, ...d3, ...d4, ...d5, ...d6, ...d7, ...d8, ...d9, ...d10, ...d11, ...d12, ...d13, ...d14, ...d15, ...d16, ...d17, ...d18, ...d19, ...d20, ...d21, ...d22, ...d23, ...d24];
export const DOCUMENT_BY_ID=new Map(DOCUMENT_CATALOG.map(d=>[d.id,d]));
export const PRINT_DOMAINS=Array.from(new Map(DOCUMENT_CATALOG.map(d=>[d.domain,{code:d.domain,label:d.domainEn,labelFr:d.domainFr,count:DOCUMENT_CATALOG.filter(t=>t.domain===d.domain).length}])).values());
export const CANONICAL_PRINT_IDS=Array.from({length:18},(_,i)=>"P"+String(i+1).padStart(2,"0"));
export const DOCUMENT_KINDS=Array.from(new Set(DOCUMENT_CATALOG.map(d=>d.kind))).sort();
export function findPrintable(id:string):PrintTemplate|undefined{return DOCUMENT_BY_ID.get(id);}
