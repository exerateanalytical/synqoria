"use client";
import {useEffect,useRef,useState} from "react";
import {call,newKey,showMoney,UUID_RE,validMoney} from "../../lib/hr/client";
import type {Failure,Page,Result} from "../../lib/hr/client";
import {enumLabel,safetyLabel} from "../../lib/hr/i18n";
import {ActionButton,ActionError,Badge,Empty,FailureView,Field,fmtTime,HrFrame,Loading,Notice,Pager,styles,toneFor,useHr,useLoad} from "./ui";

type Staff={id:string;staffNumber:string;givenName:string;familyName:string;status:string;classification:string;departmentCode:string;positionTitle:string;hireDate:string;terminationDate:string|null;
 lastEffectiveDate:string;version:number;changes:any[];history:any[];contracts?:any[]};
const KINDS=["ACTIVATE","SUSPEND","REINSTATE","TRANSFER","TERMINATE","REHIRE"];
const ALLOWED:Record<string,string[]>={ONBOARDING:["ACTIVATE","TERMINATE"],ACTIVE:["SUSPEND","TRANSFER","TERMINATE"],SUSPENDED:["REINSTATE","TRANSFER","TERMINATE"],TERMINATED:["REHIRE"]};

function Inner(){
 const {t,lang}=useHr();
 const [staffId,setStaffId]=useState("");const [q,setQ]=useState("");
 const [ch,setCh]=useState({kind:"SUSPEND",date:new Date().toISOString().slice(0,10),reason:"",dept:"",pos:""});
 const [ct,setCt]=useState({employer:"",country:"",currency:"",basis:"MONTHLY",base:"",from:new Date().toISOString().slice(0,10),to:""});
 const [acct,setAcct]=useState({token:"",mask:""});
 const [rej,setRej]=useState<{id:string;version:number;kind:"change"|"contract"}|null>(null);const [rejNote,setRejNote]=useState("");
 const [failure,setFailure]=useState<Failure|null>(null);const [done,setDone]=useState<string|null>(null);const [busy,setBusy]=useState(false);
 const key=useRef(newKey());
 useEffect(()=>{const id=new URLSearchParams(window.location.search).get("staffId")??"";if(UUID_RE.test(id))setStaffId(id);},[]);
 const org=useLoad<any>(s=>call("GET","organisation",{signal:s}),[]);
 const found=useLoad<Page<any>>(s=>q.trim().length>=2?call("GET","staff",{signal:s,query:{q:q.trim(),size:8}}):Promise.resolve({ok:true,status:200,data:{items:[],page:0,size:8,total:0}}),[q]);
 const rec=useLoad<Staff>(s=>staffId?call("GET",`staff/${staffId}`,{signal:s}):Promise.resolve({ok:false,kind:"notfound",status:0,code:"none",details:[]} as Failure),[staffId,done]);
 const queue=useLoad<Page<any>>(s=>call("GET","changes",{signal:s,query:{status:"REQUESTED",page:0,size:25}}),[done]);
 const s=staffId?rec.data:undefined;

 async function run<T>(fn:()=>Promise<Result<T>>,ok?:()=>void){
  if(busy)return;setBusy(true);setFailure(null);setDone(null);
  const r=await fn();setBusy(false);
  if(!r.ok){setFailure(r);if(r.code==="stale_version")rec.reload();return;}
  ok?.();
 }
 const allowed=s?ALLOWED[s.status]??[]:[];
 const changeReason=!s?t("disabled.select"):!allowed.includes(ch.kind)?t("disabled.status",{status:enumLabel(lang,"staff",s.status)}):!ch.reason.trim()||!ch.date?t("disabled.input"):
  ch.kind==="TRANSFER"&&(!UUID_RE.test(ch.dept)||!UUID_RE.test(ch.pos))?t("disabled.input"):null;
 const contractReason=!s?t("disabled.select"):!/^[A-Z0-9_-]{1,32}$/.test(ct.employer)||!/^[A-Z]{2}$/.test(ct.country)||!/^[A-Z]{3}$/.test(ct.currency)||!validMoney(ct.base)||!ct.from?t("disabled.input"):null;
 const accountReason=!s?t("disabled.select"):acct.token.length<8||!/^[A-Za-z0-9._:/+=-]+$/.test(acct.token)||!/^[*A-Za-z0-9 -]{4,20}$/.test(acct.mask)?t("disabled.input"):null;
 const posForDept=(org.data?.positions??[]).filter((p:any)=>!ch.dept||p.departmentId===ch.dept);

 return <>
  <ActionError failure={failure} onReload={()=>{rec.reload();setFailure(null);}}/>
  {done&&<Notice>{done}</Notice>}
  <div className={styles.cols}>
   <div>
    <section className={styles.panel} aria-label={t("emp.pick")}>
     <h2>{t("emp.pick")}</h2>
     <Field id="em-q" label={t("common.search")}><input id="em-q" type="search" className={styles.input} maxLength={80} value={q} onChange={e=>setQ(e.target.value)}/></Field>
     {found.data&&found.data.items.length>0&&<ul className={styles.list} style={{marginTop:8}}>{found.data.items.map((r:any)=><li key={r.id}>
      <button type="button" className={styles.row} aria-pressed={staffId===r.id} onClick={()=>{setStaffId(r.id);setFailure(null);}}>
       <strong>{r.familyName}, {r.givenName}</strong><span className={styles.mono}>{r.staffNumber}</span></button></li>)}</ul>}
     {!s&&!staffId&&<p>{t("emp.select")}</p>}
     {staffId&&rec.phase==="loading"&&!s&&<Loading/>}
     {staffId&&rec.phase==="failed"&&rec.failure&&<FailureView failure={rec.failure} onRetry={rec.reload}/>}
    </section>
    <section className={styles.panel} aria-label={t("emp.changes")}>
     <h2>{t("emp.pending")}</h2>
     {queue.phase==="loading"&&!queue.data&&<Loading/>}
     {queue.phase==="failed"&&queue.failure&&<FailureView failure={queue.failure} onRetry={queue.reload}/>}
     {queue.data&&queue.data.items.length===0&&<Empty hint={false}/>}
     {queue.data&&queue.data.items.length>0&&<ul className={styles.list}>{queue.data.items.map((c:any)=><li key={c.id} className={styles.card}>
      <Badge tone="info">{enumLabel(lang,"kind",c.kind)}</Badge> <strong>{c.familyName}</strong> <span className={styles.mono}>{c.staffNumber}</span>
      <div className={styles.hint}>{c.effectiveDate} · {c.reason}</div>
      <div className={styles.actions}>
       <ActionButton label={t("common.approve")} busy={busy} onClick={()=>void run(()=>call("POST",`changes/${c.id}/decision`,{body:{expectedVersion:c.version,approve:true}}),()=>setDone(t("common.approve")))}/>
       <ActionButton label={t("common.reject")} tone="secondary" onClick={()=>{setRej({id:c.id,version:c.version,kind:"change"});setRejNote("");}}/>
      </div></li>)}</ul>}
     <div className={styles.actions}><ActionButton label={t("emp.applyDue")} tone="secondary" busy={busy} onClick={()=>void run(()=>call("POST","changes/apply-due",{body:{}}),()=>setDone(t("emp.applyDue")))}/></div>
     {rej&&<div className={styles.formGrid} style={{marginTop:12}}>
      <Field id="rj-n" label={t("common.reason")} wide><input id="rj-n" className={styles.input} autoFocus maxLength={300} value={rejNote} onChange={e=>setRejNote(e.target.value)}/></Field>
      <div className={styles.actions}><ActionButton label={t("common.reject")} tone="danger" busy={busy} reason={rejNote.trim()?null:t("err.reason_required")}
       onClick={()=>void run(()=>call("POST",rej.kind==="change"?`changes/${rej.id}/decision`:`contracts/${rej.id}/decision`,{body:{expectedVersion:rej.version,approve:false,note:rejNote.trim()}}),()=>{setRej(null);setDone(t("common.reject"));})}/>
       <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={()=>setRej(null)}>{t("common.cancel")}</button></div></div>}
    </section>
   </div>

   <div>
    {s&&<section className={styles.panel} aria-label={t("nav.employment")} aria-live="polite">
     <h2>{s.familyName}, {s.givenName} <Badge tone={toneFor(s.status)}>{enumLabel(lang,"staff",s.status)}</Badge>{s.classification==="CONFIDENTIAL"&&<> <Badge tone="warn">{enumLabel(lang,"class",s.classification)}</Badge></>}</h2>
     <dl className={styles.kv}>
      <dt>{t("dir.number")}</dt><dd className={styles.mono}>{s.staffNumber}</dd><dt>{t("common.department")}</dt><dd>{s.departmentCode} · {s.positionTitle}</dd>
      <dt>{t("emp.hire")}</dt><dd>{s.hireDate}</dd><dt>{t("emp.last")}</dt><dd>{s.lastEffectiveDate}</dd>
      {s.terminationDate&&<><dt>{enumLabel(lang,"kind","TERMINATE")}</dt><dd>{s.terminationDate}</dd></>}<dt>{t("common.version")}</dt><dd>{s.version}</dd>
     </dl>
     <h3>{t("emp.requestChange")}</h3>
     <form onSubmit={e=>e.preventDefault()} noValidate><div className={styles.formGrid}>
      <Field id="ch-k" label={t("emp.kind")}><select id="ch-k" className={styles.input} value={ch.kind} onChange={e=>{setCh({...ch,kind:e.target.value});key.current=newKey();}}>
       {KINDS.map(k=><option key={k} value={k} disabled={!allowed.includes(k)}>{enumLabel(lang,"kind",k)}</option>)}</select></Field>
      <Field id="ch-d" label={t("emp.effective")}><input id="ch-d" type="date" className={styles.input} value={ch.date} onChange={e=>{setCh({...ch,date:e.target.value});key.current=newKey();}}/></Field>
      {ch.kind==="TRANSFER"&&<>
       <Field id="ch-td" label={t("emp.toDept")}><select id="ch-td" className={styles.input} value={ch.dept} onChange={e=>setCh({...ch,dept:e.target.value,pos:""})}><option value="">—</option>{(org.data?.departments??[]).map((d:any)=><option key={d.id} value={d.id}>{d.code}</option>)}</select></Field>
       <Field id="ch-tp" label={t("emp.toPos")}><select id="ch-tp" className={styles.input} value={ch.pos} onChange={e=>setCh({...ch,pos:e.target.value})}><option value="">—</option>{posForDept.map((p:any)=><option key={p.id} value={p.id}>{p.title}</option>)}</select></Field></>}
      <Field id="ch-r" label={t("common.reason")} wide><input id="ch-r" className={styles.input} maxLength={300} value={ch.reason} onChange={e=>{setCh({...ch,reason:e.target.value});key.current=newKey();}}/></Field>
     </div>
     <div className={styles.actions}><ActionButton label={t("emp.requestAction")} reason={changeReason} busy={busy}
      onClick={()=>void run(()=>call("POST",`staff/${s.id}/changes`,{key:key.current,body:{expectedVersion:s.version,kind:ch.kind,effectiveDate:ch.date,reason:ch.reason.trim(),toDepartmentId:ch.kind==="TRANSFER"?ch.dept:null,toPositionId:ch.kind==="TRANSFER"?ch.pos:null}}),()=>{key.current=newKey();setCh({...ch,reason:""});setDone(t("emp.requestAction"));})}/></div></form>
     <h3>{t("emp.changes")}</h3>
     {s.changes.length===0?<p>{t("common.none")}</p>:<ul className={styles.list}>{s.changes.map(c=><li key={c.id} className={styles.card}>
      <Badge tone={toneFor(c.status)}>{enumLabel(lang,"change",c.status)}</Badge> {enumLabel(lang,"kind",c.kind)} · {c.effectiveDate}<div className={styles.hint}>{c.reason}</div></li>)}</ul>}
     <h3>{t("common.history")}</h3>
     <ol className={styles.timeline}>{s.history.map(h=><li key={h.eventId}><strong>{h.action}</strong> {h.fromStatus?`${h.fromStatus} → `:""}{h.toStatus}<div className={styles.hint}>{fmtTime(lang,h.occurredAt)}</div></li>)}</ol>
    </section>}

    {s&&<section className={styles.panel} aria-label={t("emp.contracts")}>
     <h2>{t("emp.contracts")}</h2>
     {!s.contracts?<p className={styles.hint}>{t("emp.compHidden")}</p>:<>
      {s.contracts.length===0?<p>{t("emp.noContracts")}</p>:<ul className={styles.list}>{s.contracts.map(c=><li key={c.id} className={styles.card}>
       <Badge tone={toneFor(c.status)}>{enumLabel(lang,"contract",c.status)}</Badge> {t("emp.contractVersion",{v:c.contractVersion})} · {c.employerCode} · {c.countryCode}
       <div><strong>{showMoney(c.baseAmount,lang)} {String(c.currency).trim()}</strong> · {enumLabel(lang,"basis",c.payBasis)} · {c.effectiveFrom}{c.effectiveTo?` → ${c.effectiveTo}`:""}</div>
       <div className={styles.actions}>
        <ActionButton label={t("common.approve")} busy={busy} reason={c.status==="DRAFT"?null:t("disabled.status",{status:enumLabel(lang,"contract",c.status)})} onClick={()=>void run(()=>call("POST",`contracts/${c.id}/decision`,{body:{expectedVersion:c.version??1,approve:true}}),()=>setDone(t("common.approve")))}/>
        <ActionButton label={t("common.reject")} tone="secondary" reason={c.status==="DRAFT"?null:t("disabled.status",{status:enumLabel(lang,"contract",c.status)})} onClick={()=>{setRej({id:c.id,version:c.version??1,kind:"contract"});setRejNote("");}}/>
       </div></li>)}</ul>}
      <h3>{t("emp.draftContract")}</h3>
      <form onSubmit={e=>e.preventDefault()} noValidate><div className={styles.formGrid}>
       <Field id="ct-e" label={t("emp.employer")}><input id="ct-e" className={styles.input} value={ct.employer} maxLength={32} onChange={e=>setCt({...ct,employer:e.target.value.trim().toUpperCase()})}/></Field>
       <Field id="ct-c" label={t("emp.country")}><input id="ct-c" className={styles.input} value={ct.country} maxLength={2} onChange={e=>setCt({...ct,country:e.target.value.trim().toUpperCase()})}/></Field>
       <Field id="ct-u" label={t("emp.currency")}><input id="ct-u" className={styles.input} value={ct.currency} maxLength={3} onChange={e=>setCt({...ct,currency:e.target.value.trim().toUpperCase()})}/></Field>
       <Field id="ct-b" label={t("emp.basis")}><select id="ct-b" className={styles.input} value={ct.basis} onChange={e=>setCt({...ct,basis:e.target.value})}>{["MONTHLY","HOURLY"].map(b=><option key={b} value={b}>{enumLabel(lang,"basis",b)}</option>)}</select></Field>
       <Field id="ct-a" label={t("emp.base")}><input id="ct-a" inputMode="decimal" className={`${styles.input} ${styles.mono}`} value={ct.base} onChange={e=>setCt({...ct,base:e.target.value.trim()})}/></Field>
       <Field id="ct-f" label={t("emp.contractFrom")}><input id="ct-f" type="date" className={styles.input} value={ct.from} onChange={e=>setCt({...ct,from:e.target.value})}/></Field>
       <Field id="ct-t" label={t("emp.contractTo")}><input id="ct-t" type="date" className={styles.input} value={ct.to} onChange={e=>setCt({...ct,to:e.target.value})}/></Field>
      </div>
      <div className={styles.actions}><ActionButton label={t("emp.draftAction")} reason={contractReason} busy={busy}
       onClick={()=>void run(()=>call("POST",`staff/${s.id}/contracts`,{body:{employerCode:ct.employer,countryCode:ct.country,currency:ct.currency,payBasis:ct.basis,baseAmount:ct.base,positionId:(org.data?.positions??[]).find((p:any)=>p.title===s.positionTitle)?.id,effectiveFrom:ct.from,effectiveTo:ct.to||null}}),()=>{setCt({...ct,base:""});setDone(t("emp.draftAction"));})}/></div></form></>}
     <h3>{t("emp.account")}</h3>
     <p className={styles.hint}>{t("emp.accountHint")}</p>
     <div className={styles.formGrid}>
      <Field id="ac-t" label={t("emp.accountToken")}><input id="ac-t" autoComplete="off" className={`${styles.input} ${styles.mono}`} value={acct.token} onChange={e=>setAcct({...acct,token:e.target.value.trim()})}/></Field>
      <Field id="ac-m" label={t("emp.accountMask")}><input id="ac-m" autoComplete="off" className={styles.input} value={acct.mask} maxLength={20} onChange={e=>setAcct({...acct,mask:e.target.value.trim()})}/></Field>
     </div>
     <div className={styles.actions}><ActionButton label={t("emp.accountAction")} reason={accountReason} busy={busy} onClick={()=>void run(()=>call("POST",`staff/${s.id}/payment-account`,{body:{accountToken:acct.token,accountMask:acct.mask}}),()=>{setAcct({token:"",mask:""});setDone(t("emp.accountAction"));})}/></div>
    </section>}
   </div>
  </div>
 </>;
}
export function Employment(){return <HrFrame active="employment" title="emp.title" intro="emp.intro"><Inner/></HrFrame>;}
