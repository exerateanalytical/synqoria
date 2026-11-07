"use client";
import {useMemo,useState} from "react";
import {ActionButton,ActionError,AnFrame,Badge,Empty,Field,FailureView,Loading,Pager,styles,toneFor,useAn,useLoad} from "./ui";
import {Figure,metricName} from "./parts";
import {call,seriesPoints,showValue} from "../../lib/analytics/client";
import type {Failure,Page,ValueRow} from "../../lib/analytics/client";
import {enumLabel} from "../../lib/analytics/i18n";

type Def={id:string;metricKey:string;nameEn:string;nameFr:string;domain:string;unit:string;status:string};
type Agg={metricKey:string;state:string;minFacilities:number;items:{dimension:string;currency:string|null;state:string;value:string|null;facilities:number|null}[]};

const r1=(n:number)=>Math.round(n*10)/10;   // drawing coordinates only, never a displayed value
function isoDay(d:Date){return d.toISOString().slice(0,10);}
function Chart({points,label,empty}:{points:{label:string;y:number|null;state:string}[];label:string;empty:string}){
 const known=points.filter(p=>p.y!==null) as {label:string;y:number;state:string}[];
 if(points.length===0||known.length===0)return <p className={styles.hint}>{empty}</p>;
 const W=520,H=160,P=24;
 const ys=known.map(p=>p.y),lo=Math.min(0,...ys),hi=Math.max(...ys,lo+1);
 const x=(i:number)=>P+(points.length===1?(W-2*P)/2:i*(W-2*P)/(points.length-1));
 const y=(v:number)=>H-P-((v-lo)/(hi-lo))*(H-2*P);
 // Unknown points break the line instead of being drawn as zero.
 let d="",pen=false;
 points.forEach((p,i)=>{ if(p.y===null){pen=false;return;} d+=`${pen?"L":"M"}${r1(x(i))} ${r1(y(p.y))} `;pen=true; });
 return <svg role="img" aria-label={label} viewBox={`0 0 ${W} ${H}`} style={{width:"100%",maxWidth:560,height:"auto"}}>
  <line x1={P} y1={H-P} x2={W-P} y2={H-P} stroke="currentColor" strokeOpacity="0.4"/>
  <path d={d.trim()} fill="none" stroke="currentColor" strokeWidth="2"/>
  {points.map((p,i)=>p.y===null?<text key={i} x={x(i)} y={H-P-6} textAnchor="middle" fontSize="12">?</text>:
   <circle key={i} cx={x(i)} cy={y(p.y)} r="3.5" fill="currentColor"/>)}
 </svg>;
}

