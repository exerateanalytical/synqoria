"use client";
import {Badge,Empty,FailureView,Loading,RlFrame,styles,toneFor,useLoad,useRl} from "./ui";
import {call} from "../../lib/reliability/client";
import type {Page} from "../../lib/reliability/client";
import {enumLabel} from "../../lib/reliability/i18n";

type Svc={id:string;serviceKey:string;name:string;tier:string;ownerRef:string;recoveryBoundary:string;sloTargetPct:string|number;health:string;dependencyDegraded:boolean;signals:Record<string,string|number>};
type Board={services:Svc[];serverTime:string;freshnessSeconds:number};

function Body(){
 const {t,lang}=useRl();
 const b=useLoad<Board>(s=>call<Board>("GET","health-board",{signal:s}),[]);
 const open=useLoad<Page<unknown>>(s=>call("GET","incidents",{query:{status:"OPEN",size:1},signal:s}),[]);
 if(b.phase==="loading"&&!b.data)return <Loading/>;
 if(b.phase==="failed"||!b.data)return <FailureView failure={b.failure!} onRetry={b.reload}/>;
 const sv=b.data.services;
 const num=(v:string|number|undefined)=>v===undefined?"—":String(v);
 return <>
  <ul className={styles.hub}>
   {(["HEALTHY","DEGRADED","DOWN","UNKNOWN"] as const).map(h=><li key={h} className={styles.card}>
    <h3><Badge tone={toneFor(h)}>{enumLabel(lang,"health",h)}</Badge></h3><strong className={styles.mono}>{sv.filter(x=>x.health===h).length}</strong></li>)}
   <li className={styles.card}><h3>{t("hl.openIncidents")}</h3><strong className={styles.mono}>{open.data?String(open.data.total):"—"}</strong></li>
  </ul>
  <p className={styles.hint}>{t("hl.freshness",{n:b.data.freshnessSeconds})}</p>
  <section className={styles.panel} aria-labelledby="sv-h">
   <h2 id="sv-h">{t("hl.services")}</h2>
   <div className={styles.actions}><button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={()=>{b.reload();open.reload();}}>{t("common.refresh")}</button></div>
   {sv.length===0?<Empty message="hl.none" hint={false}/>:<>
    <div className={styles.tableWrap}><table className={styles.table}>
     <thead><tr><th>{t("common.service")}</th><th>{t("common.status")}</th><th>{t("hl.saturation")}</th><th>{t("hl.backlog")}</th><th>{t("hl.latency")}</th><th>{t("hl.errors")}</th><th>{t("hl.boundary")}</th></tr></thead>
     <tbody>{sv.map(x=><tr key={x.id}>
      <td><strong>{x.name}</strong><br/><span className={styles.mono}>{x.serviceKey}</span> · {enumLabel(lang,"tier",x.tier)} · {t("hl.owner")}: {x.ownerRef}</td>
      <td><Badge tone={toneFor(x.health)}>{enumLabel(lang,"health",x.health)}</Badge>{x.dependencyDegraded&&<span className={styles.hint} style={{display:"block"}}>{t("hl.dependency")}</span>}</td>
      <td>{num(x.signals.SATURATION_PCT)}</td><td>{num(x.signals.BACKLOG)}</td><td>{num(x.signals.LATENCY_P95_MS)}</td><td>{num(x.signals.ERROR_RATE_PCT)}</td><td>{x.recoveryBoundary}</td>
     </tr>)}</tbody>
    </table></div>
    <ul className={styles.cards}>{sv.map(x=><li key={x.id} className={styles.card}>
     <strong>{x.name}</strong> <Badge tone={toneFor(x.health)}>{enumLabel(lang,"health",x.health)}</Badge>
     <dl><dt>{t("hl.saturation")}</dt><dd>{num(x.signals.SATURATION_PCT)}</dd><dt>{t("hl.backlog")}</dt><dd>{num(x.signals.BACKLOG)}</dd><dt>{t("hl.boundary")}</dt><dd>{x.recoveryBoundary}</dd></dl>
    </li>)}</ul>
   </>}
  </section>
 </>;
}
export default function Health(){return <RlFrame active="health" title="hl.title" intro="hl.intro"><Body/></RlFrame>;}
