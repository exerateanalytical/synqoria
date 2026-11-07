"use client";
import {Badge,fmtTime,styles,toneFor,useAn} from "./ui";
import {enumLabel} from "../../lib/analytics/i18n";
import {showValue} from "../../lib/analytics/client";
import type {ValueRow} from "../../lib/analytics/client";

export const MIN_CELL=5;
type Shown={state:string;unit:string;value:string|number|null;numerator:string|number|null;stale?:boolean;unknownCount?:number};

/** A figure with its state, never a bare number: suppressed and no-data are words, unknown is never zero. */
export function Figure({row}:{row:Shown}){
 const {t,lang}=useAn();
 const text=showValue(row,lang);
 return <span>
  <strong className={styles.mono}>{text}</strong>{" "}
  <Badge tone={toneFor(row.state)}>{enumLabel(lang,"state",row.state)}</Badge>
  {row.stale&&<>{" "}<Badge tone="warn">{t("db.stale")}</Badge></>}
  <span className={styles.hint} style={{display:"block"}}>
   {row.state==="INCOMPLETE"?t("explain.INCOMPLETE",{n:row.unknownCount??0}):row.state==="SUPPRESSED"?t("explain.SUPPRESSED",{n:MIN_CELL}):row.state==="NO_DATA"?t("explain.NO_DATA"):""}
   {row.stale&&row.state!=="NO_DATA"?" "+t("explain.stale"):""}
  </span>
 </span>;
}
export function metricName(lang:string,r:{nameEn?:string;nameFr?:string;metricKey?:string;metric_key?:string}):string{
 return (lang==="fr"?r.nameFr:r.nameEn)??r.nameEn??r.metricKey??"";
}
export function Watermark({iso}:{iso:string|null|undefined}){
 const {t,lang}=useAn();
 return <span>{iso?fmtTime(lang,iso):t("db.neverReported")}</span>;
}
export type {ValueRow};
