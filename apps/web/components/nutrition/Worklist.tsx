"use client";
import Link from "next/link";
import {useState} from "react";
import {call} from "../../lib/nutrition/client";
import type {Page,WorklistRow} from "../../lib/nutrition/client";
import {enumLabel,safetyLabel} from "../../lib/nutrition/i18n";
import type {Key} from "../../lib/nutrition/i18n";
import {Badge,Empty,FailureView,Field,fmtTime,Loading,NutritionFrame,Pager,styles,toneFor,useLoad,useNutrition} from "./ui";

type Filters={q:string;ward:string;mealStatus:string;orderStatus:string;allergy:boolean;fasting:boolean;exception:boolean;
 sort:string;dir:string;size:number};
const INITIAL:Filters={q:"",ward:"",mealStatus:"",orderStatus:"",allergy:false,fasting:false,exception:false,sort:"ward",dir:"asc",size:25};
const SORTS:[string,Key][]=[["ward","wl.sort.ward"],["patient","wl.sort.patient"],["diet","wl.sort.diet"],["meal","wl.sort.meal"],["updated","wl.sort.updated"]];

function Inner(){
 const {t,lang}=useNutrition();
 const [f,setF]=useState<Filters>(INITIAL);
 const [page,setPage]=useState(0);
 const [open,setOpen]=useState<string|null>(null);
 const set=<K extends keyof Filters>(k:K,v:Filters[K])=>{setF(x=>({...x,[k]:v}));setPage(0);};
 const res=useLoad<Page<WorklistRow>>(signal=>call("GET","worklist",{signal,query:{
  q:f.q.trim()||undefined,ward:f.ward.trim().toUpperCase()||undefined,mealStatus:f.mealStatus||undefined,orderStatus:f.orderStatus||undefined,
  allergy:f.allergy||undefined,fasting:f.fasting||undefined,exception:f.exception||undefined,sort:f.sort,dir:f.dir,page,size:f.size}}),
  [f,page]);

 function dietCell(r:WorklistRow){
  if(!r.orderId)return <Badge tone="danger">{t("enum.order.NONE")}</Badge>;
  return <>
   <strong>{enumLabel(lang,"diet",r.dietCategory)}</strong><br/>{enumLabel(lang,"texture",r.texture)}<br/>
   <Badge tone={toneFor(r.orderStatus)}>{enumLabel(lang,"order",r.orderStatus)}</Badge>
  </>;
 }
 function allergyCell(r:WorklistRow){
  const al=r.allergenCodes;
  return <>
   {al===null?<Badge tone="warn">{t("wl.allergyUnknown")}</Badge>
    :al.length===0?<span>{t("wl.noAllergy")}</span>
    :<span className={styles.badges}>{al.map(a=><Badge key={a} tone="danger">{a}</Badge>)}</span>}
   {(r.restrictions??[]).length>0&&<div className={styles.badges} style={{marginTop:6}}>{r.restrictions!.map(x=><Badge key={x} tone="neutral">{x}</Badge>)}</div>}
   {r.allergyVerifiedAt&&<div className={styles.hint}>{t("wl.verified",{time:fmtTime(lang,r.allergyVerifiedAt)})}</div>}
  </>;
 }
 function fastingCell(r:WorklistRow){
  if(!r.fastingReason)return <span>{t("common.no")}</span>;
  return <><Badge tone="warn">{t("safety.FASTING_ACTIVE")}</Badge><div className={styles.hint}>
   {r.fastingUntil?t("wl.fastingUntil",{time:fmtTime(lang,r.fastingUntil)}):t("wl.fastingOpen")}</div></>;
 }
 function mealCell(r:WorklistRow){
  if(!r.mealStatus)return <span>—</span>;
  return <><Badge tone={toneFor(r.mealStatus)}>{enumLabel(lang,"mealStatus",r.mealStatus)}</Badge>
   <div className={styles.hint}>{enumLabel(lang,"meal",r.mealType)} · {fmtTime(lang,r.mealUpdatedAt)}</div></>;
 }
 function exceptionCell(r:WorklistRow){
  if(r.exceptions.length===0)return <span>{t("wl.noException")}</span>;
  return <span className={styles.badges}>{r.exceptions.map(c=><Badge key={c} tone="danger">{safetyLabel(lang,c)}</Badge>)}</span>;
 }
 function openOrder(r:WorklistRow){
  return <Link className={styles.link} href={`/workforce/nutrition/orders?patientRef=${r.patientRef}`}>{t("wl.openOrder")}</Link>;
 }

 return <>
  <section className={styles.panel} aria-label={t("wl.title")}>
   <form className={styles.filters} onSubmit={e=>e.preventDefault()}>
    <Field id="wl-q" label={t("wl.search")}><input id="wl-q" className={styles.input} type="search" value={f.q} maxLength={80} onChange={e=>set("q",e.target.value)}/></Field>
    <Field id="wl-ward" label={t("wl.ward")}><input id="wl-ward" className={styles.input} value={f.ward} maxLength={32} onChange={e=>set("ward",e.target.value)}/></Field>
    <Field id="wl-meal" label={t("wl.mealStatus")}>
     <select id="wl-meal" className={styles.input} value={f.mealStatus} onChange={e=>set("mealStatus",e.target.value)}>
      <option value="">{t("common.any")}</option>
      {["PLANNED","PREPARED","DISPATCHED","DELIVERED","ACKNOWLEDGED","HELD"].map(s=><option key={s} value={s}>{enumLabel(lang,"mealStatus",s)}</option>)}
     </select></Field>
    <Field id="wl-order" label={t("wl.orderStatus")}>
     <select id="wl-order" className={styles.input} value={f.orderStatus} onChange={e=>set("orderStatus",e.target.value)}>
      <option value="">{t("common.any")}</option>
      {["NONE","PRESCRIBED","APPROVED","SUPERSEDED","CANCELLED","REVOKED","EXPIRED"].map(s=><option key={s} value={s}>{enumLabel(lang,"order",s)}</option>)}
     </select></Field>
    <Field id="wl-sort" label={t("wl.sort")}>
     <select id="wl-sort" className={styles.input} value={f.sort} onChange={e=>set("sort",e.target.value)}>
      {SORTS.map(([v,k])=><option key={v} value={v}>{t(k)}</option>)}
     </select></Field>
    <Field id="wl-dir" label={t("wl.dir")}>
     <select id="wl-dir" className={styles.input} value={f.dir} onChange={e=>set("dir",e.target.value)}>
      <option value="asc">{t("wl.asc")}</option><option value="desc">{t("wl.desc")}</option>
     </select></Field>
    <Field id="wl-size" label={t("common.pageSize")}>
     <select id="wl-size" className={styles.input} value={f.size} onChange={e=>set("size",Number(e.target.value))}>
      {[10,25,50,100].map(n=><option key={n} value={n}>{n}</option>)}
     </select></Field>
    <fieldset className={styles.fieldset}>
     <legend>{t("wl.exception")}</legend>
     <div className={styles.checkRow}>
      <label className={styles.check}><input type="checkbox" checked={f.allergy} onChange={e=>set("allergy",e.target.checked)}/>{t("wl.allergy")}</label>
      <label className={styles.check}><input type="checkbox" checked={f.fasting} onChange={e=>set("fasting",e.target.checked)}/>{t("wl.fasting")}</label>
      <label className={styles.check}><input type="checkbox" checked={f.exception} onChange={e=>set("exception",e.target.checked)}/>{t("wl.exception")}</label>
     </div>
    </fieldset>
   </form>
   <div className={styles.actions}>
    <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={()=>{setF(INITIAL);setPage(0);}}>{t("wl.clear")}</button>
    <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={res.reload}>{t("common.refresh")}</button>
   </div>
  </section>

  <section className={styles.panel} aria-label={t("wl.title")} aria-busy={res.phase==="loading"}>
   {res.phase==="loading"&&!res.data&&<Loading/>}
   {res.phase==="failed"&&res.failure&&<FailureView failure={res.failure} onRetry={res.reload}/>}
   {res.data&&res.data.items.length===0&&res.phase!=="failed"&&<Empty/>}
   {res.data&&res.data.items.length>0&&res.phase!=="failed"&&<>
    <div className={styles.tableWrap}>
     <table className={styles.table}>
      <caption className="sr-only" style={{position:"absolute",left:-9999}}>{t("wl.title")}</caption>
      <thead><tr>
       <th scope="col">{t("wl.col.patient")}</th><th scope="col">{t("wl.col.diet")}</th><th scope="col">{t("wl.col.allergies")}</th>
       <th scope="col">{t("wl.col.fasting")}</th><th scope="col">{t("wl.col.meal")}</th><th scope="col">{t("wl.col.exceptions")}</th>
       <th scope="col">{t("common.actions")}</th></tr></thead>
      <tbody>{res.data.items.map(r=><WorklistTableRow key={r.patientRef} r={r} open={open===r.patientRef}
        toggle={()=>setOpen(open===r.patientRef?null:r.patientRef)} cells={{dietCell,allergyCell,fastingCell,mealCell,exceptionCell,openOrder}}/>)}</tbody>
     </table>
    </div>
    <ul className={styles.cards}>
     {res.data.items.map(r=><li key={r.patientRef} className={styles.card}>
      <strong>{r.patientLabel}</strong> <span className={styles.hint}>{r.wardCode} · {r.bedCode}</span>
      <dl><dt>{t("wl.col.diet")}</dt><dd>{dietCell(r)}</dd><dt>{t("wl.col.allergies")}</dt><dd>{allergyCell(r)}</dd>
       <dt>{t("wl.col.fasting")}</dt><dd>{fastingCell(r)}</dd><dt>{t("wl.col.meal")}</dt><dd>{mealCell(r)}</dd>
       <dt>{t("wl.col.exceptions")}</dt><dd>{exceptionCell(r)}</dd></dl>
      <div className={styles.actions}>{openOrder(r)}
       <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} aria-expanded={open===r.patientRef}
        onClick={()=>setOpen(open===r.patientRef?null:r.patientRef)}>{open===r.patientRef?t("common.hideDetails"):t("common.details")}</button></div>
      {open===r.patientRef&&<PatientDetail patientRef={r.patientRef}/>}
     </li>)}
    </ul>
    <Pager page={res.data.page} size={res.data.size} total={res.data.total} onPage={setPage}/>
   </>}
  </section>
 </>;
}

