"use client";
import {useRef,useState} from "react";
import {call,newKey,UUID_RE} from "../../lib/hr/client";
import type {Failure,Page,Result} from "../../lib/hr/client";
import {enumLabel,safetyLabel} from "../../lib/hr/i18n";
import {ActionButton,ActionError,Badge,Empty,FailureView,Field,fmtTime,HrFrame,Loading,Notice,Pager,styles,toneFor,useHr,useLoad} from "./ui";

function Inner(){
 const {t,lang}=useHr();
 const [dept,setDept]=useState("");const [status,setStatus]=useState("");const [page,setPage]=useState(0);
 const [sel,setSel]=useState<string|null>(null);
 const [nr,setNr]=useState({dept:"",start:"",end:""});
 const [sh,setSh]=useState({template:"",staff:"",date:"",ot:false});
 const [tp,setTp]=useState({code:"",start:"07:00",minutes:"480"});const [cov,setCov]=useState({dept:"",template:"",min:"1"});
 const [note,setNote]=useState("");const [rejecting,setRejecting]=useState(false);
 const [staffQ,setStaffQ]=useState("");
 const [failure,setFailure]=useState<Failure|null>(null);const [done,setDone]=useState<string|null>(null);const [busy,setBusy]=useState(false);
 const [chk,setChk]=useState<any>(null);
 const key=useRef(newKey());
 const org=useLoad<any>(s=>call("GET","organisation",{signal:s}),[]);
 const tpls=useLoad<any[]>(s=>call("GET","shift-templates",{signal:s}),[done]);
 const list=useLoad<Page<any>>(s=>call("GET","rosters",{signal:s,query:{departmentId:dept||undefined,status:status||undefined,page,size:25}}),[dept,status,page,done]);
 const detail=useLoad<any>(s=>sel?call("GET",`rosters/${sel}`,{signal:s}):Promise.resolve({ok:false,kind:"notfound",status:0,code:"none",details:[]} as Failure),[sel,done]);
 const staffHits=useLoad<Page<any>>(s=>staffQ.trim().length>=2?call("GET","staff",{signal:s,query:{q:staffQ.trim(),status:"ACTIVE",size:8}}):Promise.resolve({ok:true,status:200,data:{items:[],page:0,size:8,total:0}}),[staffQ]);
 const r=sel?detail.data:undefined;

 async function run<T>(fn:()=>Promise<Result<T>>,ok?:(d:T)=>void){
  if(busy)return;setBusy(true);setFailure(null);setDone(null);
  const res=await fn();setBusy(false);
  if(!res.ok){setFailure(res);if(res.code==="stale_version")detail.reload();return;}
  ok?.(res.data);
 }
 const why=(allowed:string[]):string|null=>!r?t("disabled.select"):allowed.includes(r.status)?null:t("disabled.status",{status:enumLabel(lang,"roster",r.status)});
 const newReason=!UUID_RE.test(nr.dept)||!nr.start||!nr.end||nr.end<nr.start?t("disabled.input"):null;
 const shiftReason=why(["DRAFT"])??(!UUID_RE.test(sh.template)||!sh.date||(sh.staff!==""&&!UUID_RE.test(sh.staff))?t("disabled.input"):null);

 return <div className={styles.cols}>
  <div>
   <section className={styles.panel} aria-label={t("ro.list")}>
    <h2>{t("ro.list")}</h2>
    <div className={styles.filters}>
     <Field id="rl-d" label={t("common.department")}><select id="rl-d" className={styles.input} value={dept} onChange={e=>{setDept(e.target.value);setPage(0);}}>
      <option value="">{t("common.any")}</option>{(org.data?.departments??[]).map((d:any)=><option key={d.id} value={d.id}>{d.code}</option>)}</select></Field>
     <Field id="rl-s" label={t("common.status")}><select id="rl-s" className={styles.input} value={status} onChange={e=>{setStatus(e.target.value);setPage(0);}}>
      <option value="">{t("common.any")}</option>{["DRAFT","SUBMITTED","PUBLISHED","SUPERSEDED","REJECTED"].map(x=><option key={x} value={x}>{enumLabel(lang,"roster",x)}</option>)}</select></Field>
    </div>
    <div aria-busy={list.phase==="loading"} style={{marginTop:12}}>
     {list.phase==="loading"&&!list.data&&<Loading/>}
     {list.phase==="failed"&&list.failure&&<FailureView failure={list.failure} onRetry={list.reload}/>}
     {list.data&&list.data.items.length===0&&list.phase!=="failed"&&<Empty/>}
     {list.data&&list.data.items.length>0&&list.phase!=="failed"&&<>
      <ul className={styles.list}>{list.data.items.map((x:any)=><li key={x.id}>
       <button type="button" className={styles.row} aria-pressed={sel===x.id} onClick={()=>{setSel(x.id);setChk(null);setFailure(null);setRejecting(false);}}>
        <span><Badge tone={toneFor(x.status)}>{enumLabel(lang,"roster",x.status)}</Badge> <strong>{x.departmentCode}</strong> · {x.periodStart} → {x.periodEnd}</span>
        <span className={styles.hint}>{t("ro.revision",{n:x.revision})}</span></button></li>)}</ul>
      <Pager page={list.data.page} size={list.data.size} total={list.data.total} onPage={setPage}/></>}
    </div>
    <h3>{t("ro.new")}</h3>
    <div className={styles.formGrid}>
     <Field id="rn-d" label={t("common.department")} wide><select id="rn-d" className={styles.input} value={nr.dept} onChange={e=>{setNr({...nr,dept:e.target.value});key.current=newKey();}}>
      <option value="">—</option>{(org.data?.departments??[]).map((d:any)=><option key={d.id} value={d.id}>{d.code}</option>)}</select></Field>
     <Field id="rn-s" label={t("ro.periodStart")}><input id="rn-s" type="date" className={styles.input} value={nr.start} onChange={e=>{setNr({...nr,start:e.target.value});key.current=newKey();}}/></Field>
     <Field id="rn-e" label={t("ro.periodEnd")}><input id="rn-e" type="date" className={styles.input} value={nr.end} onChange={e=>{setNr({...nr,end:e.target.value});key.current=newKey();}}/></Field>
    </div>
    <div className={styles.actions}><ActionButton label={t("ro.create")} reason={newReason} busy={busy}
     onClick={()=>void run<any>(()=>call("POST","rosters",{key:key.current,body:{departmentId:nr.dept,periodStart:nr.start,periodEnd:nr.end}}),d=>{key.current=newKey();setSel(d.id);setDone(t("ro.create"));})}/></div>
   </section>
   <section className={styles.panel} aria-label={t("ro.templates")}>
    <h2>{t("ro.templates")}</h2>
    <ul className={styles.list}>{(tpls.data??[]).map((x:any)=><li key={x.id} className={styles.card}><span className={styles.mono}>{x.code}</span> {String(x.startTime).slice(0,5)} · {x.durationMinutes} min</li>)}</ul>
    <div className={styles.formGrid}>
     <Field id="tp-c" label={t("ro.templateCode")}><input id="tp-c" className={styles.input} value={tp.code} maxLength={32} onChange={e=>setTp({...tp,code:e.target.value.trim().toUpperCase()})}/></Field>
     <Field id="tp-s" label={t("ro.start")}><input id="tp-s" className={styles.input} value={tp.start} maxLength={5} onChange={e=>setTp({...tp,start:e.target.value.trim()})}/></Field>
     <Field id="tp-m" label={t("ro.minutes")}><input id="tp-m" inputMode="numeric" className={styles.input} value={tp.minutes} onChange={e=>setTp({...tp,minutes:e.target.value.trim()})}/></Field>
    </div>
    <div className={styles.actions}><ActionButton label={t("ro.addTemplate")} busy={busy} reason={/^[A-Z0-9_-]{1,32}$/.test(tp.code)&&/^\d{2}:\d{2}$/.test(tp.start)&&/^\d{2,3}$/.test(tp.minutes)?null:t("disabled.input")}
     onClick={()=>void run(()=>call("POST","shift-templates",{body:{code:tp.code,startTime:tp.start,durationMinutes:Number(tp.minutes)}}),()=>{setTp({...tp,code:""});setDone(t("ro.addTemplate"));})}/></div>
    <h3>{t("ro.coverage")}</h3>
    <div className={styles.formGrid}>
     <Field id="cv-d" label={t("common.department")}><select id="cv-d" className={styles.input} value={cov.dept} onChange={e=>setCov({...cov,dept:e.target.value})}><option value="">—</option>{(org.data?.departments??[]).map((d:any)=><option key={d.id} value={d.id}>{d.code}</option>)}</select></Field>
     <Field id="cv-t" label={t("ro.template")}><select id="cv-t" className={styles.input} value={cov.template} onChange={e=>setCov({...cov,template:e.target.value})}><option value="">—</option>{(tpls.data??[]).map((x:any)=><option key={x.id} value={x.id}>{x.code}</option>)}</select></Field>
     <Field id="cv-m" label={t("ro.minStaff")}><input id="cv-m" inputMode="numeric" className={styles.input} value={cov.min} onChange={e=>setCov({...cov,min:e.target.value.trim()})}/></Field>
    </div>
    <div className={styles.actions}><ActionButton label={t("ro.setCoverage")} busy={busy} reason={UUID_RE.test(cov.dept)&&UUID_RE.test(cov.template)&&/^\d{1,3}$/.test(cov.min)?null:t("disabled.input")}
     onClick={()=>void run(()=>call("POST","coverage",{body:{departmentId:cov.dept,templateId:cov.template,minStaff:Number(cov.min)}}),()=>setDone(t("ro.setCoverage")))}/></div>
   </section>
  </div>

  <div>
   <ActionError failure={failure} onReload={()=>{detail.reload();setFailure(null);}}/>
   {done&&<Notice>{done}</Notice>}
   <section className={styles.panel} aria-label={t("ro.shifts")} aria-live="polite">
    {!sel&&<p>{t("ro.select")}</p>}
    {sel&&detail.phase==="loading"&&!r&&<Loading/>}
    {sel&&detail.phase==="failed"&&detail.failure&&<FailureView failure={detail.failure} onRetry={detail.reload}/>}
    {r&&<>
     <h2><Badge tone={toneFor(r.status)}>{enumLabel(lang,"roster",r.status)}</Badge> {r.periodStart} → {r.periodEnd} · {t("ro.revision",{n:r.revision})}</h2>
     <div className={styles.actions}>
      <ActionButton label={t("ro.check")} tone="secondary" reason={r?null:t("disabled.select")} busy={busy} onClick={()=>void run<any>(()=>call("GET",`rosters/${r.id}/check`),d=>setChk(d))}/>
      <ActionButton label={t("ro.submit")} reason={why(["DRAFT"])} busy={busy} onClick={()=>void run(()=>call("POST",`rosters/${r.id}/submit`,{body:{expectedVersion:r.version}}),()=>setDone(t("ro.submit")))}/>
      <ActionButton label={t("ro.publish")} reason={why(["SUBMITTED"])} busy={busy} onClick={()=>void run(()=>call("POST",`rosters/${r.id}/review`,{body:{expectedVersion:r.version,approve:true}}),()=>setDone(t("ro.publish")))}/>
      <ActionButton label={t("common.reject")} tone="danger" reason={why(["SUBMITTED"])} onClick={()=>setRejecting(true)}/>
      <ActionButton label={t("ro.revise")} tone="secondary" reason={why(["PUBLISHED"])} busy={busy} onClick={()=>void run<any>(()=>call("POST",`rosters/${r.id}/revise`,{body:{expectedVersion:r.version}}),d=>{setSel(d.id);setDone(t("ro.revise"));})}/>
     </div>
     {rejecting&&<div className={styles.formGrid} style={{marginTop:12}}>
      <Field id="rr-n" label={t("ro.reviewNote")} wide><input id="rr-n" className={styles.input} autoFocus maxLength={300} value={note} onChange={e=>setNote(e.target.value)}/></Field>
      <div className={styles.actions}><ActionButton label={t("common.reject")} tone="danger" busy={busy} reason={note.trim()?null:t("err.reason_required")}
       onClick={()=>void run(()=>call("POST",`rosters/${r.id}/review`,{body:{expectedVersion:r.version,approve:false,note:note.trim()}}),()=>{setRejecting(false);setNote("");setDone(t("common.reject"));})}/>
       <button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={()=>setRejecting(false)}>{t("common.cancel")}</button></div></div>}
     {chk&&<div className={`${styles.banner} ${chk.ok?styles.bannerOk:styles.bannerWarn}`} role="status">
      <strong>{chk.ok?t("ro.ok"):t("ro.problems")}</strong> · {t("ro.vacancies",{n:chk.vacancies})}
      {chk.problems.length>0&&<ul>{chk.problems.map((p:string)=><li key={p}>{safetyLabel(lang,p.replace(/:[0-9a-f-]{36}.*$/,""))}</li>)}</ul>}</div>}
     <h3>{t("ro.shifts")}</h3>
     {(r.shifts??[]).length===0?<Empty hint={false}/>:<ul className={styles.list}>{r.shifts.map((x:any)=><li key={x.id} className={styles.card}>
      <strong>{x.templateCode}</strong> · {fmtTime(lang,x.startsAt)} → {fmtTime(lang,x.endsAt)}
      <div>{x.staffId?<>{x.familyName} <span className={styles.mono}>{x.staffNumber}</span></>:<Badge tone="warn">{t("ro.vacancy")}</Badge>}{x.overtime&&<> <Badge tone={x.overtimeAuthorizedBy?"ok":"danger"}>{t("ro.overtime")}</Badge></>}</div>
      <div className={styles.actions}>
       <ActionButton label={t("ro.authorize")} tone="secondary" busy={busy} reason={!x.overtime?t("disabled.input"):x.overtimeAuthorizedBy?t("ro.ok"):why(["DRAFT","SUBMITTED"])} onClick={()=>void run(()=>call("POST",`shifts/${x.id}/overtime-authorization`,{body:{}}),()=>setDone(t("ro.authorize")))}/>
       <ActionButton label={t("ro.remove")} tone="secondary" busy={busy} reason={why(["DRAFT"])} onClick={()=>void run(()=>call("POST",`shifts/${x.id}/remove`,{body:{}}),()=>setDone(t("ro.remove")))}/>
      </div></li>)}</ul>}
     <h3>{t("ro.addShift")}</h3>
     <div className={styles.formGrid}>
      <Field id="as-t" label={t("ro.template")}><select id="as-t" className={styles.input} value={sh.template} onChange={e=>setSh({...sh,template:e.target.value})}><option value="">—</option>{(tpls.data??[]).map((x:any)=><option key={x.id} value={x.id}>{x.code}</option>)}</select></Field>
      <Field id="as-d" label={t("common.date")}><input id="as-d" type="date" className={styles.input} value={sh.date} onChange={e=>setSh({...sh,date:e.target.value})}/></Field>
      <Field id="as-q" label={t("ro.staffOpt")} wide><input id="as-q" type="search" className={styles.input} value={staffQ} onChange={e=>{setStaffQ(e.target.value);setSh({...sh,staff:""});}}/></Field>
      {staffHits.data&&staffHits.data.items.length>0&&<ul className={`${styles.list} ${styles.wide}`} style={{gridColumn:"1/-1"}}>{staffHits.data.items.map((x:any)=><li key={x.id}>
       <button type="button" className={styles.row} aria-pressed={sh.staff===x.id} onClick={()=>setSh({...sh,staff:x.id})}>{x.familyName}, {x.givenName} <span className={styles.mono}>{x.staffNumber}</span></button></li>)}</ul>}
      <label className={styles.check}><input type="checkbox" checked={sh.ot} onChange={e=>setSh({...sh,ot:e.target.checked})}/>{t("ro.isOvertime")}</label>
     </div>
     <div className={styles.actions}><ActionButton label={t("ro.add")} reason={shiftReason} busy={busy}
      onClick={()=>void run(()=>call("POST",`rosters/${r.id}/shifts`,{body:{templateId:sh.template,staffId:sh.staff||null,date:sh.date,overtime:sh.ot}}),()=>setDone(t("ro.add")))}/></div>
    </>}
   </section>
  </div>
 </div>;
}
export function Roster(){return <HrFrame active="roster" title="ro.title" intro="ro.intro"><Inner/></HrFrame>;}
