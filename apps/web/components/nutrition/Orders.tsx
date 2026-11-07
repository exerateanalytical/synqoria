"use client";
import {useEffect,useRef,useState} from "react";
import {call,dietProblem,newKey,parseCodes,validCodes} from "../../lib/nutrition/client";
import type {AuditEvent,Failure,Order,Page} from "../../lib/nutrition/client";
import {enumLabel,errorKey,safetyLabel} from "../../lib/nutrition/i18n";
import {ActionButton,ActionError,Badge,Empty,FailureView,Field,fmtTime,Loading,Notice,NutritionFrame,Pager,styles,toneFor,useLoad,useNutrition} from "./ui";

const DIETS=["REGULAR","DIABETIC","RENAL","CARDIAC","LOW_SODIUM","HIGH_PROTEIN","CLEAR_LIQUID","FULL_LIQUID","PUREED","NIL_BY_MOUTH"];
const TEXTURES=["NORMAL","SOFT_BITE_SIZED","MINCED_MOIST","PUREED","LIQUIDISED","NONE"];
const MEALS=["BREAKFAST","LUNCH","DINNER","SNACK"];
const STATUSES=["PRESCRIBED","APPROVED","SUPERSEDED","CANCELLED","REVOKED","EXPIRED"];

type Form={patientRef:string;clinicalOrderRef:string;diet:string;texture:string;restrictions:string;excludedAllergens:string;
 mealTiming:string[];specialInstructions:string;effectiveFrom:string;effectiveTo:string;reason:string};
