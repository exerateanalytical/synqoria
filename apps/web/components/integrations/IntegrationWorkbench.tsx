"use client";
import {useCallback,useEffect,useState} from "react";
import Link from "next/link";
import {api,uuid,code,hex,type Row} from "../../lib/integrations/client";
import styles from "../../app/workforce/integrations/integrations.module.css";

export type Screen="M35-S01"|"M35-S02"|"M35-S03"|"M35-S04"|"M35-S05"|"M35-S06";
type Locale="en"|"fr";
const screens:{id:Screen;en:string;fr:string}[]=[
 {id:"M35-S01",en:"Partner integration directory",fr:"Répertoire des partenaires"},
 {id:"M35-S02",en:"Developer API catalogue",fr:"Catalogue des API"},
 {id:"M35-S03",en:"Developer apps and credentials",fr:"Applications et identifiants développeur"},
 {id:"M35-S04",en:"Integration message monitor",fr:"Suivi des messages d'intégration"},
 {id:"M35-S05",en:"Mapping and contract validation",fr:"Validation des correspondances et contrats"},
 {id:"M35-S06",en:"Quarantine and replay review",fr:"Quarantaine et réexamen des reprises"}
];
const languages={
 en:{language:"Language",refresh:"Refresh",work:"Real scoped control-plane records",select:"Select",
  status:"Status",created:"Created",type:"Type",none:"No authorized records found.",
  code:"Partner code",partnerType:"Partner type",partner:"Partner UUID",proof:"External KYB proof UUID",
  createPartner:"Register partner",verify:"Verify independent KYB",enable:"Enable verified partner",
  suspend:"Suspend",retire:"Retire",apiCode:"API code",apiVersion:"API version",
  digest:"Contract SHA-256",publish:"Publish reviewed API product",createApi:"Register API product",
  product:"API product UUID",appCode:"Developer application code",appRequest:"Request application",
  appApprove:"Approve application (no credentials)",source:"Source system",target:"Target system",
  mapCode:"Mapping code",mapVersion:"Mapping version",createMap:"Propose mapping",
  validate:"Validate mapping",approve:"Approve independently",event:"Event type",
  message:"Quarantined message UUID",reviewMessage:"Accept independently reviewed metadata",
  replay:"Request controlled replay review",reason:"Reason code",reviewReplay:"Approve replay review only",
  denyReplay:"Deny replay",selected:"Selected record",more:"Record details",
  error:"Action denied: ",done:"Server accepted this action.",invalid:"Check required UUIDs, codes and SHA-256 digests.",
  session:"Hospital login required.",forbidden:"Your role cannot access these records.",
  unavailable:"Integration management service unavailable.",busy:"Saving",noIssue:"NO API SECRETS ARE ISSUED HERE.",
  noReplay:"Replay approval does not dispatch a network message.",
  warning:"This is an integration governance service. Production credentials, live network connectors, external message issuers and clinical interoperability require independent approval.",
  notCertified:"No real partner credential, clinical message delivery or replay has been executed."},
 fr:{language:"Langue",refresh:"Actualiser",work:"Données réelles de gouvernance autorisées",select:"Sélectionner",
  status:"État",created:"Créé",type:"Type",none:"Aucun enregistrement autorisé.",
  code:"Code du partenaire",partnerType:"Type de partenaire",partner:"UUID du partenaire",proof:"UUID de preuve KYB externe",
  createPartner:"Enregistrer le partenaire",verify:"Vérifier le KYB indépendant",enable:"Activer le partenaire vérifié",
  suspend:"Suspendre",retire:"Retirer",apiCode:"Code de l'API",apiVersion:"Version de l'API",
  digest:"Empreinte SHA-256 du contrat",publish:"Publier l'API approuvée",createApi:"Enregistrer l'API",
  product:"UUID du produit API",appCode:"Code de l'application",appRequest:"Demander une application",
  appApprove:"Approuver sans identifiants",source:"Système source",target:"Système cible",
  mapCode:"Code de correspondance",mapVersion:"Version de correspondance",createMap:"Proposer la correspondance",
  validate:"Valider",approve:"Approuver indépendamment",event:"Type d'événement",
  message:"UUID du message en quarantaine",reviewMessage:"Valider des métadonnées indépendantes",
  replay:"Demander une révision de reprise",reason:"Code du motif",reviewReplay:"Approuver la révision seulement",
  denyReplay:"Refuser la reprise",selected:"Sélection",more:"Détails",
  error:"Action refusée : ",done:"Le serveur a accepté l'action.",invalid:"Vérifiez UUID, codes et empreintes SHA-256.",
  session:"Connexion hospitalière requise.",forbidden:"Votre rôle ne permet pas cet accès.",
  unavailable:"Service de gouvernance indisponible.",busy:"Enregistrement",noIssue:"AUCUN SECRET API N'EST ÉMIS ICI.",
  noReplay:"Une approbation ne déclenche pas de reprise réseau.",
  warning:"Ce service contrôle l'intégration. Les identifiants de production, connecteurs, émetteurs de messages et transferts cliniques exigent une approbation indépendante.",
  notCertified:"Aucun identifiant réel, livraison clinique ou reprise réseau n'a été exécuté."}
} as const;
type Fields={partnerCode:string;partnerType:string;proofId:string;apiCode:string;
 apiVersion:string;contractSha256:string;partnerId:string;apiProductId:string;
 appCode:string;mapCode:string;mapVersion:string;sourceSystem:string;
 targetSystem:string;messageId:string;reasonCode:string};
