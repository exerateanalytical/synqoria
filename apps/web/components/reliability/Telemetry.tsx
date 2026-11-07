"use client";
import {useState} from "react";
import {Badge,Empty,Field,FailureView,Loading,Pager,RlFrame,fmtTime,styles,toneFor,useLoad,useRl} from "./ui";
import {call} from "../../lib/reliability/client";
import type {Page} from "../../lib/reliability/client";
import {enumLabel} from "../../lib/reliability/i18n";

type Ev={id:string;correlationId:string;traceId:string|null;serviceKey:string|null;level:string;eventType:string;message:string;attributes:Record<string,unknown>;redactions:number;occurredAt:string};
type Acc={id:string;actorId:string;querySummary:Record<string,string>;returned:number;accessedAt:string};
type Board={services:{serviceKey:string;name:string}[]};
const CORR=/^[A-Za-z0-9._-]{8,64}$/;

function Body(){
 const {t,lang}=useRl();
 const [corr,setCorr]=useState("");const [svc,setSvc]=useState("");const [level,setLevel]=useState("");const [from,setFrom]=useState("");const [to,setTo]=useState("");
 const [applied,setApplied]=useState({corr:"",svc:"",level:"",from:"",to:""});
 const [page,setPage]=useState(0);const [apage,setApage]=useState(0);
 const board=useLoad<Board>(s=>call<Board>("GET","health-board",{signal:s}),[]);
 const ev=useLoad<Page<Ev>>(s=>call("GET","telemetry/events",{query:{correlationId:applied.corr,serviceKey:applied.svc,level:applied.level,from:applied.from,to:applied.to,page,size:20},signal:s}),[applied,page]);
 const acc=useLoad<Page<Acc>>(s=>call("GET","telemetry/access-log",{query:{page:apage,size:10},signal:s}),[apage]);
 const bad=corr!==""&&!CORR.test(corr);
 const search=()=>{setPage(0);setApplied({corr,svc,level,from:from?new Date(from).toISOString():"",to:to?new Date(to).toISOString():""});acc.reload();};
 return <>
  <section className={styles.panel} aria-labelledby="f-h">
   <h2 id="f-h">{t("tl.filters")}</h2>
   <div className={styles.filters}>
    <Field id="tl-c" label={t("tl.correlation")} hint={bad?t("err.invalid_query"):undefined}><input id="tl-c" className={`${styles.input} ${styles.mono}`} value={corr} onChange={e=>setCorr(e.target.value.trim())}/></Field>
    <Field id="tl-s" label={t("common.service")}><select id="tl-s" className={styles.input} value={svc} onChange={e=>setSvc(e.target.value)}><option value="">{t("common.any")}</option>{(board.data?.services??[]).map(x=><option key={x.serviceKey} value={x.serviceKey}>{x.name}</option>)}</select></Field>
    <Field id="tl-l" label={t("tl.level")}><select id="tl-l" className={styles.input} value={level} onChange={e=>setLevel(e.target.value)}><option value="">{t("common.any")}</option>{["DEBUG","INFO","WARN","ERROR"].map(x=><option key={x} value={x}>{enumLabel(lang,"level",x)}</option>)}</select></Field>
    <Field id="tl-f" label={t("common.from")}><input id="tl-f" type="datetime-local" className={styles.input} value={from} onChange={e=>setFrom(e.target.value)}/></Field>
    <Field id="tl-t" label={t("common.to")}><input id="tl-t" type="datetime-local" className={styles.input} value={to} onChange={e=>setTo(e.target.value)}/></Field>
    <button type="button" className={styles.btn} disabled={bad} onClick={search}>{t("tl.search")}</button>
   </div>
  </section>
  <section className={styles.panel} aria-labelledby="r-h" aria-live="polite">
   <h2 id="r-h">{t("tl.results")}</h2>
   {ev.phase==="loading"&&!ev.data?<Loading/>:ev.phase==="failed"||!ev.data?<FailureView failure={ev.failure!} onRetry={ev.reload}/>:
    ev.data.items.length===0?<Empty message="tl.none" hint={false}/>:<>
    <ul className={styles.list}>{ev.data.items.map(x=><li key={x.id} className={styles.card}>
     <Badge tone={toneFor(x.level)}>{enumLabel(lang,"level",x.level)}</Badge> <strong className={styles.mono}>{x.eventType}</strong> {x.serviceKey&&<span className={styles.hint}>{x.serviceKey}</span>}
     <p>{x.message}</p>
     <p className={styles.hint}><span className={styles.mono}>{x.correlationId}</span>{x.traceId?` · ${t("tl.trace")} ${x.traceId.slice(0,8)}`:""} · {fmtTime(lang,x.occurredAt)}{x.redactions>0?` · ${t("tl.redactions",{n:x.redactions})}`:""}</p>
     {Object.keys(x.attributes).length>0&&<pre className={styles.pre}>{JSON.stringify(x.attributes,null,1)}</pre>}
    </li>)}</ul>
    <Pager page={page} size={20} total={ev.data.total} onPage={setPage}/>
   </>}
  </section>
  <section className={styles.panel} aria-labelledby="a-h">
   <h2 id="a-h">{t("tl.access")}</h2>
   {acc.phase==="loading"&&!acc.data?<Loading/>:acc.phase==="failed"||!acc.data?<FailureView failure={acc.failure!} onRetry={acc.reload}/>:
    acc.data.items.length===0?<Empty message="tl.accessNone" hint={false}/>:<>
    <ul className={styles.list}>{acc.data.items.map(x=><li key={x.id} className={styles.card}>
     <span className={styles.mono}>{x.actorId.slice(0,8)}</span> · {fmtTime(lang,x.accessedAt)} · {t("tl.returned",{n:x.returned})}
     <p className={styles.hint}>{Object.entries(x.querySummary).map(([k,v])=>`${k}=${v}`).join(" · ")||"—"}</p>
    </li>)}</ul>
    <Pager page={apage} size={10} total={acc.data.total} onPage={setApage}/>
   </>}
  </section>
 </>;
}
export default function Telemetry(){return <RlFrame active="telemetry" title="tl.title" intro="tl.intro"><Body/></RlFrame>;}
