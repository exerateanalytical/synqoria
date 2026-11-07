"use client";
import {useState} from "react";
import Link from "next/link";
import type {PrintTemplate} from "./registry";
import {profileFor,isSafetyCritical} from "./print-profiles";
import "./printable.css";

type Locale="en"|"fr";
const ui={
 en:{
 sample:"SAMPLE · NOT VALID",notIssued:"Not issued / no clinical or financial authority",preview:"Blank template proof",
 facility:"Facility / legal issuer",location:"Address / telephone",patient:"Patient / subject",name:"Full legal name",identifier:"Local patient identifier",second:"Second approved identifier",birth:"Birth date or recorded estimated age",encounter:"Encounter / stay",decedent:"Decedent identity",billing:"Billing account / payer",department:"Department / cost centre",record:"Authorized source record",period:"Reporting period",role:"Responsible department",issuer:"Approved issuer",date:"Issue date / time",version:"Document/template version",status:"Status",source:"Source reference",
 noValue:"",review:"Independent review",signatures:"Authorization / signature evidence",signed:"Signature not captured",title:"Template document",columns:"Tabular entries",choose:"Choose language",patientWarning:"Patient identity and allergy/encounter context must originate from the permitted authoritative system.",notAForm:"Blank form only. Do not enter real patient, staff, supplier or payment details into this preview.",fieldWarning:"Clinical thresholds, dose units, reference intervals, fiscal rules and mandatory fields require review before real issuance.",context:"Document context",legal:"Consent/authorization warning",consentText:"No consent is given or recorded by this example. Institution-approved procedure explanation, alternatives, material risks, capacity and voluntary choice are required.",yes:"Agreed / accepted",no:"Declined / refused",notEvaluated:"Undetermined / not recorded",money:"Ledger reconciliation",moneyText:"No prices, taxes, totals, receipt amounts or payment confirmations are calculated or represented by this example.",labelText:"LABEL PROOF ONLY — No scanner-readable barcode, no verified printer media or issued patient label.",tableEmpty:"Blank line · no patient/financial value supplied",notSuitable:"Not valid for patient care, medication dispensing, billing, consent, identification, release, audit or legal certification.",print:"Print marked blank sample",back:"All document templates",template:"Template code",sheet:"Paper proof",signerNote:"An empty signature line is not a valid electronic or legal signature.",sourceNote:"All sections are proposed; local policy, bilingual terminology and qualified domain review remain pending.",generated:"Prototype template, no issuance date",statusChoice:"No choice made",copy:"Copy template ID",printHelp:"Only the watermarked blank sample is printable. Clinical/financial issuance is disabled."
 },
 fr:{
 sample:"MODÈLE · NON VALIDE",notIssued:"Non délivré / aucune autorisation clinique ou financière",preview:"Épreuve de formulaire vierge",
 facility:"Établissement / émetteur légal",location:"Adresse / téléphone",patient:"Patient / sujet",name:"Nom légal",identifier:"Identifiant local du patient",second:"Deuxième identifiant approuvé",birth:"Naissance ou âge estimé déclaré",encounter:"Épisode de soins / séjour",decedent:"Identité du défunt",billing:"Compte de facturation / payeur",department:"Service / centre de coûts",record:"Document source autorisé",period:"Période",role:"Service responsable",issuer:"Émetteur autorisé",date:"Date et heure d'émission",version:"Version du modèle/document",status:"Statut",source:"Référence de source",
 noValue:"",review:"Contrôle indépendant",signatures:"Autorisation / preuve de signature",signed:"Aucune signature recueillie",title:"Modèle de document",columns:"Éléments tabulaires",choose:"Choisir la langue",patientWarning:"L'identité et le contexte clinique doivent provenir du système autorisé.",notAForm:"Formulaire vierge uniquement. Ne saisissez aucune donnée réelle du patient, personnel, fournisseur ou paiement.",fieldWarning:"Seuils cliniques, unités, intervalles de référence, fiscalité et champs obligatoires nécessitent validation.",context:"Contexte du document",legal:"Avertissement de consentement",consentText:"Cet exemple ne recueille aucun consentement. Une information approuvée sur l'acte, les risques, les alternatives, la capacité et le libre choix est nécessaire.",yes:"Accepté",no:"Refusé",notEvaluated:"Indéterminé",money:"Rapprochement comptable",moneyText:"Aucun prix, taxe, total ou paiement n'est calculé ni certifié par cet exemple.",labelText:"ÉPREUVE D'ÉTIQUETTE UNIQUEMENT — Aucun code-barres utilisable ni support imprimante validé.",tableEmpty:"Ligne vierge sans données réelles",notSuitable:"Non valable pour soins, médicaments, facturation, consentement, identification, délivrance, audit ou certification légale.",print:"Imprimer le modèle vierge marqué",back:"Tous les modèles",template:"Code du modèle",sheet:"Épreuve papier",signerNote:"Un trait de signature ne constitue pas une signature électronique ou légale valide.",sourceNote:"Les champs spécialisés restent en anglais jusqu'à une traduction et validation locale approuvées.",generated:"Maquette sans date de délivrance",statusChoice:"Aucun choix",copy:"Copier le code",printHelp:"Seul le modèle vierge marqué est imprimable. La délivrance clinique et financière reste bloquée."
 }
};
const sectionFr:Record<string,string>={
 "Identity":"Identité","Verification":"Vérification","Appointment":"Rendez-vous","Preparation":"Préparation","Encounter":"Épisode de soins","Clinical course":"Parcours clinique","Follow-up":"Suivi","Order":"Prescription / demande","Specimen":"Prélèvement","Analysis":"Analyse","Results":"Résultats","Release":"Libération","Payment":"Paiement","Transaction":"Transaction","Approval":"Approbation","Signatures":"Signatures","Decision":"Décision","Discussion":"Échange","Safety":"Sécurité","Patient":"Patient","Procedure":"Procédure","Plan":"Plan","Examination":"Examen","Assessment":"Évaluation","History":"Antécédents","Findings":"Constatations","Outcome":"Résultat","Status":"Statut","Authority":"Autorité","Record":"Dossier","Current":"Valeur actuelle","Transfer":"Transfert","Custody":"Garde","Consent":"Consentement","Report":"Rapport","Author":"Auteur","Work":"Travail","Quality":"Qualité","Documentation":"Documentation","Review":"Révision","Financial":"Financier","Charges":"Prestations","Issuer":"Émetteur","Recipient":"Destinataire","Scope":"Périmètre","Context":"Contexte","Risk":"Risque","Monitoring":"Surveillance","Care":"Soins","Data":"Données","Compliance":"Conformité","Source":"Source"
};
const colFr:Record<string,string>={
 "Analyte / test code":"Analyte / code","Observed result":"Résultat","Unit":"Unité","Reference interval and source":"Intervalle de référence / source","Flag / interpretation":"Alerte / interprétation","Method":"Méthode",
 "Service / product code":"Code prestation / produit","Description":"Description","Quantity":"Quantité","Unit price XAF":"Prix unitaire XAF","Tax category":"Catégorie fiscale","Line total XAF":"Montant de ligne XAF",
 "Medication / code":"Médicament / code","Strength / form":"Dosage / forme","Dose + unit":"Dose + unité","Route":"Voie","Frequency":"Fréquence","Duration":"Durée","Qty / refills":"Quantité / renouvellements"
};
function isTableKind(kind:string,cols:readonly string[]){return cols.length>=3&&kind!=="survey";}
function contextFields(context:string,t:typeof ui.en){
 const common=[[t.facility,""],[t.issuer,""],[t.location,""]];
 if(context==="patient")return [...common,[t.name,""],[t.identifier,""],[t.second,""],[t.birth,""],[t.encounter,""]];
 if(context==="decedent")return [...common,[t.decedent,""],[t.identifier,""],[t.record,""]];
 if(context==="financial")return [...common,[t.billing,""],[t.record,""],[t.period,""]];
 return [...common,[t.department,""],[t.period,""],[t.record,""]];
}
function BlankFields({section,kind,locale}:{section:PrintTemplate["sections"][number];kind:string;locale:Locale}){
 if(kind==="checklist"||kind==="screening")return <div className="syn-doc-check-list">{section.fields.map((f,i)=><div key={i} className="syn-doc-check-row"><span>{f}</span><span className="syn-doc-choice-grid"><span>□ Yes</span><span>□ No</span><span>□ N/A</span></span></div>)}</div>;
 return <div className="syn-doc-fields">{section.fields.map((field,i)=><div className={"syn-doc-field"+(/reason|findings|assessment|history|summary|description|plan|narrative|interpretation|risks|benefits|alternatives|instructions|outcome|disclosure|evidence|analysis|procedure|clinical|critical/i.test(field)?" is-narrative":"")} key={i}><span>{field}</span><div className="syn-doc-blank-line" aria-label={locale==="fr"?"Espace vide":"Blank field"} /></div>)}</div>;
}
function BlankTable({columns,rows,locale}:{columns:readonly string[];rows:number;locale:Locale}){
 return <div className="syn-doc-table-scroll"><table><caption>{locale==="fr"?"Tableau vierge — aucune donnée réelle":"Blank table — no real values"}</caption><thead><tr>{columns.map((h,i)=><th scope="col" key={i}>{locale==="fr"?colFr[h]??h:h}</th>)}</tr></thead><tbody>{Array.from({length:rows},(_,r)=><tr key={r}>{columns.map((_,c)=><td key={c}><span className="syn-doc-cell-blank" aria-hidden="true"> </span></td>)}</tr>)}</tbody></table></div>;
}
export default function PrintablePreview({template}:{template:PrintTemplate}){
 const [locale,setLocale]=useState<Locale>("en");
 const t=ui[locale],p=profileFor(template);
 const financial=["invoice","receipt","estimate","payslip","financial-statement","reconciliation","claim","purchase-order","goods-receipt"].includes(template.kind);
 const consent=template.kind==="consent";
 const paperName={"a4-portrait":"A4 portrait","a4-landscape":"A4 landscape","a5-proof":"A5 proof","label-proof":"A4 label proof","receipt-proof":"80mm thermal proof"}[p.media];
 const printSample=()=>window.print();
 return <div className="syn-doc-view" data-print-code={template.id}>
  <nav className="syn-doc-toolbar" aria-label="Document actions"><Link href="/ui/printables">← {t.back}</Link><span className="syn-doc-toolbar-title">{t.template}: <strong>{template.id}</strong></span><fieldset><legend>{t.choose}</legend><button type="button" aria-pressed={locale==="en"} onClick={()=>setLocale("en")}>English</button><button type="button" aria-pressed={locale==="fr"} onClick={()=>setLocale("fr")}>Français</button></fieldset><button type="button" className="syn-doc-print-button" onClick={printSample}>Print marked blank sample</button></nav>
  <aside className="syn-doc-screen-warning" role="status"><strong>{t.notIssued}</strong><p>{t.notAForm} {t.printHelp}</p></aside>
  <div className="syn-doc-paper-wrap"><article className="syn-doc-sheet" data-media={p.media} aria-label={locale==="fr"?template.fr:template.en}>
   <header className="syn-doc-letterhead"><div className="syn-doc-brand"><strong>SYNQORIA</strong><small>Africa medical intelligence synchronized.</small><span>{t.facility}: —</span><span>{t.location}: —</span></div><div className="syn-doc-head-meta"><strong>{t.sample}</strong><span>{t.template}: {template.id}</span><span>{t.version}: TEMPLATE 0.1</span><span>{t.status}: {t.notIssued}</span></div></header>
   <section className="syn-doc-heading"><span className="syn-doc-category">{locale==="fr"?template.domainFr:template.domainEn} · {template.module}</span><h1>{locale==="fr"?template.fr:template.en}</h1><p>{locale==="fr"?template.en:template.fr}</p><span className="syn-doc-status-badge">{t.sample}</span></section>
   <section className="syn-doc-context" aria-label={t.context}><h2>{t.context}</h2><dl>{contextFields(template.context,t).map(([label],i)=><div key={i}><dt>{label}</dt><dd aria-label={t.noValue||"Blank"}>____________________</dd></div>)}</dl><p className="syn-doc-proof-hint">{t.patientWarning}</p></section>
   {template.sections.map(section=><section className="syn-doc-content-section" id={section.id} key={section.id}>
    <h2>{locale==="fr"?sectionFr[section.heading]??section.heading:section.heading}</h2><BlankFields section={section} kind={template.kind} locale={locale}/>
   </section>)}
   {isTableKind(template.kind,p.columns)&&<section className="syn-doc-content-section syn-doc-record-table"><h2>{t.columns}</h2><BlankTable columns={p.columns} rows={["lab-result","prescription","mar","invoice","anesthesia","transfusion"].includes(template.kind)?5:3} locale={locale}/></section>}
   {financial&&<section className="syn-doc-content-section syn-doc-finance"><h2>{t.money}</h2><div className="syn-doc-money-grid">{["Subtotal / Sous-total XAF","Taxes / Taxes XAF","Discount / Remise XAF","Amount due / Montant dû XAF","Ledger source / Référence comptable"].map(x=><div key={x}><span>{x}</span><span className="syn-doc-missing">—</span></div>)}</div><p>{t.moneyText}</p></section>}
   {consent&&<section className="syn-doc-content-section syn-doc-consent"><h2>{t.legal}</h2><p>{t.consentText}</p><div className="syn-doc-consent-options"><span>□ {t.yes}</span><span>□ {t.no}</span><span>□ {t.notEvaluated}</span></div><p>{t.statusChoice}</p></section>}
   {p.media==="label-proof"&&<div className="syn-doc-notice-strong" role="note">{t.labelText}</div>}
   <section className="syn-doc-content-section syn-doc-signatures"><h2>{t.signatures}</h2><div className="syn-doc-signature-grid">{p.signers.map((role,i)=><div className="syn-doc-signer" key={i}><strong>{role}</strong><span className="syn-doc-signature-line" aria-hidden="true"/><small>{t.signed}</small><span className="syn-doc-date-line">Date/time / Date/heure: ______________</span></div>)}</div><p>{t.signerNote}</p></section>
   <section className="syn-doc-release-notice"><strong>{t.notSuitable}</strong><p>{p.safetyText}</p><p>{t.fieldWarning} {t.sourceNote}</p></section>
   <footer className="syn-doc-footer"><span>{template.id} · SYNQORIA · TEMPLATE 0.1</span><span>{t.sample}</span><span>{t.generated}</span></footer>
  </article></div>
  <p className="syn-doc-after-note">Document <code>{template.id}</code> is a structured blank sample. Printing it does not issue, sign, verify, audit or authorize any transaction.</p>
 </div>;
}