const initial:Fields={partnerCode:"",partnerType:"HOSPITAL",proofId:"",apiCode:"",
 apiVersion:"V1",contractSha256:"",partnerId:"",apiProductId:"",appCode:"",
 mapCode:"",mapVersion:"V1",sourceSystem:"FHIR",targetSystem:"LIS",
 messageId:"",reasonCode:"TRANSIENT_FAILURE"};
function Field({label,value,onChange}:{label:string;value:string;onChange:(v:string)=>void}){
 return <label className={styles.field}>{label}<input autoComplete="off"
  spellCheck={false} value={value} onChange={e=>onChange(e.target.value)}/></label>;
}
export function IntegrationWorkbench({screen}:{screen:Screen}){
 const [lang,setLang]=useState<Locale>("en");const t=languages[lang];
 const [fields,setFields]=useState<Fields>(initial);
 const [rows,setRows]=useState<Row[]>([]);
 const [messageRows,setMessageRows]=useState<Row[]>([]);
 const [selected,setSelected]=useState<Row|null>(null);
 const [state,setState]=useState<"loading"|"ready"|"signin"|"forbidden"|"error">("loading");
 const [notice,setNotice]=useState("");
 const [busy,setBusy]=useState(false);
 const current=screens.find(s=>s.id===screen)!;
 const table=screen==="M35-S01"?"partners":screen==="M35-S02"?"api_products":
  screen==="M35-S03"?"developer_apps":screen==="M35-S04"?"messages":
  screen==="M35-S05"?"mapping_versions":"replay_requests";
 const change=(field:keyof Fields)=>(value:string)=>setFields(s=>({...s,[field]:value}));
 const refresh=useCallback(async()=>{
  const result=await api<Row[]>("GET",table,{query:{limit:50}});
  if(!result.ok){
   setRows([]);setSelected(null);setNotice(result.error);
   setState(result.status===401?"signin":result.status===403?"forbidden":"error");
   return;
  }
  setRows(result.data);
  setSelected(previous=>result.data.find(x=>x.id===previous?.id)||null);
  if(screen==="M35-S06"){
   const messages=await api<Row[]>("GET","messages",{query:{limit:50}});
   if(messages.ok)setMessageRows(messages.data);else{
    setState(messages.status===403?"forbidden":"error");setNotice(messages.error);return;
   }
  }
  setState("ready");
 },[table,screen]);
 useEffect(()=>{void refresh();},[refresh]);
 const run=async(path:string,body:object)=>{
  if(busy)return;
  setBusy(true);setNotice("");
  try{
   const result=await api<{id:string;status:string;version:number}>("POST",path,{body});
   if(!result.ok){setNotice(t.error+result.error.replaceAll("_"," "));return;}
   await refresh();setNotice(t.done+" ["+result.data.status+"]");
  }finally{setBusy(false);}
 };
 const submit=()=>{
  switch(screen){
   case "M35-S01":
    if(!code.test(fields.partnerCode)){setNotice(t.invalid);return;}
    return void run("partners",{partnerCode:fields.partnerCode,partnerType:fields.partnerType});
   case "M35-S02":
    if(!code.test(fields.apiCode)||!hex.test(fields.contractSha256)){setNotice(t.invalid);return;}
    return void run("api_products",{apiCode:fields.apiCode,apiVersion:fields.apiVersion,
      contractSha256:fields.contractSha256});
   case "M35-S03":
    if(!uuid.test(fields.partnerId)||!uuid.test(fields.apiProductId)||!code.test(fields.appCode)){
     setNotice(t.invalid);return;
    }
    return void run("developer_apps",{partnerId:fields.partnerId,
     apiProductId:fields.apiProductId,appCode:fields.appCode});
   case "M35-S05":
    if(!code.test(fields.mapCode)||!code.test(fields.sourceSystem)||!code.test(fields.targetSystem)||
     !hex.test(fields.contractSha256)){setNotice(t.invalid);return;}
    return void run("mapping_versions",{mapCode:fields.mapCode,mapVersion:fields.mapVersion,
      contractSha256:fields.contractSha256,sourceSystem:fields.sourceSystem,
      targetSystem:fields.targetSystem});
   case "M35-S06":
    if(!uuid.test(fields.messageId)||!code.test(fields.reasonCode)){setNotice(t.invalid);return;}
    return void run("replay_requests",{messageId:fields.messageId,reasonCode:fields.reasonCode});
  }
 };
 const act=(command:string,proof=false,review?:boolean)=>{
  if(!selected){setNotice(t.invalid);return;}
  if(proof&&!uuid.test(fields.proofId)){setNotice(t.invalid);return;}
  const payload=review===undefined?{expectedVersion:selected.version,proofId:proof?fields.proofId:null}:
   {expectedVersion:selected.version,approve:review};
  void run(table+"/"+selected.id+"/"+command,payload);
 };
 return <section className={styles.root}>
  <div className={styles.head}><div><p className={styles.eyebrow}>SYNQORIA · M35 · {screen}</p>
   <h1>{current[lang]}</h1></div>
   <label className={styles.lang}>{t.language}<select value={lang}
    onChange={e=>setLang(e.target.value as Locale)}>
    <option value="en">English</option><option value="fr">Français</option></select></label></div>
  <p className={styles.warning}>{t.warning}</p>
  <nav className={styles.nav} aria-label="Integration management workstations">{screens.map(s=>
   <Link key={s.id} href={"/workforce/integrations/"+s.id}
    aria-current={screen===s.id?"page":undefined}>{s.id} — {s[lang]}</Link>)}</nav>
  <div className={styles.head}><h2>{t.work}</h2>
   <button disabled={busy} type="button" onClick={()=>void refresh()}>{t.refresh}</button></div>
  {state==="loading"&&<p role="status">Loading / Chargement</p>}
  {state==="signin"&&<p role="alert">{t.session}</p>}
  {state==="forbidden"&&<p role="alert">{t.forbidden}</p>}
  {state==="error"&&<p role="alert">{t.unavailable} ({notice})</p>}
  {state==="ready"&&<div className={styles.panel}>
   {!rows.length?<p role="status">{t.none}</p>:
    <div className={styles.tableWrap}><table className={styles.table}>
     <thead><tr><th>{t.select}</th><th>{t.status}</th><th>{t.created}</th><th>{t.type}</th></tr></thead>
     <tbody>{rows.map(x=><tr key={x.id}>
      <td><button type="button" aria-pressed={selected?.id===x.id}
       onClick={()=>setSelected(x)}>{x.id.slice(0,8)}</button></td>
      <td>{x.status}</td><td>{new Date(x.created_at).toLocaleString(lang)}</td>
      <td>{x.partner_type||x.event_type||x.api_code||x.map_code||x.app_code||"—"}</td></tr>)}</tbody>
    </table></div>}
   {selected&&<details className={styles.details}><summary>{t.more}: {selected.id}</summary>
    <dl>{Object.entries(selected).map(([k,v])=><div key={k}>
     <dt>{k.replaceAll("_"," ")}</dt><dd>{String(v??"—")}</dd></div>)}</dl></details>}
  </div>}
  {state==="ready"&&screen!=="M35-S04"&&<div className={styles.panel}>
   <h2>{current[lang]}</h2>
   <form onSubmit={e=>{e.preventDefault();submit();}}>
    <div className={styles.grid}>
     {screen==="M35-S01"&&<>
      <Field label={t.code} value={fields.partnerCode} onChange={change("partnerCode")}/>
      <label className={styles.field}>{t.partnerType}<select value={fields.partnerType}
       onChange={e=>change("partnerType")(e.target.value)}>
       {["HOSPITAL","LABORATORY","PAYER","GOVERNMENT","VENDOR"].map(s=>
        <option key={s}>{s}</option>)}</select></label></>}
     {screen==="M35-S02"&&<>
      <Field label={t.apiCode} value={fields.apiCode} onChange={change("apiCode")}/>
      <Field label={t.apiVersion} value={fields.apiVersion} onChange={change("apiVersion")}/>
      <Field label={t.digest} value={fields.contractSha256} onChange={change("contractSha256")}/></>}
     {screen==="M35-S03"&&<>
      <Field label={t.partner} value={fields.partnerId} onChange={change("partnerId")}/>
      <Field label={t.product} value={fields.apiProductId} onChange={change("apiProductId")}/>
      <Field label={t.appCode} value={fields.appCode} onChange={change("appCode")}/></>}
     {screen==="M35-S05"&&<>
      <Field label={t.mapCode} value={fields.mapCode} onChange={change("mapCode")}/>
      <Field label={t.mapVersion} value={fields.mapVersion} onChange={change("mapVersion")}/>
      <Field label={t.source} value={fields.sourceSystem} onChange={change("sourceSystem")}/>
      <Field label={t.target} value={fields.targetSystem} onChange={change("targetSystem")}/>
      <Field label={t.digest} value={fields.contractSha256} onChange={change("contractSha256")}/></>}
     {screen==="M35-S06"&&<>
      <Field label={t.message} value={fields.messageId} onChange={change("messageId")}/>
      <Field label={t.reason} value={fields.reasonCode} onChange={change("reasonCode")}/></>}
    </div>
    <button type="submit" disabled={busy}>{screen==="M35-S01"?t.createPartner:
     screen==="M35-S02"?t.createApi:screen==="M35-S03"?t.appRequest:
     screen==="M35-S05"?t.createMap:t.replay}</button>
   </form>
   {screen==="M35-S01"&&selected&&<>
    <Field label={t.proof} value={fields.proofId} onChange={change("proofId")}/>
    <div className={styles.actions}>
     {selected.status==="PENDING"&&<button disabled={busy} type="button"
      onClick={()=>act("verify",true)}>{t.verify}</button>}
     {selected.status==="VERIFIED"&&<button disabled={busy} type="button"
      onClick={()=>act("enable")}>{t.enable}</button>}
     {selected.status==="ENABLED"&&<button disabled={busy} type="button"
      onClick={()=>act("suspend")}>{t.suspend}</button>}
     {selected.status==="SUSPENDED"&&<button disabled={busy} type="button"
      onClick={()=>act("retire")}>{t.retire}</button>}
    </div></>}
   {screen==="M35-S02"&&selected?.status==="DRAFT"&&<button disabled={busy} type="button"
    onClick={()=>act("publish")}>{t.publish}</button>}
   {screen==="M35-S03"&&<>
    <p className={styles.warning}>{t.noIssue}</p>
    {selected?.status==="REQUESTED"&&<button disabled={busy} type="button"
     onClick={()=>act("approve")}>{t.appApprove}</button>}
    {selected?.status==="APPROVED"&&<button disabled={busy} type="button"
     onClick={()=>act("suspend")}>{t.suspend}</button>}
   </>}
   {screen==="M35-S05"&&<div className={styles.actions}>
    {selected?.status==="DRAFT"&&<button disabled={busy} type="button"
     onClick={()=>act("validate")}>{t.validate}</button>}
    {selected?.status==="VALIDATED"&&<button disabled={busy} type="button"
     onClick={()=>act("approve")}>{t.approve}</button>}
   </div>}
   {screen==="M35-S06"&&<>
    <h2>{t.message}</h2><div className={styles.tableWrap}><table className={styles.table}>
     <thead><tr><th>{t.select}</th><th>{t.event}</th><th>{t.status}</th></tr></thead>
     <tbody>{messageRows.filter(m=>m.status==="QUARANTINED").map(m=><tr key={m.id}>
      <td><button type="button" onClick={()=>change("messageId")(m.id)}>{m.id.slice(0,8)}</button></td>
      <td>{m.event_type}</td><td>{m.status}</td></tr>)}</tbody>
    </table></div>
    {selected?.status==="REQUESTED"&&<div className={styles.actions}>
     <button disabled={busy} type="button" onClick={()=>act("review",false,true)}>{t.reviewReplay}</button>
     <button disabled={busy} type="button" onClick={()=>act("review",false,false)}>{t.denyReplay}</button>
    </div>}
    <p className={styles.warning}>{t.noReplay}</p>
   </>}
  </div>}
  {state==="ready"&&screen==="M35-S04"&&<div className={styles.panel}>
   <p className={styles.warning}>{t.warning}</p>
   {selected?.status==="QUARANTINED"&&<button disabled={busy} type="button"
    onClick={()=>act("review")}>{t.reviewMessage}</button>}
  </div>}
  {notice&&state==="ready"&&<p className={styles.notice} role="status" aria-live="polite">{notice}</p>}
  <p className={styles.footer}>{t.notCertified}</p>
 </section>;
}
