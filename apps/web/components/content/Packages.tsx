"use client";
import {useRef,useState} from "react";
import {call,newKey,parseJsonObject} from "../../lib/content/client";
import type {Failure,Page} from "../../lib/content/client";
import {enumLabel} from "../../lib/content/i18n";
import {ActionButton,ActionError,Badge,ContentFrame,Empty,FailureView,Field,fmtTime,Loading,Notice,Pager,styles,toneFor,useContent,useLoad} from "./ui";

type Pkg={id:string;packageKey:string;versionNo:number;status:string;definitionHash:string;sourceAttribution:string;effectiveFrom:string;effectiveTo:string|null;
 previousId:string|null;authorId:string;version:number;updatedAt:string;definition?:any;goldenCases?:any[]};
const STATUSES=["DRAFT","VALIDATED","VALIDATION_FAILED","IN_REVIEW","APPROVED","REJECTED","REVOKED","RETIRED"];
export const EXAMPLE_DEFINITION=JSON.stringify({schemaVersion:1,rules:[{id:"R1",when:{all:[{field:"patient.age_years",op:"gte",value:65}]},
 advise:{code:"REVIEW_DOSE",severity:"WARNING",text:{en:"Review dose for older adults",fr:"Revoir la dose chez les personnes âgées"}}}],codeRefs:[]},null,1);
export const EXAMPLE_GOLDEN=JSON.stringify([{name:"older adult",facts:{patient:{age_years:70}},expected:{FIRED:["R1"]}},
 {name:"younger adult",facts:{patient:{age_years:30}},expected:{}}],null,1);

