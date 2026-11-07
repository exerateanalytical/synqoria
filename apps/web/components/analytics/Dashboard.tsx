"use client";
import {AnFrame,Badge,Empty,FailureView,Loading,fmtTime,styles,useAn,useLoad} from "./ui";
import {Figure,Watermark,metricName} from "./parts";
import {call} from "../../lib/analytics/client";

type Tile={metricKey:string;nameEn:string;nameFr:string;unit:string;periodStart:string;periodEnd:string;state:string;numerator:string|null;denominator:string|null;value:string|null;
 cohortSize:number;unknownCount:number;stale:boolean;computedAt:string};
type Fresh={sourceKey:string;name:string;domain:string;status:string;watermark:string|null;stale:boolean;quarantined:number;factCount:number};
type Dash={tiles:Tile[];freshness:Fresh[]};

function Body(){
 const {t,lang}=useAn();
 const d=useLoad<Dash>(s=>call<Dash>("GET","dashboard",{signal:s}),[]);
 if(d.phase==="loading"&&!d.data)return <Loading/>;
 if(d.phase==="failed"||!d.data)return <FailureView failure={d.failure!} onRetry={d.reload}/>;
 const {tiles,freshness}=d.data;
 return <>
  <div className={styles.actions}><button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={d.reload}>{t("common.refresh")}</button></div>
  <section className={styles.panel} aria-labelledby="tiles-h">
   <h2 id="tiles-h">{t("nav.dashboard")}</h2>
   {tiles.length===0?<Empty message="db.noTiles"/>:
    <ul className={styles.hub}>
     {tiles.map(x=><li key={x.metricKey} className={styles.card}>
      <h3>{metricName(lang,x)}</h3>
      <Figure row={x}/>
      <p className={styles.hint}>{t("db.period")}: {x.periodStart} → {x.periodEnd} · {t("db.computed")}: {fmtTime(lang,x.computedAt)}</p>
     </li>)}
    </ul>}
  </section>
  <section className={styles.panel} aria-labelledby="fresh-h">
   <h2 id="fresh-h">{t("db.freshness")}</h2>
   {freshness.length===0?<Empty hint={false}/>:
    <div className={styles.tableWrap}><table className={styles.table}>
     <thead><tr><th>{t("an.source")}</th><th>{t("common.status")}</th><th>{t("db.watermark")}</th><th>{t("db.freshness")}</th><th>{t("db.quarantineCol")}</th></tr></thead>
     <tbody>{freshness.map(f=><tr key={f.sourceKey}>
      <td><span className={styles.mono}>{f.sourceKey}</span> {f.name}</td>
      <td>{f.status}</td><td><Watermark iso={f.watermark}/></td>
      <td><Badge tone={f.stale?"warn":"ok"}>{f.stale?t("db.stale"):t("db.fresh")}</Badge></td>
      <td>{f.quarantined>0?<Badge tone="warn">{t("db.quarantined",{n:f.quarantined})}</Badge>:"0"}</td>
     </tr>)}</tbody>
    </table></div>}
   <ul className={styles.cards}>
    {freshness.map(f=><li key={f.sourceKey} className={styles.card}>
     <strong>{f.name}</strong>
     <dl><dt>{t("db.watermark")}</dt><dd><Watermark iso={f.watermark}/></dd>
     <dt>{t("db.freshness")}</dt><dd>{f.stale?t("db.stale"):t("db.fresh")}</dd>
     <dt>{t("db.quarantineCol")}</dt><dd>{f.quarantined}</dd></dl>
    </li>)}
   </ul>
  </section>
 </>;
}
export default function Dashboard(){
 return <AnFrame active="dashboard" title="db.title" intro="db.intro"><Body/></AnFrame>;
}
