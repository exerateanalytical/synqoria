"use client";
import Link from "next/link";
import {useRef,useState} from "react";
import {call,newKey,UUID_RE} from "../../lib/hr/client";
import type {Failure,Page,Result} from "../../lib/hr/client";
import {enumLabel} from "../../lib/hr/i18n";
import {ActionButton,ActionError,Badge,Empty,FailureView,Field,HrFrame,Loading,Notice,Pager,styles,toneFor,useHr,useLoad} from "./ui";

type Row={id:string;staffNumber:string;givenName:string;familyName:string;status:string;classification:string;departmentCode:string;positionTitle:string;hireDate:string};
type Org={departments:{id:string;code:string;name:string}[];positions:{id:string;code:string;title:string;departmentId:string}[]};

function Inner(){
 const {t,lang}=useHr();
 const [q,setQ]=useState("");const [dept,setDept]=useState("");const [status,setStatus]=useState("");const [page,setPage]=useState(0);
 const [f,setF]=useState({number:"",given:"",family:"",dept:"",pos:"",hire:new Date().toISOString().slice(0,10),cls:"STANDARD"});
 const [nd,setNd]=useState({code:"",name:""});const [np,setNp]=useState({code:"",title:"",dept:""});
 const [failure,setFailure]=useState<Failure|null>(null);const [done,setDone]=useState<string|null>(null);const [busy,setBusy]=useState(false);
 const key=useRef(newKey());
 const org=useLoad<Org>(s=>call("GET","organisation",{signal:s}),[done]);
 const list=useLoad<Page<Row>>(s=>call("GET","staff",{signal:s,query:{q:q.trim()||undefined,departmentId:dept||undefined,status:status||undefined,page,size:25}}),[q,dept,status,page,done]);
 async function run<T>(fn:()=>Promise<Result<T>>,ok?:()=>void){
  if(busy)return;setBusy(true);setFailure(null);setDone(null);
  const r=await fn();setBusy(false);
  if(!r.ok){setFailure(r);return;}
  ok?.();
 }
 const edit=(patch:Partial<typeof f>)=>{setF(x=>({...x,...patch}));key.current=newKey();setFailure(null);};
 const positions=(org.data?.positions??[]).filter(p=>!f.dept||p.departmentId===f.dept);
 const onboardReason=!/^[A-Z0-9_-]{1,32}$/.test(f.number)||!f.given.trim()||!f.family.trim()||!UUID_RE.test(f.dept)||!UUID_RE.test(f.pos)||!f.hire?t("disabled.input"):null;

 return <div className={styles.cols}>
  <section className={styles.panel} aria-label={t("dir.title")}>
   <div className={styles.filters}>
    <Field id="dr-q" label={t("dir.search")}><input id="dr-q" type="search" className={styles.input} maxLength={80} value={q} onChange={e=>{setQ(e.target.value);setPage(0);}}/></Field>
    <Field id="dr-d" label={t("common.department")}><select id="dr-d" className={styles.input} value={dept} onChange={e=>{setDept(e.target.value);setPage(0);}}>
     <option value="">{t("common.any")}</option>{(org.data?.departments??[]).map(d=><option key={d.id} value={d.id}>{d.code} — {d.name}</option>)}</select></Field>
    <Field id="dr-s" label={t("common.status")}><select id="dr-s" className={styles.input} value={status} onChange={e=>{setStatus(e.target.value);setPage(0);}}>
     <option value="">{t("common.any")}</option>{["ONBOARDING","ACTIVE","SUSPENDED","TERMINATED"].map(s=><option key={s} value={s}>{enumLabel(lang,"staff",s)}</option>)}</select></Field>
   </div>
   <p className={styles.hint}>{t("dir.confidentialNote")}</p>
   <div className={styles.actions}><button type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={list.reload}>{t("common.refresh")}</button></div>
   <div aria-busy={list.phase==="loading"} style={{marginTop:12}}>
    {list.phase==="loading"&&!list.data&&<Loading/>}
    {list.phase==="failed"&&list.failure&&<FailureView failure={list.failure} onRetry={list.reload}/>}
    {list.data&&list.data.items.length===0&&list.phase!=="failed"&&<Empty/>}
    {list.data&&list.data.items.length>0&&list.phase!=="failed"&&<>
     <div className={styles.tableWrap}><table className={styles.table}>
      <thead><tr><th scope="col">{t("dir.col.name")}</th><th scope="col">{t("dir.col.number")}</th><th scope="col">{t("dir.col.dept")}</th><th scope="col">{t("dir.col.status")}</th><th scope="col">{t("dir.col.hire")}</th><th scope="col">{t("common.actions")}</th></tr></thead>
      <tbody>{list.data.items.map(r=><tr key={r.id}>
       <th scope="row">{r.familyName}, {r.givenName}{r.classification==="CONFIDENTIAL"&&<> <Badge tone="warn">{enumLabel(lang,"class",r.classification)}</Badge></>}</th>
       <td className={styles.mono}>{r.staffNumber}</td><td>{r.departmentCode} · {r.positionTitle}</td>
       <td><Badge tone={toneFor(r.status)}>{enumLabel(lang,"staff",r.status)}</Badge></td><td>{r.hireDate}</td>
       <td><Link className={styles.link} href={`/workforce/hr/employment?staffId=${r.id}`}>{t("dir.open")}</Link></td></tr>)}</tbody></table></div>
     <ul className={styles.cards}>{list.data.items.map(r=><li key={r.id} className={styles.card}>
      <strong>{r.familyName}, {r.givenName}</strong> <span className={styles.mono}>{r.staffNumber}</span>
      <dl><dt>{t("dir.col.dept")}</dt><dd>{r.departmentCode} · {r.positionTitle}</dd><dt>{t("dir.col.status")}</dt><dd><Badge tone={toneFor(r.status)}>{enumLabel(lang,"staff",r.status)}</Badge></dd><dt>{t("dir.col.hire")}</dt><dd>{r.hireDate}</dd></dl>
      <Link className={styles.link} href={`/workforce/hr/employment?staffId=${r.id}`}>{t("dir.open")}</Link></li>)}</ul>
     <Pager page={list.data.page} size={list.data.size} total={list.data.total} onPage={setPage}/></>}
   </div>
  </section>

  <div>
   <ActionError failure={failure} onReload={()=>{setFailure(null);list.reload();}}/>
   {done&&<Notice>{done}</Notice>}
   <section className={styles.panel} aria-label={t("dir.onboard")}>
    <h2>{t("dir.onboard")}</h2>
    <form onSubmit={e=>e.preventDefault()} noValidate>
     <div className={styles.formGrid}>
      <Field id="on-n" label={t("dir.number")}><input id="on-n" className={`${styles.input} ${styles.mono}`} value={f.number} maxLength={32} onChange={e=>edit({number:e.target.value.trim().toUpperCase()})}/></Field>
      <Field id="on-h" label={t("dir.hire")}><input id="on-h" type="date" className={styles.input} value={f.hire} onChange={e=>edit({hire:e.target.value})}/></Field>
      <Field id="on-g" label={t("dir.given")}><input id="on-g" className={styles.input} value={f.given} maxLength={120} onChange={e=>edit({given:e.target.value})}/></Field>
      <Field id="on-f" label={t("dir.family")}><input id="on-f" className={styles.input} value={f.family} maxLength={120} onChange={e=>edit({family:e.target.value})}/></Field>
      <Field id="on-d" label={t("common.department")}><select id="on-d" className={styles.input} value={f.dept} onChange={e=>edit({dept:e.target.value,pos:""})}>
       <option value="">—</option>{(org.data?.departments??[]).map(d=><option key={d.id} value={d.id}>{d.code}</option>)}</select></Field>
      <Field id="on-p" label={t("common.position")}><select id="on-p" className={styles.input} value={f.pos} onChange={e=>edit({pos:e.target.value})}>
       <option value="">—</option>{positions.map(p=><option key={p.id} value={p.id}>{p.title}</option>)}</select></Field>
      <Field id="on-c" label={t("dir.classification")}><select id="on-c" className={styles.input} value={f.cls} onChange={e=>edit({cls:e.target.value})}>
       {["STANDARD","CONFIDENTIAL"].map(c=><option key={c} value={c}>{enumLabel(lang,"class",c)}</option>)}</select></Field>
     </div>
     <div className={styles.actions}><ActionButton label={t("dir.create")} reason={onboardReason} busy={busy}
      onClick={()=>void run(()=>call("POST","staff",{key:key.current,body:{staffNumber:f.number,givenName:f.given.trim(),familyName:f.family.trim(),departmentId:f.dept,positionId:f.pos,hireDate:f.hire,classification:f.cls,userRef:null}}),()=>{key.current=newKey();setF({...f,number:"",given:"",family:""});setDone(t("dir.create"));})}/></div>
    </form>
   </section>
   <section className={styles.panel} aria-label={t("dir.newDept")}>
    <h3>{t("dir.newDept")}</h3>
    <div className={styles.formGrid}>
     <Field id="nd-c" label={t("dir.code")}><input id="nd-c" className={styles.input} value={nd.code} maxLength={32} onChange={e=>setNd({...nd,code:e.target.value.trim().toUpperCase()})}/></Field>
     <Field id="nd-n" label={t("dir.name")}><input id="nd-n" className={styles.input} value={nd.name} maxLength={120} onChange={e=>setNd({...nd,name:e.target.value})}/></Field>
    </div>
    <div className={styles.actions}><ActionButton label={t("dir.addDept")} busy={busy} reason={/^[A-Z0-9_-]{1,32}$/.test(nd.code)&&nd.name.trim()?null:t("disabled.input")}
     onClick={()=>void run(()=>call("POST","departments",{body:{code:nd.code,name:nd.name.trim()}}),()=>{setNd({code:"",name:""});setDone(t("dir.addDept"));})}/></div>
    <h3>{t("dir.newPos")}</h3>
    <div className={styles.formGrid}>
     <Field id="np-c" label={t("dir.code")}><input id="np-c" className={styles.input} value={np.code} maxLength={32} onChange={e=>setNp({...np,code:e.target.value.trim().toUpperCase()})}/></Field>
     <Field id="np-t" label={t("dir.posTitle")}><input id="np-t" className={styles.input} value={np.title} maxLength={120} onChange={e=>setNp({...np,title:e.target.value})}/></Field>
     <Field id="np-d" label={t("common.department")} wide><select id="np-d" className={styles.input} value={np.dept} onChange={e=>setNp({...np,dept:e.target.value})}>
      <option value="">—</option>{(org.data?.departments??[]).map(d=><option key={d.id} value={d.id}>{d.code}</option>)}</select></Field>
    </div>
    <div className={styles.actions}><ActionButton label={t("dir.addPos")} busy={busy} reason={/^[A-Z0-9_-]{1,32}$/.test(np.code)&&np.title.trim()&&UUID_RE.test(np.dept)?null:t("disabled.input")}
     onClick={()=>void run(()=>call("POST","positions",{body:{code:np.code,title:np.title.trim(),departmentId:np.dept}}),()=>{setNp({code:"",title:"",dept:""});setDone(t("dir.addPos"));})}/></div>
   </section>
  </div>
 </div>;
}
export function Directory(){return <HrFrame active="directory" title="dir.title" intro="dir.intro"><Inner/></HrFrame>;}
