"use client";
import {useState} from "react";
import {Badge,Empty,Field,FailureView,Loading,OfFrame,Pager,fmtTime,styles,toneFor,useLoad,useOf} from "./ui";
import {call} from "../../lib/offline/client";
import type {Page} from "../../lib/offline/client";
import {enumLabel} from "../../lib/offline/i18n";

type Row={id:string;outcome:string;reviewer:string|null;evidence:Record<string,unknown>;finalVersion:number;finalEpoch:number;occurredAt:string;recordType:string|null;recordKey:string|null};
const OUTCOMES=["APPLIED","APPLIED_MERGED","REJECTED","EXPIRED","RESOLVED_LOCAL","RESOLVED_AUTHORITATIVE","OWNERSHIP_TRANSFERRED"];
function tone(o:string){return o.startsWith("APPLIED")||o.startsWith("RESOLVED")||o==="OWNERSHIP_TRANSFERRED"?"ok":toneFor(o==="REJECTED"?"REJECTED":"EXPIRED");}

function Body(){
 const {t,lang}=useOf();
 const [outcome,setOutcome]=useState("");
 const [page,setPage]=useState(0);
 const h=useLoad<Page<Row>>(s=>call("GET","reconciliations",{query:{outcome,page,size:20},signal:s}),[outcome,page]);
 return <section className={styles.panel} aria-labelledby="h-h">
  <h2 id="h-h">{t("nav.history")}</h2>
  <div className={styles.filters}>
   <Field id="hs-out" label={t("hs.filter")}>
    <select id="hs-out" className={styles.input} value={outcome} onChange={e=>{setOutcome(e.target.value);setPage(0);}}>
     <option value="">{t("common.any")}</option>{OUTCOMES.map(o=><option key={o} value={o}>{enumLabel(lang,"outcome",o)}</option>)}
    </select>
   </Field>
   <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={h.reload}>{t("common.refresh")}</button>
  </div>
  {h.phase==="loading"&&!h.data?<Loading/>:h.phase==="failed"||!h.data?<FailureView failure={h.failure!} onRetry={h.reload}/>:
   h.data.items.length===0?<Empty message="hs.none" hint={false}/>:<>
   <div className={styles.tableWrap}><table className={styles.table}>
    <thead><tr><th>{t("common.at")}</th><th>{t("common.record")}</th><th>{t("q.outcome")}</th><th>{t("hs.reviewer")}</th><th>{t("hs.final")}</th><th>{t("hs.finalEpoch")}</th></tr></thead>
    <tbody>{h.data.items.map(r=><tr key={r.id}>
     <td>{fmtTime(lang,r.occurredAt)}</td><td>{r.recordType?`${r.recordType} / ${r.recordKey}`:"—"}</td>
     <td><Badge tone={tone(r.outcome)}>{enumLabel(lang,"outcome",r.outcome)}</Badge></td>
     <td className={styles.mono}>{r.reviewer?r.reviewer.slice(0,8):t("hs.automatic")}</td><td>v{r.finalVersion}</td><td>{r.finalEpoch}</td>
    </tr>)}</tbody>
   </table></div>
   <ul className={styles.cards}>{h.data.items.map(r=><li key={r.id} className={styles.card}>
    <strong>{r.recordType?`${r.recordType} / ${r.recordKey}`:"—"}</strong> <Badge tone={tone(r.outcome)}>{enumLabel(lang,"outcome",r.outcome)}</Badge>
    <dl><dt>{t("common.at")}</dt><dd>{fmtTime(lang,r.occurredAt)}</dd><dt>{t("hs.reviewer")}</dt><dd>{r.reviewer?r.reviewer.slice(0,8):t("hs.automatic")}</dd><dt>{t("hs.final")}</dt><dd>v{r.finalVersion} · e{r.finalEpoch}</dd></dl>
   </li>)}</ul>
   <Pager page={page} size={20} total={h.data.total} onPage={setPage}/>
  </>}
 </section>;
}
export default function History(){return <OfFrame active="history" title="hs.title" intro="hs.intro"><Body/></OfFrame>;}