function WorklistTableRow({r,open,toggle,cells}:{r:WorklistRow;open:boolean;toggle:()=>void;cells:{
 dietCell:(r:WorklistRow)=>React.ReactNode;allergyCell:(r:WorklistRow)=>React.ReactNode;fastingCell:(r:WorklistRow)=>React.ReactNode;
 mealCell:(r:WorklistRow)=>React.ReactNode;exceptionCell:(r:WorklistRow)=>React.ReactNode;openOrder:(r:WorklistRow)=>React.ReactNode}}){
 const {t}=useNutrition();
 return <>
  <tr className={open?styles.sel:undefined}>
   <th scope="row"><strong>{r.patientLabel}</strong><div className={styles.hint}>{r.wardCode} · {r.bedCode}</div></th>
   <td>{cells.dietCell(r)}</td><td>{cells.allergyCell(r)}</td><td>{cells.fastingCell(r)}</td><td>{cells.mealCell(r)}</td>
   <td>{cells.exceptionCell(r)}</td>
   <td>{cells.openOrder(r)}<br/><button type="button" className={`${styles.btn} ${styles.btnSecondary}`} style={{marginTop:6}} aria-expanded={open} onClick={toggle}>
    {open?t("common.hideDetails"):t("common.details")}</button></td>
  </tr>
  {open&&<tr><td colSpan={7}><PatientDetail patientRef={r.patientRef}/></td></tr>}
 </>;
}