function localInput(iso?:string|null){
 const d=iso?new Date(iso):new Date();
 const p=(n:number)=>String(n).padStart(2,"0");
 return `${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}
function blank(patient=""):Form{
 return {patientRef:patient,clinicalOrderRef:"",diet:"REGULAR",texture:"NORMAL",restrictions:"",excludedAllergens:"",
  mealTiming:["BREAKFAST","LUNCH","DINNER"],specialInstructions:"",effectiveFrom:localInput(),effectiveTo:"",reason:""};
}
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function Inner(){
 const {t,lang,online}=useNutrition();
 const [status,setStatus]=useState("");
 const [patient,setPatient]=useState("");
 const [page,setPage]=useState(0);
 const [selected,setSelected]=useState<string|null>(null);
 const [mode,setMode]=useState<"new"|"amend">("new");
 const [form,setForm]=useState<Form>(blank());
 const [failure,setFailure]=useState<Failure|null>(null);
 const [done,setDone]=useState<string|null>(null);
 const [busy,setBusy]=useState(false);
 const [pending,setPending]=useState<null|"cancel"|"revoke">(null);
 const [reason,setReason]=useState("");
 const key=useRef(newKey());

 useEffect(()=>{
  const p=new URLSearchParams(window.location.search).get("patientRef")??"";
  if(UUID.test(p)){setPatient(p);setForm(blank(p));}
 },[]);

 const list=useLoad<Page<Order>>(signal=>call("GET","orders",{signal,query:{status:status||undefined,patientRef:UUID.test(patient)?patient:undefined,page,size:25}}),[status,patient,page]);
 const detail=useLoad<Order>(signal=>selected?call("GET",`orders/${selected}`,{signal}):Promise.resolve({ok:false,kind:"notfound",status:0,code:"none",details:[]} as Failure),[selected]);
 const history=useLoad<AuditEvent[]>(signal=>selected?call("GET",`orders/${selected}/history`,{signal}):Promise.resolve({ok:true,status:200,data:[]}),[selected,detail.data?.version]);
 const order=selected?detail.data:undefined;

 const edit=<K extends keyof Form>(k:K,v:Form[K])=>{setForm(f=>({...f,[k]:v}));key.current=newKey();setFailure(null);setDone(null);};
 const restrictions=parseCodes(form.restrictions),excluded=parseCodes(form.excludedAllergens);
 const problem=dietProblem(form.diet,form.texture,form.mealTiming,restrictions);
 const formError:string|null=
  !UUID.test(form.patientRef)?"err.malformed_request":!UUID.test(form.clinicalOrderRef)?"err.malformed_request":
  !validCodes(restrictions,20)||!validCodes(excluded,64)?"err.malformed_request":problem?`err.${problem}`:
  mode==="amend"&&!form.reason.trim()?"err.reason_required":null;

 function body(){
  return {patientRef:form.patientRef,clinicalOrderRef:form.clinicalOrderRef,diet:form.diet,texture:form.texture,restrictions,
   excludedAllergens:excluded,mealTiming:form.diet==="NIL_BY_MOUTH"?[]:form.mealTiming,specialInstructions:form.specialInstructions.trim(),
   effectiveFrom:new Date(form.effectiveFrom).toISOString(),effectiveTo:form.effectiveTo?new Date(form.effectiveTo).toISOString():null};
 }
 async function submit(){
  if(formError||busy)return;
  setBusy(true);setFailure(null);setDone(null);
  const r=mode==="new"
   ?await call<Order>("POST","orders",{body:body(),key:key.current})
   :await call<Order>("POST",`orders/${order!.id}/amend`,{body:{expectedVersion:order!.version,reason:form.reason.trim(),content:body()},key:key.current});
  setBusy(false);
  if(!r.ok){setFailure(r);return;}
  key.current=newKey();setDone(t("or.created"));setSelected(r.data.id);setMode("new");setForm(blank(form.patientRef));list.reload();
 }
 async function act(kind:"approve"|"cancel"|"revoke"){
  if(!order||busy)return;
  if(kind!=="approve"&&!reason.trim()){setFailure({ok:false,kind:"invalid",status:422,code:"reason_required",details:[]});return;}
  setBusy(true);setFailure(null);setDone(null);
  const r=await call<Order>("POST",`orders/${order.id}/${kind}`,{body:{expectedVersion:order.version,reason:reason.trim()||undefined},key:newKey()});
  setBusy(false);
  if(!r.ok){setFailure(r);return;}
  setPending(null);setReason("");setDone(t("or.updated"));detail.reload();list.reload();
 }
 function startAmend(){
  if(!order)return;
  setMode("amend");setFailure(null);setDone(null);key.current=newKey();
  setForm({patientRef:order.patientRef,clinicalOrderRef:order.clinicalOrderRef,diet:order.dietCategory,texture:order.texture,
   restrictions:order.restrictions.join(", "),excludedAllergens:order.excludedAllergens.join(", "),mealTiming:order.mealTiming,
   specialInstructions:order.specialInstructions,effectiveFrom:localInput(),effectiveTo:order.effectiveTo?localInput(order.effectiveTo):"",reason:""});
 }
 const why=(allowed:string[]):string|null=>!order?t("disabled.select"):allowed.includes(order.status)?null:t("disabled.status",{status:enumLabel(lang,"order",order.status)});

 return <div className={styles.cols}>
  <section className={styles.panel} aria-label={t("or.list")}>
   <h2>{t("or.list")}</h2>
   <div className={styles.filters}>
    <Field id="or-status" label={t("or.filterStatus")}>
     <select id="or-status" className={styles.input} value={status} onChange={e=>{setStatus(e.target.value);setPage(0);}}>
      <option value="">{t("common.any")}</option>{STATUSES.map(s=><option key={s} value={s}>{enumLabel(lang,"order",s)}</option>)}</select></Field>
    <Field id="or-patient" label={t("or.filterPatient")}>
     <input id="or-patient" className={`${styles.input} ${styles.mono}`} value={patient} maxLength={36} onChange={e=>{setPatient(e.target.value.trim());setPage(0);}}/></Field>
   </div>
   <div className={styles.actions}><button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={list.reload}>{t("common.refresh")}</button></div>
   <div aria-busy={list.phase==="loading"} style={{marginTop:12}}>
    {list.phase==="loading"&&!list.data&&<Loading/>}
    {list.phase==="failed"&&list.failure&&<FailureView failure={list.failure} onRetry={list.reload}/>}
    {list.data&&list.data.items.length===0&&list.phase!=="failed"&&<Empty/>}
    {list.data&&list.data.items.length>0&&list.phase!=="failed"&&<>
     <ul className={styles.list}>{list.data.items.map(o=><li key={o.id}>
      <button type="button" className={styles.row} aria-pressed={selected===o.id} onClick={()=>{setSelected(o.id);setFailure(null);setPending(null);}}>
       <span><Badge tone={toneFor(o.status)}>{enumLabel(lang,"order",o.status)}</Badge> <strong>{enumLabel(lang,"diet",o.dietCategory)}</strong> · {enumLabel(lang,"texture",o.texture)}</span>
       <span className={`${styles.hint} ${styles.mono}`}>{o.patientRef}</span>
       <span className={styles.hint}>{t("common.updated")} {fmtTime(lang,o.updatedAt)} · v{o.version}</span>
      </button></li>)}</ul>
     <Pager page={list.data.page} size={list.data.size} total={list.data.total} onPage={setPage}/></>}
   </div>
  </section>

  <div>
   <section className={styles.panel} aria-label={t("or.new")}>
    <h2>{mode==="new"?t("or.new"):t("or.amend")}</h2>
    <p className={styles.hint}>{t("or.clinicalOnly")}</p>
    <form onSubmit={e=>{e.preventDefault();void submit();}} noValidate>
     <div className={styles.formGrid}>
      <Field id="of-patient" label={t("or.form.patient")}><input id="of-patient" className={`${styles.input} ${styles.mono}`} value={form.patientRef} disabled={mode==="amend"} onChange={e=>edit("patientRef",e.target.value.trim())}/></Field>
      <Field id="of-ref" label={t("or.form.clinicalRef")}><input id="of-ref" className={`${styles.input} ${styles.mono}`} value={form.clinicalOrderRef} onChange={e=>edit("clinicalOrderRef",e.target.value.trim())}/></Field>
      <Field id="of-diet" label={t("or.form.diet")}><select id="of-diet" className={styles.input} value={form.diet} onChange={e=>edit("diet",e.target.value)}>
       {DIETS.map(d=><option key={d} value={d}>{enumLabel(lang,"diet",d)}</option>)}</select></Field>
      <Field id="of-texture" label={t("or.form.texture")}><select id="of-texture" className={styles.input} value={form.texture} onChange={e=>edit("texture",e.target.value)}>
       {TEXTURES.map(d=><option key={d} value={d}>{enumLabel(lang,"texture",d)}</option>)}</select></Field>
      <Field id="of-restr" label={t("or.form.restrictions")} hint={t("or.form.codesHint")} wide><input id="of-restr" className={styles.input} value={form.restrictions} onChange={e=>edit("restrictions",e.target.value)}/></Field>
      <Field id="of-allergens" label={t("or.form.allergens")} hint={t("or.form.codesHint")} wide><input id="of-allergens" className={styles.input} value={form.excludedAllergens} onChange={e=>edit("excludedAllergens",e.target.value)}/></Field>
      <fieldset className={`${styles.fieldset} ${styles.wide}`}><legend>{t("or.form.timing")}</legend>
       <div className={styles.checkRow}>{MEALS.map(m=><label key={m} className={styles.check}>
        <input type="checkbox" checked={form.mealTiming.includes(m)} disabled={form.diet==="NIL_BY_MOUTH"}
         onChange={e=>edit("mealTiming",e.target.checked?[...form.mealTiming,m]:form.mealTiming.filter(x=>x!==m))}/>{enumLabel(lang,"meal",m)}</label>)}</div></fieldset>
      <Field id="of-from" label={t("or.form.from")}><input id="of-from" type="datetime-local" className={styles.input} value={form.effectiveFrom} onChange={e=>edit("effectiveFrom",e.target.value)}/></Field>
      <Field id="of-to" label={t("or.form.to")}><input id="of-to" type="datetime-local" className={styles.input} value={form.effectiveTo} onChange={e=>edit("effectiveTo",e.target.value)}/></Field>
      <Field id="of-instr" label={t("or.form.instructions")} wide><textarea id="of-instr" className={styles.input} maxLength={500} value={form.specialInstructions} onChange={e=>edit("specialInstructions",e.target.value)}/></Field>
      {mode==="amend"&&<Field id="of-reason" label={t("or.form.reason")} wide><input id="of-reason" className={styles.input} maxLength={300} value={form.reason} onChange={e=>edit("reason",e.target.value)}/></Field>}
     </div>
     {formError&&form.patientRef!==""&&<p className={styles.actionNote} role="status">{t(formError as any)}</p>}
     <div className={styles.actions}>
      <ActionButton label={mode==="new"?t("common.submit"):t("or.amend")} onClick={()=>void submit()} reason={formError?t(formError as any):null} busy={busy}/>
      {mode==="amend"&&<button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={()=>{setMode("new");setForm(blank(form.patientRef));}}>{t("common.cancel")}</button>}
     </div>
    </form>
    <ActionError failure={failure} onReload={()=>{detail.reload();list.reload();setFailure(null);}}/>
    {done&&<Notice>{done}</Notice>}
   </section>

   <section className={styles.panel} aria-label={t("common.details")} aria-live="polite">
    {!selected&&<p>{t("or.selectHint")}</p>}
    {selected&&detail.phase==="loading"&&!order&&<Loading/>}
    {selected&&detail.phase==="failed"&&detail.failure&&<FailureView failure={detail.failure} onRetry={detail.reload}/>}
    {order&&<>
     <h2><Badge tone={toneFor(order.status)}>{enumLabel(lang,"order",order.status)}</Badge> {enumLabel(lang,"diet",order.dietCategory)}</h2>
     <dl className={styles.kv}>
      <dt>{t("or.form.texture")}</dt><dd>{enumLabel(lang,"texture",order.texture)}</dd>
      <dt>{t("or.field.restrictions")}</dt><dd>{order.restrictions.join(", ")||t("common.none")}</dd>
      <dt>{t("or.field.excluded")}</dt><dd>{order.excludedAllergens.join(", ")||t("common.none")}</dd>
      <dt>{t("or.field.timing")}</dt><dd>{order.mealTiming.map(m=>enumLabel(lang,"meal",m)).join(", ")||t("common.none")}</dd>
      <dt>{t("or.field.instructions")}</dt><dd>{order.specialInstructions||t("common.none")}</dd>
      <dt>{t("or.field.effective")}</dt><dd>{fmtTime(lang,order.effectiveFrom)} → {order.effectiveTo?fmtTime(lang,order.effectiveTo):"∞"}</dd>
      <dt>{t("or.field.prescriber")}</dt><dd className={styles.mono}>{order.prescriberId}</dd>
      <dt>{t("or.field.approver")}</dt><dd className={styles.mono}>{order.approverId??"—"}</dd>
      <dt>{t("or.field.allergyVersion")}</dt><dd>{order.reconciledAllergyVersion??"—"}</dd>
      {order.supersedesId&&<><dt>{t("or.field.supersedes")}</dt><dd className={styles.mono}>{order.supersedesId}</dd></>}
      {order.statusReason&&<><dt>{t("common.reason")}</dt><dd>{order.statusReason}</dd></>}
      <dt>{t("common.version")}</dt><dd>{order.version}</dd>
     </dl>
     <div className={styles.actions}>
      <ActionButton label={t("or.approve")} onClick={()=>void act("approve")} reason={why(["PRESCRIBED"])} busy={busy}/>
      <ActionButton label={t("or.amend")} tone="secondary" onClick={startAmend} reason={why(["APPROVED"])}/>
      <ActionButton label={t("or.cancel")} tone="secondary" onClick={()=>{setPending("cancel");setReason("");}} reason={why(["PRESCRIBED","APPROVED"])}/>
      <ActionButton label={t("or.revoke")} tone="danger" onClick={()=>{setPending("revoke");setReason("");}} reason={why(["APPROVED"])}/>
     </div>
     {pending&&<form className={styles.formGrid} style={{marginTop:12}} onSubmit={e=>{e.preventDefault();void act(pending);}}>
      <Field id="or-reason" label={t("or.reasonPrompt")} wide><input id="or-reason" className={styles.input} autoFocus maxLength={300} value={reason} onChange={e=>setReason(e.target.value)}/></Field>
      <div className={styles.actions}>
       <ActionButton label={t("or.confirm")} tone={pending==="revoke"?"danger":"primary"} onClick={()=>void act(pending)} reason={reason.trim()?null:t(errorKey("reason_required"))} busy={busy}/>
       <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={()=>setPending(null)}>{t("common.cancel")}</button></div>
     </form>}
     <h3>{t("or.history")}</h3>
     {history.data&&history.data.length===0&&<p>{t("or.noHistory")}</p>}
     {history.data&&history.data.length>0&&<ol className={styles.timeline}>{history.data.map(h=><li key={h.eventId}>
      <strong>{h.action}</strong> {h.fromStatus?`${h.fromStatus} → `:""}{h.toStatus} · v{h.aggregateVersion}
      <div className={styles.hint}>{fmtTime(lang,h.occurredAt)} · <span className={styles.mono}>{h.actorId}</span></div></li>)}</ol>}
    </>}
   </section>
  </div>
 </div>;
}
export function Orders(){return <NutritionFrame active="orders" title="or.title" intro="or.intro"><Inner/></NutritionFrame>;}
