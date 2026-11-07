"use client";
import {useRef,useState} from "react";
import {call,newKey,UUID_RE} from "../../lib/hr/client";
import type {Failure,Page,Result} from "../../lib/hr/client";
import {enumLabel} from "../../lib/hr/i18n";
import {ActionButton,ActionError,Badge,Empty,FailureView,Field,fmtTime,HrFrame,Loading,Notice,Pager,styles,toneFor,useHr,useLoad} from "./ui";

type Tab="attendance"|"leave"|"balances";

function Inner(){
 const {t,lang}=useHr();
 const [tab,setTab]=useState<Tab>("attendance");
 const [status,setStatus]=useState("");const [page,setPage]=useState(0);const [from,setFrom]=useState("");const [to,setTo]=useState("");
 const [lpage,setLpage]=useState(0);const [lstatus,setLstatus]=useState("SUBMITTED");
 const [cap,setCap]=useState({staff:"",date:new Date().toISOString().slice(0,10),inn:"",out:""});
 const [staffQ,setStaffQ]=useState("");
 const [fix,setFix]=useState<{id:string;version:number;mode:"dispute"|"correct"|"return"}|null>(null);const [fixNote,setFixNote]=useState("");const [fixIn,setFixIn]=useState("");const [fixOut,setFixOut]=useState("");
 const [lv,setLv]=useState({staff:"",type:"",from:"",to:""});
 const [bal,setBal]=useState("");
 const [lt,setLt]=useState({code:"",paid:true,accrual:"1.25",max:"30"});const [hol,setHol]=useState({date:"",name:""});
 const [lrej,setLrej]=useState<{id:string;version:number}|null>(null);const [lnote,setLnote]=useState("");
 const [failure,setFailure]=useState<Failure|null>(null);const [done,setDone]=useState<string|null>(null);const [busy,setBusy]=useState(false);
 const k1=useRef(newKey());const k2=useRef(newKey());
 const att=useLoad<Page<any>>(s=>call("GET","attendance",{signal:s,query:{status:status||undefined,from:from||undefined,to:to||undefined,page,size:25}}),[status,from,to,page,done]);
 const leaves=useLoad<Page<any>>(s=>call("GET","leave-requests",{signal:s,query:{status:lstatus||undefined,page:lpage,size:25}}),[lstatus,lpage,done]);
 const policy=useLoad<any>(s=>call("GET","leave-policy",{signal:s}),[done]);
 const hits=useLoad<Page<any>>(s=>staffQ.trim().length>=2?call("GET","staff",{signal:s,query:{q:staffQ.trim(),status:"ACTIVE",size:8}}):Promise.resolve({ok:true,status:200,data:{items:[],page:0,size:8,total:0}}),[staffQ]);
 const balances=useLoad<any[]>(s=>UUID_RE.test(bal)?call("GET",`leave-balances/${bal}`,{signal:s}):Promise.resolve({ok:true,status:200,data:[]}),[bal]);

 async function run<T>(fn:()=>Promise<Result<T>>,ok?:()=>void){
  if(busy)return;setBusy(true);setFailure(null);setDone(null);
  const r=await fn();setBusy(false);
  if(!r.ok){setFailure(r);return;}
  ok?.();
 }
 const iso=(local:string)=>new Date(local).toISOString();
 const capReason=!UUID_RE.test(cap.staff)||!cap.date||!cap.inn?t("disabled.input"):cap.out&&cap.out<=cap.inn?t("disabled.input"):null;
 const leaveReason=!UUID_RE.test(lv.staff)||!UUID_RE.test(lv.type)||!lv.from||!lv.to||lv.to<lv.from?t("disabled.input"):null;
 const pick=(x:any)=>{setCap(c=>({...c,staff:x.id}));setLv(c=>({...c,staff:x.id}));setBal(x.id);};

 return <>
  <div role="tablist" aria-label={t("at.title")} className={styles.tabs}>
   {([["attendance","at.tab.attendance"],["leave","at.tab.leave"],["balances","at.tab.balances"]] as const).map(([id,kk])=>
    <button key={id} role="tab" id={`at-tab-${id}`} aria-selected={tab===id} aria-controls={`at-panel-${id}`} type="button" className={styles.tab} onClick={()=>setTab(id)}>{t(kk)}</button>)}
  </div>
  <ActionError failure={failure} onReload={()=>{setFailure(null);att.reload();}}/>
  {done&&<Notice>{done}</Notice>}

  <section className={styles.panel} aria-label={t("common.staff")}>
   <Field id="at-sq" label={t("common.staff")}><input id="at-sq" type="search" className={styles.input} maxLength={80} value={staffQ} onChange={e=>setStaffQ(e.target.value)}/></Field>
   {hits.data&&hits.data.items.length>0&&<ul className={styles.list} style={{marginTop:8}}>{hits.data.items.map((x:any)=><li key={x.id}>
    <button type="button" className={styles.row} aria-pressed={cap.staff===x.id} onClick={()=>pick(x)}>{x.familyName}, {x.givenName} <span className={styles.mono}>{x.staffNumber}</span></button></li>)}</ul>}
  </section>

  {tab==="attendance"&&<div className={styles.cols} role="tabpanel" id="at-panel-attendance" aria-labelledby="at-tab-attendance">
   <section className={styles.panel} aria-label={t("at.tab.attendance")}>
    <div className={styles.filters}>
     <Field id="af-s" label={t("common.status")}><select id="af-s" className={styles.input} value={status} onChange={e=>{setStatus(e.target.value);setPage(0);}}>
      <option value="">{t("common.any")}</option>{["CAPTURED","DISPUTED","CORRECTED","APPROVED"].map(x=><option key={x} value={x}>{enumLabel(lang,"att",x)}</option>)}</select></Field>
     <Field id="af-f" label={t("common.from")}><input id="af-f" type="date" className={styles.input} value={from} onChange={e=>{setFrom(e.target.value);setPage(0);}}/></Field>
     <Field id="af-t" label={t("common.to")}><input id="af-t" type="date" className={styles.input} value={to} onChange={e=>{setTo(e.target.value);setPage(0);}}/></Field>
    </div>
    <div aria-busy={att.phase==="loading"} style={{marginTop:12}}>
     {att.phase==="loading"&&!att.data&&<Loading/>}
     {att.phase==="failed"&&att.failure&&<FailureView failure={att.failure} onRetry={att.reload}/>}
     {att.data&&att.data.items.length===0&&att.phase!=="failed"&&<Empty/>}
     {att.data&&att.data.items.length>0&&att.phase!=="failed"&&<>
      <ul className={styles.list}>{att.data.items.map((x:any)=><li key={x.id} className={styles.card}>
       <Badge tone={toneFor(x.status)}>{enumLabel(lang,"att",x.status)}</Badge> <strong>{x.familyName}</strong> <span className={styles.mono}>{x.staffNumber}</span> · {x.workDate}
       <div>{fmtTime(lang,x.clockIn)} → {x.clockOut?fmtTime(lang,x.clockOut):"…"}</div>{x.correctsId&&<div className={styles.hint}>{t("at.original")}</div>}{x.note&&<div className={styles.hint}>{x.note}</div>}
       <div className={styles.actions}>
        <ActionButton label={t("at.approveRec")} busy={busy} reason={x.status==="CAPTURED"?null:t("disabled.status",{status:enumLabel(lang,"att",x.status)})} onClick={()=>void run(()=>call("POST",`attendance/${x.id}/decision`,{body:{expectedVersion:x.version,approve:true}}),()=>setDone(t("at.approveRec")))}/>
        <ActionButton label={t("at.dispute")} tone="secondary" reason={x.status==="CAPTURED"?null:t("disabled.status",{status:enumLabel(lang,"att",x.status)})} onClick={()=>{setFix({id:x.id,version:x.version,mode:"dispute"});setFixNote("");}}/>
        <ActionButton label={t("at.correct")} tone="secondary" reason={["CAPTURED","DISPUTED"].includes(x.status)?null:t("disabled.status",{status:enumLabel(lang,"att",x.status)})} onClick={()=>{setFix({id:x.id,version:x.version,mode:"correct"});setFixNote("");setFixIn("");setFixOut("");}}/>
       </div></li>)}</ul>
      <Pager page={att.data.page} size={att.data.size} total={att.data.total} onPage={setPage}/></>}
    </div>
    {fix&&<div className={styles.formGrid} style={{marginTop:12}}>
     <Field id="fx-n" label={t("at.noteReq")} wide><input id="fx-n" className={styles.input} autoFocus maxLength={300} value={fixNote} onChange={e=>setFixNote(e.target.value)}/></Field>
     {fix.mode==="correct"&&<><Field id="fx-i" label={t("at.in")}><input id="fx-i" type="datetime-local" className={styles.input} value={fixIn} onChange={e=>setFixIn(e.target.value)}/></Field>
      <Field id="fx-o" label={t("at.out")}><input id="fx-o" type="datetime-local" className={styles.input} value={fixOut} onChange={e=>setFixOut(e.target.value)}/></Field></>}
     <div className={styles.actions}>
      <ActionButton label={fix.mode==="dispute"?t("at.dispute"):t("at.correct")} busy={busy} reason={!fixNote.trim()?t("err.reason_required"):fix.mode==="correct"&&(!fixIn||!fixOut||fixOut<=fixIn)?t("disabled.input"):null}
       onClick={()=>void run(()=>fix.mode==="dispute"?call("POST",`attendance/${fix.id}/dispute`,{body:{expectedVersion:fix.version,note:fixNote.trim()}}):call("POST",`attendance/${fix.id}/correction`,{body:{expectedVersion:fix.version,clockIn:iso(fixIn),clockOut:iso(fixOut),note:fixNote.trim()}}),()=>{setFix(null);setDone(t("common.updated"));})}/>
      <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={()=>setFix(null)}>{t("common.cancel")}</button></div></div>}
   </section>
   <section className={styles.panel} aria-label={t("at.capture")}>
    <h2>{t("at.capture")}</h2>
    <div className={styles.formGrid}>
     <Field id="ca-d" label={t("common.date")}><input id="ca-d" type="date" className={styles.input} value={cap.date} onChange={e=>{setCap({...cap,date:e.target.value});k1.current=newKey();}}/></Field><span/>
     <Field id="ca-i" label={t("at.in")}><input id="ca-i" type="datetime-local" className={styles.input} value={cap.inn} onChange={e=>{setCap({...cap,inn:e.target.value});k1.current=newKey();}}/></Field>
     <Field id="ca-o" label={t("at.out")}><input id="ca-o" type="datetime-local" className={styles.input} value={cap.out} onChange={e=>{setCap({...cap,out:e.target.value});k1.current=newKey();}}/></Field>
    </div>
    <div className={styles.actions}><ActionButton label={t("at.capture")} reason={capReason} busy={busy}
     onClick={()=>void run(()=>call("POST","attendance",{key:k1.current,body:{staffId:cap.staff,workDate:cap.date,clockIn:iso(cap.inn),clockOut:cap.out?iso(cap.out):null,source:"MANUAL",importRef:null}}),()=>{k1.current=newKey();setCap({...cap,inn:"",out:""});setDone(t("at.capture"));})}/></div>
   </section>
  </div>}

  {tab==="leave"&&<div className={styles.cols} role="tabpanel" id="at-panel-leave" aria-labelledby="at-tab-leave">
   <section className={styles.panel} aria-label={t("at.tab.leave")}>
    <Field id="lf-s" label={t("common.status")}><select id="lf-s" className={styles.input} value={lstatus} onChange={e=>{setLstatus(e.target.value);setLpage(0);}}>
     <option value="">{t("common.any")}</option>{["SUBMITTED","APPROVED","REJECTED","CANCELLED"].map(x=><option key={x} value={x}>{enumLabel(lang,"leave",x)}</option>)}</select></Field>
    <div aria-busy={leaves.phase==="loading"} style={{marginTop:12}}>
     {leaves.phase==="loading"&&!leaves.data&&<Loading/>}
     {leaves.phase==="failed"&&leaves.failure&&<FailureView failure={leaves.failure} onRetry={leaves.reload}/>}
     {leaves.data&&leaves.data.items.length===0&&leaves.phase!=="failed"&&<Empty/>}
     {leaves.data&&leaves.data.items.length>0&&leaves.phase!=="failed"&&<>
      <ul className={styles.list}>{leaves.data.items.map((x:any)=><li key={x.id} className={styles.card}>
       <Badge tone={toneFor(x.status)}>{enumLabel(lang,"leave",x.status)}</Badge> <strong>{x.familyName}</strong> · {x.leaveType}{x.paid?"":" ("+t("at.paid")+": "+t("common.none")+")"}
       <div>{x.fromDate} → {x.toDate} · {t("at.days",{n:x.days})}</div>{x.decisionNote&&<div className={styles.hint}>{x.decisionNote}</div>}
       <div className={styles.actions}>
        <ActionButton label={t("common.approve")} busy={busy} reason={x.status==="SUBMITTED"?null:t("disabled.status",{status:enumLabel(lang,"leave",x.status)})} onClick={()=>void run(()=>call("POST",`leave-requests/${x.id}/decision`,{body:{expectedVersion:x.version,approve:true}}),()=>setDone(t("common.approve")))}/>
        <ActionButton label={t("common.reject")} tone="secondary" reason={x.status==="SUBMITTED"?null:t("disabled.status",{status:enumLabel(lang,"leave",x.status)})} onClick={()=>{setLrej({id:x.id,version:x.version});setLnote("");}}/>
       </div></li>)}</ul>
      <Pager page={leaves.data.page} size={leaves.data.size} total={leaves.data.total} onPage={setLpage}/></>}
    </div>
    {lrej&&<div className={styles.formGrid} style={{marginTop:12}}>
     <Field id="lr-n" label={t("common.reason")} wide><input id="lr-n" className={styles.input} autoFocus maxLength={300} value={lnote} onChange={e=>setLnote(e.target.value)}/></Field>
     <div className={styles.actions}><ActionButton label={t("common.reject")} tone="danger" busy={busy} reason={lnote.trim()?null:t("err.reason_required")}
      onClick={()=>void run(()=>call("POST",`leave-requests/${lrej.id}/decision`,{body:{expectedVersion:lrej.version,approve:false,note:lnote.trim()}}),()=>{setLrej(null);setDone(t("common.reject"));})}/>
      <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={()=>setLrej(null)}>{t("common.cancel")}</button></div></div>}
   </section>
   <section className={styles.panel} aria-label={t("at.leaveNew")}>
    <h2>{t("at.leaveNew")}</h2>
    <div className={styles.formGrid}>
     <Field id="lv-t" label={t("at.leaveType")} wide><select id="lv-t" className={styles.input} value={lv.type} onChange={e=>{setLv({...lv,type:e.target.value});k2.current=newKey();}}>
      <option value="">—</option>{(policy.data?.leaveTypes??[]).map((x:any)=><option key={x.id} value={x.id}>{x.code}</option>)}</select></Field>
     <Field id="lv-f" label={t("at.leaveFrom")}><input id="lv-f" type="date" className={styles.input} value={lv.from} onChange={e=>{setLv({...lv,from:e.target.value});k2.current=newKey();}}/></Field>
     <Field id="lv-e" label={t("at.leaveTo")}><input id="lv-e" type="date" className={styles.input} value={lv.to} onChange={e=>{setLv({...lv,to:e.target.value});k2.current=newKey();}}/></Field>
    </div>
    <div className={styles.actions}><ActionButton label={t("at.leaveRequest")} reason={leaveReason} busy={busy}
     onClick={()=>void run(()=>call("POST","leave-requests",{key:k2.current,body:{staffId:lv.staff,leaveTypeId:lv.type,fromDate:lv.from,toDate:lv.to}}),()=>{k2.current=newKey();setDone(t("at.leaveRequest"));})}/></div>
   </section>
  </div>}

  {tab==="balances"&&<div className={styles.cols} role="tabpanel" id="at-panel-balances" aria-labelledby="at-tab-balances">
   <section className={styles.panel} aria-label={t("at.lookup")}>
    <h2>{t("at.lookup")}</h2>
    {balances.data&&balances.data.length>0?<dl className={styles.kv}>{balances.data.map((b:any)=><div key={b.leaveType} style={{display:"contents"}}><dt>{b.leaveType}{b.paid?"":" ("+t("at.paid")+": "+t("common.none")+")"}</dt><dd>{b.balance}</dd></div>)}</dl>:<p>{t("common.none")}</p>}
    <h3>{t("at.holidays")}</h3>
    <ul className={styles.list}>{(policy.data?.holidays??[]).map((h:any)=><li key={h.holidayDate} className={styles.card}>{h.holidayDate} · {h.name}</li>)}</ul>
    <div className={styles.formGrid}>
     <Field id="hd-d" label={t("common.date")}><input id="hd-d" type="date" className={styles.input} value={hol.date} onChange={e=>setHol({...hol,date:e.target.value})}/></Field>
     <Field id="hd-n" label={t("at.holidayName")}><input id="hd-n" className={styles.input} maxLength={120} value={hol.name} onChange={e=>setHol({...hol,name:e.target.value})}/></Field>
    </div>
    <div className={styles.actions}><ActionButton label={t("at.addHoliday")} busy={busy} reason={hol.date&&hol.name.trim()?null:t("disabled.input")} onClick={()=>void run(()=>call("POST","holidays",{body:{date:hol.date,name:hol.name.trim()}}),()=>{setHol({date:"",name:""});setDone(t("at.addHoliday"));})}/></div>
   </section>
   <section className={styles.panel} aria-label={t("at.types")}>
    <h2>{t("at.types")}</h2>
    <ul className={styles.list}>{(policy.data?.leaveTypes??[]).map((x:any)=><li key={x.id} className={styles.card}><strong>{x.code}</strong> · {t("at.paid")}: {x.paid?t("common.approve"):t("common.none")} · {t("at.accrual")} {x.accrualPerMonth} · {t("at.max")} {x.maxBalance}</li>)}</ul>
    <div className={styles.formGrid}>
     <Field id="lt-c" label={t("at.code")}><input id="lt-c" className={styles.input} value={lt.code} maxLength={32} onChange={e=>setLt({...lt,code:e.target.value.trim().toUpperCase()})}/></Field>
     <label className={styles.check}><input type="checkbox" checked={lt.paid} onChange={e=>setLt({...lt,paid:e.target.checked})}/>{t("at.paid")}</label>
     <Field id="lt-a" label={t("at.accrual")}><input id="lt-a" inputMode="decimal" className={styles.input} value={lt.accrual} onChange={e=>setLt({...lt,accrual:e.target.value.trim()})}/></Field>
     <Field id="lt-m" label={t("at.max")}><input id="lt-m" inputMode="decimal" className={styles.input} value={lt.max} onChange={e=>setLt({...lt,max:e.target.value.trim()})}/></Field>
    </div>
    <div className={styles.actions}><ActionButton label={t("at.addType")} busy={busy} reason={/^[A-Z0-9_-]{1,32}$/.test(lt.code)&&/^\d{1,4}(\.\d{1,4})?$/.test(lt.accrual)&&/^\d{1,6}(\.\d{1,2})?$/.test(lt.max)?null:t("disabled.input")}
     onClick={()=>void run(()=>call("POST","leave-types",{body:{code:lt.code,paid:lt.paid,accrualPerMonth:lt.accrual,maxBalance:lt.max}}),()=>{setLt({...lt,code:""});setDone(t("at.addType"));})}/></div>
   </section>
  </div>}
 </>;
}
export function Attendance(){return <HrFrame active="attendance" title="at.title" intro="at.intro"><Inner/></HrFrame>;}
