"use client";
import {useRef,useState} from "react";
import {call,newKey,parseCodes,validCodes} from "../../lib/nutrition/client";
import type {Batch,Failure,Page} from "../../lib/nutrition/client";
import {enumLabel,safetyLabel} from "../../lib/nutrition/i18n";
import {ActionButton,ActionError,Badge,Empty,FailureView,Field,fmtTime,Loading,Notice,NutritionFrame,Pager,styles,toneFor,useLoad,useNutrition} from "./ui";

const MEALS=["BREAKFAST","LUNCH","DINNER","SNACK"];
const today=()=>new Date().toISOString().slice(0,10);
function localInput(d:Date){const p=(n:number)=>String(n).padStart(2,"0");return `${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;}

function Inner(){
 const {t,lang}=useNutrition();
 const [page,setPage]=useState(0);
 const [ward,setWard]=useState("");
 const [status,setStatus]=useState("");
 const [selected,setSelected]=useState<string|null>(null);
 const [form,setForm]=useState({ward:"",date:today(),meal:"LUNCH",cutoff:localInput(new Date(Date.now()+4*3600*1000))});
 const [failure,setFailure]=useState<Failure|null>(null);
 const [done,setDone]=useState<string|null>(null);
 const [busy,setBusy]=useState(false);
 const [sub,setSub]=useState({meal:"",original:"",substitute:"",allergens:"",reason:""});
 const createKey=useRef(newKey());
 const subKey=useRef(newKey());

 const list=useLoad<Page<Batch>>(signal=>call("GET","batches",{signal,query:{ward:ward.trim().toUpperCase()||undefined,status:status||undefined,page,size:25}}),[ward,status,page]);
 const detail=useLoad<Batch>(signal=>selected?call("GET",`batches/${selected}`,{signal}):Promise.resolve({ok:false,kind:"notfound",status:0,code:"none",details:[]} as Failure),[selected]);
 const b=selected?detail.data:undefined;

 const formWard=form.ward.trim().toUpperCase();
 const createReason=!/^[A-Z0-9_-]{1,32}$/.test(formWard)?t("err.malformed_request"):!form.date||!form.cutoff?t("err.malformed_request"):null;
 const editForm=(patch:Partial<typeof form>)=>{setForm(f=>({...f,...patch}));createKey.current=newKey();setFailure(null);};

 async function create(){
  if(createReason||busy)return;
  setBusy(true);setFailure(null);setDone(null);
  const r=await call<Batch>("POST","batches",{key:createKey.current,body:{wardCode:formWard,mealDate:form.date,mealType:form.meal,cutoffAt:new Date(form.cutoff).toISOString()}});
  setBusy(false);
  if(!r.ok){setFailure(r);return;}
  createKey.current=newKey();setDone(t("pr.done"));setSelected(r.data.id);list.reload();
 }
 async function step(path:string){
  if(!b||busy)return;
  setBusy(true);setFailure(null);setDone(null);
  const r=await call<Batch>("POST",`batches/${b.id}/${path}`,{body:{expectedVersion:b.version},key:newKey()});
  setBusy(false);
  if(!r.ok){setFailure(r);detail.reload();return;}
  setDone(t("pr.done"));detail.reload();list.reload();
 }
 async function propose(){
  if(busy)return;
  setBusy(true);setFailure(null);setDone(null);
  const r=await call("POST","substitutions",{key:subKey.current,body:{mealServiceId:sub.meal,originalItemCode:sub.original.trim().toUpperCase(),
   substituteItemCode:sub.substitute.trim().toUpperCase(),substituteAllergens:parseCodes(sub.allergens),reasonCode:sub.reason.trim().toUpperCase()}});
  setBusy(false);
  if(!r.ok){setFailure(r);return;}
  subKey.current=newKey();setSub({meal:"",original:"",substitute:"",allergens:"",reason:""});setDone(t("pr.done"));detail.reload();
 }
 async function decide(id:string,version:number,approve:boolean){
  if(busy)return;
  setBusy(true);setFailure(null);setDone(null);
  const r=await call("POST",`substitutions/${id}/decision`,{body:{expectedVersion:version,approve},key:newKey()});
  setBusy(false);
  if(!r.ok){setFailure(r);detail.reload();return;}
  setDone(t("pr.done"));detail.reload();
 }
 const need=(allowed:string[]):string|null=>!b?t("disabled.select"):allowed.includes(b.status)?null:t("disabled.status",{status:enumLabel(lang,"batch",b.status)});
 const subAllergens=parseCodes(sub.allergens);
 const subReason=!sub.meal?t("disabled.select"):![sub.original,sub.substitute,sub.reason].every(v=>/^[A-Z0-9_]{2,40}$/.test(v.trim().toUpperCase()))||!validCodes(subAllergens,64)?t("err.malformed_request"):null;
 const canSubstitute=!!b&&["DRAFT","RELEASED"].includes(b.status);

 return <div className={styles.cols}>
  <div>
   <section className={styles.panel} aria-label={t("pr.new")}>
    <h2>{t("pr.new")}</h2>
    <form onSubmit={e=>{e.preventDefault();void create();}} noValidate>
     <div className={styles.formGrid}>
      <Field id="pr-ward" label={t("pr.ward")}><input id="pr-ward" className={styles.input} value={form.ward} maxLength={32} onChange={e=>editForm({ward:e.target.value})}/></Field>
      <Field id="pr-meal" label={t("pr.meal")}><select id="pr-meal" className={styles.input} value={form.meal} onChange={e=>editForm({meal:e.target.value})}>
       {MEALS.map(m=><option key={m} value={m}>{enumLabel(lang,"meal",m)}</option>)}</select></Field>
      <Field id="pr-date" label={t("pr.date")}><input id="pr-date" type="date" className={styles.input} value={form.date} onChange={e=>editForm({date:e.target.value})}/></Field>
      <Field id="pr-cutoff" label={t("pr.cutoff")}><input id="pr-cutoff" type="datetime-local" className={styles.input} value={form.cutoff} onChange={e=>editForm({cutoff:e.target.value})}/></Field>
     </div>
     <div className={styles.actions}><ActionButton label={t("pr.create")} onClick={()=>void create()} reason={createReason} busy={busy}/></div>
    </form>
   </section>
   <section className={styles.panel} aria-label={t("pr.batches")}>
    <h2>{t("pr.batches")}</h2>
    <div className={styles.filters}>
     <Field id="pb-ward" label={t("pr.ward")}><input id="pb-ward" className={styles.input} value={ward} maxLength={32} onChange={e=>{setWard(e.target.value);setPage(0);}}/></Field>
     <Field id="pb-status" label={t("or.filterStatus")}><select id="pb-status" className={styles.input} value={status} onChange={e=>{setStatus(e.target.value);setPage(0);}}>
      <option value="">{t("common.any")}</option>{["DRAFT","RELEASED","PREPARED","DISPATCH_APPROVED","CANCELLED"].map(s=><option key={s} value={s}>{enumLabel(lang,"batch",s)}</option>)}</select></Field>
    </div>
    <div className={styles.actions}><button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={list.reload}>{t("common.refresh")}</button></div>
    <div aria-busy={list.phase==="loading"} style={{marginTop:12}}>
     {list.phase==="loading"&&!list.data&&<Loading/>}
     {list.phase==="failed"&&list.failure&&<FailureView failure={list.failure} onRetry={list.reload}/>}
     {list.data&&list.data.items.length===0&&list.phase!=="failed"&&<Empty/>}
     {list.data&&list.data.items.length>0&&list.phase!=="failed"&&<>
      <ul className={styles.list}>{list.data.items.map(x=><li key={x.id}>
       <button type="button" className={styles.row} aria-pressed={selected===x.id} onClick={()=>{setSelected(x.id);setFailure(null);}}>
        <span><Badge tone={toneFor(x.status)}>{enumLabel(lang,"batch",x.status)}</Badge> <strong>{x.wardCode}</strong> · {enumLabel(lang,"meal",x.mealType)} · {x.mealDate}</span>
        <span className={styles.hint}>{t("pr.cutoff")} {fmtTime(lang,x.cutoffAt)} · {t("pr.total")} {x.portions?.TOTAL??0}</span>
       </button></li>)}</ul>
      <Pager page={list.data.page} size={list.data.size} total={list.data.total} onPage={setPage}/></>}
    </div>
   </section>
  </div>

  <section className={styles.panel} aria-label={t("pr.lines")} aria-live="polite">
   {!selected&&<p>{t("pr.selectHint")}</p>}
   {selected&&detail.phase==="loading"&&!b&&<Loading/>}
   {selected&&detail.phase==="failed"&&detail.failure&&<FailureView failure={detail.failure} onRetry={detail.reload}/>}
   {b&&<>
    <h2><Badge tone={toneFor(b.status)}>{enumLabel(lang,"batch",b.status)}</Badge> {b.wardCode} · {enumLabel(lang,"meal",b.mealType)} · {b.mealDate}</h2>
    <dl className={styles.kv}><dt>{t("pr.cutoff")}</dt><dd>{fmtTime(lang,b.cutoffAt)}</dd><dt>{t("common.version")}</dt><dd>{b.version}</dd></dl>
    <div className={styles.actions}>
     <ActionButton label={t("pr.release")} onClick={()=>void step("release")} reason={need(["DRAFT"])} busy={busy}/>
     <ActionButton label={t("pr.prepared")} tone="secondary" onClick={()=>void step("prepared")} reason={need(["RELEASED"])} busy={busy}/>
     <ActionButton label={t("pr.dispatch")} tone="secondary" onClick={()=>void step("dispatch-approval")} reason={need(["PREPARED"])} busy={busy}/>
     <ActionButton label={t("pr.cancel")} tone="danger" onClick={()=>void step("cancel")} reason={need(["DRAFT","RELEASED","PREPARED"])} busy={busy}/>
    </div>
    {b.status==="PREPARED"&&<p className={styles.actionNote}>{t("pr.independent")}</p>}
    <ActionError failure={failure} onReload={()=>{detail.reload();setFailure(null);}}/>
    {done&&<Notice>{done}</Notice>}
    <h3>{t("pr.portions")}</h3>
    <dl className={styles.kv}>{Object.entries(b.portions??{}).filter(([k])=>k!=="TOTAL").map(([k,v])=><div key={k} style={{display:"contents"}}><dt>{k.replace("DIET:",lang==="fr"?"Régime ":"Diet ")}</dt><dd>{v}</dd></div>)}
     <dt><strong>{t("pr.total")}</strong></dt><dd><strong>{b.portions?.TOTAL??0}</strong></dd></dl>
    <h3>{t("pr.lines")}</h3>
    {(b.lines??[]).length===0?<Empty hint={false}/>:<ul className={styles.list}>{b.lines!.map(l=><li key={l.id} className={styles.card}>
     <strong>{l.patientLabel}</strong> <span className={styles.hint}>{l.bedCode}</span><br/>
     {enumLabel(lang,"diet",l.dietCategory)} · {enumLabel(lang,"texture",l.texture)} · <Badge tone={toneFor(l.status)}>{enumLabel(lang,"mealStatus",l.status)}</Badge>
     {l.holdReason&&<div className={styles.hint}>{safetyLabel(lang,l.holdReason)}</div>}</li>)}</ul>}
    <h3>{t("pr.exceptions")}</h3>
    {(b.exceptions??[]).length===0?<p>{t("pr.noExceptions")}</p>:<ul className={styles.list}>{b.exceptions.map((x,i)=><li key={i} className={styles.card}>
     <span className={`${styles.mono}`}>{x.patientRef}</span><div className={styles.badges}>{x.codes.map(c=><Badge key={c} tone="danger">{safetyLabel(lang,c)}</Badge>)}</div></li>)}</ul>}
    <h3>{t("pr.substitutions")}</h3>
    {(b.substitutions??[]).length===0?<p>{t("pr.noSubstitutions")}</p>:<ul className={styles.list}>{b.substitutions!.map(s=><li key={s.id} className={styles.card}>
     <Badge tone={toneFor(s.status)}>{enumLabel(lang,"subst",s.status)}</Badge> <span className={styles.mono}>{s.originalItemCode} → {s.substituteItemCode}</span>
     {s.substituteAllergens.length>0&&<div className={styles.badges}>{s.substituteAllergens.map(a=><Badge key={a} tone="danger">{a}</Badge>)}</div>}
     <div className={styles.actions}>
      <ActionButton label={t("pr.subst.approve")} onClick={()=>void decide(s.id,s.version,true)} reason={s.status==="PROPOSED"?null:t("disabled.status",{status:enumLabel(lang,"subst",s.status)})} busy={busy}/>
      <ActionButton label={t("pr.subst.reject")} tone="secondary" onClick={()=>void decide(s.id,s.version,false)} reason={s.status==="PROPOSED"?null:t("disabled.status",{status:enumLabel(lang,"subst",s.status)})} busy={busy}/>
     </div></li>)}</ul>}
    <h3>{t("pr.propose")}</h3>
    <form onSubmit={e=>{e.preventDefault();void propose();}} noValidate>
     <div className={styles.formGrid}>
      <Field id="ps-meal" label={t("pr.subst.meal")} wide><select id="ps-meal" className={styles.input} value={sub.meal} onChange={e=>{setSub({...sub,meal:e.target.value});subKey.current=newKey();}}>
       <option value="">—</option>{(b.lines??[]).filter(l=>["PLANNED","PREPARED"].includes(l.status)).map(l=><option key={l.id} value={l.id}>{l.patientLabel} · {l.bedCode}</option>)}</select></Field>
      <Field id="ps-orig" label={t("pr.subst.original")}><input id="ps-orig" className={styles.input} value={sub.original} onChange={e=>{setSub({...sub,original:e.target.value});subKey.current=newKey();}}/></Field>
      <Field id="ps-sub" label={t("pr.subst.substitute")}><input id="ps-sub" className={styles.input} value={sub.substitute} onChange={e=>{setSub({...sub,substitute:e.target.value});subKey.current=newKey();}}/></Field>
      <Field id="ps-all" label={t("pr.subst.allergens")} hint={t("or.form.codesHint")}><input id="ps-all" className={styles.input} value={sub.allergens} onChange={e=>{setSub({...sub,allergens:e.target.value});subKey.current=newKey();}}/></Field>
      <Field id="ps-reason" label={t("pr.subst.reason")}><input id="ps-reason" className={styles.input} value={sub.reason} onChange={e=>{setSub({...sub,reason:e.target.value});subKey.current=newKey();}}/></Field>
     </div>
     <div className={styles.actions}><ActionButton label={t("pr.propose")} onClick={()=>void propose()} busy={busy}
      reason={!canSubstitute?t("disabled.status",{status:enumLabel(lang,"batch",b.status)}):subReason}/></div>
    </form>
   </>}
  </section>
 </div>;
}
export function Production(){return <NutritionFrame active="production" title="pr.title" intro="pr.intro"><Inner/></NutritionFrame>;}