function PatientDetail({patientRef}:{patientRef:string}){
 const {t,lang}=useNutrition();
 const res=useLoad<any>(signal=>call("GET",`worklist/${patientRef}`,{signal}),[patientRef]);
 if(res.phase==="loading"&&!res.data)return <Loading/>;
 if(res.phase==="failed"&&res.failure)return <FailureView failure={res.failure} onRetry={res.reload}/>;
 const d=res.data;
 return <div className={styles.panel} role="region" aria-label={t("common.details")}>
  <h3>{d.patientLabel} · {d.wardCode} {d.bedCode}</h3>
  <h3>{t("nav.orders")}</h3>
  <ul className={styles.list}>{(d.orders as any[]).slice(0,5).map(o=><li key={o.id} className={styles.card}>
   <Badge tone={toneFor(o.status)}>{enumLabel(lang,"order",o.status)}</Badge> {enumLabel(lang,"diet",o.dietCategory)} · {enumLabel(lang,"texture",o.texture)}
   <div className={styles.hint}>{fmtTime(lang,o.updatedAt)}</div></li>)}</ul>
  <h3>{t("nav.delivery")}</h3>
  <ul className={styles.list}>{(d.meals as any[]).slice(0,6).map(m=><li key={m.id} className={styles.card}>
   {enumLabel(lang,"meal",m.mealType)} · {m.mealDate} · <Badge tone={toneFor(m.status)}>{enumLabel(lang,"mealStatus",m.status)}</Badge>
   {m.holdReason&&<div className={styles.hint}>{safetyLabel(lang,m.holdReason)}</div>}</li>)}</ul>
 </div>;
}

export function Worklist(){
 return <NutritionFrame active="worklist" title="wl.title" intro="wl.intro"><Inner/></NutritionFrame>;
}
