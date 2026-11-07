"use client";
import {useRef,useState} from "react";
import {call,newKey,showMoney,validMoney} from "../../lib/hr/client";
import type {Failure,Page,Result} from "../../lib/hr/client";
import {enumLabel,safetyLabel} from "../../lib/hr/i18n";
import {ActionButton,ActionError,Badge,Empty,FailureView,Field,fmtTime,HrFrame,Loading,Notice,Pager,styles,toneFor,useHr,useLoad} from "./ui";

function download(name:string,content:string){
 const url=URL.createObjectURL(new Blob([content],{type:"text/csv;charset=utf-8"}));
 const a=document.createElement("a");a.href=url;a.download=name;a.rel="noopener";document.body.appendChild(a);a.click();a.remove();URL.revokeObjectURL(url);
}

function Inner(){
 const {t,lang}=useHr();
 const [status,setStatus]=useState("SUBMITTED");const [page,setPage]=useState(0);const [sel,setSel]=useState<string|null>(null);
 const [mode,setMode]=useState<null|{kind:"reject"|"reverse"}>(null);const [note,setNote]=useState("");
 const [conf,setConf]=useState<{id:string;amount:string;currency:string;ref:string;at:string;fail:boolean;reason:string}|null>(null);
 const [exported,setExported]=useState<{filename:string;content:string}|null>(null);
 const [failure,setFailure]=useState<Failure|null>(null);const [done,setDone]=useState<string|null>(null);const [busy,setBusy]=useState(false);
 const [report,setReport]=useState<any>(null);
 const key=useRef(newKey());
 const runs=useLoad<Page<any>>(s=>call("GET","runs",{signal:s,query:{status:status||undefined,page,size:25}}),[status,page,done]);
 const detail=useLoad<any>(s=>sel?call("GET",`runs/${sel}`,{signal:s}):Promise.resolve({ok:false,kind:"notfound",status:0,code:"none",details:[]} as Failure),[sel,done]);
 const pays=useLoad<{items:any[]}>(s=>sel?call("GET",`runs/${sel}/payments`,{signal:s}):Promise.resolve({ok:true,status:200,data:{items:[]}}),[sel,done]);
 const rec=useLoad<any>(s=>sel?call("GET",`runs/${sel}/reconciliation`,{signal:s}):Promise.resolve({ok:false,kind:"notfound",status:0,code:"none",details:[]} as Failure),[sel,done]);
 const r=sel?detail.data:undefined;

 async function run<T>(fn:()=>Promise<Result<T>>,ok?:(d:T)=>void){
  if(busy)return;setBusy(true);setFailure(null);setDone(null);
  const res=await fn();setBusy(false);
  if(!res.ok){setFailure(res);if(res.code==="stale_version")detail.reload();return;}
  ok?.(res.data);
 }
 const why=(allowed:string[]):string|null=>!r?t("disabled.select"):allowed.includes(r.status)?null:t("disabled.status",{status:enumLabel(lang,"run",r.status)});
 const confReason=!conf?null:conf.fail?(conf.reason.trim()?null:t("err.reason_required")):!/^[A-Za-z0-9._:/-]{6,120}$/.test(conf.ref)||!validMoney(conf.amount)||!/^[A-Z]{3}$/.test(conf.currency)||!conf.at?t("disabled.input"):null;

 return <div className={styles.cols}>
  <section className={styles.panel} aria-label={t("pa.runs")}>
   <h2>{t("pa.runs")}</h2>
   <Field id="rn-s" label={t("common.status")}><select id="rn-s" className={styles.input} value={status} onChange={e=>{setStatus(e.target.value);setPage(0);}}>
    <option value="">{t("common.any")}</option>{["CALCULATED","SUBMITTED","APPROVED","REJECTED","REVERSED","RECONCILED"].map(x=><option key={x} value={x}>{enumLabel(lang,"run",x)}</option>)}</select></Field>
   <div className={styles.actions}><button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={runs.reload}>{t("common.refresh")}</button></div>
   <div aria-busy={runs.phase==="loading"} style={{marginTop:12}}>
    {runs.phase==="loading"&&!runs.data&&<Loading/>}
    {runs.phase==="failed"&&runs.failure&&<FailureView failure={runs.failure} onRetry={runs.reload}/>}
    {runs.data&&runs.data.items.length===0&&runs.phase!=="failed"&&<Empty/>}
    {runs.data&&runs.data.items.length>0&&runs.phase!=="failed"&&<>
     <ul className={styles.list}>{runs.data.items.map((x:any)=><li key={x.id}>
      <button type="button" className={styles.row} aria-pressed={sel===x.id} onClick={()=>{setSel(x.id);setFailure(null);setMode(null);setConf(null);setExported(null);}}>
       <span><Badge tone={toneFor(x.status)}>{enumLabel(lang,"run",x.status)}</Badge> <strong>{x.employerCode} · {x.countryCode}</strong> {x.periodStart} → {x.periodEnd} · {t("pp.runNo",{n:x.runNo})}</span>
       <span className={styles.hint}>{t("pp.staffCount",{n:x.staffCount})} · {t("pp.totalNet")} {showMoney(x.totalNet,lang)} {String(x.currency).trim()}</span></button></li>)}</ul>
     <Pager page={runs.data.page} size={runs.data.size} total={runs.data.total} onPage={setPage}/></>}
   </div>
  </section>

  <div>
   <ActionError failure={failure} onReload={()=>{detail.reload();setFailure(null);}}/>
   {done&&<Notice>{done}</Notice>}
   <section className={styles.panel} aria-label={t("pa.runs")} aria-live="polite">
    {!sel&&<p>{t("pa.select")}</p>}
    {sel&&detail.phase==="loading"&&!r&&<Loading/>}
    {sel&&detail.phase==="failed"&&detail.failure&&<FailureView failure={detail.failure} onRetry={detail.reload}/>}
    {r&&<>
     <h2><Badge tone={toneFor(r.status)}>{enumLabel(lang,"run",r.status)}</Badge> {t("pp.runNo",{n:r.runNo})}</h2>
     <dl className={styles.kv}><dt>{t("pp.totalNet")}</dt><dd><strong>{showMoney(r.totalNet,lang)} {String(r.currency).trim()}</strong></dd><dt>{t("pp.hash")}</dt><dd className={styles.mono}>{String(r.batchHash).trim()}</dd></dl>
     <div className={styles.actions}>
      <ActionButton label={t("pa.approve")} busy={busy} reason={why(["SUBMITTED"])} onClick={()=>void run(()=>call("POST",`runs/${r.id}/decision`,{body:{expectedVersion:r.version,approve:true}}),()=>setDone(t("pa.approve")))}/>
      <ActionButton label={t("pa.reject")} tone="secondary" reason={why(["SUBMITTED"])} onClick={()=>{setMode({kind:"reject"});setNote("");}}/>
      <ActionButton label={t("pa.reverse")} tone="danger" reason={why(["APPROVED"])} onClick={()=>{setMode({kind:"reverse"});setNote("");}}/>
      <ActionButton label={t("pa.export")} tone="secondary" busy={busy} reason={why(["APPROVED","RECONCILED"])} onClick={()=>void run<any>(()=>call("POST",`runs/${r.id}/export`,{body:{}}),d=>{setExported(d);setDone(t("pa.exportDone"));})}/>
     </div>
     {exported&&<p><button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={()=>download(exported.filename,exported.content)}>{t("pa.download")}: {exported.filename}</button></p>}
     {mode&&<div className={styles.formGrid} style={{marginTop:12}}>
      <Field id="md-n" label={mode.kind==="reject"?t("common.reason"):t("pa.reverseReason")} wide><input id="md-n" className={styles.input} autoFocus maxLength={300} value={note} onChange={e=>setNote(e.target.value)}/></Field>
      <div className={styles.actions}><ActionButton label={mode.kind==="reject"?t("pa.reject"):t("pa.reverse")} tone="danger" busy={busy} reason={note.trim()?null:t("err.reason_required")}
       onClick={()=>void run(()=>mode.kind==="reject"?call("POST",`runs/${r.id}/decision`,{body:{expectedVersion:r.version,approve:false,note:note.trim()}}):call("POST",`runs/${r.id}/reversal`,{body:{expectedVersion:r.version,reason:note.trim()}}),()=>{setMode(null);setDone(t("common.updated"));})}/>
       <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={()=>setMode(null)}>{t("common.cancel")}</button></div></div>}
     <h3>{t("pp.lines")}</h3>
     <div className={styles.tableWrap}><table className={styles.table}><thead><tr><th scope="col">{t("common.staff")}</th><th scope="col">{t("pp.gross")}</th><th scope="col">{t("pp.deductions")}</th><th scope="col">{t("pp.net")}</th></tr></thead>
      <tbody>{r.lines.map((l:any)=><tr key={l.id}><th scope="row">{l.familyName} <span className={styles.mono}>{l.staffNumber}</span></th><td className={styles.mono}>{showMoney(l.gross,lang)}</td><td className={styles.mono}>{showMoney(l.totalDeductions,lang)}</td><td className={styles.mono}><strong>{showMoney(l.net,lang)}</strong></td></tr>)}</tbody></table></div>
     <ul className={styles.cards}>{r.lines.map((l:any)=><li key={l.id} className={styles.card}><strong>{l.familyName}</strong> <span className={styles.mono}>{l.staffNumber}</span>
      <dl><dt>{t("pp.gross")}</dt><dd className={styles.mono}>{showMoney(l.gross,lang)}</dd><dt>{t("pp.net")}</dt><dd className={styles.mono}><strong>{showMoney(l.net,lang)}</strong></dd></dl></li>)}</ul>
    </>}
   </section>

   {r&&["APPROVED","RECONCILED","REVERSED"].includes(r.status)&&<section className={styles.panel} aria-label={t("pa.payments")}>
    <h2>{t("pa.payments")}</h2>
    <div className={`${styles.banner} ${styles.bannerInfo}`} role="note">{t("pa.notPaid")}</div>
    <div className={styles.actions}><ActionButton label={t("pa.instruct")} busy={busy} reason={why(["APPROVED"])} onClick={()=>void run(()=>call("POST",`runs/${r.id}/payments`,{body:{expectedVersion:r.version}}),()=>setDone(t("pa.instruct")))}/></div>
    {pays.data&&pays.data.items.length>0&&<ul className={styles.list}>{pays.data.items.map((p:any)=><li key={p.id} className={styles.card}>
     <Badge tone={toneFor(p.status)}>{enumLabel(lang,"pay",p.status)}</Badge> <strong>{p.staffNumber}</strong> · {showMoney(p.amount,lang)} {String(p.currency).trim()}
     <div className={styles.hint}>{t("pa.reference")}: <span className={styles.mono}>{p.reference}</span> · {t("pa.mask")}: {p.accountMask}{p.providerRef?<> · <span className={styles.mono}>{p.providerRef}</span></>:null}{p.confirmedAt?` · ${fmtTime(lang,p.confirmedAt)}`:""}</div>
     {p.failureReason&&<div className={styles.hint}>{p.failureReason}</div>}
     <div className={styles.actions}><ActionButton label={t("pa.confirm")} tone="secondary" reason={p.status==="INSTRUCTED"?null:t("disabled.status",{status:enumLabel(lang,"pay",p.status)})}
      onClick={()=>{setConf({id:p.id,amount:"",currency:String(p.currency).trim(),ref:"",at:"",fail:false,reason:""});key.current=newKey();}}/></div></li>)}</ul>}
    {conf&&<div className={styles.formGrid} style={{marginTop:12}}>
     <label className={styles.check}><input type="checkbox" checked={conf.fail} onChange={e=>setConf({...conf,fail:e.target.checked})}/>{t("pa.fail")}</label><span/>
     {!conf.fail?<>
      <Field id="cf-r" label={t("pa.providerRef")}><input id="cf-r" autoComplete="off" className={`${styles.input} ${styles.mono}`} value={conf.ref} onChange={e=>{setConf({...conf,ref:e.target.value.trim()});key.current=newKey();}}/></Field>
      <Field id="cf-a" label={t("pa.confirmedAmount")}><input id="cf-a" inputMode="decimal" className={`${styles.input} ${styles.mono}`} value={conf.amount} onChange={e=>{setConf({...conf,amount:e.target.value.trim()});key.current=newKey();}}/></Field>
      <Field id="cf-c" label={t("pa.confirmCurrency")}><input id="cf-c" className={styles.input} maxLength={3} value={conf.currency} onChange={e=>setConf({...conf,currency:e.target.value.trim().toUpperCase()})}/></Field>
      <Field id="cf-t" label={t("pa.confirmAt")}><input id="cf-t" type="datetime-local" className={styles.input} value={conf.at} onChange={e=>setConf({...conf,at:e.target.value})}/></Field></>
      :<Field id="cf-f" label={t("pa.failReason")} wide><input id="cf-f" className={styles.input} maxLength={300} value={conf.reason} onChange={e=>setConf({...conf,reason:e.target.value})}/></Field>}
     <div className={styles.actions}><ActionButton label={conf.fail?t("pa.fail"):t("pa.confirm")} busy={busy} reason={confReason}
      onClick={()=>void run(()=>call("POST",`payments/${conf.id}/confirmation`,{key:key.current,body:conf.fail?{success:false,failureReason:conf.reason.trim()}:{success:true,providerRef:conf.ref,amount:conf.amount,currency:conf.currency,confirmedAt:new Date(conf.at).toISOString()}}),()=>{setConf(null);key.current=newKey();setDone(t("common.updated"));})}/>
      <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={()=>setConf(null)}>{t("common.cancel")}</button></div></div>}
   </section>}

   {r&&["APPROVED","RECONCILED"].includes(r.status)&&<section className={styles.panel} aria-label={t("pa.reconciliation")}>
    <h2>{t("pa.reconciliation")}</h2>
    {rec.phase==="loading"&&!rec.data&&<Loading/>}
    {rec.phase==="failed"&&rec.failure&&<FailureView failure={rec.failure} onRetry={rec.reload}/>}
    {rec.data&&<>
     <dl className={styles.kv}><dt>{t("pa.expected")}</dt><dd className={styles.mono}>{showMoney(rec.data.expectedTotal,lang)}</dd><dt>{t("pa.confirmedTotal")}</dt><dd className={styles.mono}>{showMoney(rec.data.confirmedTotal,lang)}</dd></dl>
     {rec.data.reconciled?<Notice>{t("pa.clean")}</Notice>:<><h3>{t("pa.discrepancies")}</h3><ul className={styles.list}>{rec.data.discrepancies.map((d:any,i:number)=><li key={i} className={styles.card}><Badge tone="danger">{safetyLabel(lang,d.kind)}</Badge> <span className={styles.mono}>{d.staffNumber}</span></li>)}</ul></>}
     <ul className={styles.list}>{rec.data.lines.map((l:any)=><li key={l.staffId} className={styles.card}><span className={styles.mono}>{l.staffNumber}</span> {l.familyName} · {showMoney(l.expected,lang)} · <Badge tone={toneFor(l.state)}>{enumLabel(lang,"recon",l.state)}</Badge></li>)}</ul>
     <div className={styles.actions}><ActionButton label={t("pa.reconcile")} busy={busy} reason={why(["APPROVED"])} onClick={()=>void run(()=>call("POST",`runs/${r.id}/reconcile`,{body:{expectedVersion:r.version}}),()=>{setDone(t("pa.reconcile"));})}/></div></>}
   </section>}
  </div>
 </div>;
}
export function Approval(){return <HrFrame active="approval" title="pa.title" intro="pa.intro"><Inner/></HrFrame>;}
