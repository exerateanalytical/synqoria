"use client";
import {useRef, useState} from "react";
import "./interaction.css";
type Props={components:string[];dialogs:string[];printables:string[]};
export function ComponentGallery({components,dialogs,printables}:Props){
 const ref=useRef<HTMLDialogElement>(null);
 const [selected,setSelected]=useState<number|null>(null);
 const [tab,setTab]=useState<"components"|"dialogs"|"printables">("components");
 function open(i:number){setSelected(i);ref.current?.showModal();}
 function close(){ref.current?.close();setSelected(null);}
 return <div className="syn-ui-preview syn-gallery">
  <div className="syn-real-tabs" role="group" aria-label="Specimen categories">
   <button type="button" aria-pressed={tab==="components"} onClick={()=>setTab("components")}>26 shared components</button>
   <button type="button" aria-pressed={tab==="dialogs"} onClick={()=>setTab("dialogs")}>12 dialog variants</button>
   <button type="button" aria-pressed={tab==="printables"} onClick={()=>setTab("printables")}>18 print outputs</button>
  </div>
  {tab==="components"&&<section aria-label="Reusable component register"><p>Catalogue of required components. An entry represents a design contract, not a fully integrated implementation.</p><div className="syn-component-cards">{components.map((label,i)=><div key={label} className="syn-component-entry"><strong>C{String(i+1).padStart(2,"0")} · {label}</strong><span>Specification registered · component acceptance pending</span></div>)}</div></section>}
  {tab==="dialogs"&&<section aria-label="Dialog previews"><p>Select a dialog to verify accessible opening, closing, heading, action order and exit. No clinical action is performed.</p><div className="syn-component-cards">{dialogs.map((label,i)=><button type="button" key={label} className="syn-preview-secondary syn-gallery-button" onClick={()=>open(i)}>D{String(i+1).padStart(2,"0")} · {label}</button>)}</div></section>}
  {tab==="printables"&&<section aria-label="Print outputs"><p>The 18 canonical print contracts now have dedicated blank sample templates in the wider document library. Issuance, electronic signatures, live patient information and production printer calibration remain locked pending approval.</p><p><a href="/ui/printables">Open the hospital printable document library</a></p><div className="syn-component-cards">{printables.map((label,i)=><div key={label} className="syn-component-entry"><strong>P{String(i+1).padStart(2,"0")} · {label}</strong><div className="syn-preview-paper"><p>SYNQORIA · DOCUMENT PREVIEW ONLY</p><p>{label}</p><p>NO PATIENT DATA · NOT VALID FOR CLINICAL USE</p></div><button type="button" disabled>Print or export (locked)</button></div>)}</div></section>}
  <dialog ref={ref} className="syn-preview-dialog" aria-labelledby="syn-dialog-heading" onClose={()=>setSelected(null)} onCancel={()=>setSelected(null)}>
   <form method="dialog"><h2 id="syn-dialog-heading">{selected===null?"":`D${String(selected+1).padStart(2,"0")} · ${dialogs[selected]}`}</h2><p>This dialog is a visual and keyboard specimen. It cannot authorize, confirm, discard or modify clinical records. Real wording and permission preconditions require review.</p><div className="syn-dialog-actions"><button type="submit" className="syn-preview-secondary" onClick={close}>Close preview</button><button type="button" disabled>Clinical action locked</button></div></form>
  </dialog>
 </div>;
}
