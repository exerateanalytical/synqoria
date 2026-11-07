"use client";
import {useState} from "react";
import {ActionButton,ActionError,Badge,Empty,Field,FailureView,FlFrame,Loading,Notice,Pager,styles,toneFor,useFl,useLoad} from "./ui";
import {call,newKey} from "../../lib/fleet/client";
import type {Failure,Page} from "../../lib/fleet/client";
import {enumLabel} from "../../lib/fleet/i18n";
import type {Key} from "../../lib/fleet/i18n";

type Req={id:string;requestKey:string;tenantKey:string;tenantName:string;region:string;tier:string;capacityUnits:number;serviceProfile:string[];storageIsolation:string;residencyPolicy:string;
 status:string;failureCode:string|null;requestedBy:string;approvedBy:string|null;version:number};
const KEY_RE=/^[a-z][a-z0-9-]{2,47}$/;

function Body(){
 const {t,lang}=useFl();
 const [page,setPage]=useState(0);
 const [f,setF]=useState({requestKey:"",tenantKey:"",tenantName:"",region:"eu-west",tier:"STANDARD",capacityUnits:"20",services:"clinical-core,operations",storageIsolation:"DEDICATED_DATABASE",residencyPolicy:"IN_REGION_ONLY",rollbackNote:""});
 const [sel,setSel]=useState("");
 const [note,setNote]=useState("");
 const [fail,setFail]=useState<Failure|null>(null);
 const [msg,setMsg]=useState<string|null>(null);
 const [busy,setBusy]=useState(false);
 const list=useLoad<Page<Req>>(s=>call("GET","provisioning-requests",{query:{page,size:10},signal:s}),[page]);
 const set=(k:keyof typeof f)=>(e:{target:{value:string}})=>setF(v=>({...v,[k]:e.target.value}));
 const services=f.services.split(",").map(x=>x.trim()).filter(Boolean);
 const cap=Number(f.capacityUnits);
 const submitWhy=!KEY_RE.test(f.requestKey)||!KEY_RE.test(f.tenantKey)||!f.tenantName.trim()||!f.region.trim()||!Number.isInteger(cap)||cap<1||services.length===0||!f.rollbackNote.trim()?t("disabled.input"):null;
 const act=async(p:ReturnType<typeof call>,done?:string)=>{
  setBusy(true);setFail(null);setMsg(null);
  const r=await p;setBusy(false);
  if(r.ok){if(done)setMsg(done);list.reload();}else setFail(r);
 };
 const submit=()=>act(call("POST","provisioning-requests",{body:{requestKey:f.requestKey,tenantKey:f.tenantKey,tenantName:f.tenantName.trim(),region:f.region.trim(),tier:f.tier,capacityUnits:cap,serviceProfile:services,
  storageIsolation:f.storageIsolation,residencyPolicy:f.residencyPolicy,rollbackMetadata:{note:f.rollbackNote.trim()}},key:newKey()}),t("pr.submitted"));
 const r=list.data?.items.find(x=>x.id===sel);
 const st=(wanted:string)=>!r?t("disabled.select"):r.status!==wanted?t("disabled.status",{status:enumLabel(lang,"prov",r.status)}):null;
 const step=(path:string,body:Record<string,unknown>)=>r&&act(call("POST",`provisioning-requests/${r.id}/${path}`,{body:{expectedVersion:r.version,...body},key:newKey()}));
 const code=note.trim().toLowerCase().replace(/[^a-z0-9_]+/g,"_");
 return <>
  <section className={styles.panel} aria-labelledby="pq-h">
   <h2 id="pq-h">{t("pr.request")}</h2>
   <div className={styles.formGrid}>
    <Field id="pr-rk" label={t("pr.requestKey")}><input id="pr-rk" className={styles.input} value={f.requestKey} onChange={set("requestKey")}/></Field>
    <Field id="pr-tk" label={t("pr.tenantKey")}><input id="pr-tk" className={styles.input} value={f.tenantKey} onChange={set("tenantKey")}/></Field>
    <Field id="pr-tn" label={t("pr.tenantName")} wide><input id="pr-tn" className={styles.input} value={f.tenantName} onChange={set("tenantName")}/></Field>
    <Field id="pr-rg" label={t("common.region")}><input id="pr-rg" className={styles.input} value={f.region} onChange={set("region")}/></Field>
    <Field id="pr-ti" label={t("common.tier")}><select id="pr-ti" className={styles.input} value={f.tier} onChange={set("tier")}>{["SMALL","STANDARD","LARGE"].map(x=><option key={x} value={x}>{t(`pr.${x}` as Key)}</option>)}</select></Field>
    <Field id="pr-cp" label={t("pr.capacity")}><input id="pr-cp" inputMode="numeric" className={styles.input} value={f.capacityUnits} onChange={set("capacityUnits")}/></Field>
    <Field id="pr-sv" label={t("pr.services")} wide><input id="pr-sv" className={styles.input} value={f.services} onChange={set("services")}/></Field>
    <Field id="pr-is" label={t("pr.isolation")}><select id="pr-is" className={styles.input} value={f.storageIsolation} onChange={set("storageIsolation")}>{["DEDICATED_DATABASE","DEDICATED_CLUSTER"].map(x=><option key={x} value={x}>{t(`pr.${x}` as Key)}</option>)}</select></Field>
    <Field id="pr-rs" label={t("pr.residency")}><select id="pr-rs" className={styles.input} value={f.residencyPolicy} onChange={set("residencyPolicy")}>{["IN_REGION_ONLY","IN_COUNTRY_ONLY","SAME_CONTINENT"].map(x=><option key={x} value={x}>{t(`pr.${x}` as Key)}</option>)}</select></Field>
    <Field id="pr-rb" label={t("pr.rollbackNote")} wide><input id="pr-rb" className={styles.input} maxLength={200} value={f.rollbackNote} onChange={set("rollbackNote")}/></Field>
   </div>
   <div className={styles.actions}><ActionButton label={t("pr.submit")} busy={busy} reason={submitWhy} onClick={submit}/></div>
   <ActionError failure={fail} onReload={list.reload}/>
   {msg&&<Notice>{msg}</Notice>}
  </section>
  <section className={styles.panel} aria-labelledby="pl-h">
   <h2 id="pl-h">{t("pr.list")}</h2>
   {list.phase==="loading"&&!list.data?<Loading/>:list.phase==="failed"||!list.data?<FailureView failure={list.failure!} onRetry={list.reload}/>:
    list.data.items.length===0?<Empty message="pr.none" hint={false}/>:<>
    <ul className={styles.list}>{list.data.items.map(x=><li key={x.id} className={styles.card}>
     <strong>{x.tenantName}</strong> <span className={styles.mono}>{x.tenantKey}</span> <Badge tone={toneFor(x.status)}>{enumLabel(lang,"prov",x.status)}</Badge>
     <p className={styles.hint}>{x.region} · {t(`pr.${x.tier}` as Key)} · {x.capacityUnits} · {t(`pr.${x.storageIsolation}` as Key)} · {t(`pr.${x.residencyPolicy}` as Key)}</p>
     {x.failureCode&&<p className={styles.hint}>{x.failureCode}</p>}
     <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} aria-pressed={sel===x.id} onClick={()=>{setSel(x.id);setNote("");setMsg(null);setFail(null);}}>{t("common.select")}</button>
    </li>)}</ul>
    <Pager page={page} size={10} total={list.data.total} onPage={setPage}/>
   </>}
  </section>
  <section className={styles.panel} aria-labelledby="pa-h">
   <h2 id="pa-h">{t("common.actions")}{r?`: ${r.tenantKey}`:""}</h2>
   {!r?<p className={styles.hint}>{t("pr.pick")}</p>:<Field id="pr-note" label={`${t("pr.rejectReason")} / ${t("pr.failureCode")}`}><input id="pr-note" className={styles.input} maxLength={200} value={note} onChange={e=>setNote(e.target.value)}/></Field>}
   <div className={styles.actions}>
    <ActionButton label={t("pr.approve")} busy={busy} reason={st("REQUESTED")} onClick={()=>step("decision",{approve:true,reason:null})}/>
    <ActionButton label={t("pr.reject")} tone="danger" busy={busy} reason={st("REQUESTED")??(note.trim().length<5?t("disabled.input"):null)} onClick={()=>step("decision",{approve:false,reason:note.trim()})}/>
    <ActionButton label={t("pr.apply")} busy={busy} reason={st("APPROVED")} onClick={()=>step("application",{executorResult:"OK",failureCode:null})}/>
    <ActionButton label={t("pr.applyFail")} tone="secondary" busy={busy} reason={st("APPROVED")??(code.length<3?t("disabled.input"):null)} onClick={()=>step("application",{executorResult:"FAILED",failureCode:code})}/>
    <ActionButton label={t("pr.rollback")} tone="danger" busy={busy} reason={st("APPLIED")??(note.trim().length<5?t("disabled.input"):null)} onClick={()=>step("rollback",{reason:note.trim()})}/>
   </div>
  </section>
 </>;
}
export default function Provisioning(){return <FlFrame active="provisioning" title="pr.title" intro="pr.intro"><Body/></FlFrame>;}
