"use client";
import {useRef,useState} from "react";
import {call,newKey,parseJsonObject,showMoney,UUID_RE,validMoney} from "../../lib/hr/client";
import type {Failure,Page,Result} from "../../lib/hr/client";
import {enumLabel,safetyLabel} from "../../lib/hr/i18n";
import {ActionButton,ActionError,Badge,Empty,FailureView,Field,HrFrame,Loading,Notice,Pager,styles,toneFor,useHr,useLoad} from "./ui";

export const EXAMPLE_RULES=JSON.stringify({schemaVersion:1,currency:"XAF",rounding:{mode:"HALF_UP",scale:0},monthlyHours:"173.33",components:[
 {code:"HOUSING",kind:"EARNING",type:"FIXED",amount:"0"},{code:"OT",kind:"EARNING",type:"OVERTIME",multiplier:"1.5"}]},null,1);

function Inner(){
 const {t,lang}=useHr();
 const [rf,setRf]=useState({country:"",employer:"",currency:"",from:new Date().toISOString().slice(0,10),source:"",json:EXAMPLE_RULES});
 const [pf,setPf]=useState({employer:"",country:"",currency:"",start:"",end:""});
 const [af,setAf]=useState({period:"",staff:"",kind:"ARREARS",amount:"",reason:""});const [staffQ,setStaffQ]=useState("");
 const [sel,setSel]=useState<string|null>(null);const [page,setPage]=useState(0);
 const [rej,setRej]=useState<{kind:"rules"|"adj";id:string;version:number}|null>(null);const [rejNote,setRejNote]=useState("");
 const [failure,setFailure]=useState<Failure|null>(null);const [done,setDone]=useState<string|null>(null);const [busy,setBusy]=useState(false);
 const k1=useRef(newKey());const k2=useRef(newKey());const k3=useRef(newKey());
 const rules=useLoad<any[]>(s=>call("GET","rule-sets",{signal:s}),[done]);
 const periods=useLoad<{items:any[]}>(s=>call("GET","periods",{signal:s}),[done]);
 const runs=useLoad<Page<any>>(s=>call("GET","runs",{signal:s,query:{page,size:25}}),[page,done]);
 const detail=useLoad<any>(s=>sel?call("GET",`runs/${sel}`,{signal:s}):Promise.resolve({ok:false,kind:"notfound",status:0,code:"none",details:[]} as Failure),[sel,done]);
 const hits=useLoad<Page<any>>(s=>staffQ.trim().length>=2?call("GET","staff",{signal:s,query:{q:staffQ.trim(),size:8}}):Promise.resolve({ok:true,status:200,data:{items:[],page:0,size:8,total:0}}),[staffQ]);
 const run0=sel?detail.data:undefined;
 const parsed=parseJsonObject(rf.json);

 async function run<T>(fn:()=>Promise<Result<T>>,ok?:(d:T)=>void){
  if(busy)return;setBusy(true);setFailure(null);setDone(null);
  const r=await fn();setBusy(false);
  if(!r.ok){setFailure(r);if(r.code==="stale_version")detail.reload();return;}
  ok?.(r.data);
 }
 const ruleReason=!/^[A-Z]{2}$/.test(rf.country)||!/^[A-Z0-9_-]{1,32}$/.test(rf.employer)||!/^[A-Z]{3}$/.test(rf.currency)||!rf.from||!rf.source.trim()?t("disabled.input"):!parsed.value?t("pp.badJson"):null;
 const periodReason=!/^[A-Z0-9_-]{1,32}$/.test(pf.employer)||!/^[A-Z]{2}$/.test(pf.country)||!/^[A-Z]{3}$/.test(pf.currency)||!pf.start||!pf.end||pf.end<pf.start?t("disabled.input"):null;
 const adjReason=!UUID_RE.test(af.period)||!UUID_RE.test(af.staff)||!validMoney(af.amount)||!af.reason.trim()?t("disabled.input"):null;
 const why=(allowed:string[]):string|null=>!run0?t("disabled.select"):allowed.includes(run0.status)?null:t("disabled.status",{status:enumLabel(lang,"run",run0.status)});
 const problems=failure?.code==="invalid_rule_set"?failure.details:[];

 return <div className={styles.cols}>
  <div>
   <ActionError failure={failure?.code==="invalid_rule_set"?null:failure} onReload={()=>{detail.reload();setFailure(null);}}/>
   {problems.length>0&&<div className={`${styles.banner} ${styles.bannerDanger}`} role="alert"><strong>{t("err.invalid_rule_set")}</strong><ul>{problems.slice(0,30).map((p,i)=><li key={i} className={styles.mono}>{p}</li>)}</ul></div>}
   {done&&<Notice>{done}</Notice>}
   <section className={styles.panel} aria-label={t("pp.rules")}>
    <h2>{t("pp.rules")}</h2><p className={styles.hint}>{t("pp.ruleNote")}</p>
    {rules.phase==="loading"&&!rules.data&&<Loading/>}
    {rules.phase==="failed"&&rules.failure&&<FailureView failure={rules.failure} onRetry={rules.reload}/>}
    {rules.data&&rules.data.length===0&&<Empty hint={false}/>}
    {rules.data&&rules.data.length>0&&<ul className={styles.list}>{rules.data.map((x:any)=><li key={x.id} className={styles.card}>
     <Badge tone={toneFor(x.status)}>{enumLabel(lang,"rules",x.status)}</Badge> <strong>{x.countryCode} · {x.employerCode}</strong> v{x.ruleVersion} · {String(x.currency).trim()} · {x.effectiveFrom}
     <div className={styles.hint}>{x.sourceReference}</div>
     <div className={styles.actions}>
      <ActionButton label={t("common.approve")} busy={busy} reason={x.status==="DRAFT"?null:t("disabled.status",{status:enumLabel(lang,"rules",x.status)})} onClick={()=>void run(()=>call("POST",`rule-sets/${x.id}/decision`,{body:{expectedVersion:x.version,approve:true}}),()=>setDone(t("common.approve")))}/>
      <ActionButton label={t("common.reject")} tone="secondary" reason={x.status==="DRAFT"?null:t("disabled.status",{status:enumLabel(lang,"rules",x.status)})} onClick={()=>{setRej({kind:"rules",id:x.id,version:x.version});setRejNote("");}}/>
     </div></li>)}</ul>}
    <h3>{t("pp.ruleNew")}</h3>
    <div className={styles.formGrid}>
     <Field id="rs-c" label={t("pp.country")}><input id="rs-c" className={styles.input} maxLength={2} value={rf.country} onChange={e=>{setRf({...rf,country:e.target.value.trim().toUpperCase()});k1.current=newKey();}}/></Field>
     <Field id="rs-e" label={t("pp.employer")}><input id="rs-e" className={styles.input} maxLength={32} value={rf.employer} onChange={e=>{setRf({...rf,employer:e.target.value.trim().toUpperCase()});k1.current=newKey();}}/></Field>
     <Field id="rs-u" label={t("pp.currency")}><input id="rs-u" className={styles.input} maxLength={3} value={rf.currency} onChange={e=>{setRf({...rf,currency:e.target.value.trim().toUpperCase()});k1.current=newKey();}}/></Field>
     <Field id="rs-f" label={t("pp.effective")}><input id="rs-f" type="date" className={styles.input} value={rf.from} onChange={e=>{setRf({...rf,from:e.target.value});k1.current=newKey();}}/></Field>
     <Field id="rs-s" label={t("pp.source")} wide><input id="rs-s" className={styles.input} maxLength={300} value={rf.source} onChange={e=>{setRf({...rf,source:e.target.value});k1.current=newKey();}}/></Field>
     <Field id="rs-j" label={t("pp.json")} wide><textarea id="rs-j" className={`${styles.input} ${styles.mono}`} rows={10} value={rf.json} onChange={e=>{setRf({...rf,json:e.target.value});k1.current=newKey();}}/></Field>
    </div>
    <div className={styles.actions}>
     <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={()=>setRf({...rf,json:EXAMPLE_RULES})}>{t("pp.template")}</button>
     <ActionButton label={t("pp.ruleCreate")} reason={ruleReason} busy={busy} onClick={()=>void run(()=>call("POST","rule-sets",{key:k1.current,body:{countryCode:rf.country,employerCode:rf.employer,currency:rf.currency,effectiveFrom:rf.from,rules:parsed.value,sourceReference:rf.source.trim()}}),()=>{k1.current=newKey();setDone(t("pp.ruleCreate"));})}/>
    </div>
    {rej&&<div className={styles.formGrid} style={{marginTop:12}}>
     <Field id="pr-n" label={t("common.reason")} wide><input id="pr-n" className={styles.input} autoFocus maxLength={300} value={rejNote} onChange={e=>setRejNote(e.target.value)}/></Field>
     <div className={styles.actions}><ActionButton label={t("common.reject")} tone="danger" busy={busy} reason={rejNote.trim()?null:t("err.reason_required")}
      onClick={()=>void run(()=>call("POST",rej.kind==="rules"?`rule-sets/${rej.id}/decision`:`adjustments/${rej.id}/decision`,{body:{expectedVersion:rej.version,approve:false,note:rejNote.trim()}}),()=>{setRej(null);setDone(t("common.reject"));})}/>
      <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={()=>setRej(null)}>{t("common.cancel")}</button></div></div>}
   </section>

   <section className={styles.panel} aria-label={t("pp.periods")}>
    <h2>{t("pp.periods")}</h2>
    {periods.data&&periods.data.items.length>0&&<ul className={styles.list}>{periods.data.items.map((p:any)=><li key={p.id} className={styles.card}>
     <Badge tone={toneFor(p.status)}>{enumLabel(lang,"period",p.status)}</Badge> <strong>{p.employerCode} · {p.countryCode}</strong> {p.periodStart} → {p.periodEnd}
     <div className={styles.actions}><ActionButton label={t("pp.calculate")} busy={busy} reason={p.status==="OPEN"?null:t("disabled.status",{status:enumLabel(lang,"period",p.status)})}
      onClick={()=>void run<any>(()=>call("POST",`periods/${p.id}/runs`,{body:{}}),d=>{setSel(d.id);setDone(t("pp.calculate"));})}/>
      <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={()=>setAf({...af,period:p.id})}>{t("pp.adjust")}</button></div></li>)}</ul>}
    <h3>{t("pp.periodNew")}</h3>
    <div className={styles.formGrid}>
     <Field id="pd-e" label={t("pp.employer")}><input id="pd-e" className={styles.input} maxLength={32} value={pf.employer} onChange={e=>{setPf({...pf,employer:e.target.value.trim().toUpperCase()});k2.current=newKey();}}/></Field>
     <Field id="pd-c" label={t("pp.country")}><input id="pd-c" className={styles.input} maxLength={2} value={pf.country} onChange={e=>{setPf({...pf,country:e.target.value.trim().toUpperCase()});k2.current=newKey();}}/></Field>
     <Field id="pd-u" label={t("pp.currency")}><input id="pd-u" className={styles.input} maxLength={3} value={pf.currency} onChange={e=>{setPf({...pf,currency:e.target.value.trim().toUpperCase()});k2.current=newKey();}}/></Field><span/>
     <Field id="pd-s" label={t("pp.periodStart")}><input id="pd-s" type="date" className={styles.input} value={pf.start} onChange={e=>{setPf({...pf,start:e.target.value});k2.current=newKey();}}/></Field>
     <Field id="pd-n" label={t("pp.periodEnd")}><input id="pd-n" type="date" className={styles.input} value={pf.end} onChange={e=>{setPf({...pf,end:e.target.value});k2.current=newKey();}}/></Field>
    </div>
    <div className={styles.actions}><ActionButton label={t("pp.periodCreate")} reason={periodReason} busy={busy} onClick={()=>void run(()=>call("POST","periods",{key:k2.current,body:{employerCode:pf.employer,countryCode:pf.country,currency:pf.currency,periodStart:pf.start,periodEnd:pf.end}}),()=>{k2.current=newKey();setDone(t("pp.periodCreate"));})}/></div>
    <h3>{t("pp.adjustNew")}</h3>
    <div className={styles.formGrid}>
     <Field id="aj-q" label={t("common.staff")} wide><input id="aj-q" type="search" className={styles.input} value={staffQ} onChange={e=>{setStaffQ(e.target.value);setAf({...af,staff:""});}}/></Field>
     {hits.data&&hits.data.items.length>0&&<ul className={styles.list} style={{gridColumn:"1/-1"}}>{hits.data.items.map((x:any)=><li key={x.id}><button type="button" className={styles.row} aria-pressed={af.staff===x.id} onClick={()=>setAf({...af,staff:x.id})}>{x.familyName}, {x.givenName} <span className={styles.mono}>{x.staffNumber}</span></button></li>)}</ul>}
     <Field id="aj-p" label={t("pp.periods")}><select id="aj-p" className={styles.input} value={af.period} onChange={e=>setAf({...af,period:e.target.value})}><option value="">—</option>{(periods.data?.items??[]).map((p:any)=><option key={p.id} value={p.id}>{p.periodStart} → {p.periodEnd}</option>)}</select></Field>
     <Field id="aj-k" label={t("pp.adjustKind")}><select id="aj-k" className={styles.input} value={af.kind} onChange={e=>setAf({...af,kind:e.target.value})}>{["ARREARS","BONUS","CORRECTION","ALLOWANCE","BENEFIT","DEDUCTION"].map(x=><option key={x} value={x}>{enumLabel(lang,"adj",x)}</option>)}</select></Field>
     <Field id="aj-a" label={t("pp.amount")}><input id="aj-a" inputMode="decimal" className={`${styles.input} ${styles.mono}`} value={af.amount} onChange={e=>{setAf({...af,amount:e.target.value.trim()});k3.current=newKey();}}/></Field>
     <Field id="aj-r" label={t("common.reason")}><input id="aj-r" className={styles.input} maxLength={300} value={af.reason} onChange={e=>{setAf({...af,reason:e.target.value});k3.current=newKey();}}/></Field>
    </div>
    <div className={styles.actions}><ActionButton label={t("pp.adjustRequest")} reason={adjReason} busy={busy} onClick={()=>void run(()=>call("POST","adjustments",{key:k3.current,body:{periodId:af.period,staffId:af.staff,kind:af.kind,amount:af.amount,reason:af.reason.trim()}}),()=>{k3.current=newKey();setAf({...af,amount:"",reason:""});setDone(t("pp.adjustRequest"));})}/></div>
   </section>
  </div>

  <div>
   <section className={styles.panel} aria-label={t("pp.runs")}>
    <h2>{t("pp.runs")}</h2>
    {runs.phase==="loading"&&!runs.data&&<Loading/>}
    {runs.phase==="failed"&&runs.failure&&<FailureView failure={runs.failure} onRetry={runs.reload}/>}
    {runs.data&&runs.data.items.length===0&&runs.phase!=="failed"&&<Empty/>}
    {runs.data&&runs.data.items.length>0&&runs.phase!=="failed"&&<>
     <ul className={styles.list}>{runs.data.items.map((x:any)=><li key={x.id}>
      <button type="button" className={styles.row} aria-pressed={sel===x.id} onClick={()=>{setSel(x.id);setFailure(null);}}>
       <span><Badge tone={toneFor(x.status)}>{enumLabel(lang,"run",x.status)}</Badge> <strong>{x.employerCode} · {x.countryCode}</strong> {x.periodStart} → {x.periodEnd} · {t("pp.runNo",{n:x.runNo})}</span>
       <span className={styles.hint}>{t("pp.staffCount",{n:x.staffCount})} · {t("pp.totalNet")} {showMoney(x.totalNet,lang)} {String(x.currency).trim()}</span></button></li>)}</ul>
     <Pager page={runs.data.page} size={runs.data.size} total={runs.data.total} onPage={setPage}/></>}
   </section>
   <section className={styles.panel} aria-label={t("pp.lines")} aria-live="polite">
    {!sel&&<p>{t("pp.select")}</p>}
    {sel&&detail.phase==="loading"&&!run0&&<Loading/>}
    {sel&&detail.phase==="failed"&&detail.failure&&<FailureView failure={detail.failure} onRetry={detail.reload}/>}
    {run0&&<>
     <h2><Badge tone={toneFor(run0.status)}>{enumLabel(lang,"run",run0.status)}</Badge> {t("pp.runNo",{n:run0.runNo})}</h2>
     <dl className={styles.kv}><dt>{t("pp.totalNet")}</dt><dd><strong>{showMoney(run0.totalNet,lang)} {String(run0.currency).trim()}</strong></dd><dt>{t("pp.hash")}</dt><dd className={styles.mono}>{String(run0.batchHash).trim()}</dd></dl>
     <div className={styles.actions}><ActionButton label={t("pp.submit")} busy={busy} reason={why(["CALCULATED"])} onClick={()=>void run(()=>call("POST",`runs/${run0.id}/submit`,{body:{expectedVersion:run0.version}}),()=>setDone(t("pp.submit")))}/></div>
     <h3>{t("pp.adjust")}</h3>
     {(run0.adjustments??[]).length===0?<p>{t("common.none")}</p>:<ul className={styles.list}>{run0.adjustments.map((a:any)=><li key={a.id} className={styles.card}>
      <Badge tone={toneFor(a.status)}>{a.status}</Badge> {enumLabel(lang,"adj",a.kind)} · {showMoney(a.amount,lang)} <span className={styles.hint}>{a.reason}</span>
      <div className={styles.actions}>
       <ActionButton label={t("common.approve")} busy={busy} reason={a.status==="REQUESTED"?null:t("disabled.status",{status:a.status})} onClick={()=>void run(()=>call("POST",`adjustments/${a.id}/decision`,{body:{expectedVersion:a.version??1,approve:true}}),()=>setDone(t("common.approve")))}/>
       <ActionButton label={t("common.reject")} tone="secondary" reason={a.status==="REQUESTED"?null:t("disabled.status",{status:a.status})} onClick={()=>{setRej({kind:"adj",id:a.id,version:a.version??1});setRejNote("");}}/>
      </div></li>)}</ul>}
     <h3>{t("pp.lines")}</h3>
     <div className={styles.tableWrap}><table className={styles.table}><thead><tr><th scope="col">{t("common.staff")}</th><th scope="col">{t("pp.gross")}</th><th scope="col">{t("pp.deductions")}</th><th scope="col">{t("pp.net")}</th></tr></thead>
      <tbody>{run0.lines.map((l:any)=><tr key={l.id}><th scope="row">{l.familyName} <span className={styles.mono}>{l.staffNumber}</span></th><td className={styles.mono}>{showMoney(l.gross,lang)}</td><td className={styles.mono}>{showMoney(l.totalDeductions,lang)}</td><td className={styles.mono}><strong>{showMoney(l.net,lang)}</strong></td></tr>)}</tbody></table></div>
     <ul className={styles.cards}>{run0.lines.map((l:any)=><li key={l.id} className={styles.card}><strong>{l.familyName}</strong> <span className={styles.mono}>{l.staffNumber}</span>
      <dl><dt>{t("pp.gross")}</dt><dd className={styles.mono}>{showMoney(l.gross,lang)}</dd><dt>{t("pp.deductions")}</dt><dd className={styles.mono}>{showMoney(l.totalDeductions,lang)}</dd><dt>{t("pp.net")}</dt><dd className={styles.mono}><strong>{showMoney(l.net,lang)}</strong></dd></dl></li>)}</ul>
     <details><summary>{t("pp.breakdown")}</summary>{run0.lines.map((l:any)=><div key={l.id} className={styles.card}><strong>{l.staffNumber}</strong><ul>{(l.breakdown as any[]).map((b,i)=><li key={i} className={styles.mono}>{b.kind==="EARNING"?"+":"−"} {b.code}: {showMoney(b.amount,lang)}</li>)}</ul></div>)}</details>
    </>}
   </section>
  </div>
 </div>;
}
export function Preparation(){return <HrFrame active="preparation" title="pp.title" intro="pp.intro"><Inner/></HrFrame>;}