function Body({domain}:{domain:"CLINICAL"|"FINANCIAL"}){
 const {t,lang}=useAn();
 const today=useMemo(()=>new Date(),[]);
 const [from,setFrom]=useState(isoDay(new Date(today.getTime()-90*86400000)));
 const [to,setTo]=useState(isoDay(today));
 const [metricKey,setMetricKey]=useState("");
 const [page,setPage]=useState(0);
 const [currency,setCurrency]=useState("");
 const [aggFail,setAggFail]=useState<Failure|null>(null);
 const [agg,setAgg]=useState<Agg|null>(null);
 const size=25;
 const defs=useLoad<Page<Def>>(s=>call("GET","metrics",{query:{domain,status:"APPROVED",size:100},signal:s}),[domain]);
 const vals=useLoad<Page<ValueRow>>(s=>metricKey?call("GET","metric-values",{query:{domain,metricKey,from,to,page,size},signal:s}):
  Promise.resolve({ok:true as const,status:200,data:{items:[],page:0,size,total:0}}),[domain,metricKey,from,to,page]);
 const list=defs.data?.items??[];
 const selected=list.find(x=>x.metricKey===metricKey);
 const rows=vals.data?.items??[];
 const currencies=[...new Set(rows.map(r=>(r.currency??"").trim()).filter(Boolean))];
 const activeCur=domain==="FINANCIAL"?(currency||currencies[0]||""):"";
 const shown=domain==="FINANCIAL"?rows.filter(r=>(r.currency??"").trim()===activeCur):rows;
 const pts=seriesPoints(shown,domain==="FINANCIAL"?activeCur:null);
 const loadAgg=async()=>{
  setAggFail(null);setAgg(null);
  const r=await call<Agg>("GET","cell-aggregates",{query:{metricKey,from,to}});
  if(r.ok)setAgg(r.data);else setAggFail(r);
 };
 if(defs.phase==="loading"&&!defs.data)return <Loading/>;
 if(defs.phase==="failed"||!defs.data)return <FailureView failure={defs.failure!} onRetry={defs.reload}/>;
 const name=selected?metricName(lang,selected):"";
 return <>
  <section className={styles.panel} aria-labelledby="filters-h">
   <h2 id="filters-h" className={styles.eyebrow}>{t("an.metric")}</h2>
   {list.length===0?<Empty message="an.noMetrics" hint={false}/>:
   <div className={styles.filters}>
    <Field id="an-metric" label={t("an.metric")}>
     <select id="an-metric" className={styles.input} value={metricKey} onChange={e=>{setMetricKey(e.target.value);setPage(0);setAgg(null);setAggFail(null);}}>
      <option value="">{t("an.pickMetric")}</option>
      {list.map(m=><option key={m.id} value={m.metricKey}>{metricName(lang,m)}</option>)}
     </select>
    </Field>
    <Field id="an-from" label={t("an.from")}><input id="an-from" type="date" className={styles.input} value={from} max={to} onChange={e=>{setFrom(e.target.value);setPage(0);}}/></Field>
    <Field id="an-to" label={t("an.to")}><input id="an-to" type="date" className={styles.input} value={to} min={from} onChange={e=>{setTo(e.target.value);setPage(0);}}/></Field>
    {domain==="FINANCIAL"&&currencies.length>1&&<Field id="an-cur" label={t("an.currency")}>
     <select id="an-cur" className={styles.input} value={activeCur} onChange={e=>setCurrency(e.target.value)}>
      {currencies.map(c=><option key={c} value={c}>{c}</option>)}
     </select></Field>}
    <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={vals.reload}>{t("common.refresh")}</button>
   </div>}
  </section>
  {metricKey&&<>
   {vals.phase==="loading"&&!vals.data?<Loading/>:vals.phase==="failed"?<FailureView failure={vals.failure!} onRetry={vals.reload}/>:
   rows.length===0?<Empty/>:<>
    <section className={styles.panel} aria-labelledby="series-h">
     <h2 id="series-h">{t("an.series")}: {name}</h2>
     {domain==="FINANCIAL"&&activeCur&&<p><Badge tone="info">{activeCur}</Badge></p>}
     <Chart points={pts} label={t("an.chartLabel",{metric:name,n:pts.length})} empty={t("an.chartEmpty")}/>
     <p className={styles.hint}>{t("an.suppressedNote")}</p>
    </section>
    <section className={styles.panel} aria-labelledby="table-h">
     <h2 id="table-h">{t("an.table")}</h2>
     <div className={styles.tableWrap}><table className={styles.table}>
      <caption className={styles.hint}>{name}</caption>
      <thead><tr><th>{t("common.from")}</th><th>{t("common.to")}</th><th>{t("an.dimension")}</th>{domain==="FINANCIAL"&&<th>{t("an.currency")}</th>}
       <th>{t("an.metric")}</th><th>{t("an.cohort")}</th><th>{t("an.unknown")}</th><th>{t("an.source")}</th></tr></thead>
      <tbody>{shown.map(r=><tr key={r.id}>
       <td>{r.periodStart}</td><td>{r.periodEnd}</td><td>{r.dimensionKey}</td>{domain==="FINANCIAL"&&<td>{(r.currency??"").trim()}</td>}
       <td><Figure row={r}/></td><td>{r.state==="SUPPRESSED"?"—":r.cohortSize}</td><td>{r.unknownCount}</td><td>{r.sourceWatermark??"—"}</td>
      </tr>)}</tbody>
     </table></div>
     <ul className={styles.cards}>{shown.map(r=><li key={r.id} className={styles.card}>
      <strong>{r.periodStart} → {r.periodEnd}</strong>
      <dl><dt>{t("an.dimension")}</dt><dd>{r.dimensionKey}</dd>
       {domain==="FINANCIAL"&&<><dt>{t("an.currency")}</dt><dd>{(r.currency??"").trim()}</dd></>}
       <dt>{t("an.metric")}</dt><dd><Figure row={r}/></dd>
       <dt>{t("an.unknown")}</dt><dd>{r.unknownCount}</dd></dl>
     </li>)}</ul>
     <Pager page={page} size={size} total={vals.data?.total??0} onPage={setPage}/>
    </section>
   </>}
   <section className={styles.panel} aria-labelledby="agg-h">
    <h2 id="agg-h">{t("an.cellAggregate")}</h2>
    <p className={styles.hint}>{t("an.cellNote")}</p>
    <ActionButton label={t("an.cellLoad")} tone="secondary" onClick={loadAgg}/>
    <ActionError failure={aggFail}/>
    {agg&&(agg.items.length===0?<Empty hint={false}/>:<ul className={styles.list}>
     {agg.items.map((i,k)=><li key={k}>
      {i.dimension} {i.currency?`(${i.currency.trim()}) `:""}
      {i.state==="OK"?<><strong className={styles.mono}>{showValue({state:i.state,unit:selected?.unit??"COUNT",value:i.value,numerator:i.value},lang)}</strong> · {t("an.facilities",{n:i.facilities??0})}</>:
       <Badge tone="warn">{t("an.cellHidden")}</Badge>}
      {" "}<Badge tone={toneFor(i.state)}>{enumLabel(lang,"state",i.state)}</Badge>
     </li>)}
    </ul>)}
   </section>
  </>}
 </>;
}
export default function Analytics({domain}:{domain:"CLINICAL"|"FINANCIAL"}){
 const clin=domain==="CLINICAL";
 return <AnFrame active={clin?"clinical":"financial"} title={clin?"an.clinicalTitle":"an.financialTitle"} intro={clin?"an.clinicalIntro":"an.financialIntro"}><Body domain={domain}/></AnFrame>;
}