function Inner(){
 const {t,lang}=useContent();
 const [status,setStatus]=useState("");const [keyQ,setKeyQ]=useState("");const [page,setPage]=useState(0);
 const [sel,setSel]=useState<string|null>(null);
 const [f,setF]=useState({key:"",source:"",from:new Date().toISOString().slice(0,16),to:"",def:EXAMPLE_DEFINITION,golden:EXAMPLE_GOLDEN,deps:""});
 const [facts,setFacts]=useState('{\n "patient": {"age_years": 80}\n}');
 const [evalOut,setEvalOut]=useState<any>(null);
 const [failure,setFailure]=useState<Failure|null>(null);const [done,setDone]=useState<string|null>(null);const [busy,setBusy]=useState(false);
 const createKey=useRef(newKey());
 const list=useLoad<Page<Pkg>>(s=>call("GET","packages",{signal:s,query:{status:status||undefined,key:/^[A-Za-z0-9._-]{1,64}$/.test(keyQ)?keyQ:undefined,page,size:25}}),[status,keyQ,page]);
 const detail=useLoad<Pkg>(s=>sel?call("GET",`packages/${sel}`,{signal:s}):Promise.resolve({ok:false,kind:"notfound",status:0,code:"none",details:[]} as Failure),[sel]);
 const p=sel?detail.data:undefined;
 const edit=(patch:Partial<typeof f>)=>{setF(x=>({...x,...patch}));createKey.current=newKey();setFailure(null);};
 const def=parseJsonObject(f.def);const goldenParsed=(()=>{try{const v=JSON.parse(f.golden);return Array.isArray(v)&&v.length<=200?v:null;}catch{return null;}})();
 const deps=f.deps.split("\n").map(x=>x.trim()).filter(Boolean);
 const createReason=!/^[A-Za-z0-9._-]{1,64}$/.test(f.key)||!f.source.trim()?t("disabled.input"):!def.value?t(def.problem==="definition_too_large"?"pk.tooBig":"pk.badJson"):!goldenParsed?t("pk.badJson"):deps.length>20?t("disabled.input"):null;
 const factsParsed=parseJsonObject(facts,64000);

 async function run<T>(fn:()=>Promise<import("../../lib/content/client").Result<T>>,ok?:(d:T)=>void){
  if(busy)return;setBusy(true);setFailure(null);setDone(null);
  const r=await fn();setBusy(false);
  if(!r.ok){setFailure(r);if(r.code==="stale_version")detail.reload();return;}
  ok?.(r.data);list.reload();if(sel)detail.reload();
 }
 const why=(allowed:string[]):string|null=>!p?t("disabled.select"):allowed.includes(p.status)?null:t("disabled.status",{status:enumLabel(lang,"package",p.status)});

 return <div className={styles.cols}>
  <section className={styles.panel} aria-label={t("pk.list")}>
   <h2>{t("pk.list")}</h2>
   <div className={styles.filters}>
    <Field id="pk-k" label={t("pk.key")}><input id="pk-k" className={styles.input} value={keyQ} maxLength={64} onChange={e=>{setKeyQ(e.target.value.trim());setPage(0);}}/></Field>
    <Field id="pk-s" label={t("tm.status")}><select id="pk-s" className={styles.input} value={status} onChange={e=>{setStatus(e.target.value);setPage(0);}}>
     <option value="">{t("common.any")}</option>{STATUSES.map(s=><option key={s} value={s}>{enumLabel(lang,"package",s)}</option>)}</select></Field>
   </div>
   <div className={styles.actions}><button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={list.reload}>{t("common.refresh")}</button></div>
   <div aria-busy={list.phase==="loading"} style={{marginTop:12}}>
    {list.phase==="loading"&&!list.data&&<Loading/>}
    {list.phase==="failed"&&list.failure&&<FailureView failure={list.failure} onRetry={list.reload}/>}
    {list.data&&list.data.items.length===0&&list.phase!=="failed"&&<Empty/>}
    {list.data&&list.data.items.length>0&&list.phase!=="failed"&&<>
     <ul className={styles.list}>{list.data.items.map(x=><li key={x.id}>
      <button type="button" className={styles.row} aria-pressed={sel===x.id} onClick={()=>{setSel(x.id);setFailure(null);setEvalOut(null);}}>
       <span><Badge tone={toneFor(x.status)}>{enumLabel(lang,"package",x.status)}</Badge> <strong>{x.packageKey}</strong> v{x.versionNo}</span>
       <span className={styles.hint}>{x.sourceAttribution}</span><span className={styles.hint}>{t("common.updated")} {fmtTime(lang,x.updatedAt)}</span>
      </button></li>)}</ul>
     <Pager page={list.data.page} size={list.data.size} total={list.data.total} onPage={setPage}/></>}
   </div>
   <h3>{t("pk.new")}</h3>
   <form onSubmit={e=>e.preventDefault()} noValidate>
    <div className={styles.formGrid}>
     <Field id="pn-k" label={t("pk.key")}><input id="pn-k" className={styles.input} maxLength={64} value={f.key} onChange={e=>edit({key:e.target.value.trim()})}/></Field>
     <Field id="pn-s" label={t("pk.source")}><input id="pn-s" className={styles.input} maxLength={500} value={f.source} onChange={e=>edit({source:e.target.value})}/></Field>
     <Field id="pn-f" label={t("pk.from")}><input id="pn-f" type="datetime-local" className={styles.input} value={f.from} onChange={e=>edit({from:e.target.value})}/></Field>
     <Field id="pn-t" label={t("pk.to")}><input id="pn-t" type="datetime-local" className={styles.input} value={f.to} onChange={e=>edit({to:e.target.value})}/></Field>
     <Field id="pn-d" label={t("pk.definition")} wide><textarea id="pn-d" className={`${styles.input} ${styles.mono}`} rows={10} value={f.def} onChange={e=>edit({def:e.target.value})}/></Field>
     <Field id="pn-g" label={t("pk.golden")} wide><textarea id="pn-g" className={`${styles.input} ${styles.mono}`} rows={6} value={f.golden} onChange={e=>edit({golden:e.target.value})}/></Field>
     <Field id="pn-p" label={t("pk.dependsOn")} wide><textarea id="pn-p" className={`${styles.input} ${styles.mono}`} rows={2} value={f.deps} onChange={e=>edit({deps:e.target.value})}/></Field>
    </div>
    <div className={styles.actions}>
     <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={()=>edit({def:EXAMPLE_DEFINITION,golden:EXAMPLE_GOLDEN})}>{t("pk.template")}</button>
     <ActionButton label={t("pk.create")} reason={createReason} busy={busy} onClick={()=>void run<Pkg>(()=>call("POST","packages",{key:createKey.current,body:{packageKey:f.key,definition:def.value,sourceAttribution:f.source.trim(),
      dependsOn:deps.map(k=>({type:"CODE_SYSTEM",key:k})),effectiveFrom:new Date(f.from).toISOString(),effectiveTo:f.to?new Date(f.to).toISOString():null,goldenCases:goldenParsed}}),d=>{createKey.current=newKey();setSel(d.id);setDone(t("pk.create"));})}/>
    </div>
   </form>
  </section>

  <div>
   <ActionError failure={failure} onReload={()=>{detail.reload();list.reload();setFailure(null);}}/>
   {done&&<Notice>{done}</Notice>}
   <section className={styles.panel} aria-label={t("common.details")} aria-live="polite">
    {!sel&&<p>{t("pk.select")}</p>}
    {sel&&detail.phase==="loading"&&!p&&<Loading/>}
    {sel&&detail.phase==="failed"&&detail.failure&&<FailureView failure={detail.failure} onRetry={detail.reload}/>}
    {p&&<>
     <h2><Badge tone={toneFor(p.status)}>{enumLabel(lang,"package",p.status)}</Badge> {p.packageKey} v{p.versionNo}</h2>
     <dl className={styles.kv}>
      <dt>{t("pk.source")}</dt><dd>{p.sourceAttribution}</dd><dt>{t("pk.hash")}</dt><dd className={styles.mono}>{p.definitionHash}</dd>
      <dt>{t("pk.from")}</dt><dd>{fmtTime(lang,p.effectiveFrom)} → {p.effectiveTo?fmtTime(lang,p.effectiveTo):"∞"}</dd>
      {p.previousId&&<><dt>{t("pk.previous")}</dt><dd className={styles.mono}>{p.previousId}</dd></>}<dt>{t("common.version")}</dt><dd>{p.version}</dd>
     </dl>
     <div className={styles.actions}><ActionButton label={t("pk.validate")} reason={why(["DRAFT"])} busy={busy} onClick={()=>void run(()=>call("POST",`packages/${p.id}/validate`,{body:{expectedVersion:p.version}}),()=>setDone(t("pk.validate")))}/></div>
     <p className={styles.actionNote}>{t("ap.stepNote")}</p>
     <h3>{t("pk.definition")}</h3>
     <pre className={`${styles.mono} ${styles.pre}`}>{JSON.stringify(p.definition,null,1)}</pre>
     <h3>{t("pk.evaluate")}</h3>
     <p className={styles.hint}>{t("pk.advisory")}</p>
     <Field id="ev-f" label={t("pk.facts")} wide><textarea id="ev-f" className={`${styles.input} ${styles.mono}`} rows={5} value={facts} onChange={e=>setFacts(e.target.value)}/></Field>
     <div className={styles.actions}><ActionButton label={t("pk.run")} tone="secondary" reason={factsParsed.value?null:t("pk.badJson")} busy={busy}
      onClick={()=>void run<any>(()=>call("POST",`packages/${p.id}/evaluate`,{body:{facts:factsParsed.value}}),d=>setEvalOut(d))}/></div>
     {evalOut&&<><h3>{t("pk.results")}</h3><ul className={styles.list}>{evalOut.results.map((r:any)=><li key={r.ruleId} className={styles.card}>
      <span className={styles.mono}>{r.ruleId}</span> <Badge tone={toneFor(r.verdict==="FIRED"?"FIRED":r.verdict==="INDETERMINATE"?"INDETERMINATE":"x")}>{enumLabel(lang,"verdict",r.verdict)}</Badge> {r.code} ({r.severity})</li>)}</ul></>}
    </>}
   </section>
  </div>
 </div>;
}
export function Packages(){return <ContentFrame active="packages" title="pk.title" intro="pk.intro"><Inner/></ContentFrame>;}
