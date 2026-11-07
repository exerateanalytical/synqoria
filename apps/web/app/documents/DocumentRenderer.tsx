"use client";
import {useState} from "react";
import Link from "next/link";
import type {ReactNode} from "react";
import {DOCUMENT_RELEASE} from "./document-runtime.mjs";
import "./document-print.css";

export type DocumentField={id:string;label:string;type:"table"|"text"|"identifier"|"money"|"date"|"datetime"|"measurement"|"textarea"|"tel"|"email";required:string;source:string;recordValue:boolean;columns?:string[]|null};
export type DocumentSection={id:string;title:string;fields:DocumentField[]};
export type DocumentTemplate={
 id:string;module:string;title:string;media:string;canonicalPrintCode:string|null;groupKeys:string[];audience:string;
 mediaPolicy:string;release:{mode:string;clinical:string;legal:string;signatures:string;retention:string;authorization:string;dataSource:string};
 language:{primary:string;secondary:string;translationStatus:string};
 sections:DocumentSection[];
};

const normalize=(s:string)=>s.replace(/([A-Z])/g," $1").replace(/^./,x=>x.toUpperCase());
const requiredLabels:Record<string,string>={a4:"A4 form",a5:"A5 intended (A4 sample)",receipt:"Thermal receipt intended (A4 sample)",label:"Printer label intended (A4 sample)",card:"Identity card intended (A4 sample)"};
function DocumentCell({field}:{field:DocumentField}):ReactNode{
 if(field.type==="table"){
  const columns=field.columns??[];
  if(columns.length>6)return <div className="syn-doc-complex-lineitems" role="group" aria-label={field.label+" blank item fields"}>
    <p>{field.label} — unissued blank line item</p>
    {[0].map(row=><section key={row} className="syn-doc-lineitem-card" aria-label={"Blank line item "+(row+1)}>
      {columns.map((column,i)=><div key={i}><strong>{column}</strong><span>—</span></div>)}
    </section>)}
    <small>The full medication/dose line must remain together; validated source values and units are required.</small>
   </div>;
  return <div className="syn-doc-table-wrap"><table className="syn-doc-table"><caption>{field.label} — blank structured table</caption><thead><tr>{columns.map((col,i)=><th scope="col" key={i}>{col}</th>)}</tr></thead><tbody>{[0,1].map(i=><tr key={i}>{columns.map((_,j)=><td key={j}><span className="syn-doc-empty-cell" aria-label="Blank field">—</span></td>)}</tr>)}</tbody></table></div>;
 }
 if(field.type==="textarea")return <div className="syn-doc-blank-multiline" aria-label={"Blank field: "+field.label}><span>Not entered — approved content required</span><span/><span/></div>;
 return <div className="syn-doc-blank" aria-label={"Blank field: "+field.label}><span>—</span>{field.type==="money"&&<small>Currency / tax rules: pending configuration</small>}{field.type==="measurement"&&<small>Unit and clinical validity: pending approval</small>}</div>;
}
function FormSections({template}:{template:DocumentTemplate}){
 return <div className="syn-doc-sections">{template.sections.map((section,i)=><section className="syn-doc-section" key={section.id} id={"doc-section-"+section.id} aria-labelledby={"doc-section-heading-"+i}>
  <h2 id={"doc-section-heading-"+i}><span>{String(i+1).padStart(2,"0")}</span>{normalize(section.title)}</h2>
  <div className="syn-doc-field-grid">{section.fields.map(field=><div key={field.id} data-document-field={field.id} className={"syn-doc-field"+(field.type==="table"||field.type==="textarea"?" is-wide":"")}><div className="syn-doc-field-label">{field.label}</div><DocumentCell field={field}/></div>)}</div>
 </section>)}</div>;
}
function SafetyNotice({template}:{template:DocumentTemplate}){
 const consent=template.groupKeys.includes("consent");
 const lab=template.groupKeys.includes("labResults")||template.groupKeys.includes("microbiology")||template.groupKeys.includes("pathology");
 const prescription=template.groupKeys.includes("prescription")||template.groupKeys.includes("medAdministration");
 const billing=template.groupKeys.includes("financeLines")||template.groupKeys.includes("payment");
 return <div className="syn-doc-safety">
  <strong>SAMPLE TEMPLATE — NOT ISSUED OR VALID</strong>
  <p>This is an intentionally blank, unauthenticated design specimen. No values, author signatures, medical orders, test results, payments or consent have been recorded. Do not use for real patient care or financial transactions.</p>
  {consent&&<p><strong>Consent safeguard:</strong> The procedure-specific information, material risks, benefits, alternatives, capacity, interpreter, refusal rights and legal wording must be approved by the facility and discussed by an authorized practitioner. This empty form does not establish consent.</p>}
  {lab&&<p><strong>Diagnostic safeguard:</strong> Units, methods, specimen source, reference intervals, result flags, critical value handling and licensed release/signature must be verified by the responsible laboratory before any result is issued.</p>}
  {prescription&&<p><strong>Medication safeguard:</strong> Medication details, dosing, route, frequency, duration, allergy screening, clinical checks and prescriber authorization must come from current reviewed clinical records. Blank lines are not orders.</p>}
  {billing&&<p><strong>Financial safeguard:</strong> Prices, taxes, legal numbering, settlement status and currency must come from authorized finance services and locally reviewed fiscal rules. This specimen is not a receipt or tax invoice.</p>}
 </div>;
}
export function DocumentRenderer({template}:{template:DocumentTemplate}){
 const [expanded,setExpanded]=useState(false);
 return <div className="syn-doc-app">
  <a href="#syn-doc-paper" className="syn-doc-skip">Skip to document</a>
  <div className="syn-doc-toolbar" aria-label="Template preview controls">
   <div><Link href="/documents">All document templates</Link><span aria-hidden="true"> / </span><strong>{template.id}</strong></div>
   <div className="syn-doc-toolbar-actions">
    <button type="button" onClick={()=>setExpanded(v=>!v)} aria-expanded={expanded}>{expanded?"Hide specification":"Template specification"}</button>
    <button type="button" className="syn-doc-print-btn" onClick={()=>window.print()}>Print blank SAMPLE / Save PDF</button>
   </div>
  </div>
  {expanded&&<section className="syn-doc-meta" aria-label="Template status and source">
   <h2>Template specification</h2><dl>
    <div><dt>Template ID</dt><dd>{template.id}</dd></div>
    <div><dt>Module / owner</dt><dd>{template.module} · policy owner pending assignment</dd></div>
    <div><dt>Intended media</dt><dd>{requiredLabels[template.media]??template.media}</dd></div>
    <div><dt>Media calibration</dt><dd>{template.mediaPolicy}</dd></div>
    <div><dt>Language</dt><dd>English prototype · French translation approval pending</dd></div>
    <div><dt>Generation rights</dt><dd>{template.release.mode} · real issuance prohibited</dd></div>
    <div><dt>Retention and disclosure</dt><dd>{template.release.retention} · {template.release.authorization}</dd></div>
   </dl>
   <p>Digital signatures, barcodes and QR authenticity certificates are intentionally absent until validated identity, licensed workflow, immutable source version and controlled verification URL are available.</p>
  </section>}
  <main id="syn-doc-paper" className="syn-doc-paper" data-template-id={template.id} data-intended-media={template.media} aria-label={template.title+" blank printable sample"}>
   <header className="syn-doc-letterhead">
    <div className="syn-doc-brand"><strong>SYNQORIA</strong><small>Africa medical intelligence synchronized.</small></div>
    <div className="syn-doc-facility">
     <strong>[Hospital / Facility — Not configured]</strong>
     <span>[Address · Telephone · Registration/licence reference]</span>
     <span>[Facility service / approved issuer]</span>
    </div>
   </header>
   <div className="syn-doc-sample-ribbon">SAMPLE · UNISSUED · NO PATIENT DATA · NOT VALID FOR CLINICAL OR FINANCIAL USE</div>
   <div className="syn-doc-heading">
    <div><p className="syn-doc-kicker">{template.module} · {template.canonicalPrintCode?"Canonical output":"Supplementary candidate"} · {template.id}</p><h1>{template.title}</h1><p>Structured blank template for hospital review and printer testing.</p></div>
    <div className="syn-doc-issue-box">
     <div><span>Status</span><strong>UNISSUED SAMPLE</strong></div>
     <div><span>Document No.</span><strong>Not assigned</strong></div>
     <div><span>Version</span><strong>Template v1 / Source pending</strong></div>
     <div><span>Issue time</span><strong>Not issued</strong></div>
    </div>
   </div>
   <SafetyNotice template={template}/>
   <FormSections template={template}/>
   <footer className="syn-doc-sheet-footer">
    <div className="syn-doc-authority"><strong>Verified sign-off</strong><p>Not signed. Author, credentials, organization, effective source revision and cryptographic evidence must be provided by an authorized backend service.</p></div>
    <div className="syn-doc-footer-row"><span>SYNQORIA · {template.id} · SAMPLE ONLY</span><span>Retention: facility policy pending</span><span>Printable sample, not an issued document</span></div>
   </footer>
  </main>
  <nav className="syn-doc-outside-nav" aria-label="Document navigation"><Link href="/documents">Back to full library</Link><Link href="/ui/catalog/components">Shared components</Link><Link href="/ui/catalog/workflows">Hospital workflow maps</Link></nav>
 </div>;
}
