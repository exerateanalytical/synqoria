"use client";
import {useEffect,useRef,useState} from "react";
import {call,contentChecksum,newKey,parseConceptCsv} from "../../lib/content/client";
import type {Failure,Page} from "../../lib/content/client";
import {enumLabel,safetyLabel} from "../../lib/content/i18n";
import {ActionButton,ActionError,Badge,ContentFrame,Empty,FailureView,Field,fmtTime,Loading,Notice,Pager,styles,toneFor,useContent,useLoad} from "./ui";

type Sys={id:string;systemUri:string;name:string;licensingClass:string;rejectedImports:number;effectiveVersion:null|{id:string;versionLabel:string;conceptCount:number}};
type Hit={id:string;code:string;status:string;displayEn:string;displayFr:string|null;parentCode:string|null;versionId:string;versionLabel:string;systemUri:string};
type Tab="browse"|"import"|"mappings";
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function Inner(){
 const {t,lang}=useContent();
 const [tab,setTab]=useState<Tab>("browse");
 const [sys,setSys]=useState<Sys|null>(null);
 const [q,setQ]=useState("");const [status,setStatus]=useState("");const [inactive,setInactive]=useState(false);const [page,setPage]=useState(0);
 const [pick,setPick]=useState<{versionId:string;code:string}|null>(null);
 const [failure,setFailure]=useState<Failure|null>(null);const [done,setDone]=useState<string|null>(null);const [busy,setBusy]=useState(false);
 const [reg,setReg]=useState({uri:"",name:"",licensing:"OPEN",licenseRef:""});
 const [imp,setImp]=useState({label:"",from:new Date().toISOString().slice(0,16),provenance:"",csv:"code,status,displayEn,displayFr,parent,synonyms\n"});
 const [sum,setSum]=useState("");
 const [map,setMap]=useState({targetVersion:"",targetCode:"",relation:"EQUIVALENT"});
 const [mapStatus,setMapStatus]=useState("PROPOSED");const [mapPage,setMapPage]=useState(0);
 const regKey=useRef(newKey());const impKey=useRef(newKey());

 const systems=useLoad<Page<Sys>>(s=>call("GET","code-systems",{signal:s,query:{page:0,size:100}}),[]);
 const hits=useLoad<Page<Hit>>(s=>sys?call("GET","concepts",{signal:s,query:{systemId:sys.id,q:q.trim()||undefined,status:status||undefined,includeInactive:inactive||undefined,page,size:25}}):Promise.resolve({ok:true,status:200,data:{items:[],page:0,size:25,total:0}}),[sys?.id,q,status,inactive,page]);
 const concept=useLoad<any>(s=>pick?call("GET",`code-system-versions/${pick.versionId}/concepts/${pick.code}`,{signal:s}):Promise.resolve({ok:false,kind:"notfound",status:0,code:"none",details:[]} as Failure),[pick?.versionId,pick?.code]);
 const versions=useLoad<any[]>(s=>sys?call("GET",`code-systems/${sys.id}/versions`,{signal:s}):Promise.resolve({ok:true,status:200,data:[]}),[sys?.id,done]);
 const maps=useLoad<Page<any>>(s=>call("GET","mappings",{signal:s,query:{status:mapStatus||undefined,page:mapPage,size:25}}),[mapStatus,mapPage,done]);

 const parsed=parseConceptCsv(imp.csv);
 useEffect(()=>{let live=true;if(parsed.problems.length===0&&parsed.concepts.length>0)contentChecksum(parsed.concepts).then(h=>{if(live)setSum(h);});else setSum("");return()=>{live=false;};},[imp.csv]);

 async function run<T>(fn:()=>Promise<import("../../lib/content/client").Result<T>>,ok?:(d:T)=>void){
  if(busy)return;setBusy(true);setFailure(null);setDone(null);
  const r=await fn();setBusy(false);
  if(!r.ok){setFailure(r);return;}
  ok?.(r.data);
 }
 const regReason=!/^[a-z][a-z0-9+.-]*:[A-Za-z0-9._~:/%-]{2,255}$/.test(reg.uri)||!reg.name.trim()?t("disabled.input"):reg.licensing==="LICENSED_RESTRICTED"&&!reg.licenseRef.trim()?t("err.license_reference_required"):null;
 const impReason=!sys?t("disabled.select"):!/^[A-Za-z0-9._-]{1,64}$/.test(imp.label)||!imp.provenance.trim()||parsed.problems.length>0||parsed.concepts.length===0||!sum?t("disabled.input"):null;
 const mapReason=!pick?t("disabled.select"):!UUID.test(map.targetVersion)||!/^[A-Za-z0-9._-]{1,64}$/.test(map.targetCode)?t("disabled.input"):null;
 const failed=failure&&failure.code==="import_rejected"?failure.details:[];

 return <>
  <div role="tablist" aria-label={t("tm.title")} className={styles.tabs}>
   {([["browse","tm.concepts"],["import","tm.import"],["mappings","tm.mapList"]] as const).map(([id,k])=>
    <button key={id} role="tab" id={`tm-tab-${id}`} aria-selected={tab===id} aria-controls={`tm-panel-${id}`} type="button" className={styles.tab} onClick={()=>setTab(id)}>{t(k)}</button>)}
  </div>
  <p className={styles.hint}>{t("tm.licenseNotice")}</p>
  <ActionError failure={failure&&failure.code!=="import_rejected"?failure:null} onReload={()=>{setFailure(null);}}/>
  {failure&&failure.code==="import_rejected"&&<div className={`${styles.banner} ${styles.bannerDanger}`} role="alert"><strong>{t("err.import_rejected")}</strong>
   <ul>{failed.slice(0,50).map((d,i)=><li key={i}>{safetyLabel(lang,d)}</li>)}</ul></div>}
  {done&&<Notice>{done}</Notice>}
  <div className={styles.cols}>
   <section className={styles.panel} aria-label={t("tm.systems")}>
    <h2>{t("tm.systems")}</h2>
    {systems.phase==="loading"&&!systems.data&&<Loading/>}
    {systems.phase==="failed"&&systems.failure&&<FailureView failure={systems.failure} onRetry={systems.reload}/>}
    {systems.data&&systems.data.items.length===0&&<Empty/>}
    {systems.data&&systems.data.items.length>0&&<ul className={styles.list}>{systems.data.items.map(s=><li key={s.id}>
     <button type="button" className={styles.row} aria-pressed={sys?.id===s.id} onClick={()=>{setSys(s);setPage(0);setPick(null);}}>
      <strong>{s.name}</strong><span className={`${styles.hint} ${styles.mono}`}>{s.systemUri}</span>
      <span className={styles.badges}><Badge tone="neutral">{enumLabel(lang,"licensing",s.licensingClass)}</Badge>
       {s.effectiveVersion?<Badge tone="ok">{t("tm.effective")}: {s.effectiveVersion.versionLabel}</Badge>:<Badge tone="warn">{t("tm.noVersion")}</Badge>}
       {s.rejectedImports>0&&<Badge tone="danger">{t("tm.rejectedImports",{n:s.rejectedImports})}</Badge>}</span>
     </button></li>)}</ul>}
    <h3>{t("tm.newSystem")}</h3>
    <form onSubmit={e=>{e.preventDefault();}} noValidate>
     <div className={styles.formGrid}>
      <Field id="rs-uri" label={t("tm.uri")} wide><input id="rs-uri" className={`${styles.input} ${styles.mono}`} value={reg.uri} onChange={e=>{setReg({...reg,uri:e.target.value.trim()});regKey.current=newKey();}}/></Field>
      <Field id="rs-name" label={t("tm.name")}><input id="rs-name" className={styles.input} value={reg.name} maxLength={200} onChange={e=>{setReg({...reg,name:e.target.value});regKey.current=newKey();}}/></Field>
      <Field id="rs-lic" label={t("tm.licensing")}><select id="rs-lic" className={styles.input} value={reg.licensing} onChange={e=>{setReg({...reg,licensing:e.target.value});regKey.current=newKey();}}>
       {["OPEN","INTERNAL","LICENSED_RESTRICTED"].map(x=><option key={x} value={x}>{enumLabel(lang,"licensing",x)}</option>)}</select></Field>
      {reg.licensing==="LICENSED_RESTRICTED"&&<Field id="rs-ref" label={t("tm.licenseRef")} wide><input id="rs-ref" className={styles.input} value={reg.licenseRef} maxLength={200} onChange={e=>{setReg({...reg,licenseRef:e.target.value});regKey.current=newKey();}}/></Field>}
     </div>
     <div className={styles.actions}><ActionButton label={t("tm.createSystem")} reason={regReason} busy={busy} onClick={()=>void run(()=>call("POST","code-systems",{key:regKey.current,body:{systemUri:reg.uri,name:reg.name.trim(),licensingClass:reg.licensing,licenseRef:reg.licensing==="LICENSED_RESTRICTED"?reg.licenseRef.trim():null}}),()=>{regKey.current=newKey();setReg({uri:"",name:"",licensing:"OPEN",licenseRef:""});systems.reload();})}/></div>
    </form>
   </section>

   <div>
    {tab==="browse"&&<section className={styles.panel} role="tabpanel" id="tm-panel-browse" aria-labelledby="tm-tab-browse">
     {!sys&&<p>{t("tm.select")}</p>}
     {sys&&<>
      <div className={styles.filters}>
       <Field id="tm-q" label={t("tm.search")}><input id="tm-q" type="search" className={styles.input} value={q} maxLength={100} onChange={e=>{setQ(e.target.value);setPage(0);}}/></Field>
       <Field id="tm-st" label={t("tm.status")}><select id="tm-st" className={styles.input} value={status} onChange={e=>{setStatus(e.target.value);setPage(0);}}>
        <option value="">{t("common.any")}</option>{["ACTIVE","INACTIVE","DEPRECATED"].map(x=><option key={x} value={x}>{enumLabel(lang,"cstatus",x)}</option>)}</select></Field>
       <label className={styles.check}><input type="checkbox" checked={inactive} onChange={e=>{setInactive(e.target.checked);setPage(0);}}/>{t("tm.includeInactive")}</label>
      </div>
      <div aria-busy={hits.phase==="loading"} style={{marginTop:12}}>
       {hits.phase==="loading"&&!hits.data&&<Loading/>}
       {hits.phase==="failed"&&hits.failure&&<FailureView failure={hits.failure} onRetry={hits.reload}/>}
       {hits.data&&hits.data.items.length===0&&hits.phase!=="failed"&&<Empty/>}
       {hits.data&&hits.data.items.length>0&&hits.phase!=="failed"&&<>
        <ul className={styles.list}>{hits.data.items.map(h=><li key={h.id}>
         <button type="button" className={styles.row} aria-pressed={pick?.code===h.code&&pick?.versionId===h.versionId} onClick={()=>setPick({versionId:h.versionId,code:h.code})}>
          <span><span className={styles.mono}>{h.code}</span> <Badge tone={toneFor(h.status)}>{enumLabel(lang,"cstatus",h.status)}</Badge></span>
          <span>{lang==="fr"&&h.displayFr?h.displayFr:h.displayEn}</span>
         </button></li>)}</ul>
        <Pager page={hits.data.page} size={hits.data.size} total={hits.data.total} onPage={setPage}/></>}
      </div>
     </>}
    </section>}

    {tab==="browse"&&pick&&<section className={styles.panel} aria-label={t("common.details")} aria-live="polite">
     {concept.phase==="loading"&&!concept.data&&<Loading/>}
     {concept.phase==="failed"&&concept.failure&&<FailureView failure={concept.failure} onRetry={concept.reload}/>}
     {concept.data&&<>
      <h2><span className={styles.mono}>{concept.data.code}</span> <Badge tone={toneFor(concept.data.status)}>{enumLabel(lang,"cstatus",concept.data.status)}</Badge></h2>
      <dl className={styles.kv}>
       <dt>{t("tm.labelEn")}</dt><dd>{concept.data.displayEn}</dd><dt>{t("tm.labelFr")}</dt><dd>{concept.data.displayFr??"—"}</dd>
       <dt>{t("tm.synonyms")}</dt><dd>{(concept.data.synonyms as string[]).join(", ")||t("common.none")}</dd>
       <dt>{t("tm.ancestors")}</dt><dd>{(concept.data.ancestors as any[]).map(a=><button key={a.code} type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={()=>setPick({versionId:pick.versionId,code:a.code})}>{a.code}</button>)}{(concept.data.ancestors as any[]).length===0&&t("common.none")}</dd>
       <dt>{t("tm.children")}</dt><dd>{(concept.data.children as any[]).map(a=><button key={a.code} type="button" className={`${styles.btn} ${styles.btnSecondary}`} onClick={()=>setPick({versionId:pick.versionId,code:a.code})}>{a.code}</button>)}{(concept.data.children as any[]).length===0&&t("common.none")}</dd>
       <dt>{t("tm.mappings")}</dt><dd>{(concept.data.mappings as any[]).length===0?t("tm.noMappings"):<ul className={styles.list}>{(concept.data.mappings as any[]).map(m=><li key={m.id}>{enumLabel(lang,"relation",m.relation)} → <span className={styles.mono}>{m.targetCode}</span> {m.targetDisplay} <span className={styles.hint}>{m.targetSystem}</span></li>)}</ul>}</dd>
      </dl>
      <h3>{t("tm.mapPropose")}</h3>
      <div className={styles.formGrid}>
       <Field id="mp-v" label={t("tm.mapTarget")}><input id="mp-v" className={`${styles.input} ${styles.mono}`} value={map.targetVersion} onChange={e=>setMap({...map,targetVersion:e.target.value.trim()})}/></Field>
       <Field id="mp-c" label={t("tm.mapTargetCode")}><input id="mp-c" className={styles.input} value={map.targetCode} onChange={e=>setMap({...map,targetCode:e.target.value.trim()})}/></Field>
       <Field id="mp-r" label={t("tm.mapRelation")}><select id="mp-r" className={styles.input} value={map.relation} onChange={e=>setMap({...map,relation:e.target.value})}>
        {["EQUIVALENT","NARROWER","BROADER","RELATED"].map(x=><option key={x} value={x}>{enumLabel(lang,"relation",x)}</option>)}</select></Field>
      </div>
      <div className={styles.actions}><ActionButton label={t("tm.mapPropose")} reason={mapReason} busy={busy} onClick={()=>void run(()=>call("POST","mappings",{body:{sourceVersionId:pick.versionId,sourceCode:pick.code,targetVersionId:map.targetVersion,targetCode:map.targetCode,relation:map.relation}}),()=>{setDone(t("tm.mapPropose"));maps.reload();})}/></div>
     </>}
    </section>}

    {tab==="import"&&<section className={styles.panel} role="tabpanel" id="tm-panel-import" aria-labelledby="tm-tab-import">
     <h2>{t("tm.import")}{sys?` — ${sys.name}`:""}</h2>
     {!sys&&<p>{t("tm.select")}</p>}
     {sys&&<form onSubmit={e=>e.preventDefault()} noValidate>
      <div className={styles.formGrid}>
       <Field id="im-l" label={t("tm.versionLabel")}><input id="im-l" className={styles.input} value={imp.label} maxLength={64} onChange={e=>{setImp({...imp,label:e.target.value.trim()});impKey.current=newKey();}}/></Field>
       <Field id="im-f" label={t("tm.effectiveFrom")}><input id="im-f" type="datetime-local" className={styles.input} value={imp.from} onChange={e=>{setImp({...imp,from:e.target.value});impKey.current=newKey();}}/></Field>
       <Field id="im-p" label={t("tm.provenance")} wide><input id="im-p" className={styles.input} value={imp.provenance} maxLength={500} onChange={e=>{setImp({...imp,provenance:e.target.value});impKey.current=newKey();}}/></Field>
       <Field id="im-c" label={t("tm.csv")} wide><textarea id="im-c" className={`${styles.input} ${styles.mono}`} rows={8} value={imp.csv} onChange={e=>{setImp({...imp,csv:e.target.value});impKey.current=newKey();}}/></Field>
      </div>
      <p className={styles.hint}>{t("tm.checksum")}: <span className={styles.mono}>{sum||"—"}</span> · {parsed.concepts.length}</p>
      {parsed.problems.length>0&&<div className={`${styles.banner} ${styles.bannerWarn}`} role="status"><strong>{t("tm.csvProblems")}</strong><ul>{parsed.problems.slice(0,10).map((p,i)=><li key={i}>{p}</li>)}</ul></div>}
      <div className={styles.actions}><ActionButton label={t("tm.importAction")} reason={impReason} busy={busy}
       onClick={()=>void run(()=>call("POST",`code-systems/${sys.id}/imports`,{key:impKey.current,body:{versionLabel:imp.label,declaredChecksum:sum,effectiveFrom:new Date(imp.from).toISOString(),provenance:imp.provenance.trim(),concepts:parsed.concepts}}),()=>{impKey.current=newKey();setDone(t("tm.importDone"));systems.reload();hits.reload();})}/></div>
     </form>}
     {sys&&<><h3>{t("tm.versions")}</h3>
      {versions.data&&<ul className={styles.list}>{versions.data.map((v:any)=><li key={v.id} className={styles.card}>
       <Badge tone={toneFor(v.status)}>{enumLabel(lang,"vstatus",v.status)}</Badge> <strong>{v.versionLabel}</strong> · {v.conceptCount} · {fmtTime(lang,v.effectiveFrom)}
       <div className={styles.hint}>{v.provenance}</div>
       {(v.rejectionCodes??[]).length>0&&<div className={styles.badges}>{(v.rejectionCodes as string[]).slice(0,6).map(c=><Badge key={c} tone="danger">{safetyLabel(lang,c)}</Badge>)}</div>}</li>)}</ul>}</>}
    </section>}

    {tab==="mappings"&&<section className={styles.panel} role="tabpanel" id="tm-panel-mappings" aria-labelledby="tm-tab-mappings">
     <h2>{t("tm.mapList")}</h2>
     <Field id="mm-st" label={t("tm.status")}><select id="mm-st" className={styles.input} value={mapStatus} onChange={e=>{setMapStatus(e.target.value);setMapPage(0);}}>
      <option value="">{t("common.any")}</option>{["PROPOSED","APPROVED","REJECTED"].map(x=><option key={x} value={x}>{enumLabel(lang,"mapping",x)}</option>)}</select></Field>
     {maps.phase==="loading"&&!maps.data&&<Loading/>}
     {maps.phase==="failed"&&maps.failure&&<FailureView failure={maps.failure} onRetry={maps.reload}/>}
     {maps.data&&maps.data.items.length===0&&maps.phase!=="failed"&&<Empty message="state.empty" hint={false}/>}
     {maps.data&&maps.data.items.length>0&&<><ul className={styles.list}>{maps.data.items.map((m:any)=><li key={m.id} className={styles.card}>
      <Badge tone={toneFor(m.status)}>{enumLabel(lang,"mapping",m.status)}</Badge> <span className={styles.mono}>{m.sourceCode} → {m.targetCode}</span> {enumLabel(lang,"relation",m.relation)}
      <div className={styles.actions}>
       <ActionButton label={t("tm.approve")} reason={m.status==="PROPOSED"?null:t("disabled.status",{status:enumLabel(lang,"mapping",m.status)})} busy={busy} onClick={()=>void run(()=>call("POST",`mappings/${m.id}/decision`,{body:{expectedVersion:m.version,approve:true}}),()=>maps.reload())}/>
       <ActionButton label={t("tm.reject")} tone="secondary" reason={m.status==="PROPOSED"?null:t("disabled.status",{status:enumLabel(lang,"mapping",m.status)})} busy={busy} onClick={()=>void run(()=>call("POST",`mappings/${m.id}/decision`,{body:{expectedVersion:m.version,approve:false}}),()=>maps.reload())}/>
      </div></li>)}</ul><Pager page={maps.data.page} size={maps.data.size} total={maps.data.total} onPage={setMapPage}/></>}
    </section>}
   </div>
  </div>
 </>;
}
export function Terminology(){return <ContentFrame active="terminology" title="tm.title" intro="tm.intro"><Inner/></ContentFrame>;}
